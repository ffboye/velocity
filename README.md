# Velocity

A native Android GPS speedometer and local journey recorder, built with Kotlin and Jetpack Compose. The app opens directly to its speedometer. Secure GitHub updates are integrated into Settings and use [ffboye/velocity](https://github.com/ffboye/velocity).

## Use the app

1. Open Velocity and tap **Start journey** or **Enable GPS access**. Grant **precise** location while using the app and enable device location. GPS works best outdoors; a cold fix may take some time. The display uses real device GPS, with no simulated readings in the app.
2. Speed is live when Velocity tracking is on, even before recording. Start a journey to measure distance, recorded time, average speed, maximum speed, and a route. **Pause** excludes paused time and movement. Stationary time while recording counts in the average. **Finish & save** lets you add a name and notes.
3. Recording uses a location foreground service and a visible notification so it can continue with the screen off or while another app is open. Its notification can finish and save the trip. Notification permission is optional on Android 13+; granting it makes recording easier to control.
4. Open **Journeys** for saved statistics, seven-day distance summaries, weighted average speed, editable names/notes and speed-over-recorded-time graphs. Graphs are recorded for new trips; older journeys retain their original data and show when no graph is available. Open a journey to view its street map, export GPX or delete it after confirmation.
5. **Settings** controls km/h or mph, keeping the speedometer screen awake, and an optional visual speed reminder. It also links to Android's permission settings.
6. **GPS** shows live satellites in view, those used by the receiver, signal readings in dB-Hz, a north-up sky view, location accuracy, fix age and location source. Satellite reports and fused location are separate: a valid location can be available even when satellite details are not reported. Missing or stale satellite data is shown as unavailable, never invented as zero. Multi-band observations of a satellite count once.

7. **Map** displays real streets and the selected journey's route, start and finish. Separate route sections preserve reception gaps. Optional offline downloads default off: enable them, pan/zoom to select an area, review the estimate and storage requirement, and download. Wi-Fi-only downloads default on. Pause, resume, view or delete an area without deleting journeys. Keep Velocity open during downloads.

The five tabs share a minimal colour palette, flat surfaces and consistent navigation. During a journey, pause/resume and finish controls remain available on every tab. Android Back returns to the speedometer. **GPS > Velocity tracking** stops this app's location requests and pauses an active journey; turning it back on never resumes automatically. Switching Android device location off/on reconnects the receiver while preserving the journey and route gaps.

All journeys are stored in the app's private storage. Internet access retrieves map resources from OpenFreeMap and update metadata/APKs from GitHub. Your routes are drawn locally and are never uploaded; the map provider receives the requested geographic area. There are no accounts or analytics. Automatic cloud backup is excluded. Settings > Back up journeys includes names, notes and graph samples; restoring merges without replacing existing journeys. GPX exports include the journey name and notes. Uninstalling removes local data, so verify a backup before switching signing keys.

Maps use MapLibre Native and [OpenFreeMap](https://openfreemap.org/quick_start/), with OpenStreetMap attribution. Offline selection is limited to small areas (at most 900 estimated tiles, zooms 6–14, a 40 MB resource-size guard), with at most four HTTP requests and an application-specific User-Agent. Estimates are approximate; the completed resource size is shown in the app. Up to ten offline areas can be retained. Downloads pause in the background or when the selected network policy is not met, and never restart automatically after process death.

## Automatic updates

Android's fused location service may separately use Google Location Accuracy, according to the phone's location settings. Satellite diagnostics are held in memory only. Velocity does not upload journeys or diagnostics.

Velocity checks the configured public GitHub repository on launch without blocking startup. Settings contains automatic-check controls and Check for Updates. Updates authenticate a detached signed manifest before using it; APK size, SHA-256, package, version, SDK, ABI, and installed signing certificate are verified. Android asks for installation approval, and installation is blocked while a journey is recording or paused.

See [the setup and publishing guide](docs/UPDATES.md). Private signing keys are not in this repository. Install the permanent release-signed baseline before using release-channel updates. An update-enabled debug APK is provided for safely exporting existing journeys before the one-time signing-key switch.

## Recording and recovery

GPS fixes older than five seconds, poor-accuracy fixes, duplicate/out-of-order fixes, and implausible position jumps are rejected. Stationary jitter is filtered from trip distance. A long signal gap starts a separate route segment rather than joining across missing data. Speed returns to zero when a fix becomes stale. Long route traces are simplified in memory while distance statistics remain independent.

History and active-trip checkpoints use Android AtomicFile on an IO dispatcher. Checkpoints are written every ten seconds and at recording state changes. Update installation waits for pending saves; failed writes show a persistent Retry save action and block installation until a retry succeeds. If Android terminates the process, the next launch recovers the last checkpoint as an interrupted saved journey; the app never silently resumes recording. At most the portion since the last successful checkpoint can be lost. Paused time is excluded from average speed. Device manufacturers may still stop foreground services under aggressive battery restrictions.

## Build and test on Windows

Use Android Studio with the existing SDK configuration, or PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat formatKotlin
.\gradlew.bat checkKotlinFormat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# With an emulator or Android device connected:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. The original application ID is retained so signed releases update existing installations. The debug build can replace earlier debug APKs and export a complete backup; production releases use the permanent release keystore.

Tests cover GPS-derived speed and units, real distance and timing, pause/resume, stale and inaccurate fixes, GPS jumps, jitter, signal gaps, new-trip reset, JSON persistence, GPX segments, and app navigation. An emulator can validate permission/service/UI behavior and injected GPS data; real-world accuracy and manufacturer-specific background behavior should also be checked outdoors on a phone.

The recording integration test feeds Android's test GPS provider, records movement while the Activity is backgrounded, checks pause/resume, finishes through the UI, and verifies saved JSON on device. It also checks the real journey/installer interlock. Test GPS injection exists only in instrumentation, never in the application. Updater tests exercise signatures, metadata validation, file checks, APK identity, cancellation, interruptions, cache reuse, and installation handoff. The release generator derives metadata from built APKs and has separate Node tests.
