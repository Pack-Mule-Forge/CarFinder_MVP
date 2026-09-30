# Car Finder — MVP Requirements

**Status:** Draft v3 — reformulated 2026-09-26 by folding MVP-build findings back into v2.
**Scope:** MVP only. Future-phase items are captured as architectural constraints, not requirements.
**Change from v2:** v2 was the seed fed into the first GitHub Spec-Kit run. That run (feature
`001-park-detect-guidance`) surfaced 15 code-review findings and two `/speckit-analyze` passes'
worth of ambiguity/gap findings, all recorded in that feature's `analysis-findings.md` ledger.
This revision folds every finding whose root cause was an upstream requirements gap (as opposed
to a one-off implementation slip) back into this document, so a fresh Spec-Kit run starting from
v3 shouldn't have to rediscover them. Each change below is tagged with the ledger ID(s) that
motivated it, so the chain from "thing that went wrong" to "requirement that now prevents it" is
traceable the same way code-to-requirement traceability is required in §3.6. Nothing here has
been re-validated by an actual re-run yet — that re-run is the next step, not something this
revision claims to guarantee.

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
- **PARKED**: 3 consecutive samples converge within 10m; Parked Location stored
- **FINDING**: **no Parked Location is currently held.** [Resolves R1-I1, CRITICAL — v2 used
  "FINDING" to mean two incompatible things during the MVP build: (a) no parked location has ever
  been captured, and (b) a location is held but live signal has degraded. Only (a) is a lifecycle
  state. (b) is a *display* condition — see the note on display state below — and must never be
  confused with FINDING again.]
- **Arrival confirmation**: "Do you see your car?" prompt shown when cone half-angle
  reaches 45°. Confirming (yes or no) simply dismisses the prompt for MVP — no
  additional state is introduced. The Parked Location is not cleared by this
  confirmation. **This dismissal behavior is a MUST, not an illustrative aside — see REQ-TEST-06,
  added below, which exists specifically because this exact behavior shipped unimplemented for
  most of the MVP build with nothing catching it (ledger R1-G2, escalated to CRITICAL).**
- **Return to DRIVING**: speed > 25 mph clears the current Parked Location
  (REQ-PARK-05) and the cycle restarts.

Speeds between 5–25 mph are an intentional dead zone — no state transition occurs.
This excludes biking/jogging/stop-and-go traffic from falsely triggering PARKING or
DRIVING transitions.

**Note — lifecycle state vs. guidance display state (new in v3).** The four states above govern
*when data is captured or invalidated*. They are not the same thing as what the guidance screen
shows at any given moment, which additionally depends on whether a live location fix exists, how
fresh it is, and whether a heading reading exists. Treating these as one diagram, rather than two
related but distinct functions, was the direct root cause of three separate MVP findings
(R1-I5, R1-I6, R1-D1, and code-review finding CR-11, where "PARKING with no stored location" — a
completely normal, expected combination — fell through to the wrong message because the
display-state logic checked "is there a location" before "what lifecycle state are we in," and
nothing in v2 or the resulting spec said which check should win). **Requirement:** the guidance
display's state shall be specified as an explicit, exhaustively ordered priority list — highest-
priority condition checked first, every reachable combination of (lifecycle state × stored-location
presence × live-fix presence/freshness × heading presence) accounted for by exactly one branch —
not left to be inferred from a diagram or from prose describing only the "normal" cases. See
REQ-GUIDE-05 and REQ-GUIDE-06 below for the specific branches this build had to add after the fact.

## 3. Requirements

### 3.1 Parking Detection

- **REQ-PARK-01 (Trigger):** The system shall increase location sampling frequency to
  once every 5 seconds when phone speed is ≤ 5 mph, following a preceding DRIVING
  state.
- **REQ-PARK-02 (Convergence):** The system shall declare state PARKED when 3
  consecutive location samples each fall within a 10-meter radius of one another.
  **Convergence is defined pairwise across all three samples, not just consecutively**
  [Resolves R1-A1, HIGH — v2's wording was read two different ways during the MVP build].
  Formally: samples S1, S2, S3 converge if and only if
  `distance(S1,S2) ≤ 10m AND distance(S2,S3) ≤ 10m AND distance(S1,S3) ≤ 10m`.
  Worked example: pairwise distances of 4m, 6m, and 9m converge (all three ≤ 10m). Pairwise
  distances of 4m, 6m, and 11m do **not** converge, even though two of the three pairs are within
  range — the third pair failing is sufficient to reject convergence.
- **REQ-PARK-03 (Parked Location):** Upon PARKED declaration, the system shall store
  the centroid of the 3 converging samples as the Parked Location.
- **REQ-PARK-04 (Persistence):** The Parked Location shall be persisted to durable
  storage such that it survives app termination, phone restart, and OS process death,
  and shall be restored on next app launch if state is PARKED or FINDING.
- **REQ-PARK-05 (Invalidation):** The system shall delete the current Parked Location
  (not any future History record) upon transition from PARKED or FINDING to DRIVING,
  where DRIVING is detected at speed > 25 mph. Invalidation shall also transition the persisted
  lifecycle state itself to FINDING/DRIVING as appropriate, not merely clear the location field
  while leaving a stale state value behind [clarifies a distinction that a code-review finding,
  CR-10, showed was easy to get half-right: clearing the location without also updating state].

### 3.2 Uncertainty

- **REQ-UNC-01 (Definition):** The system shall calculate Parked Location uncertainty
  as the sum of the reported sensor precision (accuracy radius) of the stored Parked
  Location fix and the reported sensor precision of the current live phone location
  fix.

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
    **`device_heading` shall be the phone's raw magnetic compass reading corrected for magnetic
    declination to true north before use in this formula. Raw, uncorrected magnetic heading shall
    not be used** [Resolves R1-U1, CRITICAL — this is the most dangerous kind of gap in the whole
    build: an uncorrected reading produces a cone that looks entirely plausible and is simply
    wrong, which is exactly the "seems incomplete, not wrong" line this project is trying not to
    cross].

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
- **REQ-GUIDE-05 (Fix staleness fallback — new in v3):** If the live location fix's age
  exceeds `FIX_STALENESS_TIMEOUT` (Section 4), the system shall treat live guidance as
  unavailable and fall back to the same "location unavailable" display used when no live fix has
  ever been received, rather than continuing to render a cone computed from a stale fix. This
  applies regardless of whether a Parked Location is held. [Resolves R1-U2/HIGH and code-review
  CR-1/CR-2 — v2 had no requirement at all for what to do when the phone's location signal
  degrades (e.g., walking into a parking garage) while guidance is active; without one, the MVP
  build shipped a cone that kept pointing from the last known fix — a believable but wrong
  answer, not an obviously broken one.]
- **REQ-GUIDE-06 (Display-state priority order — new in v3):** The guidance display's rendered
  state shall be computed by evaluating, in this fixed order, the first branch that applies (see
  the lifecycle-vs-display-state note in Section 2): (1) lifecycle state is DRIVING → show the
  driving message; (2) lifecycle state is FINDING (no Parked Location held) → show "location
  unavailable"; (3) lifecycle state is PARKING → show "sensing you will be parking soon"; (4)
  lifecycle state is PARKED and (no live fix, OR live fix is stale per REQ-GUIDE-05, OR no heading
  reading) → show "location unavailable"; (5) lifecycle state is PARKED and a fresh fix and
  heading both exist → render guidance per REQ-GUIDE-01–04. No other combination is reachable.
  [This is the ordering that R1-I5, R1-I6, R1-D1, and CR-11 collectively forced the MVP build to
  discover ad hoc; stating it explicitly here is meant to remove the need to rediscover it.]

### 3.4 Existing Location Services

- The application shall determine whether an existing always-on location service can
  provide required location information before starting another precision location
  service. (Concrete Android strategy — e.g., Fused Location Provider reuse — is an
  open research item, not yet a numbered requirement.)
- **Known deferred gap (v3):** when more than one internal consumer needs location at once (the
  background parking-detection service and the on-screen guidance display both did during the
  MVP build), this document does not yet specify an arbitration rule between them. The MVP build
  handled this by having each consumer open its own request independently; a shared-provider
  arbitration policy, and a review of whether the PARKED-state background sampling tier is fast
  enough to double as the guidance-path source, are explicitly deferred to production (ledger
  R1-I3, R1-I4 — HIGH severity, but architectural rather than correctness-critical for a
  single-consumer-at-a-time MVP).

### 3.5 Testing

- **REQ-TEST-01 (Domain coverage):** All shared domain logic in the KMP common module
  — the parking state machine, convergence calculation (REQ-PARK-02), uncertainty
  calculation (REQ-UNC-01), and the bearing/distance/cone-geometry math
  (REQ-GUIDE-01, REQ-GUIDE-04) — shall have automated unit test coverage, since this
  logic is shared across platforms and errors here affect both Android and any
  future iOS build.
- **REQ-TEST-02 (Requirement-to-test linkage):** Every numbered requirement in this
  document shall have at least one associated automated test case, and each such
  test shall identify the requirement ID it verifies (via test name or an explicit
  annotation/tag, e.g. `REQ-PARK-02`), so that a requirement's verification status
  can be determined without manually reading test bodies. **A requirement with zero
  linked tests is a build-blocking gap, not an informational note** — see REQ-DOC-03.
- **REQ-TEST-03 (Platform adapter testing):** Platform-specific adapters (Android
  sensors/location/activity recognition; iOS Core Location/Core Motion, when built)
  shall be tested using platform test doubles/fakes rather than real hardware. **Any
  adapter that a test needs to fake shall be defined as an interface with a platform-specific
  implementation class, never as a Kotlin Multiplatform `expect class`** [Resolves CR-8,
  CRITICAL — a final `expect class` cannot be subclassed or faked; this exact contradiction
  between the constitution and the platform-adapter contract went undetected until the first test
  compile, and blocked the entire shared test suite until it was found. `expect`/`actual` remains
  appropriate only for small, stateless platform functions that no test needs to substitute.]
- **REQ-TEST-04 (UI testing):** The Compose guidance display (Section 3.7) shall have
  automated UI tests, using Compose's semantics-based testing APIs, that verify at
  minimum: (a) the rendered cone half-angle matches the formula in REQ-GUIDE-01 for a
  given uncertainty/distance input, (b) distance text switches from feet to miles at
  the `DISTANCE_UNIT_THRESHOLD` boundary, (c) the arrival confirmation prompt
  (REQ-GUIDE-03) appears when cone half-angle reaches `ARRIVAL_CONE_HALF_ANGLE`, (d) the default
  view renders on first launch with no interaction, and (e) the person/car icons anchor at the
  cone's base/apex with no visible centerline drawn. [(d) and (e) are new in v3: these were found
  to be implemented but genuinely untested — not merely a tooling artifact — by the corrected
  traceability report late in the MVP build, ledger entries under "Verified test-coverage gaps."]
- **REQ-TEST-05 (Regression on constants):** Changing any named constant in Section 4
  shall not require rewriting existing tests to pass — tests shall reference the
  named constants rather than hardcoding their values, so tests validate behavior
  against whatever the constant is currently set to.
- **REQ-TEST-06 (Observable-behavior assertions — new in v3):** A requirement describing a
  user-observable interaction effect (e.g., "confirming a prompt dismisses it") shall have a test
  that asserts the observable effect described — that the element is no longer rendered, that the
  displayed state changed — not merely that some internal field changed value, and not merely that
  the requirement is "in scope" of an existing test file. [Resolves R1-G2, escalated to CRITICAL
  during the MVP build: the arrival-prompt dismissal behavior in Section 2 was already stated
  correctly in v2, but nothing enforced that a real test exercise it, and it shipped as an
  unimplemented no-op for most of the build before being caught by direct code inspection rather
  than by any test or analysis pass.]

### 3.6 Documentation & Traceability

- **REQ-DOC-01 (Code annotation):** Every requirement in this document that has
  corresponding code shall be traceable from that code via an in-code annotation
  referencing the requirement ID (e.g. a KDoc tag such as `@requirement REQ-PARK-02`,
  or an equivalent structured comment convention chosen at implementation time).
- **REQ-DOC-02 (Traceability report):** A traceability report shall be generatable —
  manually or via a simple script; MVP does not require CI automation — that lists,
  for each requirement ID in this document, the source file(s)/function(s)
  implementing it (per REQ-DOC-01) and the test(s) verifying it (per REQ-TEST-02).
  **The report's own logic (regexes, version requirements, etc.) shall be validated against a
  real, representative sample of the spec and source-file formats it will parse before being
  relied upon** [a code-review finding, CR-14, showed a traceability script can be written,
  believed correct, and never actually produce a valid result for months, because its assumptions
  about file format didn't match reality and nothing exercised it end to end].
- **REQ-DOC-03 (Discrepancy flagging):** When the traceability report is generated, it
  shall flag: (a) any requirement in this document with no linked code annotation,
  (b) any code annotation referencing a requirement ID that does not exist in
  this document, and (c) **any requirement with a code annotation but zero linked tests, as a
  build-blocking gap rather than an informational note** (new in v3, ties to REQ-TEST-02/06).
- **REQ-DOC-04 (Deferred — diff-derived traceability):** unchanged from v2; still explicitly
  out of MVP scope.
- **REQ-DOC-05 (Findings ledger — new in v3):** Any automated cross-artifact consistency check
  (e.g., an LLM-driven analysis pass) used during Spec-Kit's `/speckit-analyze` step or
  equivalent shall have its findings recorded in an append-only ledger, keyed by finding ID, that
  a later run of the same check is not permitted to silently drop, downgrade, or contradict
  without an explicit recorded reason. [This directly generalizes the mechanism this MVP build
  had to invent ad hoc after discovering that two `/speckit-analyze` runs on nearly identical
  files produced different, non-overlapping finding sets, with the second run reporting "zero
  ambiguity" while several CRITICAL items from the first run were still genuinely unresolved in
  the files. Treating any single analysis pass as authoritative was the mistake; the ledger is
  the fix.]

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
  holds. **(Made explicit in v3; this exact behavior is what REQ-TEST-06 now requires a test to
  assert.)**

### 3.8 Runtime Permissions & Foreground Execution (new in v3)

v2 had no requirements at all in this area. This gap produced two of the most serious findings
in the MVP build: a 100%-reproducible launch crash on a fresh install with no permissions ever
granted (code-review CR-5), and a second, distinct crash reachable only when a permission already
granted was revoked *while the background service was running*, which the OS could trigger by
restarting the service with no app code path involved (code-review CR-13). Both were found only
by actually running the app on an emulator — no unit test using fakes could have caught either,
because both are about how the OS itself behaves at a boundary the app doesn't fully control.

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

## 4. Named Constants

```
PARKING_SPEED_THRESHOLD    = 5 mph
DRIVING_SPEED_THRESHOLD    = 25 mph   (dead zone 5–25 mph: no state change)
CONVERGENCE_RADIUS         = 10 meters
CONVERGENCE_SAMPLE_COUNT   = 3
SAMPLING_INTERVAL_PARKING  = 5 seconds
ARRIVAL_CONE_HALF_ANGLE    = 45 degrees
DISTANCE_UNIT_THRESHOLD    = 500 feet   (≤ threshold: display feet; > threshold: display miles)
FIX_STALENESS_TIMEOUT      = 30 seconds (new in v3 — see REQ-GUIDE-05; a live fix older than this
                                          is treated as unavailable, not as a valid-but-old input)
```

Constants in this document are stated as "values that can be changed in one place without
rewriting call sites" — not necessarily user-configurable settings. (Reworded in v3 from v2's
"configurable," which was read during the MVP build as implying an exposed settings UI that was
never intended; owner decision 2026-09-26.)

## 5. Deferred / Open Items (conscious deferrals, not gaps)

- **Non-convergence handling:** what happens if 3 samples never converge (idling,
  circling for a spot)? MVP assumption: keep sliding the 3-reading window. Not yet
  formalized as a requirement. **Still open after the MVP build — not resolved by this
  revision.**
- **Android location-service reuse strategy:** whether/how to detect and reuse an
  existing always-on location service rather than starting a new one. Flagged as a
  Phase 3 research item.
- **Bearing calculation:** already implemented in a prior project by the user
  ("nothing tricky about it") — to be ported/reused rather than redesigned. **The true-north
  correction requirement (REQ-GUIDE-01) applies regardless of which implementation is reused.**
- **Rotation-aware cone rendering:** MVP renders the cone against the minimum display
  dimension only (see REQ-GUIDE-01); a full rotation-aware or elliptical rendering
  that uses the full display in any orientation is a possible future refinement, not
  an MVP requirement.
- **Diff-derived traceability (REQ-DOC-04):** deferred architectural direction, see
  Section 3.6.
- **Location-provider arbitration between concurrent internal consumers, and PARKED-tier sampling
  rate for guidance use (REQ-PARK/3.4):** deferred to production; see the note under Section 3.4.
- **Permission rationale screens / Settings deep-links for permanently-denied permissions, and a
  distinct signal-loss message separate from the generic "unavailable" state:** deferred to
  production; the MVP's REQ-PERM section requires non-crashing degradation, not a polished
  recovery UX.

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
  decisions (e.g. in the location service or guidance-display domain model) that
  would make it unnecessarily difficult to share this infrastructure later.
- **Beta instrumentation (Phase 5):** parking-event telemetry (sensor readings,
  convergence data, timing) will eventually be transmitted to a server for algorithm
  evaluation. This layer must remain architecturally decoupled from the core Parked
  Location state machine, and will require user consent/opt-in when implemented.

## 7. Platform Architecture

- **Android-first**, with the goal that the shared requirement set does ~90%+ of the
  work needed for a future iOS build.
- Proposed split: **shared domain logic** (parking state machine, Parked Location
  model, location-quality/uncertainty calculations, distance/bearing math, guidance
  math, persistence model) as Kotlin Multiplatform; **platform-specific services**
  (Android sensors/location/activity recognition vs. iOS Core Location/Core Motion,
  permissions, background execution, battery management) as native adapters.
  **Adapters that a test needs to fake are interfaces with platform implementation classes, not
  `expect class`; `expect`/`actual` is reserved for small stateless platform functions with no
  fake requirement** (see REQ-TEST-03 — restated here because Section 7 is where the MVP build's
  planning artifacts actually contradicted this and it went uncaught until code was written).
- A `PermissionController` abstraction should normalize Android's `Activity`-based
  runtime permission flow and iOS's delegate-callback-based flow into a single suspend
  function per platform. On Android, this abstraction needs a live `Activity`/permission-launcher
  seam to actually show a dialog — a version of it that only reads status against an Application
  `Context` is insufficient and was an MVP build finding (research note R-10, ledger T110).
- **Known platform asymmetry:** on iOS, raw accelerometer/gyroscope data via
  `CMMotionManager` requires no permission; only `CMMotionActivityManager` (activity
  recognition) and `CMPedometer` (step counting) are gated behind
  `NSMotionUsageDescription`, and that permission is requested implicitly on first use
  rather than via an explicit request call. Do not force a symmetric "request
  permission" abstraction across both platforms for motion — expose observable status
  instead.
- KMP vs. Kotlin+later-Swift is not yet a locked decision; the domain/platform split
  above should make either viable. A Phase 0 "Platform Capability Assessment" is
  planned to confirm iOS can provide equivalent semantic events (driving/walking
  detection, low-power movement detection, background location, compass, sensor
  fusion) at acceptable accuracy/power cost.
- The Android UI layer is Jetpack Compose (Section 3.7); this is an Android
  presentation-layer decision and does not affect the shared KMP domain boundary
  above.

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
  (REQ-DOC-04)

Testing (3.5), Documentation & Traceability (3.6), and Runtime Permissions & Foreground Execution
(3.8, new in v3) are cross-cutting and apply across all phases from Phase 1 onward, rather than
being scoped to a single phase.

## 9. Provenance (new in v3)

This revision was produced by reading the complete `analysis-findings.md` ledger from the
`001-park-detect-guidance` Spec-Kit feature build against this document, line by line, and folding
forward every finding whose root cause was traceable to an ambiguity or omission in v2 (as opposed
to a one-off coding mistake with no upstream lesson). Findings intentionally **not** folded in
here because they were pure implementation bugs with no requirements-level cause: CR-4 (turned out
to be a non-issue on inspection), CR-6/CR-7/CR-9 (compile and dependency-collection bugs), CR-12
(missing dependency-injection wiring). Those remain valuable process lessons but belong in the
Spec-Kit handoff/constitution document, not here — see `car-finder-speckit-handoff.md`.
