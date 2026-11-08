# Velocity automatic updates

The real app continues to launch on its speedometer. Settings > App Updates is connected to the public repository **ffboye/velocity**, using a pinned RSA public key in `config/update-public-key.txt`. `config/android-signing-sha256.txt` pins the permanent APK release certificate for CI.

## Finish the one-time GitHub setup

Run this command **from this project in PowerShell 7**:

```powershell
pwsh -File .\scripts\configure-github.ps1
```

It installs GitHub CLI if needed, lets you sign in through GitHub's own login flow, and sends the five locally generated signing values directly to **ffboye/velocity Actions secrets**. It does not print their contents or commit them. You need admin access to that repository. The connected GitHub coding tools do not expose a secrets-management API, so this is a local user step.

Secrets configured by the helper:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `MANIFEST_PRIVATE_KEY_BASE64`

Private files are in `%LOCALAPPDATA%\Velocity\Signing`, restricted to your Windows account and SYSTEM. `velocity-release.p12` is the permanent APK signing keystore; `manifest-key.pem` signs release manifests. `password.dpapi` encrypts the keystore password for the current Windows account. **Do not generate replacement keys after distributing the baseline.** Back up the key material securely. DPAPI alone is not a portable password backup. You can copy an individual value to your password manager without printing it:

```powershell
pwsh -File .\scripts\configure-github.ps1 -CopySecret ANDROID_KEYSTORE_PASSWORD
```

Clear the clipboard after storing the value securely. Keep private keys and portable credentials out of source control and chat. The helper refuses to overwrite existing manifest keys.

## Switch the existing debug installation safely

The earlier Velocity 1.1.0 APK was debug-signed. Android will reject a release-signed APK installed over it because the signing certificates differ. **Do not uninstall it if you have journeys without a verified backup.**

1. Install **Velocity-1.1.1-migration-debug.apk** over the earlier debug build. This uses the same local debug keystore and retains its data.
2. Finish any active journey. Open Settings > Back up journeys and save the JSON file somewhere outside Velocity's private storage. Keep it until the restored data is checked.
3. Confirm the file is present. Uninstall the debug app only when ready for this one-time switch, then install **Velocity-1.1.1-release.apk** (or `velocity.apk` from the signed first release).
4. Open Settings > Restore journeys and choose that JSON backup. Check the saved routes and statistics in Journeys.

Restoration accepts bounded, validated Velocity backups up to 16 MB and 1000 journeys. Existing journeys are retained when IDs overlap. All future same-key, same-package, higher-versionCode releases upgrade normally and preserve application data. Debug and production APKs share the original ID `com.example.myapplication`; they are not side-by-side apps. The migration debug build cannot install production-channel updates, because certificate matching deliberately rejects them.

## Publish the initial and future releases

After the source and workflow are on `main` and secrets are configured:

1. Open GitHub > Actions > **Publish signed release** > Run workflow on `main`. This publishes the version in `version.properties`; the prepared baseline is **1.1.1, code 3**.
2. For subsequent updates, increase **both** the appropriate `versionName` and integer `versionCode`, add real notes in `release-notes/<versionName>.md`, and commit to `main`.
3. Run the workflow again, or push a matching version tag such as `v1.1.2` after setting those actual values in the project. A code must strictly increase above the previous signed release.

The workflow installs Java 25, Android platform `android-37.0`, Build Tools 36.0.0, and Node 22; checks formatting; runs Android lint and JUnit tests; builds a signed release APK; verifies its signature and certificate fingerprint; extracts the actual package/version/minSdk with aapt2; calculates actual SHA-256 and size; generates schema-1 `update.json`; and signs its exact UTF-8 bytes with RSA PKCS#1 v1.5/SHA-256. `update.json.sig` is a detached **binary** signature. It checks previous signed metadata for increasing versionCode and a stable package ID.

The APK, manifest, signature, and notes are uploaded to a **draft** release, downloaded again for byte comparison, then published together. A failed run may leave a draft for investigation; never publish an incomplete draft. Inspect/remove a failed draft before retrying. Existing published releases are never overwritten automatically. No release should be published before signing secrets are configured.

## Local signed build

```powershell
pwsh -File .\scripts\build-release.ps1
```

This opens the protected local signing configuration and builds `app/build/outputs/apk/release/app-release.apk`. Passwords are passed through process environment variables, never command-line literals. Only the public key and public certificate fingerprint are tracked.

## Runtime security and behavior

- Automatic checks default on, run once per application process/session, and never delay the first screen. A dismissed version is not prompted again automatically within that session; manual checks can reopen it. A missing initial GitHub release is shown as “No update has been published” for manual checks.
- Metadata is fetched over HTTPS from the exact configured GitHub repository. API redirects are rejected; release-asset redirects are restricted to the exact `release-assets.githubusercontent.com` host, with a bounded redirect count. User-info URLs, alternate ports, fragments, untrusted hosts, and unexpected signed APK paths are rejected. No wildcard hosts or API tokens are embedded in the app.
- RSA authentication happens before manifest parsing/use. Download size and SHA-256, PackageManager package/version/minSdk/current signer sets, and native ABI are verified before caching and again before installer handoff. Stable APK and manifest keys are required; certificate rotation, split APK sets, and AAB installation are not supported by this channel. The manifest includes mandatory `packageName` in addition to the requested schema fields.
- APKs stay in app-private updater storage. Partial/failed files are removed; completed files are reused only after revalidation. Download cancellation closes the active connection. Interrupted downloads can be retried. Backgrounding/rotation does not stop a download while the process stays alive; process termination stops it, and the next attempt cleans partial data and retries. No notification/foreground service is used solely for update downloads.
- The **actual JourneyRepository** atomically gates installation. Recording, paused journeys, and loading/restoring state block handoff. The recorder also rejects a start while the installer owns the gate. Finishing a journey enables Install update; installation never starts automatically when a trip ends.
- Android's installer always asks for user approval. Per-app unknown-source permission is requested only after an explicit install action. The user taps Install update again after granting permission. FileProvider is non-exported and grants temporary access only to the private updater directory. Cancellation and errors retain a verified file for retry. Installed versionCode is checked on the next launch before reporting an update completed.
- Journeys are never sent to GitHub. Backup/restore and GPX export use Android's file picker with user-selected files. Automatic cloud backup remains disabled.

References: [Android publishing and installation](https://developer.android.com/studio/publish), [APK signing](https://developer.android.com/studio/publish/app-signing), [GitHub releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).
