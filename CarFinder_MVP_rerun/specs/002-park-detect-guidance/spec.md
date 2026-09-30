# Feature Specification: Automatic Park Detection & Guidance Back to the Vehicle

**Feature Branch**: `main` (no branch hook configured; spec directory `specs/002-park-detect-guidance`)

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: see `Claude Prompts/prompt-spec.md` — "Build Car Finder, a mobile app
(Android-first, with a shared core intended to support a future iOS release) that determines when a
user has parked their vehicle and then guides them back to it..."

## Clarifications

### Session 2026-09-30

- Q: What does FINDING mean, and what triggers PARKED → FINDING? → A: The premise was corrected.
  FINDING means "no Parked Location is currently held." It is the initial state (covering a fresh
  install with no extra flag). There is **no** PARKED → FINDING transition: the walk back, the
  guidance display, and arrival confirmation all happen while the state stays PARKED. PARKED exits
  only to DRIVING, when speed crosses the driving threshold, and that transition also clears the
  Parked Location.
- Q: What state does a fresh install start in? → A: FINDING (follows from the answer above).
- Q: Does losing the position fix or compass reading while a location is held change the state?
  → A: No. It is only a fallback in how the display looks; the state stays PARKED and the location
  is kept.
- Q: Should one speed reading above 25 mph from PARKED be enough to delete the location? → A: No,
  and the same applies to every speed-based transition. Each speed sample passes through a
  rolling median filter (window = speed-filter window size, default 3) before any threshold check.
  This covers DRIVING entry, PARKING entry, and drive-away equally. It is a median filter, not a
  count of consecutive readings.
- Q: How should "3 readings converge within a 10-meter radius" be measured? → A: Pairwise. Every
  pair of readings in the window must be at most the convergence radius (10 m) apart. Distance to
  the centroid is not the test.
- Q: How should parking detection run when the app isn't open on screen? → A: Always on. Detection
  runs continuously in the background, whether or not the app is open, in every state (FINDING,
  DRIVING, PARKING, PARKED). A persistent notification stays visible from the time location
  permission is granted, and detection restarts after the phone reboots. It is not gated by motion,
  because PARKED needs live samples to detect drive-away even if the app is never opened.
- Q: What should the guidance display show while PARKED when the live fix or compass heading is
  lost? → A: No guidance. All three cases show the single no-guidance view "Parked location
  unavailable.": no location held (FINDING), a stale or unusable fix, and a missing heading. Fix
  loss and heading loss are treated identically, and there are no input-specific messages. The
  principle is to show incomplete information rather than incorrect guidance. This also renames the
  FINDING message, which was "No parked Location yet." in the input.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parking is detected and remembered automatically (Priority: P1)

A driver arrives somewhere, parks, and walks away without touching the phone. Car Finder notices
that the drive has ended, watches the phone settle in one place, and saves where the car is —
together with how precise that saved position is. The saved position is still there hours later,
even if the app was closed, the operating system reclaimed it, or the phone was restarted.

**Why this priority**: Nothing else in the product works without a trustworthy, automatically
captured parked location. This slice alone delivers value: the location exists and can be inspected.

**Independent Test**: Feed a scripted sequence of speed/location readings (driving above the driving
threshold, then slowing to or below the parking threshold, then three readings clustered within the
convergence radius) and verify that the state becomes PARKED and a Parked Location equal to the
centroid of those three readings is stored; then terminate/restart the app and verify it persists.

**Acceptance Scenarios**:

1. **Given** the state is DRIVING, **When** speed drops to or below the parking-speed threshold,
   **Then** the state becomes PARKING and location sampling rises to one reading per
   parking-sampling interval.
2. **Given** the state is PARKING, **When** the most recent convergence-sample-count readings all
   lie within the convergence radius of each other, **Then** the state becomes PARKED and the
   centroid of those readings is stored as the Parked Location together with its accuracy.
3. **Given** the state is PARKING and readings have not converged, **When** another reading
   arrives, **Then** the oldest reading drops out of the window and convergence is re-evaluated
   (no timeout, no failure state).
4. **Given** the state is PARKED with a stored Parked Location, **When** the app is terminated, the
   OS kills its process, or the phone restarts, **Then** on next launch the state is still PARKED
   and the Parked Location is still present.
5. **Given** the state is DRIVING or PARKING, **When** speed is between the parking-speed and
   driving-speed thresholds, **Then** no state transition occurs (dead zone).
6. **Given** a fresh install (state FINDING), **When** the phone is stationary or moving slowly
   (at or below the parking-speed threshold), **Then** the state stays FINDING and no Parked Location
   is stored; PARKING is reachable only from DRIVING.

---

### User Story 2 - Guided back to the car with honest direction and distance (Priority: P1)

Later, the user opens the app and immediately sees which way to walk and how far it is. The
guidance is a cone rather than a single arrow: when the saved position or the user's own position
is imprecise, the cone widens to say so. As the user walks and turns, the cone rotates with the
phone's heading and the distance counts down. The whole walk back happens while the state stays
PARKED.

**Why this priority**: This is the payoff of the product. Together with Story 1 it is the MVP.

**Independent Test**: With a known Parked Location, a known current position, known accuracy
radii, and a known compass heading, verify the cone's direction, half-angle, and the distance text
(including the feet↔miles switch) match the formulas in FR-020–FR-026.

**Acceptance Scenarios**:

1. **Given** the state is PARKED, **When** the app is opened, **Then** the guidance display is
   shown without any user action, and the state remains PARKED.
2. **Given** the guidance display is shown, **When** the user's position or the phone's heading
   changes, **Then** the cone direction and distance text update to match.
3. **Given** the combined uncertainty grows (either fix becomes less precise), **When** the display
   updates, **Then** the cone widens accordingly.
4. **Given** the distance to the car is 500 feet or less, **Then** it is shown in feet; **Given**
   it is more than 500 feet, **Then** it is shown in miles.
5. **Given** the device is rotated between portrait and landscape, **Then** the cone keeps the
   same size and proportions, centered on the screen.
6. **Given** the state is PARKED, **When** the live fix goes stale (FR-034), lacks an accuracy
   radius, or the compass heading is unavailable, **Then** "Parked location unavailable." is shown
   instead of guidance, the state stays PARKED, and the Parked Location is kept; guidance returns
   as soon as a current fix and a heading are both available again.

---

### User Story 3 - Arrival is recognized (Priority: P2)

As the user gets close, the remaining distance becomes smaller than the uncertainty itself — the
cone can no longer honestly point anywhere. Instead of continuing to imply more walking is needed,
the app says "You have arrived" and asks "Do you see your car?"

**Why this priority**: Prevents misleading guidance at the end of the walk; valuable but the MVP
still works without it.

**Independent Test**: Drive the computed cone half-angle up to the arrival threshold and verify the
cone is replaced by the arrival message and confirmation prompt; answer either way and verify the
prompt closes, the state is still PARKED, and the Parked Location is unchanged.

**Acceptance Scenarios**:

1. **Given** the guidance display is shown, **When** the cone half-angle reaches the arrival
   half-angle (45°), **Then** the cone and distance text are replaced by "You have arrived" and the
   prompt "Do you see your car?" appears.
2. **Given** the arrival prompt is shown, **When** the user answers Yes or No, **Then** the prompt
   is dismissed, the state stays PARKED, and the Parked Location is not cleared.

---

### User Story 4 - Always-on status when there is nothing to guide to (Priority: P2)

Whenever the app is open but guidance isn't possible, it tells the user plainly: they are
driving, it is sensing that they are about to park, or a parked location is unavailable (none is
held, or guidance can't be computed honestly right now).

**Why this priority**: The default view must never be blank or ambiguous; required for a coherent
MVP but secondary to the core find loop.

**Independent Test**: Put the system into each state and verify the view shown follows the
priority order in FR-016.

**Acceptance Scenarios**:

1. **Given** the state is DRIVING (including the first drive after a fresh install), **Then** the
   view shows "Driving - Waiting to Park."
2. **Given** the state is FINDING (including a fresh install), **Then** the view shows "Parked
   location unavailable."
3. **Given** the state is PARKING, **Then** the view shows "Sensing you will be Parking Soon."
4. **Given** the state is PARKED and guidance is available (FR-031), **Then** the guidance display
   is shown.
5. **Given** the state is PARKED and guidance is not available (FR-031), **Then** the view shows
   "Parked location unavailable."

---

### User Story 5 - Driving away clears the old location (Priority: P2)

When the user gets back in the car and drives off, the old parked location is discarded so a later
"find my car" never points at yesterday's parking spot.

**Why this priority**: Protects the trustworthiness of Story 2; a stale location is worse than none.

**Independent Test**: From PARKED, feed a speed above the driving threshold and verify the state
becomes DRIVING and no Parked Location remains stored, including after an app restart.

**Acceptance Scenarios**:

1. **Given** the state is PARKED, **When** speed rises above the driving-speed threshold, **Then**
   the Parked Location is deleted and the state becomes DRIVING.
2. **Given** a Parked Location was just deleted by driving away, **When** the app is restarted,
   **Then** no Parked Location is present and the state is not PARKED.

---

### Edge Cases

- **Readings within 10 m of their centroid but more than 10 m apart from each other** (e.g. 3
  readings 9 m apart in a line): not converged; the window keeps sliding.
- **Never converging** (idling, circling for a space): the 3-reading window keeps sliding
  indefinitely in PARKING; no timeout or failure is declared (consciously deferred for MVP).
- **Speed rises above the driving threshold while PARKING** (false stop, e.g. a long red light):
  the state returns to DRIVING and no Parked Location is stored.
- **Slow or stationary phone in FINDING** (fresh install at home, walking): no transition; PARKING
  is reachable only from DRIVING, so no false park occurs before the first drive.
- **Speed in the 5–25 mph dead zone** in any state: no transition.
- **A single anomalous speed reading** (a GPS spike while walking near the car, or one slow reading
  at speed): the rolling median (FR-032) absorbs it, so there is no DRIVING entry, no PARKING
  entry, and no drive-away, and the Parked Location is kept.
- **Distance to the car is zero or effectively zero**: the half-angle is treated as 90°, which is
  above the arrival threshold, so arrival is shown (no division-by-zero failure).
- **Live fix goes stale, is lost, or reports no accuracy radius while PARKED**, or **compass
  heading is unavailable while PARKED**: all are handled identically. "Parked location
  unavailable." is shown (no cone, no distance text); the state stays PARKED and the Parked
  Location is kept. Staleness is re-checked over time, so a fix that stops updating is caught
  even with no new fix arriving (FR-034).
- **Readings without an accuracy radius during PARKING**: excluded from the convergence window,
  because uncertainty must never be silently dropped or approximated.
- **Restored state is PARKED but no Parked Location can be read** (corrupted storage): the state
  falls back to FINDING, so the rule "a location is held only in PARKED" (FR-018) still holds.
- **Arrival prompt dismissed while still within the arrival threshold**: "You have arrived"
  remains shown; the prompt is not re-shown until the half-angle first drops below 45° and then
  reaches it again.
- **Exactly 500 feet**: shown in feet. **Exactly 45°**: arrival is shown.
- **Crossing the feet/miles boundary repeatedly** while standing near 500 feet: the unit follows
  the rule on every update (no hysteresis at MVP).
- **Only foreground location granted**: FR-033 is not met, because restart after reboot requires
  background location ("Allow all the time"). Android does not grant while-in-use location to a
  location foreground service started from a boot receiver. Detection runs while the service is
  running, and after a reboot it resumes the next time the app is opened. While in this state, the
  persistent notification MUST say that background detection needs "Allow all the time", and tapping
  it MUST open the app's location-permission settings. **Permission granted later in system
  Settings**: the service starts the next time the app comes to the foreground.
- **Permissions revoked after grant**: out of assumed scope for MVP behavior design (see
  Assumptions); the app MUST NOT crash.

## Requirements *(mandatory)*

### Functional Requirements

**Parking lifecycle (state machine)**

- **FR-001**: The system MUST maintain exactly one lifecycle state at a time from: FINDING, DRIVING,
  PARKING, PARKED.
- **FR-002**: The system MUST infer parking from the phone's motion and location alone, with no
  manual "I parked" action required from the user.
- **FR-003**: The system MUST transition to DRIVING from any state when filtered speed (FR-032)
  exceeds the driving-speed threshold (25 mph).
- **FR-004**: The system MUST transition DRIVING → PARKING when filtered speed (FR-032) is at or
  below the parking-speed threshold (5 mph). PARKING MUST NOT be entered from any other state.
- **FR-005**: The system MUST NOT change state in response to filtered speeds strictly between the
  parking-speed and driving-speed thresholds (dead zone).
- **FR-032**: Every speed sample MUST pass through a rolling median filter spanning the most recent
  speed-filter-window-size readings (default 3) before any speed threshold check. Every speed-based
  transition (FR-003, FR-004, FR-010, FR-015) MUST be evaluated against the filtered speed, never a
  raw sample, so that a single anomalous reading cannot trigger DRIVING entry, PARKING entry, or
  drive-away. The filter is a median, not a count of consecutive readings.
- **FR-006**: While in PARKING, the system MUST sample location once per parking-sampling interval
  (5 seconds).
- **FR-007**: The system MUST transition PARKING → PARKED when the most recent
  convergence-sample-count (3) consecutive readings converge. Convergence is pairwise: every pair of
  readings in the window MUST be at most the convergence radius (10 m) apart. Distance to the
  centroid MUST NOT be used as the test. Worked example: three readings 9 m apart in a line span
  18 m end to end and MUST fail, even though each lies within 10 m of their centroid.
- **FR-008**: If readings in PARKING do not converge, the system MUST keep sliding the reading
  window (dropping the oldest, adding the newest) and MUST NOT time out or declare failure.
- **FR-009**: FINDING MUST mean "no Parked Location is currently held." There MUST be no
  PARKED → FINDING transition. PARKED MUST exit only to DRIVING, and only when speed exceeds the
  driving-speed threshold; the walk back, the guidance display, and arrival confirmation all happen
  while the state remains PARKED.
- **FR-010**: The system MUST transition PARKING → DRIVING (discarding any partial readings,
  storing nothing) when filtered speed exceeds the driving-speed threshold before convergence.
- **FR-011**: On a fresh install, the system MUST start in FINDING. No other flag or marker is
  required to represent "never parked."

**Parked Location & persistence**

- **FR-012**: On entering PARKED, the system MUST store the centroid of the converging readings as
  the Parked Location, together with its accuracy radius, defined as the largest value, over the
  converging readings, of that reading's accuracy radius plus its distance from the centroid.
- **FR-013**: The system MUST track only a single current Parked Location at a time.
- **FR-014**: The Parked Location and the current lifecycle state MUST survive app termination, OS
  process death, and phone restart.
- **FR-015**: On the PARKED → DRIVING transition (triggered by filtered speed, FR-032), the system
  MUST delete the current Parked Location
  so no stale location remains, including across restarts.
- **FR-033**: Once location permission is granted, the lifecycle state machine MUST run
  continuously in the background, whether or not the app is open, in every state (FINDING, DRIVING,
  PARKING, PARKED), with a persistent notification visible the whole time. It MUST restart
  automatically after the phone reboots. Drive-away detection from PARKED (FR-015) MUST work even if
  the app is never opened after parking.
- **FR-018**: A Parked Location MUST be held if and only if the state is PARKED. If a restored state
  of PARKED has no readable Parked Location, the system MUST fall back to FINDING.

**Default view selection**

- **FR-016**: Whenever the app is open, the system MUST show exactly one default view, chosen by
  the first matching rule in this priority order:
  1. State is DRIVING → "Driving - Waiting to Park."
  2. State is FINDING (no Parked Location held), **or** state is PARKED but guidance is not
     available (FR-031) → "Parked location unavailable."
  3. State is PARKING → "Sensing you will be Parking Soon."
  4. State is PARKED and guidance is available → the guidance display.
  The three status messages MUST use exactly this wording, capitalization, and punctuation.
- **FR-017**: The default view MUST be shown without any user action (it is the app's home
  screen, not a view the user navigates to).

**Uncertainty**

- **FR-019**: The system MUST compute uncertainty as the sum of the stored Parked Location's
  accuracy radius and the current live fix's accuracy radius, and MUST use it in the guidance
  display without dropping or approximating it.

**Guidance display**

- **FR-020**: The guidance display MUST show a cone whose half-angle is
  `atan(uncertainty_radius / distance_to_parked_location)`.
- **FR-021**: The cone MUST be centered on the direction to the Parked Location adjusted for the
  phone's compass heading: `display_bearing = (360 − device_heading + bearing_to_car) mod 360`.
- **FR-022**: A person icon MUST be anchored at the base (apex) of the cone representing the
  user's position, and a car icon MUST be anchored at the opposite end.
- **FR-023**: The cone's centerline MUST NOT be drawn.
- **FR-024**: Cone geometry MUST be computed and rendered relative to the minimum display
  dimension (portrait width) and centered within the screen, so its appearance is the same in any
  device orientation.
- **FR-025**: The distance to the Parked Location MUST be shown as text in the middle of the
  display and MUST update as the user moves, except while the arrival view (FR-028) is shown.
- **FR-026**: Distance MUST be shown in feet when it is at or below the distance-unit threshold
  (500 ft) and in miles when above it.
- **FR-027**: The guidance display MUST reflect each location and heading update in the next
  rendered frame. A guidance update MUST NOT recompose anything outside the guidance display. During
  a 60-second guidance session with heading updates at the sensor's UI rate, at least 95% of frames
  MUST render within the display's frame budget (16.7 ms at 60 Hz) on the reference test device
  (see Assumptions).
- **FR-031**: Guidance is *available* only when the state is PARKED, a Parked Location exists, the
  live fix is current (FR-034) and has an accuracy radius, and a compass heading is available. When
  the state is PARKED and guidance is not available, the system MUST show the same no-guidance view
  as FINDING ("Parked location unavailable."), with no cone, distance text, or input-specific
  message. Fix loss and heading loss MUST be treated identically. This fallback is display-only: it
  MUST NOT change the lifecycle state or clear the Parked Location. A compass heading is *available*
  when the device has a heading sensor, the sensor does not report its accuracy as unreliable, and a
  heading reading has arrived within the heading-staleness timeout.
- **FR-034**: A live fix is *current* when it was received no more than the fix-staleness timeout
  (30 s) ago. A fix that is not current MUST NOT be used to compute guidance. Currency MUST be
  re-checked over time, not only when a new fix arrives, so that a fix that goes stale because
  updates stopped is detected.

**Arrival**

- **FR-028**: When the cone half-angle reaches or exceeds the arrival half-angle (45°), the system
  MUST replace the cone, the person and car icons, and the distance text with the message "You have
  arrived" and show the prompt "Do you see your car?".
- **FR-029**: Answering the arrival prompt (Yes or No) MUST only dismiss the prompt: the state MUST
  remain PARKED and the Parked Location MUST NOT be cleared.

**Named constants**

- **FR-030**: The following values MUST each be defined once as a named, configurable constant and
  referenced by name everywhere they are used or tested: parking-speed threshold (5 mph),
  driving-speed threshold (25 mph), convergence radius (10 m), convergence sample count (3),
  parking-sampling interval (5 s), arrival cone half-angle (45°), distance-unit threshold (500 ft),
  speed-filter window size (3 readings), fix-staleness timeout (30 s), heading-staleness timeout
  (2 s).

### Lifecycle Transition Summary

| From | Condition | To | Side effect |
|------|-----------|----|-------------|
| FINDING (initial) | filtered speed > driving threshold | DRIVING | — |
| DRIVING | filtered speed ≤ parking threshold | PARKING | sampling rises to parking interval |
| PARKING | last N readings within convergence radius | PARKED | store centroid + accuracy |
| PARKING | filtered speed > driving threshold | DRIVING | discard partial readings |
| PARKED | filtered speed > driving threshold | DRIVING | delete Parked Location |
| any | dead-zone filtered speed, single raw speed spike, lost fix, lost heading, arrival answer | (unchanged) | display-only effects |

### Quality & Engineering Requirements

These are called out as their own requirement areas at the explicit request of the feature input
and because the project constitution (Principles I, II, IV, V) makes them binding. They are the
only place this spec names implementation technology.

**Automated test coverage (Constitution I, III)**

- **QR-001**: The state machine, convergence calculation, uncertainty calculation, guidance math
  (bearing, distance, cone half-angle, display bearing, unit selection), and default-view selection
  logic MUST each have automated unit tests.
- **QR-002**: Tests MUST reference the named constants of FR-030 rather than their literal values.
- **QR-003**: The guidance display and the three status views MUST have automated UI tests
  covering at minimum: cone geometry for a given uncertainty/distance input, the feet↔miles switch,
  the arrival prompt appearing at the arrival threshold, and each of the three status messages.
- **QR-004**: Platform adapters (location, motion, heading, persistence, permissions) MUST be
  covered by automated tests using test doubles, not real hardware.

**Requirement traceability (Constitution II)**

- **QR-005**: Every FR and QR in this spec MUST be verified by at least one automated test that
  identifies the requirement ID it verifies.
- **QR-006**: Every FR with corresponding code MUST be traceable from that code via an in-code
  annotation naming the requirement ID.
- **QR-007**: It MUST be possible (manually or via a simple script) to generate a traceability
  report listing, per requirement ID, implementing code and verifying tests, and flagging untraced
  requirements and orphaned annotations.

**UI construction (Constitution IV, V)**

- **QR-008**: The default view — guidance display and all three status messages — MUST be built
  entirely in Jetpack Compose, with no legacy Android View components.
- **QR-009**: All state shown by the default view (lifecycle state, view selection, cone geometry,
  distance text, arrival status, fix/heading fallback) MUST be computed in the shared domain layer
  and exposed to the UI; the UI MUST be a pure function of that state.
- **QR-010**: Lifecycle, convergence, uncertainty, guidance math, and the persistence model MUST
  live in the shared cross-platform core with no dependency on platform APIs.

### Key Entities

- **Lifecycle State**: One of FINDING (initial; no Parked Location held), DRIVING, PARKING, PARKED
  (the only state in which a Parked Location is held). Persisted so it survives restarts.
- **Location Reading**: A position sample with timestamp, speed, and accuracy radius. Readings
  without an accuracy radius are not usable for convergence or guidance.
- **Convergence Window**: The most recent N (= convergence sample count) readings taken during
  PARKING; slides as new readings arrive. Converged when every pair of readings is at most the
  convergence radius apart; its centroid becomes the Parked Location.
- **Parked Location**: The single current saved car position — centroid coordinates, accuracy
  radius, and time captured. Created on entering PARKED; deleted on PARKED → DRIVING.
- **Guidance State**: Derived, not persisted — uncertainty radius, distance, bearing to car,
  display bearing, cone half-angle, distance display (value + unit), arrival status, and whether
  guidance is available (FR-031).
- **Default View**: Which of the four views is shown, derived from Lifecycle State and guidance
  availability.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In scripted replay of recorded drive-and-park sessions, 100% of sessions with a
  genuine stop that converges produce a stored Parked Location within 15 seconds of the third
  converging reading being available, with no manual action.
- **SC-002**: In scripted replay, 0% of sessions consisting solely of dead-zone speeds (walking,
  jogging, cycling, stop-and-go traffic between 5 and 25 mph) produce a state change, and 0% of
  fresh-install sessions without a drive produce a Parked Location.
- **SC-003**: The stored Parked Location lies within its own recorded accuracy radius of the true
  parking spot in at least 90% of field tests.
- **SC-004**: 100% of Parked Locations remain available after each of: app force-close, OS
  process kill, and phone restart.
- **SC-005**: 0 stale Parked Locations remain after any drive-away event in replay and field tests.
- **SC-006**: A user opening the app while parked sees guidance within 2 seconds, with no taps.
- **SC-007**: In field tests, at least 90% of users who follow the guidance reach within sight of
  their car on the first attempt.
- **SC-008**: Direction and distance shown update within 1 second of the user moving or turning,
  with no perceptible stutter.
- **SC-009**: The arrival message appears in 100% of test cases where uncertainty ≥ distance, and
  in 0% of cases where it is less.
- **SC-010**: In 100% of test cases where the fix goes stale or the heading is lost while parked,
  "Parked location unavailable." is shown within 1 second of whichever comes first: the
  fix-staleness timeout elapsing, the heading-staleness timeout elapsing, or the heading sensor
  reporting unreliable accuracy. No guidance is shown, the state stays PARKED, and the Parked Location is kept.
- **SC-011**: 100% of numbered requirements appear in the traceability report with at least one
  implementing location (where applicable) and at least one verifying test.

## Assumptions

- The user grants location "Allow all the time" (background), motion (activity), and notification
  permissions; behavior when
  permissions are denied or later revoked is limited to not crashing at MVP.
- Speed comes from the phone's location provider; there is no vehicle integration.
- Only one vehicle and one current Parked Location are tracked (no multi-vehicle support).
- Bearing/distance math reuses an existing prior implementation rather than being designed anew.
- Outside PARKING, location sampling runs at whatever lower rate is enough to detect the speed
  thresholds (including drive-away from PARKED), chosen with battery consumption in mind. The
  exact rate is decided at planning. Background operation itself is now a requirement (FR-033),
  not an assumption.
- **Deviations from the input's wording, caused by the FINDING redefinition** (see
  Clarifications):
  - The input's lifecycle "DRIVING → PARKING → PARKED → FINDING → (arrival confirmation) → back to
    DRIVING" is replaced by the table above: FINDING is the no-location state, not a step after
    PARKED.
  - Input view rule 2 ("no parked location has ever been stored and state is not DRIVING") becomes
    "state is FINDING". Taken literally, the old rule would need an "ever parked" flag and would
    show "No parked Location yet." during the first PARKING cycle.
  - Input view rule 4 ("PARKED or FINDING, and a parked location exists") becomes "state is
    PARKED", since FINDING never holds a location.
  - The input's "drives away from PARKED or FINDING" becomes "drives away from PARKED".
  - The input's FINDING message "No parked Location yet." becomes "Parked location unavailable.",
    so that it stays true when a location is held but guidance is unavailable (FR-031).
- Distance text: whole feet at or below 500 ft; miles to two decimal places above it.
- Core capabilities (detection, storage, guidance) work fully offline.
- **Reference test device** (FR-027, SC-006, SC-008): a physical phone on API 34 or later with a
  60 Hz display, recorded by model in `validation-results.md`. It is the lowest-performance device
  the release is validated on. Currently: **`<model to be named before the FR-027 benchmark runs>`**.
- The Android release is the only deliverable for this version; the shared core must not preclude
  an iOS release but no iOS UI is built now.

### Out of Scope (not to be architecturally foreclosed)

- Map-based view of the parked location.
- History of past parking events.
- Advertising.
- Server-side telemetry.
- "Phone Finder" companion capability (a paired smartwatch using this location/guidance
  infrastructure to find the phone).
- Rotation-aware/elliptical cone rendering that uses the full screen in any orientation.
- Timeout/failure handling for PARKING windows that never converge.
