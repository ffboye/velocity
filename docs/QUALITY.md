# Release quality and GPS diagnostics

Velocity keeps the existing application ID, backward-compatible journey format and signing certificate. Existing files without names, notes or graph samples load with empty defaults; old routes are preserved. No uninstall is needed for release-signed installations.

## Satellite data

Satellite readings come from Android's GNSS status callback using AndroidX compatibility APIs. The receiver reports satellite ID, constellation, C/N₀, elevation, azimuth and use in its latest fix. Multiple frequencies for one satellite are counted once; the strongest signal and use on either frequency are retained. Invalid readings are excluded. A report older than ten seconds is no longer displayed as live. No report is represented by a dash, while a fresh zero-satellite report is represented by zero.

Fused location remains active alongside raw GPS. Its location accuracy and fix age are independent of the GNSS report. Velocity does not infer Wi-Fi/mobile contributions, percentage signal scores, or satellite counts from a fused fix. Satellite data stays in memory and is not saved or uploaded.

## Recording and storage

Listeners are removed when idle in the background and restored when the activity returns. A foreground recording retains its location listeners. Permission loss or device location being switched off clears live readings and the distance anchor. Reconnection starts a new route segment while retaining journey statistics. Late callbacks from old registrations are ignored.

Active journey controls remain visible on every tab. Saves are serialized and conflated, with revisions preventing an older completed write from marking newer changes as saved. Update installation is blocked while recording, paused, loading, saving, or after a failed save. A persistent save error offers Retry save; installation becomes available after the retry succeeds.

## Verification

Motion uses finite Compose animations and Android's motion duration scale. Primary actions and tabs use standard touch feedback without flags that bypass system preferences. The motion regression runs with a controlled duration scale, checks press/cancel/release and disabled actions, rapidly switches tabs while animations are in flight, and checks navigation with motion disabled. Existing layout and recording tests also run with system animations disabled. GPS quality and speed values are never interpolated or inferred for animation; satellite bars only animate the visual transition to the reported strength.

The spacing audit renders the app with display-only journey, GPS, offline-map and update fixtures at standard text size and at 150% text with Easy reading enabled. It checks visible controls for unintended overlap and text for accidental layout overflow, allowing intentional title ellipses. Captures cover speed and metrics, permission recovery, recording/paused controls, empty and populated journey lists, route maps, statistics, editors with/without the keyboard, delete confirmations, Settings, privacy, the offline guide/picker/download list, GPS details and update phases. The fixtures never write journeys or download/install updates. Screenshot artifacts are reviewed alongside the existing real recording, map rendering and persistence tests.

The readability regression verifies the default Start journey button is at least 64 dp high, enables Easy reading through Settings, recreates the Activity and checks the saved preference. On an isolated emulator it temporarily sets Android text size to 150%, checks larger controls and navigation remain visible, and verifies that the offline Download area and Done actions remain reachable without scrolling. The test restores device and app settings afterwards and captures normal/large-text screenshots. Physical phones are excluded from this device-setting test.

Version 1.3 adds actual online/offline street-map rendering tests, download deletion that retains journeys, network-policy and region-size bounds, old-file compatibility, metadata and graph backup round-trips, GPX escaping, weighted averages, graph gap/pause timing, tracking off/on and editable names/notes. Offline verification disables the map SDK's network connectivity and checks that downloaded features render. Fixture routes and native mock data exist only in instrumentation.

GPS quality is Good for accepted fixes up to 20 m and three seconds old; accepted less accurate/older fixes and fresh coarse observations are Weak. After five seconds an accepted fix is no longer used. No fresh location is Searching; app tracking, permission or device location off is Off. Satellite signal strength remains separate. Tracking off pauses manually and clears readings; it does not change Android's device-wide setting.

Recorded time includes stationary time while recording and excludes manual pauses. Average speed is total distance divided by recorded seconds. There is no automatic stop detection or auto-pause. Speed samples use recorded elapsed time and segment IDs, are downsampled on long trips, and never reconstruct older journeys from positions or timestamps.

Automated checks cover satellite normalization and freshness, Android GNSS report adaptation, live/unavailable diagnostic UI, four-tab navigation and Back, fused location without raw GPS, background recording, device location off/on reconnection, pause/finish and disk persistence, pending-save installation blocking, failed-write recovery, backups, GPX export and updater integrity. Instrumentation uses isolated test data and Android mock providers; no demonstration satellites are included in the app.

The checks workflow runs formatting, unit tests, lint, Android instrumentation and process-death recovery on main and pull requests. Releases repeat those checks, then install the exact signed APK and test startup, automatic-update transport, offline Settings and map area selection before publication. Artifacts contain test reports, bounded emulator diagnostics and synthetic-data screenshots, never signing keys.

The map regression opens a real journey's embedded route, verifies rendered street and route features, downloads an area, switches downloads off and disables the map SDK's connectivity, then recreates the journey map and verifies streets and route features again. It deletes the download through Settings and checks that the journey remains. Navigation checks confirm that the Map tab is absent and the offline guide and area picker are reachable in Settings. Screenshots are captured on remote test emulators for visual review; local emulators need not be started.

## Process-death regression

Run on a disposable emulator with the debug app installed. This starts a journey, waits for a real ten-second background checkpoint, kills the app process, and relaunches twice. It verifies exact checkpoint recovery, no duplicate history, no automatic recording resume, and preservation of existing journeys. It refuses physical-device serials and never clears app data.

```powershell
$env:ANDROID_SERIAL = 'emulator-5556'
$env:ADB_PATH = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
.\gradlew.bat :app:connectedDebugAndroidTest
& $env:ADB_PATH -s $env:ANDROID_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
node scripts/check-recording-recovery.mjs
```

The release CI emulator is disposable; only there is the debug package uninstalled before testing the differently signed release APK. A user's phone must receive release updates over the installed app.

## Physical-phone release checklist

Emulator checks do not establish real satellite reception, battery consumption or Samsung-specific background behavior. On the S24 Ultra:

- Install over the previous release and confirm existing names, notes, graphs and routes remain.
- Start outdoors, lock the screen for at least 15 minutes, then confirm recording time and route continued. Compare battery use under similar conditions rather than assuming an improvement.
- Turn device location off and on; confirm clear signal feedback and a route gap without a distance jump.
- Pause, background, resume and finish; confirm paused time is excluded from the average.
- Try denied/revoked location permission and weak reception; confirm recovery controls remain usable.
- Enable offline support only when wanted, download a small area, then test its map in airplane mode. Check pause/delete controls.
- Export and restore a backup, confirming names, notes, graphs and routes. Check large text and TalkBack on the main controls.

Record device/Android version, app version and observed results. Do not label unperformed phone checks as passed.
