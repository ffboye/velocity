# Development priorities

Keep Velocity a small, reliable local journey recorder. New features should fit its minimal interface, preserve old journey files, and work without an account. Stops and automatic pause remain out of scope. Offline map support stays a user choice.

## Foundation in 1.4.2

- Shared validation for local storage and backups, preserving unreadable files and rejecting invalid saves before replacement.
- Stable route snapshots without repeated full-route copying on timer ticks.
- Android recording, persistence, GPS, navigation and map regressions on pull requests and releases.
- An external process-kill regression proving interrupted journeys recover exactly once.
- Signed APK runtime checks before publication; release optimization stays disabled after the 1.3.0 startup regression.

## Next changes, in order

1. Measure physical-phone background reliability and battery use using the quality checklist. Change sampling or provider policy only when measurements justify it.
2. Profile large histories and long journeys. Move from whole-file JSON to indexed storage only when measured load/save costs justify migration. Keep an export path and test migration against old files before shipping it.
3. Improve journey review: select a graph time to highlight its matching recorded route position. Respect reception gaps and missing samples rather than inventing a position. Add journey search when history size makes it useful.
4. Improve data recovery: make restoring a verified backup possible when local storage is damaged, while retaining the original file for deliberate recovery. Define deletion and backup retention before adding automatic backup copies.
5. Make location status and permission recovery clearer where testing reveals confusion. Check TalkBack and large text with each changed screen; keep GPS diagnostics optional.

These are future work, not implemented features. Prioritize one measured user problem per change rather than adding more tabs or explanatory text.

## Working agreement

Keep GPS calculations independent of Android UI, storage rules shared, and IO off the main thread. Preserve the package ID and permanent signing certificate. Never check private keys into source or add analytics/location uploads by default. Add regression coverage at the failure boundary and run the checks in `QUALITY.md`. Publish only after the exact signed artifact passes runtime checks, and distinguish emulator results from physical-phone evidence.
