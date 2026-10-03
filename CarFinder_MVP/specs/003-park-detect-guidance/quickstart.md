# Quickstart & Validation Guide: Park Detection & Guidance (Run 2)

How to build the feature, run its checks and validate it end to end. Types and contracts are in
[data-model.md](data-model.md) and [contracts/](contracts/).

## Prerequisites

- A JDK to launch Gradle, with `JAVA_HOME` set. Android Studio's bundled runtime works. Gradle
  provisions its own daemon toolchain (JDK 25, `gradle/gradle-daemon-jvm.properties`).
- Android SDK; `local.properties` points at it.
- PowerShell 5.1 or later and Pester 5 (`Install-Module Pester -MinimumVersion 5.0 -Scope CurrentUser`).
- For §5 and §6: an emulator or phone on Android 8.0 or later, and `adb`. Field checks need a phone
  with GPS and a compass.

## 1. Build

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: success, and `app/build/outputs/apk/debug/app-debug.apk` exists.

## 2. Automated tests

```powershell
.\gradlew.bat :shared:testAndroidHostTest :app:testDebugUnitTest :app:lintDebug
Invoke-Pester tools\traceability\tests
```

Expected: all pass, lint reports no errors.

| Suite | Proves |
|---|---|
| `:shared` commonTest | State machine, speed filter, convergence, recovery, sampling policy, guidance math, view selection, arrival prompt, engine, presenter, scripted replay |
| `:shared` androidHostTest | Android adapters through their seams: field-presence mapping, single subscription, heading math per rotation, storage round-trip and corruption, permission order |
| `:app` test | Compose semantics tests in [contracts/guidance-ui.md](contracts/guidance-ui.md), service, boot receiver, notification, Activity |
| Pester | Traceability script and findings-ledger format |

A task is closed only on the output of these commands, not on a description of it.

## 3. Traceability

```powershell
.\tools\traceability\Get-TraceabilityReport.ps1 -FailOnGaps
```

Expected: exit code 0, every FR and QR `TRACED` or `NO-CODE`, no orphaned tags (SC-013).

## 4. Scripted replay (QR-016)

These are `commonTest` cases using `ReplayRunner`. No hardware.

| Scenario | Expected | Criterion |
|---|---|---|
| Drive, stop, three converging readings | PARKED; stored location is the centroid with the FR-016 radius | SC-001 |
| Dead-zone speeds only; no drive; filter not full | No state change, nothing stored | SC-002 |
| Brief converging stop, creep, second convergence inside the window | Second location stored, original declaration time | SC-003 |
| Same, second convergence after the window | First location kept | SC-003 |
| PARKED, then smoothed speed above the driving threshold | DRIVING, nothing stored after re-creating the engine | SC-005 |
| PARKED with fix and heading; guidance becomes visible | Guidance state on the visibility step, with no added wait (the 2 s target itself is field check V2) | SC-006 |
| Walk back to the car with seeded reading error | True car inside the cone in at least the containment target share of cone frames | SC-007 |
| Uncertainty ≥ distance, and < distance | Arrived, and not arrived | SC-009 |
| Fix stops; heading stops; sensor unreliable | `Unavailable` within 1 s; PARKED and location kept | SC-010 |
| Every state × recovery × visibility × in-vehicle combination | Requested interval equals FR-027 | SC-012 |

## 5. Operating-system behavior (emulator or device; required, not optional)

Fakes cannot show what the operating system does at these boundaries. Each check is required before
the related tasks are closed.

| # | Check | Expected |
|---|---|---|
| O1 | Fresh install; walk through the prompts, granting all | At launch, one prompt at a time: location, background location, activity recognition, notifications. Relaunch: none is asked again (FR-048) |
| O2 | Fresh install; deny location | The confirmation for location is shown. Press back: the location prompt is shown again. Deny again and tap "Close": "Car Finder is closing." for the shutdown-notice duration (FR-055), then the app closes with no crash dialog. No other prompt was shown, and there is no service and no notification. Relaunch: location is asked for again (FR-048, FR-049, FR-052, FR-056) |
| O3 | Grant all; reach PARKED; `adb shell am force-stop`, relaunch | PARKED, same location (SC-004) |
| O4 | `adb shell am kill` with the app in the background | Service returns; PARKED, same location |
| O5 | "Allow all the time"; reboot; do not open the app | Notification returns; detection running (FR-053) |
| O6 | "While using the app" only; reboot | No service after boot. Open the app: detection resumes and the notification asks for "Allow all the time" (FR-054) |
| O7 | With the service running, revoke location in Settings | No crash; the service stops or does not restart without the permission. Return to the app: location is asked for again (FR-048, FR-049, FR-050) |
| O9 | Fresh install; grant location, deny notifications | The confirmation for notifications is shown; no service starts. Tap "Allow": the notification prompt is shown again. Deny and tap "Close": the closing message is shown, then the app closes with no crash. Relaunch: notifications are asked for again; grant them and detection starts (FR-048, FR-049, FR-056) |
| O10 | Android 12+, fresh install; choose "Approximate" at the location prompt | The confirmation for location is shown, as for a denial (FR-056) |
| O11 | Deny location several times until the prompt no longer appears | Tapping "Allow" brings the confirmation straight back; "Close" still closes the app; granting location in Settings and relaunching works. Accepted behavior (OD-1) |
| O12 | Fresh install; rotate the phone while the location prompt is open, then grant | The sequence continues to the next prompt; it does not stall (FR-048, AN2-U2) |
| O13 | Deny location, tap "Close", press Home during the closing message; reopen after a few seconds. Then launch again from the launcher | On reopening, the app closes at once. On the next launch there is no closing message and location is asked for again (FR-056, AN2-U1) |
| O8 | Corrupt the stored file (`adb shell run-as`), relaunch | FINDING, a log entry, and the next park stores normally (FR-020) |

Record results in `validation-results.md` in this directory.

## 6. Field checks (phone)

| # | Check | Pass |
|---|---|---|
| V1 | Park and walk away without touching the phone | PARKED soon after stopping |
| V2 | Open the app while parked | Cone within 2 s, no taps (SC-006) |
| V3 | Turn in place, then walk | Cone follows; distance updates; no visible stutter (FR-046, SC-008) |
| V4 | Rotate between portrait and landscape | Cone same size, centered, still points at the car (FR-033, FR-036) |
| V5 | Walk to the car | "You have arrived" and the prompt; answering only dismisses it (FR-039) |
| V6 | Drive away | Location cleared (SC-005) |
| V7 | Stop about 15 s at a lot entrance, creep to a space, park | Location moves to the real space within the window, with no prompt (FR-022) |
| V8 | Park, wait past the recovery window, walk 50 m and stand a minute | Location does not move (FR-024) |
| V9 | During V7, log the time between delivered readings | Note any delivered under 5 s apart (research R5 risk) |
| V10 | A day of normal use | Record battery use |
