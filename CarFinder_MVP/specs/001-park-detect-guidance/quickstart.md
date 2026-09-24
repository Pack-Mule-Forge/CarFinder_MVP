# Quickstart: Validating Automatic Park Detection and Return Guidance

**Date**: 2026-09-18 | **Plan**: [plan.md](./plan.md)

How to build, test, and prove this feature works. Scenarios are ordered so each is runnable as soon
as its part of the implementation lands — you do not need the whole feature to start validating.

---

## Prerequisites

- JDK 11+ (the project targets Java 11 bytecode)
- Android SDK with compileSdk 37 installed
- An emulator or device on API 24+ — **use at least one API 24–28 target** as well as a modern one;
  R-06's background-permission behavior genuinely differs and that path is live at minSdk 24
- Google Play Services on the test device (Fused Location Provider, R-03)
- PowerShell (already required by the repo's Spec Kit scripts)

No network, account, or backend is needed for any scenario here — that is itself SC-010.

---

## Scenario 1 — Shared domain unit tests *(Principle I; FR-035, FR-037)*

The fastest loop. No device, no emulator.

```powershell
./gradlew :shared:allTests
```

**Expected**: all pass, covering the state machine, convergence, uncertainty, guidance math, and the
four-way view selection.

**What to check beyond green**:

- Boundary cases exist and pass: speed exactly 5 mph enters PARKING; speed exactly 25 mph does
  **not** enter DRIVING; distance exactly 500 ft renders in feet; half-angle exactly 45° is arrival
  (see [data-model.md](./data-model.md)).
- Zero distance yields arrival, not NaN.
- The 359°→1° heading wrap takes the 2° path.
- No test contains a literal `5`, `25`, `10`, `500`, or `45` as a threshold — grep for them. If one
  appears, FR-037 is violated even though the suite is green:

```powershell
Select-String -Path shared/src/**/*Test.kt -Pattern '\b(2\.2352|11\.176|152\.4|500\.0|45\.0)\b'
```

---

## Scenario 2 — Adapter tests against fakes *(Principle III)*

```powershell
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest
```

**Expected**: adapter behavior verified with `FakeLocationProvider`, `FakeHeadingProvider`,
`FakePermissionController`, `FakeActivityRecognizer`, and `FakeClock`. No test touches real hardware.

**Specifically confirm** the permission divergence is covered: `request(BACKGROUND_LOCATION)` returns
`Granted` on API 24–28 and requires a prior foreground grant on API 30+
([contracts/platform-adapters.md](./contracts/platform-adapters.md)).

---

## Scenario 3 — Compose UI tests *(FR-036)*

```powershell
./gradlew :app:connectedDebugAndroidTest
```

**Expected**: semantics-based tests pass for all four views. The three the constitution names
explicitly:

1. Cone geometry for a given uncertainty/distance input
2. The feet↔miles distance-unit switch
3. The arrival-confirmation prompt appearing at the arrival threshold

**What to check**: the cone-geometry test asserts the *rendered* half-angle via the custom semantics
property, not a recomputed `atan(u/d)`. A test that recomputes the formula and compares it to itself
passes even when the Composable is wired to the wrong field.

---

## Scenario 4 — Full park cycle without touching the device *(US1; SC-001, SC-011)*

The scenario that proves the premise. Use mock locations so it is repeatable.

1. Install the debug build; grant foreground **and** background location when prompted.
2. **Do not open the app again.** Send it to the background or force-stop it.
3. Feed a drive-then-park track:
   ```powershell
   adb emu geo fix <lon> <lat>          # repeat along a route, or use
   # Android Studio ▸ Extended Controls ▸ Location ▸ Routes for a timed playback
   ```
   The track must exceed 25 mph, then drop to 0, then hold still for at least
   3 × 5 s = 15 s so the convergence window can fill.
4. Open the app.

**Expected**: the guidance display, not "Parked location unavailable." The park was captured with the app
closed — SC-011. If it shows the status message instead, the state machine is not running in the
background and FR-014 is not met.

---

## Scenario 5 — Surviving process death *(FR-011; SC-002)*

With a parked location stored:

```powershell
adb shell am force-stop com.packmuleforge.carfinder_mvp
adb shell am kill com.packmuleforge.carfinder_mvp
adb reboot
```

**Expected**: after each, reopening the app shows the same parked location and the same state. Run
all three separately — they exercise different teardown paths, and reboot additionally exercises the
`BOOT_COMPLETED` receiver.

---

## Scenario 6 — Drive-away clears the location *(US5; FR-010, FR-013, SC-006)*

With a parked location stored, feed a mock track rising above 25 mph.

**Expected**: the location is deleted and the state becomes DRIVING. Reopening the app mid-drive
shows `Driving - Waiting to Park`; after slowing, `Sensing you will be Parking Soon.` — **never** the previous
location. A stale location here is the worst failure mode in the feature.

---

## Scenario 7 — Guidance in the field *(US2, US3; SC-003, SC-004, SC-005)*

Walk away from a parked location, then back.

**Expected**:

- The cone points at the car as you turn — **verify against a known landmark**, since a declination
  bug (R-05) produces a cone that looks plausible and is consistently up to ~20° wrong.
- The cone narrows as you approach and widens as accuracy degrades.
- Distance switches feet→miles at 500 ft.
- Rotating the device does not change the cone's size or shape (FR-027).
- No stutter while walking (SC-004). Confirm with:
  ```powershell
  adb shell dumpsys gfxinfo com.packmuleforge.carfinder_mvp framestats
  ```
- On arrival, "You have arrived" replaces the cone and the prompt appears. Answering either way
  dismisses it and leaves the location stored (FR-033).

---

## Scenario 8 — Traceability report *(FR-038–FR-040)*

```powershell
./tools/traceability/Get-TraceabilityReport.ps1
```

**Expected**: all 42 requirements listed with implementing code and verifying tests; zero untraced
requirements; zero orphaned annotations.

**Read the header caveat**: four requirements (FR-034, FR-037, FR-041, FR-042) are absence
properties that no annotation can prove — see
[contracts/traceability-report.md](./contracts/traceability-report.md). A green report does not
attest to those; their dedicated tests do.

---

## Scenario 9 — Offline operation *(SC-010)*

Enable airplane mode with location still on, then run Scenarios 4, 6, and 7.

**Expected**: identical behavior. GNSS fixes take longer to acquire cold, but nothing fails. Any
network dependency that has crept in surfaces here.

---

## Scenario 10 — Battery sanity *(R-07; constitution proportionality constraint)*

```powershell
adb shell dumpsys batterystats --reset
# leave the device idle and stationary for 12 hours
adb shell dumpsys batterystats com.packmuleforge.carfinder_mvp
```

**Expected**: under ~2% of battery over 12 idle hours, with the app near-idle because activity
recognition has not reported `IN_VEHICLE`. This is an engineering target from R-07, not a spec
requirement — the spec sets no numeric battery bound. Materially exceeding it means the tier gating
is not working.

---

## Known gap to resolve before implementation

[research.md](./research.md) R-05 identifies a correctness gap the spec does not cover: the compass
reports **magnetic** heading while `Geodesy` returns **true** bearings, and FR-024 combines them
without mentioning declination. Uncorrected, the cone points up to ~20° off in some regions —
consistently and plausibly enough that Scenario 7 will pass a casual look. Raise this in
`/speckit-analyze` before `/speckit-implement`.
