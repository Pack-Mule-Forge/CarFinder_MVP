# Quickstart & Validation Guide: Park Detection & Guidance

This guide explains how to build the feature, run its automated checks, and validate it end to end. The API and
schema details are in [contracts/](contracts/) and [data-model.md](data-model.md), and are not repeated here.

## Prerequisites

- JDK 17 or later, and the Android SDK with the platform that `compileSdk` names. Android Studio's bundled JBR
  works.
- Gradle comes from the checked-in wrapper (9.5). No global Gradle install is needed.
- PowerShell 5.1 or later, and Pester 5 for the traceability-tool tests (`Install-Module Pester -Scope
  CurrentUser`).
- Optional, for the device checks: an Android phone on API 34 or later with GPS and a magnetometer, plus `adb`.

## 1. Build

```powershell
.\gradlew.bat :shared:assemble :app:assembleDebug
```

Expected: the build succeeds, and `app/build/outputs/apk/debug/app-debug.apk` exists.

## 2. Automated tests (the primary gate: Constitution I, III and IV)

```powershell
.\gradlew.bat :shared:allTests :shared:testAndroidHostTest :app:testDebugUnitTest
```

The exact host-test task name depends on the AGP KMP plugin. Run `gradlew tasks --all | findstr -i test` to
confirm it.

| Suite | Proves | Key scenarios |
|---|---|---|
| `:shared` commonTest | Domain logic, engine and presenter against fakes | The state machine table in data-model, including dead zone, single-spike immunity, FINDING never entering PARKING, and PARKED exiting only to DRIVING. Also: the 9 m-in-a-line non-convergence, the window sliding indefinitely, `atan2` at distance 0, the display-bearing wraparound, feet/miles at exactly 500 ft, arrival at exactly 45°, the arrival re-arm only after the half-angle drops below 45°, and staleness detected by the clock tick with no new fix. |
| `:shared` androidHostTest | Android adapters through their seams | Location→`LocationReading` mapping with missing accuracy or speed. A profile change re-issues a single subscription. Heading math for each display rotation, with declination. DataStore round-trip. A corrupted file yields FINDING. Permission sequencing, including an early stop on denial. |
| `:app` testDebugUnitTest | Compose semantics and the service | The seven UI tests listed in [contracts/guidance-ui.md](contracts/guidance-ui.md), the Compose-only source scan, and a service `START_STICKY` plus notification check through Robolectric `ServiceController`. |

Expected: every test passes, and no test hard-codes an FR-030 literal. To spot-check for literals, run
`Select-String -Path shared\src\*Test\**\*.kt -Pattern '\b(25\.0|10\.0|45\.0|500\.0)\b'`. It should find nothing
outside `CarFinderConstants` references.

**Frame-timing benchmark (FR-027)**: this is automated, but it needs a connected physical device and is not
part of the fast loop.

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
```

Expected: `GuidanceJankBenchmark` passes, with the 95th-percentile `frameDurationCpuMs` at or below 16.7 ms over
the 60 s synthetic guidance session.

## 3. Traceability report (Constitution II)

```powershell
Invoke-Pester tools\traceability\tests
.\tools\traceability\Get-TraceabilityReport.ps1 -FailOnGaps
```

Expected: the Pester tests pass. The report is written to `specs/002-park-detect-guidance/traceability.md` with
every FR and QR `TRACED`, or `UNIMPLEMENTED` only for QR-005 to QR-007. The report has 0 orphaned tags and the
exit code is 0. This satisfies SC-011.

## 4. Scripted replay validation (SC-001, SC-002, SC-005, SC-009, SC-010)

These run as `commonTest` cases that feed recorded or synthetic reading sequences through `ParkingEngine` with
fakes. They need no hardware.

| Scenario | Input | Expected outcome |
|---|---|---|
| Drive, stop, converge | A speed above the driving threshold, then 3 or more samples at or below the parking threshold, then 3 clustered readings | PARKED, and the stored location equals the centroid |
| Dead-zone only | Walking, cycling or traffic speeds strictly between the thresholds | FINDING throughout, with no transitions |
| Fresh install, no drive | Slow or stationary readings | FINDING, and the store holds no location |
| Drive-away | PARKED, then 3 or more fast readings | DRIVING, with no location in the store after an engine re-create |
| Single spike | PARKED, then one fast reading among slow ones | PARKED is kept |
| Brief stop, then the real spot (SC-012) | Drive, a converging stop, slow spread-out readings, then a second converging stop inside the parked-recovery window | PARKED throughout, and the stored location equals the second centroid with the original capture time |
| Walk away and settle (SC-012) | PARKED, the clock advances past the parked-recovery window, then readings converge elsewhere | The stored location is unchanged |
| Stale fix | PARKED with guidance showing, then the clock advances past the staleness timeout with no fix | `Unavailable` within one recheck tick, with lifecycle PARKED and the location kept |

## 5. Persistence survival (SC-004, FR-014)

Run this on an emulator or device. It is supplementary integration evidence, not a substitute for §2.

1. Reach PARKED, either by driving or by using an emulator GPX route in Extended Controls → Location → Routes:
   run it at driving speed, then stop.
2. Force-close the app: `adb shell am force-stop com.packmuleforge.carfindermvp`. Relaunch it and confirm that
   guidance or "Parked location unavailable." is shown with the state still PARKED (the notification text).
3. Kill the process: `adb shell am kill com.packmuleforge.carfindermvp` with the app in the background. Relaunch
   and check the same result.
4. Reboot: `adb reboot`. After boot, confirm that the notification reappears without opening the app, which is
   research R5 and FR-033. Open the app and check the same result.

## 6. Field checks (SC-003, SC-006, SC-007, SC-008; physical device)

| # | Check | Pass criterion |
|---|---|---|
| V1 | Park in a real lot and walk away without touching the phone | The notification shows PARKED within about 15 s of stopping (SC-001) |
| V2 | Open the app from the lock screen while parked | The cone appears within 2 s with no taps (SC-006) |
| V3 | Turn in place, then walk | The cone rotates and the distance updates within 1 s, with no visible stutter (SC-008) |
| V4 | Rotate the phone between portrait and landscape | The cone keeps the same size and stays centered (FR-024) |
| V5 | Walk to the car | "You have arrived" and "Do you see your car?" appear. Answering keeps the state PARKED. |
| V6 | Reboot the phone while PARKED and do not open the app, then drive away | The notification returns after boot, and drive-away clears the location (FR-033, FR-015). If the boot start is blocked, record the gap as research R5 describes. Then repeat with **only** "While using the app" granted. Expected: no detection after reboot until the app is opened, and the notification asks for "Allow all the time" (spec Edge Cases). If detection does resume after reboot, record that, because it would disprove research R5. |
| V8 | Parking-lot creep (CR-18): stop for about 15 s at a lot entrance or stop sign after driving, then creep to a space without exceeding the driving threshold, park, and stay at the car for about 15 s | PARKED may appear at the entrance stop. Within the parked-recovery window the location moves to the real space with no prompt, and guidance from a distance points at the real space (FR-035). Also record: whether a stoplight in ordinary driving still shows PARKED early (expected; cleared on drive-away), and whether a space reached after the window closed kept the entrance location (expected). |
| V9 | Park normally, wait past the parked-recovery window, walk at least 50 m away and stand still for a minute | The Parked Location does not move (FR-035 bound) |
| V7 | Over a day of normal use, check the battery screen | Car Finder's usage is reasonable, which is a judgment call per the constitution's "proportionate sensor use". Record the figure for later tuning of `IDLE_WATCH_SAMPLING_INTERVAL_MILLIS`. |
