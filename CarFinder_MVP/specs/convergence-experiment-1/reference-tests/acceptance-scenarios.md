# Car Finder — Portable Acceptance Scenario Reference

**Status:** Live reference. This is the implementation-agnostic convergence oracle for the Spec-Kit
convergence experiment — see `requirements-as-truth-overseer-agent-notes.md`, "Load-bearing
thresholds vs. prescribed approaches — and naming the actual convergence oracle" (2026-10-02).

**Why this exists.** No two Spec-Kit builds — human or LLM, 001, 002/Run 1, or Run 2 — will ever
produce the same code, the same internal class structure, or even the same internal formulas. That
is not a defect to score against; it is a structural fact about giving a builder freedom to make its
own implementation choices. What *can* be scored fairly, across any number of independently-built
implementations, is whether each one satisfies the same set of black-box, use-case-level behaviors:
feed a sequence of sensor readings in, observe the resulting lifecycle/display state, check it
against what the spec says should happen. This document compiles that scenario set once, held out
from the generation seed (`car-finder-mvp-requirements-v5.md`), so that scoring Run 2 — or any future
run — does not depend on it happening to reproduce `002`'s specific formulas or class names.

**Provenance.** Extracted 2026-10-02 from `002-park-detect-guidance`'s `spec.md` (its User Stories,
Acceptance Scenarios, and Edge Cases were already written at the right level of abstraction — this
document mainly compiles them into a standalone, versioned form that survives Run 2 regenerating its
own `spec.md` from scratch) and cross-checked against `002`'s actual test suite for concrete worked
examples and current evidence of what's been verified. Every concrete number below traces to either
an existing `002` test (cited) or is explicitly marked "constructed" where no existing test covers
it. This cross-check also surfaced real coverage gaps — both scenarios nothing tests, and tested
behaviors nothing in `spec.md` describes — listed in Section 3; these are follow-up items, not
blockers for Run 2.

**How to use this for Run 2.** Run 2 will not share `002`'s internal structure. Scoring it against
this document means building a thin adapter — per v5 Section 9's "scripted replay testing"
methodology — that feeds each scenario's input sequence into whatever Run 2's actual top-level entry
point turns out to be, and reads back Run 2's own observable lifecycle/display state in whatever
shape Run 2 represents it. The scenario definitions themselves (Given/When/Then, concrete input
values, expected observable outcome) do not change; only the adapter does. Section 4 discusses how
well `002`'s own test suite already demonstrates this separation, as a sanity check before relying
on the same pattern for Run 2.

**Named constants referenced below** (current values — see `car-finder-mvp-requirements-v5.md`
Section 4 for the authoritative, owner-confirmed list): parking-speed 5 mph, driving-speed 25 mph,
convergence radius 10 m, convergence sample count 3, parking-sampling interval 5 s, arrival
half-angle 45°, distance-unit threshold 500 ft, speed-filter window 3, fix-staleness 30 s,
heading-staleness 2 s, parked-recovery window **180 s per v5** (worked examples below use 120 s,
`002`'s shipped/tested value at extraction time — see RR1-014 in `run-ledger.json` for why these
differ; rescale by the ratio 180/120 if replaying these exact examples against a build that uses
180 s).

---

## 1. Acceptance Scenarios

### User Story 1 — Parking detected and remembered automatically

**AS-US1-01** — *Source: US1 Acceptance Scenario 1, FR-003/FR-004*
Given DRIVING, When filtered speed drops to ≤5 mph, Then state → PARKING and sampling rises to 1
reading/5s.
*Worked example:* Feed 3 readings at 26 mph (DRIVING) → DRIVING; then 3 readings at 4 mph (≤5 mph)
→ PARKING, sampling interval becomes 5000 ms.
*Current evidence (002):* `ParkingStateMachineParkTest.driving_filteredSpeedAtOrBelowParkingThreshold_goesToParking`, `ParkingEngineParkTest.samplingProfile_followsLifecycle`, `ParkingReplayTest`/`FullLifecycleReplayTest`.

**AS-US1-02** — *Source: US1 AS2, FR-007/FR-012*
Given PARKING, When the most recent 3 readings are pairwise ≤10m apart, Then PARKED and the
centroid+accuracy is stored.
*Worked example:* 3 readings offset 0m/1m/2m north of a point, each accuracy 2.5m, speed 4 mph →
centroid ≈1m north; stored accuracy = max(own accuracy + distance-to-centroid) ≈3.5m (per REQ-UNC-02).
*Current evidence:* `ParkingReplayTest.driveStopConverge_storesParkedLocationAtCentroid_withNoManualAction`, `ParkingStateMachineParkTest.parking_convergedReadings_goToParked_withLocationSet`, `ConvergenceWindowTest.toParkedLocation_isCentroid_withMaxOfAccuracyPlusDistance`, `FullLifecycleReplayTest`.

**AS-US1-03** — *Source: US1 AS3, FR-008*
Given PARKING not yet converged, When another reading arrives, Then the oldest drops and
convergence re-evaluates (no timeout).
*Worked example:* One outlier reading 50m north + 2 clustered readings → not converged; one more
clustered reading evicts the outlier → converges.
*Current evidence:* `ConvergenceWindowTest.window_slides_droppingOldestReading`.

**AS-US1-04** — *Source: US1 AS4, FR-014*
Given PARKED with a stored location, When the app/process/phone restarts, Then state and location
survive.
*Worked example:* Write a PARKED record (lat/lon, accuracy 2.5m, captured timestamp) to durable
storage, fully tear down the writer (simulating process death), reopen a fresh reader over the same
storage, read back an identical record.
*Current evidence:* `DataStoreParkingStoreTest.writeThenRead_roundTrips_acrossStoreInstances`, `ParkingEngineParkTest.recreatedEngineOverSameStore_restoresParkedWithSameLocation`, `ParkingEngineRestoreTest.restore_publishesPersistedState_withoutStartingAnything`.

**AS-US1-05** — *Source: US1 AS5, FR-005*
Given DRIVING or PARKING, When speed is strictly between 5–25 mph, Then no transition.
*Worked example:* 15 mph fed from DRIVING → stays DRIVING; fed from PARKING → stays PARKING.
*Current evidence:* `ParkingStateMachineParkTest.deadZoneSpeeds_causeNoChange_inDrivingOrParking`.

**AS-US1-06** — *Source: US1 AS6, FR-004/FR-011*
Given fresh install (FINDING), When stationary/slow, Then stays FINDING; PARKING only reachable
from DRIVING.
*Worked example:* 15 readings at 4 mph plus two full converging 3-reading clusters, with no prior
drive → lifecycle stays FINDING throughout, zero store writes.
*Current evidence:* `ParkingReplayTest.freshInstallWithoutADrive_storesNoLocation`, `ParkingStateMachineParkTest.finding_slowOrStationary_staysFinding_andNeverEntersParking`.

**AS-US1-07** — *Source: US1 AS7, REQ-PARK-06*
Given PARKED at a brief stop within the recovery window, When the driver creeps and reconverges,
Then the location is silently replaced, state stays PARKED, no announcement, and the window's
anchor time does not change.
*Worked example (120s window shown; see note above on 180s rescaling):* Drive → slow → converge at
the lot entrance (0m) → PARKED declared at t=0; within 120s, creep through 3 spread readings
(30/60/90m, too spread to converge) then converge a real 3-reading cluster 150m north → Parked
Location silently replaced by that centroid, **same original `capturedAtEpochMillis`** (v5's
anchor-not-reset correction — see RR1-014), no lifecycle transition emitted for the correction.
Boundary: a convergence completing at exactly the window's edge corrects; one millisecond later does
not.
*Current evidence:* `ParkingEngineRecoveryTest.briefStopThenCreepToRealSpot_withinRecoveryWindow_storesTheRealSpot_silently`, `ParkingStateMachineRecoveryTest.parkedWithinRecoveryWindow_newConvergence_correctsLocation_andStaysParked`, `.convergenceCompletingExactlyAtWindowEnd_corrects_oneMillisecondLater_doesNot`. **Caution:** `002`'s shipped tests verify the window resets on each correction (v4 behavior); v5 changed this to anchor-only. A build claiming to satisfy v5 must NOT reset the window on correction — verify this explicitly for Run 2, don't assume the existing 002 tests already prove the v5 behavior.

**AS-US1-08** — *Source: US1 AS8, REQ-PARK-06*
Given PARKED and the recovery window has closed, When readings converge elsewhere, Then the
location is unchanged.
*Worked example:* Same setup as AS-US1-07 but the clock is advanced past the full window before the
creep+settle readings are fed — the resulting Parked Location is identical to the one recorded at
the premature park.
*Current evidence:* `ParkingEngineRecoveryTest.parkThenWalkAwayAndSettle_afterRecoveryWindow_keepsTheParkedLocation`, `ParkingStateMachineRecoveryTest.parkedAfterRecoveryWindow_newConvergence_keepsOriginalLocation`.

### User Story 2 — Guided back to the car

**AS-US2-01** — *Source: US2 AS1, FR-017*
Given PARKED, When the app is opened, Then guidance shows with no user action, state stays PARKED.
*Worked example:* Seeded PARKED record; a fix 60m south (accuracy 2.5m) and heading 0° are
delivered → guidance view shown, distance in feet, display bearing 0°, no taps.
*Current evidence:* `HomeScreenPresenterGuidanceTest.parkedWithCurrentFixAndHeading_showsGuidance`, `HomeScreenPresenterStatusTest.parkedWithGuidance_showsGuidance_andWithoutIt_showsUnavailable`, `FullLifecycleReplayTest`.

**AS-US2-02** — *Source: US2 AS2*
Given guidance shown, When position/heading changes, Then cone direction and distance text update.
*Worked example (constructed — see gap B in Section 3):* feed fixes at 200ft, 150ft, 100ft from the
car (heading held constant) and assert the rendered distance string changes "200 ft" → "150 ft" →
"100 ft" each time.
*Current evidence (partial only):* `HomeScreenPresenterArrivalTest.atArrival_guidanceIsArrived_andPromptIsVisible` (position-change path, no intermediate text assertions); `GuidanceRecompositionTest` (heading-change path, at the Compose-internals level only — not a black-box text assertion).

**AS-US2-03** — *Source: US2 AS3, FR-020*
Given combined uncertainty grows, Then the cone widens.
*Worked example (constructed — see gap A):* Parked accuracy 7.5m, distance 70m. Case A: fix
accuracy 3.25m → uncertainty 10.75m → half-angle ≈8.74°. Case B: fix accuracy degrades to 20m →
uncertainty 27.5m → half-angle ≈21.4° — visibly wider.
*Current evidence (formula only, not comparative):* `GuidanceCalculatorTest.halfAngle_isAtan2OfUncertaintyOverDistance_inDegrees`.

**AS-US2-04** — *Source: US2 AS4, FR-026*
Distance ≤500ft shown in feet; >500ft in miles.
*Worked example:* 499ft → "499 ft"; exactly 500ft → "500 ft"; 501ft → "0.09 mi"; 0.37mi → "0.37 mi";
1.2451mi → "1.25 mi" (half-up rounding); 11.999mi → "12.00 mi".
*Current evidence:* `GuidanceCalculatorTest.unitSelection_feetAtOrBelowThreshold_milesAbove`, `.feetText_isWholeFeet`, `.milesText_hasTwoDecimals`; `GuidanceDisplayTest.distanceText_isFeetAtOrBelowThreshold_milesAbove`.

**AS-US2-05** — *Source: US2 AS5, FR-024*
Rotation between portrait/landscape keeps the same cone size/proportions.
*Worked example:* Same guidance state rendered in a 400dp×800dp box then an 800dp×400dp box —
drawn cone size asserted equal in both.
*Current evidence:* `GuidanceDisplayTest.coneGeometry_rendersHalfAngleAndBearingFromInputs_sameSizeInPortraitAndLandscape`.

**AS-US2-06** — *Source: US2 AS6, FR-031/FR-034, REQ-GUIDE-07 (v5)*
Stale fix / no accuracy / no heading while PARKED → "Parked location unavailable.", state stays
PARKED, location kept, guidance returns once restored.
*Worked example:* Guidance shown; clock advanced past the fix-staleness timeout with no new fix →
view becomes Unavailable (re-checked over time, no new triggering event needed); same for heading
past its staleness timeout; heading explicitly absent → Unavailable, lifecycle stays PARKED, store
still holds the location; a fresh fix after staleness immediately restores guidance; a fix with no
accuracy → Unavailable (per REQ-PARK-08).
*Current evidence:* `HomeScreenPresenterGuidanceTest.fixGoingStaleWithoutNewFix_showsUnavailableWithinOneRecheckTick`, `.headingGoingStaleWithoutNewEvent_showsUnavailableWithinOneRecheckTick`, `.missingHeading_showsUnavailable_andKeepsParkedLocation`, `.freshFixAfterStaleness_restoresGuidance`, `.fixWithoutAccuracy_showsUnavailable`; `DefaultViewSelectorTest` (several related cases).

### User Story 3 — Arrival is recognized

**AS-US3-01** — *Source: US3 AS1, FR-028*
Half-angle reaches 45° → cone/icons/distance replaced by "You have arrived" + "Do you see your
car?"
*Worked example:* Fix 60m south → not arrived; fix 1m south (fix accuracy 3.0m, parked accuracy
2.5m → uncertainty 5.5m vs distance 1m → half-angle ≈79.7°, past 45°) → arrived, prompt visible.
Exact boundary: half-angle of exactly 45.0° is arrived; 44.999999° is not.
*Current evidence:* `ArrivalTest.arrived_atExactlyTheArrivalAngle_notJustBelow`, `.arrived_iffUncertaintyAtLeastDistance`, `.arrived_atZeroDistance`; `ArrivalDisplayTest.atArrival_coneIconsAndDistanceAreReplacedByMessageAndPrompt`, `.justBelowArrival_coneShows_andArrivalDoesNot`; `HomeScreenPresenterArrivalTest.atArrival_guidanceIsArrived_andPromptIsVisible`; `FullLifecycleReplayTest`.

**AS-US3-02** — *Source: US3 AS2, FR-029*
Answering Yes/No dismisses the prompt only; state stays PARKED, location not cleared.
*Worked example:* At 1m from the car (arrived), answering "yes" and separately "no" each dismiss
only the prompt; arrival message stays shown, lifecycle stays PARKED, the stored location is
untouched, no new store write occurs.
*Current evidence:* `HomeScreenPresenterArrivalTest.answeringYesOrNo_hidesPrompt_withoutTouchingLifecycleOrStore`, `.promptReturns_onlyAfterLeavingAndReArriving`; `ArrivalDisplayTest.yesAndNo_invokeCallbackWithTheAnswer`; `FullLifecycleReplayTest`.

### User Story 4 — Always-on status

**AS-US4-01** — *Source: US4 AS1, FR-016*
DRIVING (incl. first drive from fresh install) → "Driving - Waiting to Park."
*Current evidence:* `HomeScreenPresenterStatusTest.firstDriveAfterFreshInstall_showsDriving`, `StatusMessagesTest.eachStatusState_showsItsExactMessage_andNoCone`.

**AS-US4-02** — *Source: US4 AS2, FR-016*
FINDING (incl. fresh install) → "Parked location unavailable."
*Current evidence:* `HomeScreenPresenterStatusTest.freshInstall_showsUnavailable`; `MainActivityLaunchTest.launch_showsDefaultViewWithoutUserAction`.

**AS-US4-03** — *Source: US4 AS3, FR-016*
PARKING → "Sensing you will be Parking Soon."
*Current evidence:* `HomeScreenPresenterStatusTest.parking_showsParking`, `StatusMessagesTest`.

**AS-US4-04 / AS-US4-05** — *Source: US4 AS4/AS5, FR-016/FR-031*
PARKED + guidance available → guidance display; PARKED + guidance unavailable → "Parked location
unavailable."
*Current evidence:* `HomeScreenPresenterStatusTest.parkedWithGuidance_showsGuidance_andWithoutIt_showsUnavailable`; `DefaultViewSelectorTest.priorityOrder_acrossAllStatesAndAvailability`.

### User Story 5 — Driving away clears the location

**AS-US5-01** — *Source: US5 AS1, FR-003/FR-015*
PARKED, speed > 25 mph → location deleted, DRIVING.
*Current evidence:* `ParkingStateMachineDriveAwayTest.parked_filteredSpeedAboveDrivingThreshold_goesToDriving_andDeletesLocation`, `ParkingEngineDriveAwayTest.seededParked_thenDrivingWithNoUi_storesDrivingWithNoLocation`, `FullLifecycleReplayTest`, `ParkingStateMachineRecoveryTest.drivingSpeedDuringRecovery_stillDrivesAway_andDeletesLocation` (same transition mid-recovery — drive-away takes precedence over REQ-PARK-06).

**AS-US5-02** — *Source: US5 AS2, FR-015*
After deletion, restart → no location, not PARKED.
*Current evidence:* `ParkingEngineDriveAwayTest.afterDriveAway_recreatedEngine_isNotParked_andHasNoLocation`, `DataStoreParkingStoreTest.write_replacesWholeRecord_soStateAndLocationAgree`.

---

## 2. Edge Cases

| Edge case | Worked example / notes | Current evidence |
|---|---|---|
| 3 readings 9m apart in a line: within 10m of centroid but not pairwise | Line spaced so each reading is ≤10m of centroid, but the two ends are >10m apart → not converged | `ConvergenceWindowTest.readingsInALine_withinRadiusOfCentroid_butNotPairwise_doNotConverge` |
| Never converging — window slides indefinitely, no timeout | Demonstrated for one slide-and-evict step; extended non-converging stream not stress-tested (gap C) | `ConvergenceWindowTest.window_slides_droppingOldestReading` (partial) |
| False stop (long red light) while PARKING → back to DRIVING, nothing stored | | `ParkingStateMachineParkTest.parking_fastReadings_goToDriving_withWindowResetAndNothingStored`, `ParkingReplayTest.longRedLight_returnsToDriving_withNothingStored` |
| Brief stop then recovery within window | = AS-US1-07 | (above) |
| Brief stop, real spot reached after window closes | = AS-US1-08 | (above) |
| Walk away & stand still within window (pay station) may move location | Mechanically identical to AS-US1-07's test; never exercised as its own named scenario (gap D) | Same mechanism as AS-US1-07, not a distinct test |
| Walk away & settle after window closes → unchanged | = AS-US1-08 | (above) |
| Guidance open during recovery window → readings thinned to ~1/interval | | `ParkingEngineRecoveryTest.guidanceVisibleDuringRecovery_keepsGuidanceProfile`, `.minimumSpacing_toleratesJitter_butStaysWellAboveTheGuidanceInterval`; `ParkingStateMachineRecoveryTest.readingsFasterThanMinimumSpacing_areThinned_soAMovingPhoneDoesNotConverge` |
| Slow/stationary in FINDING — no transition | = AS-US1-06 | (above) |
| Dead zone (5-25 mph) in any state — no transition | | `ParkingStateMachineParkTest.deadZoneSpeeds_causeNoChange_inDrivingOrParking`, `ParkingStateMachineDriveAwayTest.parked_deadZoneOrSlowSpeeds_stayParked`, `ParkingReplayTest.deadZoneOnlySession_neverChangesState` |
| Single anomalous speed reading absorbed by median filter | 3-reading window with one spike | `SpeedMedianFilterTest.singleSpikeAmongSlowSamples_isAbsorbed`, `.filterIsAMedian_notAConsecutiveCount`; related ParkingStateMachine tests |
| Distance ≈0 → half-angle = 90°, arrival shown, no div-by-zero | | `GuidanceCalculatorTest.halfAngle_atZeroDistance_isNinetyDegrees`; `ArrivalTest.arrived_atZeroDistance` |
| Stale/lost fix or heading while PARKED, re-checked over time | = AS-US2-06 | (above) |
| Readings w/o accuracy excluded from convergence & recovery (REQ-PARK-08) | Tested for both the PARKING window and the recovery window | `ConvergenceWindowTest.readingWithoutAccuracy_isIgnored`; `ParkingStateMachineRecoveryTest.readingsWithoutAccuracy_areNotUsedForRecovery` |
| Restored PARKED with no readable location → falls back to FINDING (REQ-PARK-09) | Tested at pure-reducer, engine, and UI-restore layers; storage corruption also falls back to default | `ParkingStateMachineParkTest.restoredParkedRecordWithoutLocation_fallsBackToFinding_andAsksToPersist`; `ParkingEngineParkTest.restoredParkedRecordWithoutLocation_becomesFinding_andIsRewritten`; `ParkingEngineRestoreTest.restore_ofRecordNeedingNormalization_showsFinding_butDoesNotRewriteTheStore`; `DataStoreParkingStoreTest.garbageBytes_readAsDefault_withoutThrowing`, `.unknownSchemaVersion_readsAsDefault` |
| Arrival prompt dismissed stays dismissed until half-angle drops below 45° and returns | Arrive, answer yes, move closer (still arrived) → stays hidden; move away then back → reappears | `HomeScreenPresenterArrivalTest.promptReturns_onlyAfterLeavingAndReArriving` |
| Exactly 500ft → feet; exactly 45° → arrival | Both boundaries tested explicitly | `GuidanceCalculatorTest.unitSelection_feetAtOrBelowThreshold_milesAbove`; `ArrivalTest.arrived_atExactlyTheArrivalAngle_notJustBelow` |
| Crossing feet/mi boundary repeatedly, no hysteresis | Only tested crossing once ascending, not oscillating (gap E) | `GuidanceDisplayTest.distanceText_isFeetAtOrBelowThreshold_milesAbove` (partial) |
| Foreground-only permission (REQ-PERM-07): no restart, notification asks for "Allow all the time," tap opens settings; granted later → starts next foreground | The before/after sequence itself (grant mid-session → works next open) isn't tested as one sequence (gap F) | `BootReceiverTest.bootCompleted_withForegroundOnly_doesNotStartService`; `ParkingDetectionServiceTest.foregroundOnlyPermission_notificationAsksForAllTheTime_andOpensAppSettings` (partial) |
| Permissions revoked mid-session — must not crash | No test found (gap G) | — |

---

## 3. Coverage Gaps (follow-up items, not blockers)

**Spec scenarios/edge cases with no, or only partial, dedicated test in `002`:**

- **A.** US2 AS3 (cone widens as uncertainty grows): only a single-point formula test exists; no
  test compares two uncertainty values at the same distance and asserts the cone is visibly wider.
- **B.** US2 AS2 end-to-end (distance text and cone direction visibly updating): no test asserts
  concrete before/after *text values* across a sequence of fixes at the presenter or UI layer.
- **C.** "Never converging, window slides indefinitely, no timeout": slide mechanism proven for one
  eviction step only; no extended-stream stress test, no explicit test that no failure state exists.
- **D.** "Pay-station stand-still within the recovery window may move the location": mechanically
  identical to AS-US1-07's test but never exercised as its own named scenario.
- **E.** "Crossing the feet/mi boundary repeatedly, no hysteresis": only crossed once ascending,
  never oscillated to prove no retained state.
- **F.** "Permission granted later in Settings → service starts next time app opened": the two
  permission states are tested in isolation, not as one app instance transitioning between them.
- **G.** "Permissions revoked after grant → must not crash": no test found for mid-session
  revocation.
- **H.** SC-003/SC-007-style field-test criteria (location accuracy, user-reaches-car rate) are
  inherently field-test, not unit-testable — expected, not a defect.

**Tested behavior not captured anywhere in `spec.md`'s scenarios/edge cases** (candidates for
promotion into formal scenarios, or explicit scoping decisions, in a future requirements revision):

1. **Concurrency safety** (concurrent reading delivery from multiple threads) — a robustness
   property, not a use-case scenario; worth keeping as a separate non-functional check.
2. **Sampling-profile switching by lifecycle state** (REQ-PARK-07 in v5) — was an implementation
   optimization in `002` until v5 formalized it; now covered by v5, included above where relevant.
3. **Persist-before-publish ordering** — a crash-safety guarantee (never show a state the store
   doesn't already have) supporting REQ-PARK-04, never stated as its own Given/When/Then.
4. **`engine.start()` idempotency** — implementation robustness, not a use case.
5. **Single-location invariant across two full park cycles** — a strong candidate to promote into
   a formal acceptance scenario in a future requirements revision (currently a bare FR with no
   accompanying scenario).
6. **In-vehicle activity-recognition hint pre-emptively upgrading the sampling profile** (separate
   from REQ-PARK-06's deferred activity-gated recovery) — `002` uses activity-recognition hints for
   a purpose spec.md's user stories and edge cases never mention at all. **Flagged for the owner:**
   confirm whether this is an intended scope addition (fold into a future requirements revision) or
   an unintended scope creep to drop for Run 2 parity.

---

## 4. Is the Replay/Engine-Driver Pattern a Good Portable Harness?

`002`'s own test suite already demonstrates the right separation for a portable harness: tests in
the `ParkingEngine*Test`, `HomeScreenPresenter*Test`, and `*ReplayTest` families build the top-level
engine/presenter wired to a fake platform, feed in a sequence of plain location/heading readings,
and assert only against the public, small-surface observable state (a lifecycle enum, a sealed
display-state type, a persisted record) — never reaching into the internal convergence/state-machine/
geometry classes directly. Confirmed directly in `FullLifecycleReplayTest.kt`: it touches only
`ParkingEngine`, `HomeScreenPresenter`, and a `FakePlatform`, asserting against `presenter.state.value`
and `platform.store.record`.

Two caveats, so this isn't overstated:

1. **The driver isn't literally type-neutral.** It still depends on `002`'s specific Kotlin shapes
   (reading field names, the lifecycle enum's exact names, the display-state sealed subtypes).
   Scoring Run 2 against these scenarios will require writing a translation layer between Run 2's
   own state representation and this document's scenario language — "swap the thin adapter" is the
   right framing, but that adapter is real work, not zero-effort.
2. **No single test file is a complete scenario set.** The full scenario coverage lives across the
   whole `ParkingEngine*Test`/`HomeScreenPresenter*Test`/`*ReplayTest` family, not just the two files
   named "Replay" — standardize on that whole family's driver contract (reading sequence in,
   small observable state out), not literally just `FullLifecycleReplayTest.kt` and
   `ParkingReplayTest.kt`.

This document was extracted by reading source only; the underlying `002` tests were not re-run as
part of this extraction (their pass/fail status was independently confirmed separately — see
`run-ledger.json` RR1-013's test counts — but that confirmation predates this document and should
not be read as re-verifying every individual example above).
