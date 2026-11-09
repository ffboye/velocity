import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const serial = process.env.ANDROID_SERIAL;
if (!/^emulator-\d+$/.test(serial ?? '')) throw Error('Select an isolated emulator with ANDROID_SERIAL');
if (!process.env.APK_PATH) throw Error('Set APK_PATH to the signed release APK');
const adb = process.env.ADB_PATH || 'adb';
const pkg = 'com.example.myapplication';
const output = process.env.SMOKE_OUTPUT_DIR || 'build/release-smoke';
mkdirSync(output, { recursive: true });
const call = (...args) => execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 60000 });
const pause = () => new Promise(resolve => setTimeout(resolve, 1000));
function healthy() {
  const crashes = call('logcat', '-b', 'crash', '-d');
  writeFileSync(`${output}/crash.txt`, crashes);
  if (crashes.includes(`Process: ${pkg}`) || crashes.includes(`Cmdline: ${pkg}`) || crashes.includes(`>>> ${pkg} <<<`)) throw Error(`Signed APK crashed. See ${output}/crash.txt`);
  if (!call('shell', 'pidof', pkg).trim()) throw Error('Signed APK process exited');
}
async function screen(text, scroll = false) {
  for (let attempt = 0; attempt < 30; attempt++) {
    await pause();
    healthy();
    try {
      call('shell', 'uiautomator', 'dump', '/sdcard/velocity-release-smoke.xml');
      const xml = call('shell', 'cat', '/sdcard/velocity-release-smoke.xml');
      writeFileSync(`${output}/screen.xml`, xml);
      if (xml.includes(`text="${text}"`) || xml.includes(`content-desc="${text}"`)) return xml;
      if (scroll) {
        const sizes = [...call('shell', 'wm', 'size').matchAll(/(\d+)x(\d+)/g)];
        const size = sizes.at(-1);
        if (!size) throw Error('Cannot read emulator screen dimensions');
        call('shell', 'input', 'swipe', String(Math.round(+size[1] / 2)), String(Math.round(+size[2] * 0.75)), String(Math.round(+size[1] / 2)), String(Math.round(+size[2] * 0.3)), '250');
      }
    } catch (error) { if (attempt === 29) throw error; }
  }
  throw Error(`Signed APK did not show ${text}`);
}
function tap(xml, text) {
  for (const node of xml.matchAll(/<node\b[^>]*>/g)) {
    const attributes = Object.fromEntries([...node[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
    if ((attributes.text !== text && attributes['content-desc'] !== text) || attributes.package !== pkg) continue;
    const bounds = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(attributes.bounds);
    if (!bounds) throw Error('Invalid navigation bounds');
    call('shell', 'input', 'tap', String(Math.round((+bounds[1] + +bounds[3]) / 2)), String(Math.round((+bounds[2] + +bounds[4]) / 2)));
    return;
  }
  throw Error(`Navigation control missing: ${text}`);
}

call('install', '-r', resolve(process.env.APK_PATH));
call('shell', 'am', 'force-stop', pkg);
call('logcat', '-c');
call('shell', 'am', 'start', '-W', '-n', `${pkg}/.MainActivity`);
const home = await screen('Start journey');
// Startup automatically exercises the updater transport that failed in 1.3.0.
for (let i = 0; i < 10; i++) { await pause(); healthy(); }
if (home.includes('text="Map"')) throw Error('The removed Map tab is still present');
tap(home, 'Settings');
tap(await screen('Enable offline map downloads', true), 'Enable offline map downloads');
const settings = await screen('Choose area to download', true);
call('shell', 'screencap', '-p', '/data/local/tmp/velocity-signed-settings.png');
call('pull', '/data/local/tmp/velocity-signed-settings.png', `${output}/settings.png`);
tap(settings, 'Choose area to download');
await screen('Choose offline area');
for (let i = 0; i < 8; i++) { await pause(); healthy(); }
call('shell', 'screencap', '-p', '/data/local/tmp/velocity-signed-picker.png');
call('pull', '/data/local/tmp/velocity-signed-picker.png', `${output}/area-picker.png`);
call('shell', 'input', 'keyevent', '4');
await screen('Choose area to download');
call('shell', 'input', 'keyevent', '4');
await screen('Start journey');
healthy();
console.log('Signed release passed startup, automatic-update transport, offline Settings, map area selection and return-to-home checks.');
