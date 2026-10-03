# Car Finder — MVP Requirements

**Status:** Draft v5 — updated 2026-10-02. Hardened seed for the "Run 2" convergence experiment
(a fresh Spec-Kit regeneration, same model, scored against both `001-park-detect-guidance` and
`002-park-detect-guidance`). **Change from v4:** v4 folded forward the first real-world driving
test's findings (REQ-PARK-06). v5 folds forward everything found by the deliberate, after-the-fact
side-by-side comparison between the two independent Spec-Kit builds generated from the same
requirements lineage (001, built from pre-v3 seeds with Claude Sonnet 4; 002/"Run 1", built from
pre-v4 seeds with Claude Opus 5.5) — eleven correctness-relevant behavioral divergences that
neither build's own `/speckit.clarify` or `/speckit.analyze` passes caught, because the ambiguity
that let each build go its own way was never written down as a decision in the first place. Every
new or revised requirement below exists because two independently-generated, individually
plausible builds disagreed about something this document should have settled. See
`convergence-efficacy-tracker.md` for the full comparison this version is built from, and
`run-ledger.json` (RR1-011 through RR1-013) for the real-world test findings that prompted the
owner decisions folded in here. Everything from v4 is preserved below unchanged except where noted.

---

## 1. Concept

The phone determines when the user has parked, records the best representation of that
parked location, and later guides the user back to it using direction, distance, and
uncertainty.

## 2. System Lifecycle (State Machine)

```
DRIVING → PARKING → PARKED → FINDING → (confirmation) → DRIVING
```

- **DRIVING**: speed > 25 mph
- **PARKING**: speed ≤ 5 mph following a DRIVING state; increased sampling begins
- **PARKED**: 3 consecutive samples converge within 10m; Parked Location stored. PARKED is not
  necessarily final the instant it's declared — a bounded recovery window follows, during which
  continued low-speed movement can silently correct a premature declaration (REQ-PARK-06). While
  PARKED, the system's background sampling cadence is itself state-dependent, not fixed — see
  REQ-PARK-07 (new in v5).
- **FINDING**: no Parked Location is currently held. [Resolves R1-I1, CRITICAL — v2 used
  "FINDING" to mean two incompatible things during the MVP build: (a) no parked location has ever
  been captured, and (b) a location is held but live signal has degraded. Only (a) is a lifecycle
  state. (b) is a *display* condition — see the note on display state below — and must never be
  confused with FINDING again.]
- **Arrival confirmation**: "Do you see your car?" prompt shown when cone half-angle
  reaches 45°. Confirming (yes or no) simply dismisses the prompt for MVP — no
  additional state is introduced. The Parked Location is not cleared by this
  confirmation. This dismissal behavior is a MUST, not an illustrative aside — see REQ-TEST-06.
- **Return to DRIVING**: speed > 25 mph clears the current Parked Location
  (REQ-PARK-05) and the cycle restarts.

Speeds between 5–25 mph are an intentional dead zone — no state transition occurs.
This excludes biking/jogging/stop-and-go traffic from falsely triggering PARKING or
DRIVING transitions. This dead zone is deliberately preserved — see REQ-PARK-06's
provenance note for why narrowing it was considered and rejected as the fix for a real-world
finding.

**Note — lifecycle state vs. guidance display state.** The four states above govern
*when data is captured or invalidated*. They are not the same thing as what the guidance screen
shows at any given moment, which additionally depends on whether a live location fix exists, how
fresh it is, and whether a heading reading exists. **Requirement:** the guidance
display's state shall be specified as an explicit, exhaustively ordered priority list — highest-
priority condition checked first, every reachable combination of (lifecycle state × stored-location
presence × live-fix presence/freshness × heading presence) accounted for by exactly one branch —
not left to be inferred from a diagram or from prose describing only the "normal" cases. See
REQ-GUIDE-06 for the specific branches.

## 3. Requirements

### 3.1 Parking Detection

- **REQ-PARK-01 (Trigger):** The system shall increase location sampling frequency to
  once every 5 seconds when phone speed is ≤ 5 mph, following a preceding DRIVING
  state.
- **REQ-PARK-02 (Convergence):** The system shall declare state PARKED when 3
  consecutive location samples each fall within a 10-meter radius of one another.
  Convergence is defined pairwise across all three samples, not just consecutively.
  Formally: samples S1, S2, S3 converge if and only if
  `distance(S1,S2) ≤ 10m AND distance(S2,S3) ≤ 10m AND distance(S1,S3) ≤ 10m`.
  Worked example: pairwise distances of 4m, 6m, and 9m converge (all three ≤ 10m). Pairwise
  distances of 4m, 6m, and 11m do **not** converge, even though two of the three pairs are within
  range — the third pair failing is sufficient to reject convergence.
- **REQ-PARK-03 (Parked Location):** Upon PARKED declaration, the system shall store
  the centroid of the 3 converging samples as the Parked Location, with an accuracy radius
  computed per REQ-UNC-02 (new in v5 — see Section 3.2).
- **REQ-PARK-04 (Persistence):** The Parked Location shall be persisted to durable
  storage such that it survives app termination, phone restart, and OS process death,
  and shall be restored on next app launch if state is PARKED or FINDING. If the persisted record
  cannot be read back, see REQ-PARK-09 (new in v5).
- **REQ-PARK-05 (Invalidation):** The system shall delete the current Parked Location
  (not any future History record) upon transition from PARKED or FINDING to DRIVING,
  where DRIVING is detected at speed > 25 mph. Invalidation shall also transition the persisted
  lifecycle state itself to FINDING/DRIVING as appropriate, not merely clear the location field
  while leaving a stale state value behind.
- **REQ-PARK-06 (Re-convergence recovery):** While state is PARKED, the system shall continue
  evaluating incoming location samples at the `SAMPLING_INTERVAL_PARKING` cadence for a bounded
  recovery window of `PARKED_RECOVERY_WINDOW` following the PARKED declaration's timestamp — this
  evaluation is not gated on a preceding DRIVING state, unlike REQ-PARK-01. If, within that
  window, a new set of `CONVERGENCE_SAMPLE_COUNT` samples converges (per REQ-PARK-02) at a
  location outside `CONVERGENCE_RADIUS` of the currently stored Parked Location, the system shall
  treat the original PARKED declaration as premature: it shall discard the previous Parked
  Location, store the newly converged location per REQ-PARK-03, and remain in state PARKED. The
  recovery window is measured from the **original** PARKED declaration's timestamp and is never
  reset or extended by a correction — a corrected Parked Location keeps the time of the
  declaration that opened the window, not the time of the correction. [**Resolved in v5,
  2026-10-02:** v4 stated that each correction *resets* the window from its own timestamp, to
  cover a longer search for a spot via multiple corrections in sequence. Real implementation
  experience (RR1-013) found the opposite is the safer default: an unbounded chain of resets makes
  the window's upper bound meaningless for the exact failure mode it exists to prevent — a driver
  who parks, walks away, and settles somewhere else produces the identical "movement, then a new
  convergence" pattern as a legitimate multi-step search for a spot, and a resetting window cannot
  tell them apart no matter how the reset is worded. Anchoring to the original declaration is a
  strictly simpler, strictly safer rule, at the acceptable cost that a genuinely long search for a
  spot (longer than `PARKED_RECOVERY_WINDOW`) is not corrected — this is the known limit already
  documented below, not a new one introduced by this change.] Once `PARKED_RECOVERY_WINDOW` has
  elapsed since the original PARKED declaration with no further correction, this mechanism stops;
  only REQ-PARK-05's drive-away invalidation (crossing `DRIVING_SPEED_THRESHOLD`) may change the
  stored location from that point forward. Readings used for recovery convergence shall be at
  least `SAMPLING_INTERVAL_PARKING` apart; a flood of readings arriving faster than that cadence
  (e.g., from a display actively sampling at a faster guidance-visible rate — see REQ-PARK-07)
  shall be thinned rather than allowed to converge a recovery window at a pace no real walking or
  driving could produce.

  **Why this exists, and why it's time-bounded rather than open-ended:** found in real-world
  testing on an actual parking-lot access road — driving at highway speed (DRIVING), slowing to an
  access-road speed of 15-20 mph (inside the 5-25 mph dead zone, so state stays DRIVING, no
  transition), then a brief stop at ≤5 mph before entering the lot (e.g., checking for cross
  traffic) triggered PARKING and, if held long enough, a false PARKED — after which creeping
  through the lot at low speed looking for a real spot never reached `DRIVING_SPEED_THRESHOLD`
  (parking-lot speeds don't reach 25 mph), so REQ-PARK-05 never fired and the false location was
  never cleared. Confirmed recurring, unchanged, in a second independent build (RR1-012) — this is
  not a one-off defect of a single implementation, it is a gap in what the requirements said.

  Two alternative fixes were considered and rejected/deferred, recorded here so they aren't
  rediscovered: (1) simply lowering `DRIVING_SPEED_THRESHOLD` (e.g. to 16 mph) was rejected —
  it would shrink the 5-25 mph dead zone that specifically protects against false triggers from
  ordinary city stop-and-go traffic and biking/jogging, trading this failure for a more common one
  elsewhere. (2) Gating the recovery on the device's activity-recognition state remaining
  in-vehicle was identified as a more robust long-term fix, but deferred to production as more
  implementation scope than this MVP correction warrants; see Section 5.

  **This MVP fix is itself understood to be a placeholder for a much better production mechanism
  (see Section 6) — `PARKED_RECOVERY_WINDOW` is very likely too short for a production scenario
  like waiting for another driver to back out of a spot, which is exactly the kind of dwell-time
  case production detection is meant to handle properly rather than via a fixed time window.**

  **The time bound itself is the safety-critical part of this requirement, not an incidental
  detail:** without it, a driver who genuinely parks and then walks away carrying the phone would
  trigger the identical pattern this mechanism watches for, and the mechanism would silently
  overwrite the correct parked location with the driver's own walking destination. That would be a
  worse defect than the one being fixed here (a confidently wrong answer instead of a merely
  premature one), so this recovery must never run unbounded, and must never be extended by its own
  corrections.

- **REQ-PARK-07 (Background sampling cadence by lifecycle state — new in v5, resolves divergence
  #1):** The system's location-sampling rate shall vary by lifecycle state and display visibility,
  not run at a single fixed rate throughout. Specifically: `DRIVING` and `PARKING` states sample
  at `SAMPLING_INTERVAL_PARKING`; `PARKED` with the guidance display visible samples at
  `SAMPLING_INTERVAL_GUIDANCE`; `PARKED` with the guidance display not visible and outside any open
  recovery window (REQ-PARK-06) samples at `SAMPLING_INTERVAL_IDLE`; `PARKED` with an open recovery
  window samples at `SAMPLING_INTERVAL_PARKING` regardless of guidance visibility, so a correction
  can converge in a comparable amount of time to the original declaration. If the platform
  provides an in-vehicle activity-recognition hint while idle-watching, the system shall raise its
  sampling rate to `SAMPLING_INTERVAL_PARKING` to detect drive-away sooner; this hint shall never
  change the lifecycle state itself or pause sampling, only adjust its rate. [**Resolves divergence
  #1:** one of the two existing builds requests a single fixed-rate location subscription and
  never re-profiles it despite a doc comment and fully-implemented-but-dead faster tiers claiming
  otherwise — its real background convergence latency (~60-90s) silently contradicts its own named
  constant (~10-15s implied). This requirement exists so a fresh build cannot make that same
  claim-vs-behavior gap, in either direction, without it being a visible spec violation rather than
  an unnoticed one.]
- **REQ-PARK-08 (Missing or invalid sensor field handling — new in v5, resolves divergence #3):** A
  location reading with no reported accuracy value shall be excluded from convergence-window
  evaluation (REQ-PARK-02, REQ-PARK-06) entirely — never treated as a default of zero, and never
  interpreted as "perfectly accurate." A reading with no reported speed value shall be excluded
  from any speed-threshold-based lifecycle transition check — never treated as a default of zero,
  and never interpreted as "stationary." [**Resolves divergence #3:** one of the two existing
  builds never checks for a missing accuracy/speed field before using it, and the underlying
  platform API returns a bare `0.0` for an absent field rather than throwing or returning null —
  silently manufacturing false precision (treating "unknown accuracy" as "perfect accuracy") or a
  false stop (treating "unknown speed" as "stationary").]
- **REQ-PARK-09 (Persistence corruption handling — new in v5, resolves divergence #6):** If the
  persisted Parked Location or lifecycle state cannot be read back due to corruption or a
  deserialization failure, the system shall log the failure and fall back to state FINDING with no
  stored Parked Location, rather than silently continuing in an undefined or stale state. This
  fallback preserves the invariant already required by REQ-PARK-04/FR-018 (a location is held only
  in PARKED) even when the stored record itself is unreadable. A corrupted record shall not be
  left in place to cause the same failure on every subsequent launch; the storage layer shall
  recover to a default (empty) state so later writes succeed normally. [**Resolves divergence #6:**
  one of the two existing builds silently swallows any read exception with no logging and never
  repairs the corrupted file on disk, so a once-corrupted store stays corrupted and invisible
  forever.]
- **REQ-PARK-10 (Speed-filter cold-start semantics — new in v5, resolves divergence #7):** The
  smoothed-speed filter (used for all speed-threshold lifecycle transitions) shall report "no
  valid smoothed speed available" until its full sample window (`SPEED_FILTER_WINDOW_SIZE`
  readings) has been populated. No speed-threshold-based lifecycle transition shall fire from an
  incomplete window; the system shall hold its current state until enough samples exist. [**Resolves
  divergence #7:** one of the two existing builds returns the single raw, unsmoothed reading until
  3 samples exist, meaning a transition can fire on sample #1 with no smoothing applied at all —
  defeating the purpose of having a filter in the first place during exactly the startup window
  where a spurious single-sample spike is most likely.]

### 3.2 Uncertainty

- **REQ-UNC-01 (Live guidance uncertainty):** The system shall calculate the uncertainty used
  during active guidance as the sum of the reported sensor precision (accuracy radius) of the
  stored Parked Location fix and the reported sensor precision of the current live phone location
  fix.
- **REQ-UNC-02 (Parked Location accuracy-radius formula — new in v5, resolves divergence #2):**
  Upon storing a Parked Location (REQ-PARK-03) or correcting one (REQ-PARK-06), the system shall
  compute its accuracy radius as the **maximum, over the converging samples, of that sample's own
  reported accuracy radius plus that sample's distance from the computed centroid.** This is
  deliberately more conservative than a simple arithmetic mean of the samples' own accuracy
  values, because it accounts for spatial spread between the converging samples, not only each
  sample's individual sensor precision — two samples that are individually very accurate but
  3 meters apart from each other produce a less certain centroid than either sample's own accuracy
  alone would suggest, and the formula must reflect that. [**Resolves divergence #2:** this was
  identified as a difference during the Run 1 generation itself and deliberately left unsteered at
  the time, under the two-tier methodology, as a seemingly neutral engineering choice. The
  after-the-fact comparison found it is not neutral — it directly affects cone width and arrival
  timing, which are user-facing and safety-relevant (an under-wide cone is a confidently wrong
  answer, the same class of defect REQ-GUIDE-01's heading-correction requirement exists to
  prevent). It must be pinned explicitly rather than left to agent judgment.]

### 3.3 Guidance Display

- **REQ-GUIDE-01 (Cone geometry):** The system shall render a guidance cone whose
  half-angle equals `atan(uncertainty_radius / distance_to_parked_location)`, with the
  cone centerline pointing toward the Parked Location and adjusted for the phone's
  magnetic orientation. Cone centerline is not itself visible. Person icon anchors at
  the user's position (base of cone); car icon anchors at the opposite end, along the
  centerline.

  - **Centerline bearing calculation:** the display bearing shall be computed as:

    ```
    display_bearing = (360 − device_heading + bearing_to_car) mod 360
    ```

    where `device_heading` is the phone's current compass heading and `bearing_to_car`
    is the absolute/true bearing computed from the phone's lat/long to the Parked Location's
    lat/long. If the raw sum is ≥ 360, subtract 360 to keep the result in the range 0–360 degrees.
    `device_heading` shall be the phone's raw magnetic compass reading, remapped for the device's
    current display/surface rotation (new in v5 — see below) and corrected for magnetic
    declination to true north, before use in this formula. Raw, uncorrected magnetic heading shall
    not be used.

  - **Display-rotation remapping (new in v5, resolves divergence #5):** the raw heading sensor
    reading is reported in the device's default sensor coordinate frame, which does not
    automatically track the current display/Surface rotation. Before magnetic-declination
    correction, the system shall remap the sensor reading for the device's current display
    rotation (e.g., via the platform's coordinate-system remap facility keyed to the current
    `Surface` rotation on Android, or the equivalent on other platforms) so that `device_heading`
    reflects the phone's actual pointing direction regardless of whether it is held in portrait or
    landscape. [**Resolves divergence #5:** one of the two existing builds shipped this
    unimplemented, with an explicit TODO comment acknowledging the cone's bearing is wrong in
    non-default device orientations. This was a known gap left as a comment rather than a
    requirement, and must be a MUST-implement, testable requirement so it cannot ship
    unimplemented again.]

  - **Heading smoothing (new in v5, folds forward ledger CR-17):** `device_heading` shall be
    derived from a smoothed/filtered orientation source (e.g., the platform's fused
    rotation-vector sensor) rather than the raw, instantaneous magnetometer reading, to avoid
    visible cone jitter from ordinary sensor noise. [Real-world testing found the cone visibly
    jittery when fed raw compass data on every update; one of the two existing builds
    independently avoided this by choosing a gyro-smoothed rotation vector, the other did not
    specify a choice at all. This requirement makes that choice explicit rather than leaving it to
    be rediscovered or left undecided.]

  - **Non-round display handling (MVP):** because the device display is not round,
    the rendered cone half-angle would otherwise vary with device rotation
    (portrait vs. landscape use different physical axes). For MVP, cone half-angle
    geometry shall be computed and rendered relative to the minimum display
    dimension (display width in portrait orientation), centered within that
    dimension, so the cone renders consistently regardless of rotation. This trades
    unused space on the longer axis for a simple, distortion-free MVP rendering; a
    rotation-aware/elliptical rendering is deferred as a future refinement, not an
    MVP requirement.

- **REQ-GUIDE-02 (Arrival threshold):** When the computed cone half-angle reaches 45°,
  the system shall replace the cone display with a "You have arrived" message.
- **REQ-GUIDE-03 (Arrival confirmation):** Upon reaching the arrival threshold, the
  system shall present a confirmation prompt ("Do you see your car?").
- **REQ-GUIDE-04 (Distance display):** The system shall display the distance to the
  Parked Location as text in the middle of the guidance display, updated as the user
  moves. Distance shall be shown in feet when the distance is ≤ 500 feet, and in
  miles when the distance exceeds 500 feet (see `DISTANCE_UNIT_THRESHOLD` in Section
  4).
- **REQ-GUIDE-05 (Fix staleness fallback):** If the live location fix's age
  exceeds `FIX_STALENESS_TIMEOUT` (Section 4), the system shall treat live guidance as
  unavailable and fall back to the same "location unavailable" display used when no live fix has
  ever been received, rather than continuing to render a cone computed from a stale fix. This
  applies regardless of whether a Parked Location is held.
- **REQ-GUIDE-06 (Display-state priority order):** The guidance display's rendered
  state shall be computed by evaluating, in this fixed order, the first branch that applies (see
  the lifecycle-vs-display-state note in Section 2): (1) lifecycle state is DRIVING → show the
  driving message; (2) lifecycle state is FINDING (no Parked Location held) → show "location
  unavailable"; (3) lifecycle state is PARKING → show "sensing you will be parking soon"; (4)
  lifecycle state is PARKED and (no live fix, OR live fix is stale per REQ-GUIDE-05, OR no heading
  reading per REQ-GUIDE-07) → show "location unavailable"; (5) lifecycle state is PARKED and a
  fresh fix and heading both exist → render guidance per REQ-GUIDE-01–04. No other combination is
  reachable.
- **REQ-GUIDE-07 (Heading staleness and reliability — new in v5, resolves divergence #4):** The
  system shall treat a heading reading as stale — and therefore absent for the purposes of
  REQ-GUIDE-06's priority order — once `HEADING_STALENESS_TIMEOUT` (Section 4) has elapsed since
  the last heading update with no new reading arriving. The system shall additionally treat a
  heading as absent for as long as the platform reports its orientation sensor as unreliable
  (e.g., Android's `SENSOR_STATUS_UNRELIABLE`), resuming normal use immediately once a fresh,
  reliable reading arrives. [**Resolves divergence #4:** one of the two existing builds has no
  staleness concept at all once a heading is non-null, and never reacts to a platform-reported
  sensor-unreliable signal — a guidance display could silently keep rendering a cone from a
  heading reading that stopped updating or that the OS itself has flagged as unreliable.]
- **REQ-GUIDE-08 (Guidance responsiveness target — new in v5, resolves divergence #8):** While
  state is PARKED with a valid live fix and heading already available, the system shall render the
  guidance cone within `TIME_TO_GUIDANCE_VISIBLE_TARGET` (Section 4) of the guidance screen
  becoming visible. **This number is an owner-revisable placeholder, not a load-bearing
  correctness gate** — it exists so a fresh Spec-Kit run has one explicit number to target instead
  of inventing its own, which is what produced two different, uncompared numbers (5s vs. 2s)
  between the two existing builds.
- **REQ-GUIDE-09 (Cone containment target — new in v5, resolves divergence #8):** Across scripted
  replay testing (see Section 9, Validation Methodology), at least `CONE_CONTAINMENT_TARGET`
  (Section 4) of sampled guidance frames during active guidance shall have the true parked
  location fall within the rendered uncertainty cone. **This number is an owner-revisable
  placeholder, not a load-bearing correctness gate**, for the same reason as REQ-GUIDE-08 — the two
  existing builds measured this two different ways at two different thresholds (a 95%-containment
  metric vs. two separate 90%-threshold metrics), and neither should be assumed correct by
  default.

### 3.4 Existing Location Services

- The application shall determine whether an existing always-on location service can
  provide required location information before starting another precision location
  service. (Concrete Android strategy — e.g., Fused Location Provider reuse — is an
  open research item, not yet a numbered requirement.)
- **Known deferred gap:** when more than one internal consumer needs location at once (the
  background parking-detection service and the on-screen guidance display both did during the
  MVP build), this document does not yet specify an arbitration rule between them. REQ-PARK-07
  (new in v5) specifies which sampling tier applies in which state, which partially resolves
  whether the PARKED-state background tier is fast enough to double as the guidance-path source
  (it explicitly is, for the GUIDANCE-visible tier) — a full shared-provider arbitration policy
  for genuinely concurrent, independent consumers remains deferred to production.

### 3.5 Testing

- **REQ-TEST-01 (Domain coverage):** All shared domain logic in the KMP common module
  — the parking state machine, convergence calculation (REQ-PARK-02), uncertainty
  calculation (REQ-UNC-01, REQ-UNC-02), the recovery mechanism (REQ-PARK-06, REQ-PARK-07), and the
  bearing/distance/cone-geometry math (REQ-GUIDE-01, REQ-GUIDE-04) — shall have automated unit
  test coverage, since this logic is shared across platforms and errors here affect both Android
  and any future iOS build.
- **REQ-TEST-02 (Requirement-to-test linkage):** Every numbered requirement in this
  document shall have at least one associated automated test case, and each such
  test shall identify the requirement ID it verifies (via test name or an explicit
  annotation/tag, e.g. `REQ-PARK-02`), so that a requirement's verification status
  can be determined without manually reading test bodies. A requirement with zero
  linked tests is a build-blocking gap, not an informational note.
- **REQ-TEST-03 (Platform adapter testing):** Platform-specific adapters (Android
  sensors/location/activity recognition; iOS Core Location/Core Motion, when built)
  shall be tested using platform test doubles/fakes rather than real hardware. Any
  adapter that a test needs to fake shall be defined as an interface with a platform-specific
  implementation class, never as a Kotlin Multiplatform `expect class`. `expect`/`actual` remains
  appropriate only for small, stateless platform functions that no test needs to substitute.
- **REQ-TEST-04 (UI testing):** The Compose guidance display (Section 3.7) shall have
  automated UI tests, using Compose's semantics-based testing APIs, that verify at
  minimum: (a) the rendered cone half-angle matches the formula in REQ-GUIDE-01 for a
  given uncertainty/distance input, (b) distance text switches from feet to miles at
  the `DISTANCE_UNIT_THRESHOLD` boundary, (c) the arrival confirmation prompt
  (REQ-GUIDE-03) appears when cone half-angle reaches `ARRIVAL_CONE_HALF_ANGLE`, (d) the default
  view renders on first launch with no interaction, and (e) the person/car icons anchor at the
  cone's base/apex with no visible centerline drawn.
- **REQ-TEST-05 (Regression on constants):** Changing any named constant in Section 4
  shall not require rewriting existing tests to pass — tests shall reference the
  named constants rather than hardcoding their values, so tests validate behavior
  against whatever the constant is currently set to. This applies to every constant added in v5
  as much as to any existing one.
- **REQ-TEST-06 (Observable-behavior assertions):** A requirement describing a
  user-observable interaction effect (e.g., "confirming a prompt dismisses it") shall have a test
  that asserts the observable effect described — that the element is no longer rendered, that the
  displayed state changed — not merely that some internal state field changed value, and not
  merely that the requirement is "in scope" of an existing test file.
- **REQ-TEST-07 (No unspecified numeric or formula behavior — new in v5, resolves divergences #9,
  #10):** Any numeric threshold, named constant, geometric or mathematical formula, or
  timing/sampling behavior that this document describes shall be captured as a numbered,
  testable requirement in this document (or, for a value Spec-Kit itself must choose during
  generation — e.g. a Success Criterion this document deliberately leaves as a placeholder — in
  `spec.md`'s own numbered Success Criteria) — never left only to `plan.md` or `research.md` prose,
  and never left for the implementing agent to invent a reasonable-sounding default with no
  upstream requirement backing it. [**Resolves divergences #9 and #10:** permission-request
  behavior was a set of formal, traceable FRs in one existing build but only informal Edge-
  Case/Assumptions prose in the other; speed-smoothing and magnetic-declination correction were
  formal FRs in one build and only plan/research-level notes in the other, inconsistently, not in
  the same direction both times. Both gaps meant the traceability/test-coverage gate (REQ-DOC-03)
  could not see the behavior at all in the build that left it informal. This requirement makes
  "where does this belong" a structural rule rather than a per-build judgment call.]

### 3.6 Documentation & Traceability

- **REQ-DOC-01 (Code annotation):** Every requirement in this document that has
  corresponding code shall be traceable from that code via an in-code annotation
  referencing the requirement ID (e.g. a KDoc tag such as `@requirement REQ-PARK-02`,
  or an equivalent structured comment convention chosen at implementation time).
- **REQ-DOC-02 (Traceability report):** A traceability report shall be generatable —
  manually or via a simple script; MVP does not require CI automation — that lists,
  for each requirement ID in this document, the source file(s)/function(s)
  implementing it (per REQ-DOC-01) and the test(s) verifying it (per REQ-TEST-02).
  The report's own logic (regexes, version requirements, etc.) shall be validated against a
  real, representative sample of the spec and source-file formats it will parse before being
  relied upon.
- **REQ-DOC-03 (Discrepancy flagging):** When the traceability report is generated, it
  shall flag: (a) any requirement in this document with no linked code annotation,
  (b) any code annotation referencing a requirement ID that does not exist in
  this document, and (c) any requirement with a code annotation but zero linked tests, as a
  build-blocking gap rather than an informational note.
- **REQ-DOC-04 (Deferred — diff-derived traceability):** unchanged from v2; still explicitly
  out of MVP scope.
- **REQ-DOC-05 (Findings ledger):** Any automated cross-artifact consistency check
  (e.g., an LLM-driven analysis pass) used during Spec-Kit's `/speckit-analyze` step or
  equivalent shall have its findings recorded in an append-only ledger, keyed by finding ID, that
  a later run of the same check is not permitted to silently drop, downgrade, or contradict
  without an explicit recorded reason.

### 3.7 User Interface (Jetpack Compose)

- **REQ-UI-01 (Compose only):** The Android guidance display — cone, person/car
  icons, distance text (REQ-GUIDE-04), and arrival confirmation prompt
  (REQ-GUIDE-03) — shall be implemented using Jetpack Compose. No part of this
  screen shall be built on the legacy Android View system.
- **REQ-UI-02 (State hoisting):** Guidance display rendering (cone geometry per
  REQ-GUIDE-01, icon placement) shall be driven by state hoisted from the shared KMP
  domain layer (e.g. exposed as `StateFlow`) rather than owned or computed inside the
  Composable itself. The Composable shall be a pure function of that state.
- **REQ-UI-03 (Recomposition performance):** The guidance display shall recompose on
  each location or heading update without introducing visible jank, and shall
  respect the minimum-display-dimension cone geometry defined in REQ-GUIDE-01 across
  recompositions triggered by device rotation.
- **REQ-UI-04 (Arrival prompt):** The arrival confirmation prompt (REQ-GUIDE-03)
  shall be implemented as a Compose dialog or overlay, dismissible on either response
  ("yes" or "no") per the existing state-machine behavior — dismissal alone, no
  additional state transition. The dismissed state shall persist across recomposition and device
  rotation for as long as the arrival condition (REQ-GUIDE-02) continues to hold, and shall
  automatically re-arm (become dismissible/showable again) once the arrival condition no longer
  holds.

### 3.8 Runtime Permissions & Foreground Execution

- **REQ-PERM-01 (Required capabilities):** The system shall identify, at requirements time, the
  complete set of runtime-gated capabilities it depends on. For the Android MVP this is: fine
  location, background location (Android 10+), activity recognition (motion/state detection), and
  notifications (Android 13+, required for the foreground service below).
- **REQ-PERM-02 (Request sequencing):** The system shall request required runtime permissions in
  a fixed, deterministic order, one capability per prompt, at the point each is first needed
  rather than as a single bulk request. MVP order: Location → Background Location → Activity
  Recognition → Notifications.
- **REQ-PERM-03 (Non-crashing degradation):** Under no combination of granted, denied, or
  mid-session-revoked permissions shall the system crash or become unresponsive. The absence of a
  required permission shall route to an explicit, user-visible "unavailable" state rather than
  allowing execution to proceed on an unmet precondition (e.g., starting a location-typed
  foreground service without location access).
- **REQ-PERM-04 (Mid-session revocation is not the app's own action):** The system shall not
  assume that permission state can only change through its own UI. A background capability (e.g.
  a foreground service) shall independently verify its own required permission at every point the
  OS could plausibly have restarted or re-entered it — not only at the call site that originally
  started it — because the OS can restart certain background components (e.g. a "sticky" service)
  with no application code in the call stack at all.
- **REQ-PERM-05 (Foreground service disclosure):** Any long-running background capability that
  requires a persistent foreground service shall display a persistent, user-visible notification
  describing what is running and why. Its content shall be decided and recorded here rather than
  left for an implementer to invent mid-build. MVP content (owner-decided 2026-09-26, illustrative
  and revisable): title "Car Finder", text "Monitoring for parking".
- **REQ-PERM-06 (No implicit background start):** The system shall not start any location- or
  sensor-consuming background service before the corresponding runtime permission is confirmed
  granted, including on boot/auto-restart paths.
- **REQ-PERM-07 (Degraded behavior under foreground-only location permission — new in v5, confirms
  RR1-009/F1 on-device):** If only foreground ("while using the app") location permission is
  granted — not background/"allow all the time" — the system shall not attempt to restart its
  location-sensing foreground service from a boot-completed or other background-only entry point;
  the OS denies this for a location-typed foreground service regardless of boot's general
  background-start exemption. Detection shall instead resume automatically the next time the user
  opens the app while state is PARKED or FINDING, and the persistent notification (REQ-PERM-05)
  shall additionally prompt the user to grant "Allow all the time" to restore automatic
  restart-after-reboot. This is a documented, intentional degraded mode, not a crash, a silent
  failure, or an unresolved ambiguity. [Confirmed on-device 2026-10-02 (RR1-011): with only
  foreground permission granted, no detection resumed after reboot and the expected notification
  appeared; with background permission granted, the notification was restored within seconds of
  boot. Both match this requirement.]

## 4. Named Constants

```
PARKING_SPEED_THRESHOLD        = 5 mph
DRIVING_SPEED_THRESHOLD        = 25 mph   (dead zone 5-25 mph: no state change; deliberately kept
                                            as-is -- see REQ-PARK-06's provenance note)
CONVERGENCE_RADIUS             = 10 meters
CONVERGENCE_SAMPLE_COUNT       = 3
SAMPLING_INTERVAL_PARKING      = 5 seconds   (DRIVING, PARKING, and PARKED-with-open-recovery-
                                               window; see REQ-PARK-07)
SAMPLING_INTERVAL_GUIDANCE     = 1 second    (PARKED, guidance screen visible; new in v5, REQ-PARK-07)
SAMPLING_INTERVAL_IDLE         = 20 seconds  (PARKED, guidance not visible, no open recovery
                                               window; new in v5, REQ-PARK-07)
ARRIVAL_CONE_HALF_ANGLE        = 45 degrees
DISTANCE_UNIT_THRESHOLD        = 500 feet    (<= threshold: display feet; > threshold: display miles)
FIX_STALENESS_TIMEOUT          = 30 seconds
HEADING_STALENESS_TIMEOUT      = 2 seconds   (new in v5, named explicitly -- REQ-GUIDE-07)
PARKED_RECOVERY_WINDOW         = 180 seconds (3 minutes). A first estimate, not yet validated
                                               against more real-world data: long enough to cover a
                                               normal search for a spot after a false-early stop,
                                               short enough that a driver who has genuinely parked
                                               and started walking is very unlikely to still be
                                               within it by the time they'd settle somewhere new.
                                               Treat as tunable. Known to be too short for some
                                               production scenarios, e.g. waiting for another car to
                                               back out of a spot -- see Section 6. [**Confirmed in
                                               v5, 2026-10-02:** the first implementation of
                                               REQ-PARK-06 (RR1-013) used 120 seconds as its own
                                               estimate, because this document's value wasn't
                                               available to it at implementation time. The owner has
                                               confirmed 180 seconds is the intended value; that
                                               implementation is accordingly now out of spec against
                                               this document and is tracked for correction
                                               separately (see run-ledger.json) rather than blocking
                                               this seed-hardening pass.]
TIME_TO_GUIDANCE_VISIBLE_TARGET = 2 seconds  (new in v5, REQ-GUIDE-08 -- owner-revisable placeholder)
CONE_CONTAINMENT_TARGET         = 90%        (new in v5, REQ-GUIDE-09 -- owner-revisable placeholder)
SPEED_FILTER_WINDOW_SIZE        = 3 readings (named explicitly -- REQ-PARK-10)
```

Constants in this document are stated as "values that can be changed in one place without
rewriting call sites" — not necessarily user-configurable settings.

## 5. Deferred / Open Items (conscious deferrals, not gaps)

- **Non-convergence handling:** what happens if 3 samples never converge (idling,
  circling for a spot)? MVP assumption: keep sliding the 3-reading window. Not yet
  formalized as a requirement. Still open.
- **Android location-service reuse strategy:** whether/how to detect and reuse an
  existing always-on location service rather than starting a new one. Flagged as a
  Phase 3 research item.
- **Bearing calculation:** already implemented in a prior project by the user
  ("nothing tricky about it") — to be ported/reused rather than redesigned. The true-north
  correction and display-rotation remapping requirements (REQ-GUIDE-01) apply regardless of which
  implementation is reused.
- **Rotation-aware cone rendering:** MVP renders the cone against the minimum display
  dimension only (see REQ-GUIDE-01); a full rotation-aware or elliptical rendering
  is a possible future refinement, not an MVP requirement.
- **Diff-derived traceability (REQ-DOC-04):** deferred architectural direction, see
  Section 3.6.
- **Location-provider arbitration between concurrent internal consumers (REQ-PARK/3.4):** deferred
  to production; REQ-PARK-07 (new in v5) resolves which sampling tier applies per state but not a
  general arbitration policy for independent concurrent consumers.
- **Permission rationale screens / Settings deep-links for permanently-denied permissions, and a
  distinct signal-loss message separate from the generic "unavailable" state:** deferred to
  production.
- **Activity-recognition-gated re-convergence:** using the existing but currently
  unwired `ActivityRecognizer` component to gate REQ-PARK-06's recovery mechanism on the device
  staying in-vehicle, instead of (or in addition to) the time-window bound — considered during the
  2026-09-29 owner decision, judged more robust but more implementation scope than this MVP
  correction warrants. Deferred to production; the time-window-only mechanism is the MVP choice.
- **Production parking-detection mechanism — deliberately not scoped further right now, see
  Section 6.**

## 6. Architectural Constraints (not MVP features — must not be foreclosed)

- **Parked Location vs. Map Pin:** core object is a Parked Location, independent of
  any future map projection.
- **Advertising:** belongs to a future presentation layer, not the location/navigation
  domain model.
- **Breadcrumbs:** future location-history tracking (~every 50 ft while hiking) should
  not be precluded by MVP's location storage structure.
- **Parked History:** "Parking Event → Parked Location" is conceptually distinct from
  "Current Parked Location," to eventually support cases like drive → carpool away →
  need original location later. MVP stores only current location, but the naming/
  structure should not foreclose a future history table.
- **Shared Location Service boundary / Phone Finder:** Car Finder should not assume
  it owns all location sensing. A future sibling system, "Phone Finder," will link
  the phone to a smartwatch via Bluetooth: the phone continuously updates the watch
  with the phone's current location, and on request the watch renders the same
  cone/distance guidance display toward the phone that Car Finder renders toward the
  car. Additionally, the watch can signal the phone to emit a "sonar"-like audio
  ping, giving the user an audio cue to the phone's location as a supplement to the
  visual guidance display. No abstraction is built for this now — only avoid
  decisions that would make it unnecessarily difficult to share this infrastructure later.
- **Beta instrumentation (Phase 5):** parking-event telemetry (sensor readings,
  convergence data, timing) will eventually be transmitted to a server for algorithm
  evaluation. This layer must remain architecturally decoupled from the core Parked
  Location state machine, and will require user consent/opt-in when implemented.
- **Production parking-detection direction (captured for continuity, explicitly
  not MVP scope):** the MVP's speed-threshold state machine (Section 2) is understood to be a
  simplification that production is expected to replace or substantially extend, not a design the
  production system must inherit. Three directions were named and should not be architecturally
  foreclosed by MVP decisions, though none is designed or scoped here:
  - **Longer, more tolerant dwell handling.** Ordinary situations (most concretely: waiting for
    another vehicle to back out of a spot before pulling in) can hold a car at ≤5 mph for longer
    than MVP's fixed `PARKED_RECOVERY_WINDOW` comfortably covers. Production detection needs a
    better basis for distinguishing "still maneuvering into a spot" from "actually left the car"
    than a single fixed timeout.
  - **Walking-activity detection as a corroborating signal.** The existing, currently-unwired
    `ActivityRecognizer` component (Section 5) is intended to eventually help distinguish "user
    got out and is walking away" from "car is still creeping through a lot," rather than inferring
    both purely from speed/location samples.
  - **Direct vehicle-off signal via a car head-unit companion service (the preferred long-term
    mechanism).** Loading a companion service onto the vehicle's Android-based infotainment/
    navigation system (e.g. Android Auto or a similar in-dash Android environment) and using
    Bluetooth to relay location updates and an explicit "engine/ignition off" event would give
    production detection a direct, unambiguous parking signal, rather than inferring parking from
    phone motion at all. If pursued, this would likely become the primary detection path, with the
    phone-speed state machine (Section 2) demoted to a fallback for when no paired vehicle
    companion service is present. No design work has started on this; it requires its own
    feasibility assessment before it becomes more than a stated direction.

## 7. Platform Architecture

- **Android-first**, with the goal that the shared requirement set does ~90%+ of the
  work needed for a future iOS build.
- **Minimum SDK (new in v5, resolves divergence #11):** `minSdk = 26` (Android 8.0). This is an
  explicit requirement, not an implementation detail left to the implementing agent's judgment.
  [**Resolves divergence #11:** the two existing builds targeted different minSdk values (24 vs.
  26), meaning one of them had to implement and the other did not have to implement a real
  pre-`ACCESS_BACKGROUND_LOCATION` permission code path (that permission was introduced in
  Android 10/API 29, but the practical compatibility question is framed around API 26 vs. 24 for
  other platform APIs this app depends on). Owner-decided 2026-10-02: 26, favoring a simpler
  permission model and one fewer legacy code path to specify, implement, and test, over wider
  device-compatibility range. If broader device support becomes a product requirement later, this
  is the specific line to revisit, and REQ-PERM-01 through REQ-PERM-07 would need an explicit
  legacy-path addendum at that time.]
- Proposed split: **shared domain logic** (parking state machine, Parked Location
  model, location-quality/uncertainty calculations, distance/bearing math, guidance
  math, persistence model) as Kotlin Multiplatform; **platform-specific services**
  (Android sensors/location/activity recognition vs. iOS Core Location/Core Motion,
  permissions, background execution, battery management) as native adapters.
  Adapters that a test needs to fake are interfaces with platform implementation classes, not
  `expect class`; `expect`/`actual` is reserved for small stateless platform functions with no
  fake requirement.
- A `PermissionController` abstraction should normalize Android's `Activity`-based
  runtime permission flow and iOS's delegate-callback-based flow into a single suspend
  function per platform. On Android, this abstraction needs a live `Activity`/permission-launcher
  seam to actually show a dialog.
- **Known platform asymmetry:** on iOS, raw accelerometer/gyroscope data via
  `CMMotionManager` requires no permission; only `CMMotionActivityManager` (activity
  recognition) and `CMPedometer` (step counting) are gated behind
  `NSMotionUsageDescription`, requested implicitly on first use. Do not force a symmetric
  "request permission" abstraction across both platforms for motion — expose observable status
  instead.
- KMP vs. Kotlin+later-Swift is not yet a locked decision. A Phase 0 "Platform Capability
  Assessment" is planned to confirm iOS can provide equivalent semantic events at acceptable
  accuracy/power cost.
- The Android UI layer is Jetpack Compose (Section 3.7); this is an Android
  presentation-layer decision and does not affect the shared KMP domain boundary above.

## 8. Phase Plan (working decomposition, pre-Spec Kit)

- **Phase 0** — System definition, platform capability assessment
- **Phase 1** — Parking Detection (this document's Parking Detection requirements)
- **Phase 2** — Find Car (this document's Guidance Display and Compose UI
  requirements)
- **Phase 3** — Battery / sensor optimization, existing-location-service reuse
- **Phase 4** — MVP integration, real-world testing
- **Phase 5** — Beta instrumentation (telemetry, server evaluation, consent)
- **Later** — Map, advertising, breadcrumbs, parked history, algorithm optimization,
  Phone Finder / shared location infrastructure, diff-derived traceability
  (REQ-DOC-04), activity-recognition-gated re-convergence (Section 5), production
  parking-detection mechanism redesign (Section 6, including the head-unit/Bluetooth companion
  service direction)

Testing (3.5), Documentation & Traceability (3.6), and Runtime Permissions & Foreground Execution
(3.8) are cross-cutting and apply across all phases from Phase 1 onward.

## 9. Validation Methodology (new in v5)

For any requirement stated as a percentage or a time target against real-world variability
(currently REQ-GUIDE-08, REQ-GUIDE-09), "scripted replay testing" means: a recorded or synthetic
sequence of location/heading/speed readings, played back through the shared domain logic under
test exactly as a live sensor stream would arrive, with the requirement's target evaluated against
the resulting sequence of computed guidance states. This is distinct from, and does not replace,
the real-device testing already required for OS-boundary behavior (permission flows,
foreground-service lifecycle, process death/restart) under the runtime/emulator gate established
in `car-finder-speckit-handoff-v2.md`. Scripted replay is for behavior that is deterministic given
a fixed input sequence; it is not a substitute for exercising genuine OS unpredictability.

## 10. Provenance

v3 was produced by reading the complete `analysis-findings.md` ledger from the
`001-park-detect-guidance` Spec-Kit feature build against v2, folding forward every finding whose
root cause was traceable to an ambiguity or omission in the requirements.

v4 added the first real-world (actual device, actual driving) test findings, logged as ledger
CR-17 and CR-18 on 2026-09-29: REQ-PARK-06 (re-convergence recovery, time-window-bounded) was
added following two explicit owner decisions made together.

**v5 (2026-10-02) folds forward the Run 1 convergence experiment.** `002-park-detect-guidance` was
generated fresh from the pre-v4 seed documents (blind to the shipped `001` implementation) with a
live Overseer throughout generation, then implemented, device-tested, and had REQ-PARK-06 retrofitted
after real-world testing reproduced CR-18 in the fresh build too (RR1-012, RR1-013 — confirming
this is a requirements gap, not a one-off implementation mistake). Separately, a deliberate,
after-the-fact side-by-side comparison between `001` and `002` — two independent builds descending
from the same requirements lineage, built by different model generations under different process
rigor — surfaced eleven correctness-relevant behavioral divergences that neither build's own
`/speckit.clarify` or `/speckit.analyze` passes had caught, because nothing in the requirements
forced a single answer. v5 closes all eleven (REQ-PARK-07 through REQ-PARK-10, REQ-UNC-02,
REQ-GUIDE-07 through REQ-GUIDE-09, REQ-PERM-07, REQ-TEST-07, and the minSdk requirement in Section
7), corrects REQ-PARK-06's reset-vs-anchor behavior based on real implementation experience
(RR1-013), and resolves the specific PARKED_RECOVERY_WINDOW value discrepancy between this
document (3 minutes) and the first shipped implementation of REQ-PARK-06 (120 seconds) in the
owner's favor (3 minutes stands; the implementation is tracked as a known, separately-handled
discrepancy). See `convergence-efficacy-tracker.md` for the full comparison and the efficacy
metrics this hardening pass is meant to improve on the next run, and `run-ledger.json` for the
underlying findings (RR1-011 through RR1-013).

**This document is the seed for "Run 2"** — a fresh Spec-Kit regeneration into a new feature
directory, same model (Claude Opus 5.5, held constant deliberately — see the confounds section of
`convergence-efficacy-tracker.md`), scored against both `001` and `002`/Run 1, with the specific
goal of measuring whether this hardening pass reduces clarify/analyze burden and post-hoc
divergence count relative to Run 1 — not whether the resulting app is "better" in isolation.
