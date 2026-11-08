# Velocity

A native Android GPS speedometer and local journey recorder, built with Kotlin and Jetpack Compose. The app opens directly to its speedometer. Secure GitHub updates are integrated into Settings and use [ffboye/velocity](https://github.com/ffboye/velocity).

## Use the app

1. Open Velocity and tap **Start journey** or **Enable GPS access**. Grant **precise** location while using the app and enable device location. GPS works best outdoors; a cold fix may take some time. The display uses real device GPS, with no simulated readings in the app.
2. Speed is live even before recording. Start a journey to measure distance, active time, average speed, maximum speed, and a route trace. **Pause** excludes paused time and movement. **Finish & save** stores it in Journeys.
3. Recording uses a location foreground service and a visible notification so it can continue with the screen off or while another app is open. Its notification can finish and save the trip. Notification permission is optional on Android 13+; granting it makes recording easier to control.
4. Open **Journeys** for saved statistics and offline, north-up route traces. Open a journey to export a standards-based GPX file through Android's file picker, or delete it after confirmation. Route traces do not download street-map tiles.
5. **Settings** controls km/h or mph, keeping the speedometer screen awake, and an optional visual speed reminder. It also links to Android's permission settings.

All journeys are stored in the app's private storage. Internet access is used for GitHub update metadata and release downloads; journeys are never uploaded. There are no accounts or analytics. Automatic cloud backup of journey data is excluded. Use Settings > Back up journeys to keep all routes and statistics, or GPX export for an individual route. Restoring a Velocity backup merges journeys without replacing existing records. Uninstalling removes local data, so verify your backup before switching from a debug-signed build.

## Automatic updates

Velocity checks the configured public GitHub repository on launch without blocking startup. Settings contains automatic-check controls and Check for Updates. Updates authenticate a detached signed manifest before using it; APK size, SHA-256, package, version, SDK, ABI, and installed signing certificate are verified. Android asks for installation approval, and installation is blocked while a journey is recording or paused.

See [the setup and publishing guide](docs/UPDATES.md). Private signing keys are not in this repository. Install the permanent release-signed baseline before using release-channel updates. An update-enabled debug APK is provided for safely exporting existing journeys before the one-time signing-key switch.

## Recording and recovery

GPS fixes older than five seconds, poor-accuracy fixes, duplicate/out-of-order fixes, and implausible position jumps are rejected. Stationary jitter is filtered from trip distance. A long signal gap starts a separate route segment rather than joining across missing data. Speed returns to zero when a fix becomes stale. Long route traces are simplified in memory while distance statistics remain independent.

History and active-trip checkpoints use Android AtomicFile on an IO dispatcher. Checkpoints are written every ten seconds and at recording state changes. If Android terminates the process, the next launch recovers the last checkpoint as an interrupted saved journey; the app never silently resumes recording. At most the portion since the last successful checkpoint can be lost. Paused time is excluded from average speed. Device manufacturers may still stop foreground services under aggressive battery restrictions.

## Build and test on Windows

Use Android Studio with the existing SDK configuration, or PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat formatKotlin
.\gradlew.bat checkKotlinFormat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# With an emulator or Android device connected:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Version 1.1.1, code 3 keeps the original application ID. The debug build can replace the earlier debug APK and export a complete backup; production releases use the permanent release keystore.

Tests cover GPS-derived speed and units, real distance and timing, pause/resume, stale and inaccurate fixes, GPS jumps, jitter, signal gaps, new-trip reset, JSON persistence, GPX segments, and app navigation. An emulator can validate permission/service/UI behavior and injected GPS data; real-world accuracy and manufacturer-specific background behavior should also be checked outdoors on a phone.

The recording integration test feeds Android's test GPS provider, records movement while the Activity is backgrounded, checks pause/resume, finishes through the UI, and verifies saved JSON on device. It also checks the real journey/installer interlock. Test GPS injection exists only in instrumentation, never in the application. Updater tests exercise signatures, metadata validation, file checks, APK identity, cancellation, interruptions, cache reuse, and installation handoff. The release generator derives metadata from built APKs and has separate Node tests.
