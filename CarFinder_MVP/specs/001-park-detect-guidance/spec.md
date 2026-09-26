# Feature Specification: Automatic Park Detection and Return Guidance

**Feature Branch**: `001-park-detect-guidance` *(spec directory name; no git branch was created — no `before_specify` hook is configured)*

**Created**: 2026-09-18

**Status**: Draft

**Input**: User description: "Build Car Finder, a mobile app (Android-first, with a shared core intended to support a future iOS release) that determines when a user has parked their vehicle and then guides them back to it." — full description in `Claude Prompts/prompt-spec.md`

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parking Is Captured Without Being Asked (Priority: P1)

A driver arrives at their destination, parks, and walks away without touching their phone. The
system has already noticed the drive ended, settled on where the vehicle came to rest, and saved
that spot — along with how confident it is about it. The driver did nothing.

**Why this priority**: Every other capability consumes the Parked Location. Nothing downstream can
work without it, and the whole value proposition rests on the capture being automatic — a user who
must remember to tap "I parked" will forget on exactly the occasions they most need the app.

**Independent Test**: Replay a recorded or synthesized speed-and-location sequence representing a
drive that ends in a park. Verify the state progression, that a Parked Location is stored, and that
its position is the centroid of the converging samples — all without any simulated user input.
Delivers value on its own: the location is captured and inspectable even before any guidance UI
exists.

**Acceptance Scenarios**:

1. **Given** the system is in DRIVING, **When** observed speed falls to or below the parking-speed
   threshold, **Then** the system enters PARKING and begins sampling location at the parking-sample
   interval.
2. **Given** the system is in PARKING, **When** the required number of consecutive samples all fall
   within the convergence radius of one another, **Then** the system enters PARKED and stores the
   centroid of those samples as the Parked Location.
3. **Given** the system is in PARKING and the user is circling for a spot so samples never converge,
   **When** each new sample arrives, **Then** the system slides its evaluation window forward by one
   sample and keeps trying indefinitely, without declaring a timeout, error, or failure state.
4. **Given** a Parked Location has been stored, **When** the app is terminated, the device is
   restarted, or the operating system kills the process, **Then** on next launch the Parked Location
   and the current parking state are both restored.

---

### User Story 2 - Guided Back to the Vehicle, Honestly (Priority: P2)

A user returns to a large parking structure or unfamiliar street with no memory of where they left
the car. They open the app and are immediately shown which way to walk and how far — with the
display widening or narrowing to reflect how sure the system actually is, rather than pretending to
a precision it does not have.

**Why this priority**: This is the payoff the user actually experiences. It is second only because
it consumes what User Story 1 produces. Conveying uncertainty honestly is part of the requirement,
not a refinement: a confident-looking arrow that points at the wrong row of a parking garage is
worse than a wide cone that tells the truth.

**Independent Test**: Seed a Parked Location and accuracy values directly, then drive the display
with synthetic current-position and heading inputs. Verify cone half-angle, cone orientation, icon
placement, and distance text against computed expectations. Testable with no parking detection
running at all.

**Acceptance Scenarios**:

1. **Given** a stored Parked Location, a current position, and a current heading, **When** the
   guidance display renders, **Then** the cone's half-angle equals the arctangent of the uncertainty
   radius divided by the distance to the Parked Location.
2. **Given** a device heading and a bearing to the vehicle, **When** the guidance display renders,
   **Then** the cone is centered on the display bearing derived from both, so the cone points at the
   vehicle in the real world as the user turns.
3. **Given** the user is 400 feet from the vehicle, **When** the distance text renders, **Then** it
   is expressed in feet; **and Given** the user is 1,200 feet away, **Then** it is expressed in
   miles.
4. **Given** the guidance display is showing, **When** the device is rotated between portrait and
   landscape, **Then** the cone geometry remains consistent because it is sized relative to the
   minimum display dimension and centered within it.
5. **Given** the user is walking, **When** position and heading updates arrive, **Then** the cone
   orientation, cone width, and distance text all update without visible stuttering or dropped
   frames.

---

### User Story 3 - The App Admits When You've Arrived (Priority: P3)

A user following the guidance gets close enough that "keep walking that way" stops being useful
advice — the vehicle is within the margin of error. Instead of a cone that swells to fill the
screen while still implying more walking, the app says so and asks whether they can see the car.

**Why this priority**: Without it the guidance degrades into nonsense at exactly the moment of
success, because the cone half-angle grows toward a right angle as distance shrinks. It is separable
from User Story 2 — guidance is useful before this exists — but the experience is unfinished
without it.

**Independent Test**: Seed positions that produce a cone half-angle at, just below, and just above
the arrival threshold. Verify the cone is replaced by the arrival message and confirmation prompt at
and above the threshold, and not below it.

**Acceptance Scenarios**:

1. **Given** guidance is showing, **When** the computed cone half-angle reaches the arrival
   threshold, **Then** the cone is replaced by a "You have arrived" message and a "Do you see your
   car?" confirmation prompt.
2. **Given** the arrival confirmation prompt is showing, **When** the user answers either yes or no,
   **Then** the prompt is dismissed, the parking state is unchanged, and the Parked Location is not
   cleared.

---

### User Story 4 - Knowing What the App Is Doing (Priority: P4)

A user opens the app at a moment when directional guidance makes no sense — they are mid-drive, they
have just installed the app, or the system is in the middle of deciding whether they have parked.
Rather than a blank screen or a stale cone, they get a plain statement of what the app currently
knows.

**Why this priority**: It prevents the app from lying or appearing broken in the three states where
guidance is not applicable. Lower priority than guidance itself because these are the states in
which the user needs nothing from the app, but the four-way selection is the thing that makes the
default view trustworthy.

**Independent Test**: Drive the view-selection logic directly with each combination of parking state
and location-present/absent, including combinations that exercise the priority ordering, and verify
exactly one of the four outcomes is selected each time.

**Acceptance Scenarios**:

1. **Given** the system is in DRIVING and no Parked Location has ever been stored, **When** the app
   is opened, **Then** "Driving - Waiting to Park" is shown — the DRIVING rule outranks the
   no-location rule.
2. **Given** the system is in FINDING — no Parked Location is held — and is not in DRIVING,
   **When** the app is opened, **Then** "Parked location unavailable." is shown.
3. **Given** the system is in PARKING and has not yet converged, **When** the app is opened, **Then**
   "Sensing you will be Parking Soon." is shown.
4. **Given** the system is in PARKED and a Parked Location exists, **When** the app is opened,
   **Then** the directional guidance display is shown without the user requesting it.
5. **Given** the app was just installed and no parking cycle has completed, **When** the app is
   opened, **Then** the system is in FINDING and "Parked location unavailable." is shown.
6. **Given** the system is in PARKED with a stored Parked Location, **When** no live position fix is
   available, or the latest live fix is older than `FIX_STALENESS_TIMEOUT`, or no device heading is
   available, **Then** "Parked location unavailable." is shown instead of guidance, the parking state
   remains PARKED, and the stored Parked Location is neither altered nor deleted.
7. **Given** scenario 6 is in effect, **When** a current live fix and a device heading are both
   available again, **Then** the guidance display returns without any user action.

---

### User Story 5 - Driving Away Resets the Cycle (Priority: P5)

A user gets in their car and drives off. The location they were being guided to is no longer where
their car is, so it is discarded — and a later "where's my car?" is never answered with yesterday's
parking spot.

**Why this priority**: It is the correctness guarantee that keeps the app from actively misleading
users over time. Last in priority only because it is unobservable until at least one full cycle has
run, but a stale location is a worse failure than no location.

**Independent Test**: With a stored Parked Location and the system in PARKED, feed speeds crossing
the driving threshold. Verify the location is deleted, the state becomes DRIVING, and no subsequent
read can retrieve the deleted location. Repeat from FINDING to verify the transition still occurs
when there is no location to delete.

**Acceptance Scenarios**:

1. **Given** the system is in PARKED with a stored Parked Location, **When** observed speed exceeds
   the driving-speed threshold, **Then** the Parked Location is deleted and the system enters
   DRIVING.
2. **Given** the system is in FINDING and therefore holds no Parked Location, **When** observed speed
   exceeds the driving-speed threshold, **Then** the system enters DRIVING and the absence of a
   location to delete is a no-op rather than an error.
3. **Given** a Parked Location was deleted by drive-away, **When** the app is next opened before a
   new parking cycle completes, **Then** the deleted location is never shown; the DRIVING or PARKING
   message is shown as the state requires.

---

### Edge Cases

- **Dead-zone motion**: observed speed above the parking threshold but at or below the driving
  threshold — biking, jogging, running, stop-and-go traffic — produces no state transition in either
  direction.
- **Speed exactly at a threshold**: speed exactly equal to the parking threshold enters PARKING (the
  rule is "at or below"); speed exactly equal to the driving threshold does *not* enter DRIVING (the
  rule is "above") and therefore falls in the dead zone.
- **Samples never converge**: the user idles at a curb or circles a lot for ten minutes. The window
  slides forever; no timeout and no failure state. The view stays on "Sensing you will be Parking
  Soon."
- **Interruption mid-cycle**: app termination, device restart, or OS process death while PARKED or
  FINDING. Both the location and the state survive.
- **Brand-new install, first drive**: no Parked Location has ever existed, and the user starts
  driving before any parking cycle completes. The DRIVING message takes precedence.
- **Distance exactly at the unit threshold**: exactly 500 feet is expressed in feet, not miles.
- **Cone half-angle exactly at the arrival threshold**: exactly 45 degrees triggers arrival.
- **Uncertainty exceeding distance**: when the uncertainty radius is larger than the distance to the
  vehicle, the computed half-angle exceeds 45 degrees and arrival is therefore shown — the intended
  behavior, since the system cannot distinguish "here" from "there" at that point.
- **Distance of zero**: the half-angle formula divides by distance. Zero distance must not produce a
  crash or an undefined value; it is treated as arrival.
- **Arrival reached, then user walks away**: the user answers the prompt, then moves far enough that
  the half-angle drops back below the arrival threshold.
- **Device rotation during guidance**: geometry stays consistent, sized to the minimum display
  dimension.
- **Signal lost while a location is stored**: the user is walking back to the car and enters an
  underground structure, so the live fix stops updating. Once the latest fix is older than
  `FIX_STALENESS_TIMEOUT`, the display shows the FINDING view ("Parked location unavailable.")
  rather than guidance computed from a stale position. The parking state stays PARKED and the stored
  Parked Location is untouched; guidance returns when a current fix arrives (FR-030, FR-043).
- **App opened before the first fix**: after a long absence the user opens the app; no live fix or
  heading has arrived yet. The FINDING view is shown until both are available (FR-030).
- **Compass unavailable**: no device heading can be obtained. The FINDING view is shown in place of
  guidance; the parking state is unaffected (FR-030).
- **App left in the background while parked**: live position and heading collection for guidance
  stops while the default view is not visible, and starts again when it becomes visible (FR-044). The
  background state machine is unaffected (FR-014).
- **Permission denied**: the user declines location permission. The background state machine is not
  started, the FINDING view is shown, and the app asks again the next time it is opened (FR-045,
  FR-046). Declining does not crash the app or leave a half-started service.
- **Background location on newer Android versions**: the platform requires background location to be
  requested separately, after foreground location, and may send the user to a system Settings screen
  to choose "Allow all the time". The app requests in that order and continues to work with whatever
  the user grants (FR-045).
- **First launch after installation**: no parking cycle has ever run, so the system is in FINDING
  from the outset rather than in an undefined state.
- **App never opened**: the user installs the app, grants permissions, and drives and parks without
  ever bringing it to the foreground. The park is still captured, because the state machine runs in
  the background regardless.
- **App opened mid-cycle**: the user opens the app during PARKING or at the moment of convergence.
  Opening it changes nothing about the state machine's behavior or timing.

## Requirements *(mandatory)*

### Functional Requirements

#### Parking State Machine

- **FR-001**: System MUST maintain exactly one current parking state at all times, drawn from
  DRIVING, PARKING, PARKED, and FINDING.
- **FR-002**: System MUST enter DRIVING from any state when observed speed exceeds
  `DRIVING_SPEED_THRESHOLD`.
- **FR-003**: System MUST transition from DRIVING to PARKING when observed speed falls to or below
  `PARKING_SPEED_THRESHOLD`.
- **FR-004**: System MUST NOT perform any state transition while observed speed is above
  `PARKING_SPEED_THRESHOLD` and at or below `DRIVING_SPEED_THRESHOLD`.
- **FR-005**: While in PARKING, System MUST sample location once per `PARKING_SAMPLE_INTERVAL`.
- **FR-006**: System MUST transition from PARKING to PARKED when `CONVERGENCE_SAMPLE_COUNT`
  consecutive location samples all lie within `CONVERGENCE_RADIUS` of one another. Convergence is
  determined by the **all-pairwise** criterion: each sample's distance to every other sample in the
  window MUST be at most `CONVERGENCE_RADIUS` meters. (This is the stricter interpretation: three
  samples in a line 9 m apart each would fail at pairwise distances of 18 m, even though each is
  individually within the radius of some others.)
- **FR-007**: When entering PARKED, System MUST store the centroid of the converging samples as the
  Parked Location.
- **FR-008**: While in PARKING with a non-converging sample window, System MUST advance the window by
  one sample on each new sample and re-evaluate, indefinitely — no timeout, no failure state, and no
  transition out of PARKING other than by speed.
- **FR-009**: System MUST be in FINDING whenever no Parked Location is held. This includes the
  first launch after installation, before any parking cycle has completed. FINDING is therefore the
  initial state of a newly installed system. Loss of position signal or of the compass while a
  Parked Location is held MUST NOT change the parking state; that condition is handled by the
  display fallback of FR-030.
- **FR-010**: System MUST delete the current Parked Location and enter DRIVING when observed speed
  exceeds `DRIVING_SPEED_THRESHOLD` while in PARKED or in FINDING. In FINDING no Parked Location is
  held, so the deletion is a no-op and MUST NOT be treated as an error.
- **FR-010a**: The parking state machine MUST be initialized and running at all times after the
  user grants the required location permission. The system MUST NOT wait for the app to be opened
  or brought to the foreground. This ensures parking detection operates continuously in the
  background regardless of app visibility.

#### Parked Location Persistence and Lifecycle

- **FR-011**: System MUST persist the Parked Location and the current parking state such that both
  survive app termination, device restart, and operating-system process death, and are restored on
  next launch.
- **FR-012**: System MUST hold at most one Parked Location at any time.
- **FR-013**: After a Parked Location is deleted, System MUST NOT present, return, or restore that
  location through any subsequent read.
- **FR-014**: The parking state machine MUST run as a background operation, continuously and
  independently of whether the app is in the foreground. Bringing the app to the foreground,
  sending it to the background, or closing it MUST NOT start, stop, pause, reset, accelerate, or
  otherwise alter state-machine behavior. Parking detection, drive-away detection, and stale-location
  deletion MUST therefore occur whether or not the user ever opens the app. The foreground app is a
  read-only observer of state-machine output: no user interaction — including opening the app,
  viewing the guidance display, or answering the arrival confirmation prompt — may cause a state
  transition.

#### Uncertainty

- **FR-015**: System MUST compute the uncertainty radius as the sum of the reported accuracy radius
  of the stored parked fix and the reported accuracy radius of the user's current live fix.
- **FR-016**: System MUST recompute the uncertainty radius on every position update and MUST use the
  computed value directly in guidance rendering — it MUST NOT be rounded away, clamped to a
  placeholder, or substituted with a fixed value.

#### Default View Selection

- **FR-017**: System MUST present the default view whenever the app is open, with no user action
  required to request it.
- **FR-018**: System MUST evaluate the following four outcomes in strict priority order and present
  exactly one of them: (1) state is DRIVING, (2) state is FINDING, (3) state is PARKING,
  (4) state is PARKED and guidance is available. When the state is PARKED but guidance is not
  available (FR-030), the outcome-(2) view is presented instead.
- **FR-019**: When the state is DRIVING, System MUST show "Driving - Waiting to Park", including when
  no Parked Location has ever been stored.
- **FR-020**: When the state is FINDING — no Parked Location is held — and the state is not
  DRIVING, or when the state is PARKED but guidance is not available (FR-030), System MUST show
  "Parked location unavailable." This wording is deliberately true in both cases: it makes no claim
  about whether a location has ever been stored.
- **FR-021**: When the state is PARKING, System MUST show "Sensing you will be Parking Soon."
- **FR-022**: When the state is PARKED, a Parked Location exists, and guidance is available
  (FR-030), System MUST show the directional guidance display.

#### Guidance Display

- **FR-023**: System MUST render a cone whose half-angle equals
  `atan(uncertainty_radius / distance_to_parked_location)`.
- **FR-024**: System MUST center the cone on `display_bearing = (360 − device_heading_true_north +
  bearing_to_car) mod 360`, where `device_heading_true_north` is the true-north corrected heading
  derived from the compass reading by applying the geomagnetic field declination at the user's
  current location via `GeomagneticField.getDeclination()` or equivalent. The bearing to the car is
  already computed in true-north by the geodesy module. The cone MUST track the vehicle as the
  device turns.
- **FR-025**: System MUST anchor a person icon at the user's current position, at the base of the
  cone, and a car icon at the opposite end of the cone.
- **FR-026**: System MUST NOT draw the cone's centerline.
- **FR-027**: System MUST compute and render the cone geometry relative to the minimum display
  dimension, centered within it, so the cone's appearance is unchanged by device rotation.
- **FR-028**: System MUST display the distance to the Parked Location as text in the middle of the
  display, updating as the user's position changes.
- **FR-029**: System MUST express that distance in feet when it is at or below
  `DISTANCE_UNIT_THRESHOLD`, and in miles when it is above that threshold.
- **FR-030**: Guidance is *available* only when the state is PARKED, a Parked Location is held, a
  current live position fix exists (FR-043), and a device heading is available. Whenever any of
  these is missing, System MUST present the FINDING view of FR-020 in place of guidance. This is a
  display rule only: it MUST NOT change the parking state, and it MUST NOT alter or delete the stored
  Parked Location, so guidance resumes automatically when current inputs return. Before the first
  live fix and heading have arrived after the app is opened, the FINDING view is shown. Because the
  FINDING view is the fallback for every combination not otherwise resolved, the selection in FR-018
  always resolves to exactly one outcome and no undefined display condition can arise.

#### Arrival

- **FR-031**: System MUST replace the cone with a "You have arrived" message when the computed cone
  half-angle reaches `ARRIVAL_CONE_HALF_ANGLE`.
- **FR-032**: On arrival, System MUST show a confirmation prompt reading "Do you see your car?"
- **FR-032a**: The foreground-service notification (required by FR-014 on Android 8+) MUST read
  title "Car Finder" and text "Monitoring for parking" (owner decision 2026-09-26, R1-U3; exact string, no trailing period, matching the implemented notification).
- **FR-033**: System MUST dismiss the arrival prompt on either answer, and MUST NOT change the
  parking state or clear the Parked Location as a result of either answer.

#### Named Constants

- **FR-034**: System MUST define the following as named, single-sourced values that can be changed
  in one place without rewriting call sites, and MUST NOT duplicate their literal values at any
  point of use or in any test:
  `PARKING_SPEED_THRESHOLD` (5 mph), `DRIVING_SPEED_THRESHOLD` (25 mph), `CONVERGENCE_RADIUS`
  (10 meters), `CONVERGENCE_SAMPLE_COUNT` (3), `PARKING_SAMPLE_INTERVAL` (5 seconds),
  `ARRIVAL_CONE_HALF_ANGLE` (45 degrees), `DISTANCE_UNIT_THRESHOLD` (500 feet),
  `FIX_STALENESS_TIMEOUT` (30 seconds).

#### Automated Test Coverage

- **FR-035**: Automated tests MUST cover the parking state machine, sample convergence, uncertainty
  calculation, the bearing/distance/cone-geometry math, and the four-way default-view selection
  logic.
- **FR-036**: Automated user-interface tests MUST cover the guidance display and each of the three
  non-guidance status messages.
- **FR-037**: Automated tests MUST reference the named constants of FR-034 rather than their literal
  values, so that changing a constant does not require rewriting a passing test.

#### Requirement Traceability

- **FR-038**: Every numbered requirement in this specification MUST be traceable forward to the
  code that implements it, via an in-code annotation carrying that requirement's ID.
- **FR-039**: Every numbered requirement in this specification MUST be verified by at least one
  automated test that identifies the requirement ID it verifies.
- **FR-040**: It MUST be possible to generate a report listing, per requirement ID, the implementing
  code and the verifying tests, and flagging both untraced requirements and orphaned annotations
  that reference requirement IDs no longer present.

#### Default View Construction

- **FR-041**: The default view — the guidance display and all three status messages — MUST be built
  entirely in Jetpack Compose, with no legacy Android View-system component anywhere in that screen.
- **FR-042**: The default view MUST render from state exposed by the shared domain layer; view state
  MUST NOT be computed or owned inside the user-interface layer.

#### Live Input Freshness and Lifecycle

- **FR-043**: A live position fix is *current* when it was received no more than
  `FIX_STALENESS_TIMEOUT` ago. A fix that is not current MUST NOT be used to compute guidance, and
  currency MUST be re-evaluated over time, not only when a new fix arrives, so that a fix which goes
  stale because updates stopped is detected.
- **FR-044**: Live position and heading collection for the guidance display MUST run only while the
  default view is visible to the user, and MUST stop when it is not. This MUST NOT affect the
  background state machine of FR-014.

#### Permissions

- **FR-045**: On first launch, and on any later launch while a required permission is not granted,
  System MUST request the permissions it needs, while the app is visible, in the order the platform
  requires: precise location first, then background location, then physical-activity recognition and
  notifications where the platform version requires them. System MUST NOT repeat a request within the
  same app session after the user has declined it.
- **FR-046**: System MUST start the background parking state machine (FR-010a) only after location
  permission has been granted, and MUST start it automatically when permission is granted — without
  the user having to restart the app. While location permission is not granted, System MUST show the
  FINDING view (FR-020), MUST NOT present guidance, and MUST NOT start the background service. After
  a device restart, the service MUST be restarted only if the permission is still granted.

### Key Entities

- **Parking State**: The single current lifecycle position — DRIVING, PARKING, PARKED, or FINDING.
  Persisted alongside the Parked Location. FINDING is the default and initial state, meaning no
  Parked Location is held; PARKED is the state in which a location is held and guidance is shown,
  including while the user walks back to the vehicle.
- **Location Sample**: One observation taken during PARKING. Carries a position, a reported accuracy
  radius, an observed speed, and a timestamp. Consumed in windows of `CONVERGENCE_SAMPLE_COUNT`.
- **Parked Location**: Where the vehicle is believed to be. Carries the centroid position of the
  converging samples, the accuracy radius of that fix, and when it was captured. At most one exists
  at a time; deleted on drive-away.
- **Uncertainty Radius**: A derived value — the parked fix's accuracy radius plus the current live
  fix's accuracy radius. Drives cone width and therefore arrival detection.
- **Guidance View State**: The derived, displayable description of the current moment — which of the
  four views applies, and when guidance applies, the distance, the distance unit, the display
  bearing, the cone half-angle, and whether arrival has been reached.
- **Configuration Constants**: The eight named tuning values of FR-034, read by both the domain logic
  and its tests.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who drives and parks normally has their parking location captured with zero
  taps, zero screen views, and zero prompts during the entire park event.
- **SC-002**: In 100% of interruption trials — app force-stop, device restart, and simulated process
  death — the parked location and lifecycle state are both recovered intact on next launch.
- **SC-003**: A returning user opening the app can tell which direction to start walking within 5
  seconds of the app becoming visible, with no navigation or menu interaction.
- **SC-004**: The displayed direction and distance update continuously as the user walks, with no
  stuttering or dropped frames visible to the user during movement.
- **SC-005**: The width of the displayed cone is proportional to the system's actual positional
  uncertainty, such that the vehicle's true location falls inside the displayed cone in at least 95%
  of trials. **Validation methodology**: In field testing, the user opens the app after parking at a
  known landmark (e.g., a building entrance with sub-meter GPS survey coordinates). The cone's
  position and width are recorded for 50+ trials across different locations and times of day. Ground
  truth is the landmark's surveyed coordinates. A trial passes if the true location falls within the
  rendered cone half-angle and distance formula. The 95% threshold represents the expected
  cumulative accuracy of both the device's GPS and the app's uncertainty model.
- **SC-006**: A stale parking location is presented to the user in 0 out of 100 drive-away-and-return
  trials.
- **SC-007**: Arrival is announced in 100% of approaches where the user reaches a distance at which
  the uncertainty equals or exceeds the remaining distance, and is never announced before that point.
- **SC-008**: 100% of numbered requirements in this specification appear in the traceability report
  with both implementing code and at least one verifying test; the report shows zero untraced
  requirements and zero orphaned annotations.
- **SC-009**: Normal motion that is not driving — walking, jogging, and cycling — produces zero false
  parking captures across a full day of continuous use.
- **SC-010**: All core behavior — detection, storage, and guidance — functions with the device in
  airplane mode or otherwise without network connectivity.
- **SC-011**: A park event is captured in 100% of trials in which the app is never brought to the
  foreground at any point during the drive or the park.
- **SC-012**: Opening, backgrounding, or closing the app at any point in a park cycle changes
  neither the resulting state sequence nor the captured location, compared with an identical trial
  in which the app is never opened.
- **SC-013**: When live position or compass input is lost, the display shows the FINDING view within
  `FIX_STALENESS_TIMEOUT` and never presents guidance computed from a non-current fix; guidance
  returns without user action once current inputs are available again.
- **SC-014**: On a fresh install, a user who accepts the permission prompts reaches a running
  background state machine with no further action — no app restart, and no visit to system Settings
  except the platform's own "Allow all the time" screen where the platform requires it. A user who
  declines location permission sees the FINDING view and a service that is not running.

## Out of Scope

Deliberately excluded from this version. None of these may be architecturally foreclosed by
decisions made here:

- A map-based view of the parked location.
- A history of past parking events. This version tracks only the single current parked location.
- Advertising.
- Server-side telemetry.
- A "Phone Finder" companion capability allowing a paired smartwatch to use this same
  location/guidance infrastructure to locate the phone.
- A rotation-aware or elliptical cone rendering that uses the full screen in any orientation. The
  minimum-display-dimension approach of FR-027 is the intended behavior for this version, and the
  fuller rendering is a deferred refinement.
- Automatic derivation of traceability from commit diffs (backward traceability). FR-038 through
  FR-040 specify forward traceability only.
- **Deferred to the production version.** This MVP is a pre-production build whose purpose is to show
  that a viable product can be produced from requirements; each of the following will receive its
  own requirements later: a distinct signal-loss state or status message separate from FINDING; a
  "Getting your location…" message while the first fix is acquired; battery and location-request
  optimization beyond FR-044; arbitration between the background and guidance location requests;
  handling of a very poor live fix that widens the cone into a false arrival; permission rationale
  screens, Settings deep-links for permanently denied permissions, and any degraded mode beyond
  FR-046.

## Assumptions

- The user is asked for the location, motion and notification permissions the app needs, **including
  the background location permission** that FR-014 requires (FR-045). If location permission is not
  granted the app stays inert and shows the FINDING view (FR-046); rationale screens, Settings
  deep-links and degraded modes are deferred to the production version.
- FINDING is exited only to DRIVING, when observed speed exceeds `DRIVING_SPEED_THRESHOLD`. The
  source description did not state FINDING's exit conditions; this is the minimum needed for the
  state machine to be closed. PARKED is entered only from PARKING, by convergence (FR-006).
- **Signal loss and the FINDING view**: While a Parked Location is held, loss of the live position
  fix or of the compass does not change the parking state (FR-009). The display instead falls back
  to the FINDING view (FR-030), and the wording of FR-020 is deliberately accurate whether or not a
  location is stored. Showing no guidance is preferred over showing guidance that may be wrong: for
  this MVP the product should appear incomplete rather than incorrect. A distinct signal-loss
  treatment is deferred to the production version (see Out of Scope).
- Speed is derived from the device's own location provider, not from any vehicle integration.
- A single current parked location is tracked at a time. Multi-vehicle support is not in scope.
- The bearing and distance math required for guidance reuses an existing prior implementation rather
  than being designed from scratch; it is still subject to the test-coverage requirement of FR-035.
- Position accuracy radii are reported by the platform's location provider and are treated as
  trustworthy inputs to the uncertainty calculation.
- Once the arrival prompt has been answered for a given parked location, it is not shown again until
  the user leaves the arrival zone — the computed half-angle drops back below
  `ARRIVAL_CONE_HALF_ANGLE` — and returns. The "You have arrived" message itself continues to show
  for as long as the arrival condition holds.
- The three status messages are shown with their exact wording as quoted in FR-019 through FR-021,
  including the existing capitalization and punctuation. The FINDING message was reworded from the
  source description's "No parked Location yet." so that it stays true when a location is stored but
  guidance is unavailable (FR-020).
- While in PARKED, location sampling may run at a lower rate than `PARKING_SAMPLE_INTERVAL`, since
  the elevated rate exists to achieve convergence. Guidance updates while in PARKED are driven by
  position updates from the guidance path (FR-044) rather than by that interval.
- Speed observations may be noisy; the thresholds in FR-002 through FR-004 are evaluated against the
  speed reported with each location update.
