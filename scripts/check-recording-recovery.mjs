import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';

// This test changes app state and kills its process. Never target a phone or a daily-use emulator.
const serial = process.env.ANDROID_SERIAL;
if (!/^emulator-\d+$/.test(serial ?? '')) throw Error('Select an isolated test emulator with ANDROID_SERIAL');
const adb = process.env.ADB_PATH || 'adb';
const pkg = 'com.example.myapplication';
const output = process.env.RECOVERY_OUTPUT_DIR || 'build/recording-recovery';
mkdirSync(output, { recursive: true });
const call = (...args) => execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 30000, stdio: ['ignore', 'pipe', 'pipe'] });
const pause = () => new Promise(resolve => setTimeout(resolve, 1000));
async function until(description, read, accept, seconds = 35) {
  let lastError;
  for (let attempt = 0; attempt < seconds; attempt++) {
    try {
      const value = read();
      if (accept(value)) return value;
    } catch (error) { lastError = error; }
    await pause();
  }
  throw Error(`Timed out: ${description}`, { cause: lastError });
}
const launch = () => call('shell', 'am', 'start', '-W', '-n', `${pkg}/.MainActivity`);
const saved = () => JSON.parse(call('shell', 'run-as', pkg, 'cat', 'files/journeys.json'));
const xml = () => {
  call('shell', 'uiautomator', 'dump', '/sdcard/velocity-recovery.xml');
  const value = call('shell', 'cat', '/sdcard/velocity-recovery.xml');
  writeFileSync(`${output}/screen.xml`, value);
  return value;
};
const screen = text => until(`screen ${text}`, xml, value => value.includes(`text="${text}"`));
function tap(value, text) {
  for (const node of value.matchAll(/<node\b[^>]*>/g)) {
    const attributes = Object.fromEntries([...node[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
    if (attributes.text !== text || attributes.package !== pkg) continue;
    const bounds = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(attributes.bounds);
    if (!bounds) throw Error('Invalid recording control bounds');
    call('shell', 'input', 'tap', String(Math.round((+bounds[1] + +bounds[3]) / 2)), String(Math.round((+bounds[2] + +bounds[4]) / 2)));
    return;
  }
  throw Error(`Control missing: ${text}`);
}

try {
  // run-as requires a debuggable build. No clear-data, uninstall, or storage replacement is used.
  call('shell', 'run-as', pkg, 'id');
  call('shell', 'am', 'force-stop', pkg);
  for (const permission of ['ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION']) {
    call('shell', 'pm', 'grant', pkg, `android.permission.${permission}`);
  }
  if (+call('shell', 'getprop', 'ro.build.version.sdk').trim() >= 33) {
    call('shell', 'pm', 'grant', pkg, 'android.permission.POST_NOTIFICATIONS');
  }
  call('shell', 'cmd', 'location', 'set-location-enabled', 'true');
  launch();
  const home = await screen('Start journey');
  // A clean install may not have a file until the first save.
  let existing = [];
  if (call('shell', 'run-as', pkg, 'ls', 'files').split(/\s+/).includes('journeys.json')) {
    const before = saved();
    assert.ok(!before.active, 'Previous active checkpoint has not finished recovering');
    existing = before.journeys;
  }
  tap(home, 'Start journey');
  await screen('Pause');
  call('shell', 'input', 'keyevent', '3');
  const checkpoint = await until('background ten-second recording checkpoint', saved, value => value.active?.durationMillis >= 10000);
  const active = checkpoint.active;
  assert.ok(!existing.some(trip => trip.id === active.id), 'Recording ID was reused');
  const pid = call('shell', 'pidof', pkg).trim();
  assert.match(pid, /^\d+$/, 'Expected exactly one app process');
  call('shell', 'run-as', pkg, 'kill', '-9', pid);
  await until('recording process exit', () => {
    try { return call('shell', 'pidof', pkg).trim(); } catch { return ''; }
  }, value => value === '');
  // A save may have completed between our first read and the kill. Compare recovery
  // with the final durable checkpoint, rather than racing the service's timer.
  const atDeath = saved().active;
  assert.equal(atDeath?.id, active.id);
  assert.ok(atDeath.durationMillis >= active.durationMillis);
  launch();
  await screen('Start journey');
  const recovered = await until('interrupted checkpoint saved', saved, value => !value.active && value.journeys.some(trip => trip.id === active.id));
  const restored = recovered.journeys.filter(trip => trip.id === active.id);
  assert.equal(restored.length, 1);
  assert.deepEqual(restored[0], { ...atDeath, interrupted: true });
  for (const trip of existing) assert.deepEqual(recovered.journeys.find(value => value.id === trip.id), trip);
  assert.equal(recovered.journeys.length, existing.length + 1);
  call('shell', 'am', 'force-stop', pkg);
  launch();
  await screen('Start journey');
  assert.deepEqual(saved(), recovered, 'Second launch duplicated or changed the recovered journey');
  console.log('Background checkpoint survived process death; recovered exactly once without auto-resuming or altering history.');
} catch (error) {
  try { writeFileSync(`${output}/crash.txt`, call('logcat', '-b', 'crash', '-d')); } catch { /* Keep the original failure. */ }
  throw error;
}
