# Validation Results: Park Detection & Guidance (Run 2)

Results of the checks in [quickstart.md](quickstart.md). Automated suites are recorded at the gates in
[tasks.md](tasks.md) and in the commit messages; this file holds the operating-system checks (§5) and the field
checks (§6).

## Full automated gate (T118)

**Date**: 2026-10-03 · **Command**: `gradlew :app:assembleDebug :shared:testAndroidHostTest :app:testDebugUnitTest
:app:lintDebug --rerun-tasks`, then `Invoke-Pester tools\traceability\tests` · **JDK**: Gradle-provisioned 25

| Suite | Result |
|---|---|
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:shared:testAndroidHostTest` | 196 tests, 0 failed |
| `:app:testDebugUnitTest` | 53 tests, 0 failed |
| `:app:lintDebug` | 0 errors, 16 warnings: 14 newer-version notices for the pinned catalog versions (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`), `MissingApplicationIcon`, `DataExtractionRules` |
| Pester (`tools/traceability/tests`) | 24 passed, 0 failed |
| Traceability (`-FailOnGaps`, T117) | 72 requirements, 72 traced, 0 gaps, 0 orphaned tags, exit 0 |

## §5 Operating-system behavior (T114)

**Date**: 2026-10-03 · **Device**: Android Emulator, AVD `Pixel_9`, system image `android-37.2` (Google Play),
headless, x86_64 · **Build**: `app-debug.apk` from branch `feature/permission-denial-flow` at commit `1a73326`
· **Method**: adb, with UI read from `uiautomator dump` and taps by visible text; GPS fixes injected with
`adb emu geo fix` (velocity in knots); process state from `dumpsys activity`, `dumpsys location`,
`dumpsys notification`; crashes from `logcat -b crash` (empty for every check).

This is an emulator, not a phone. The §6 field checks (T119) still need a real device.

| # | Check | Result | Evidence |
|---|---|---|---|
| O1 | Fresh install; walk through the prompts, granting all | PASS | One prompt at a time: location (with Precise/Approximate), background location (settings page), physical activity, notifications. Service running; notification "Car Finder" / "Monitoring for parking". Force-stop and relaunch: no prompt. |
| O2 | Fresh install; deny location | PASS | Confirmation "Car Finder needs location permission to run. Close Car Finder?" shown. Back: the location prompt again. Denied again, tapped Close: "Car Finder is closing." shown, then the task was gone; no other prompt, no service, no notification, no crash. Relaunch: location asked for again (Android now answers at once after two denials, so the confirmation came straight back; accepted, OD-1). |
| O3 | Grant all; reach PARKED; force-stop, relaunch | PASS | Simulated drive at 30 kn, then stopped: DRIVING → "Sensing you will be parking soon" → PARKED stored (centroid, 5.0 m radius, declaration time). After `am force-stop` and relaunch the stored record was byte-identical and the service ran again. |
| O4 | Kill the process in the background | PASS | `am kill` does not end a process holding a foreground service, so the process was killed with `run-as … kill -9`. The sticky service came back (pid 10506 → 11065); stored record unchanged. |
| O5 | "Allow all the time"; reboot; do not open the app | PASS | The boot broadcast reached `BootReceiver` about 2 s after `sys.boot_completed`; "Background started FGS: Allowed … code:BOOT_COMPLETED". Notification "Car Finder" / "Monitoring for parking"; a high-accuracy fused location request held by the app. The restored record was 171 s old, so sampling started at 5 s and dropped to 20 s when the recovery window closed. |
| O6 | "While using the app" only; reboot | PASS | Boot broadcast received; no service started. Opening the app started detection; the notification text asks the user to choose "Allow all the time". (The tap action that opens settings is covered by `DetectionNotificationTest`; not tapped on the device.) |
| O7 | With the service running, revoke location | PASS | `pm revoke` fine and coarse: no crash; the process restarted but the service stopped itself without location. Returning to the app: the location prompt again. |
| O8 | Corrupt the stored file, relaunch | PASS | File replaced with non-JSON bytes. Relaunch: "Location unavailable" (FINDING), record reset to `{"schemaVersion":1,"state":"FINDING","parkedLocation":null}`, log line `W CarFinder: StoreUnreadable count=1` (no coordinates). A new drive and park then stored DRIVING and PARKED normally. |
| O9 | Fresh install; grant location, deny notifications | PASS | Notification confirmation shown; no service. "Allow": the notification prompt again. Denied again and tapped Close: closing message, task gone, no crash. Relaunch: notifications asked for again (confirmation at once after two denials); granted in Settings and tapped "Allow": detection started and the notification was posted. |
| O10 | Android 12+; choose "Approximate" | PASS | Fine `false`, coarse `true`: the location confirmation was shown. "Allow" led to Android's "Change … to precise?" prompt; accepting it resumed detection. |
| O11 | Deny until the prompt no longer appears | PASS | "Allow" brought the confirmation straight back; "Close" closed the app; after granting location in Settings, a relaunch continued the sequence. |
| O12 | Rotate while the location prompt is open, then grant | PASS | Rotated to landscape with the prompt open; granted there; the sequence continued to the background-location page. |
| O13 | Deny, Close, Home during the closing message; reopen; launch again | PASS | Reopening brought the app back and it closed at once (the launcher was the resumed activity 3 s later). The next launch showed no closing message and asked for location again. |

One test artifact seen and not counted: force-stopping the app while Android's permission dialog was on top left
that dialog alone in the task, so the next launch showed an orphaned dialog with no app behind it. Users cannot
force-stop from under the system dialog in normal use; it was not reproduced with a normal launch.

## §6 Field checks (T119)

Not run. They need a phone with GPS and a compass outdoors; no such device was available in this session.
