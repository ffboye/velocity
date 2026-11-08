# Release quality and GPS diagnostics

Velocity 1.3.0 keeps the existing application ID, backward-compatible journey format and signing certificate. Existing files without names, notes or graph samples load with empty defaults; old routes are preserved. No uninstall is needed for release-signed installations.

## Satellite data

Satellite readings come from Android's GNSS status callback using AndroidX compatibility APIs. The receiver reports satellite ID, constellation, C/N₀, elevation, azimuth and use in its latest fix. Multiple frequencies for one satellite are counted once; the strongest signal and use on either frequency are retained. Invalid readings are excluded. A report older than ten seconds is no longer displayed as live. No report is represented by a dash, while a fresh zero-satellite report is represented by zero.

Fused location remains active alongside raw GPS. Its location accuracy and fix age are independent of the GNSS report. Velocity does not infer Wi-Fi/mobile contributions, percentage signal scores, or satellite counts from a fused fix. Satellite data stays in memory and is not saved or uploaded.

## Recording and storage

Listeners are removed when idle in the background and restored when the activity returns. A foreground recording retains its location listeners. Permission loss or device location being switched off clears live readings and the distance anchor. Reconnection starts a new route segment while retaining journey statistics. Late callbacks from old registrations are ignored.

Active journey controls remain visible on all four tabs. Saves are serialized and conflated, with revisions preventing an older completed write from marking newer changes as saved. Update installation is blocked while recording, paused, loading, saving, or after a failed save. A persistent save error offers Retry save; installation becomes available after the retry succeeds.

## Verification

Version 1.3 adds actual online/offline street-map rendering tests, download deletion that retains journeys, network-policy and region-size bounds, old-file compatibility, metadata and graph backup round-trips, GPX escaping, weighted averages, graph gap/pause timing, tracking off/on and editable names/notes. Offline verification disables the map SDK's network connectivity and checks that downloaded features render. Fixture routes and native mock data exist only in instrumentation.

GPS quality is Good for accepted fixes up to 20 m and three seconds old; accepted less accurate/older fixes and fresh coarse observations are Weak. After five seconds an accepted fix is no longer used. No fresh location is Searching; app tracking, permission or device location off is Off. Satellite signal strength remains separate. Tracking off pauses manually and clears readings; it does not change Android's device-wide setting.

Recorded time includes stationary time while recording and excludes manual pauses. Average speed is total distance divided by recorded seconds. There is no automatic stop detection or auto-pause. Speed samples use recorded elapsed time and segment IDs, are downsampled on long trips, and never reconstruct older journeys from positions or timestamps.

Automated checks cover satellite normalization and freshness, Android GNSS report adaptation, live/unavailable diagnostic UI, four-tab navigation and Back, fused location without raw GPS, background recording, device location off/on reconnection, pause/finish and disk persistence, pending-save installation blocking, failed-write recovery, backups, GPX export and updater integrity. Instrumentation uses isolated test data and Android mock providers; no demonstration satellites are included in the app.

The release workflow runs formatting, unit tests, lint and signed APK/manifest verification before publication. Real satellite reception, battery consumption and Samsung-specific background behavior still require outdoor physical-device checks. Passing emulator tests does not establish those properties.
