# Feature Specification: Automatic Park Detection & Guidance Back to the Vehicle (Run 2)

**Feature Branch**: `main` (no branch hook configured; spec directory `specs/003-park-detect-guidance`)

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: `Claude outputs/car-finder-mvp-requirements-v5.md` — "Car Finder — MVP
Requirements, Draft v5": the phone determines when the user has parked, records the best
representation of that parked location, and later guides the user back to it using direction,
distance, and uncertainty.

Each requirement below names the seed requirement it comes from (for example `REQ-PARK-02`), so the
seed document can be checked against this spec line by line. Where the seed was silent or
contradicted itself, the choice made here is listed under [Assumptions](#assumptions).

## Clarifications

### Session 2026-10-02

- Q: Should FINDING mean only "no parked location is currently held", or also a step entered after
  PARKED while the user walks back? → A: Only "no Parked Location is currently held". It is the
  starting state, PARKED never moves to FINDING, and the walk back and arrival happen in PARKED. The
  seed's lifecycle diagram and its "PARKED or FINDING" wording are superseded on this point.
- Q: How should speed readings be smoothed before the threshold checks: median or average of the
  last three? → A: Median of the last three readings that carry a speed, so a single anomalous
  reading can never cause a transition.
- Q: During the recovery window, with the guidance screen open, should position be sampled every
  second or every 5 seconds? → A: Every 5 seconds, as REQ-PARK-07 is worded. The recovery window
  takes precedence over guidance visibility, so guidance updates every 5 seconds for the first
  180 seconds after the PARKED declaration.
- Q: When location permission is missing or revoked while the state is DRIVING or PARKING, should
  the screen show "Location unavailable" instead of the driving or parking message? → A: Yes. A
  first rule, "location permission not granted → Location unavailable", is added ahead of the
  seed's five display rules, whatever the lifecycle state (now FR-042 rule 2). The lifecycle state and
  any Parked Location are not changed by the loss of permission.
- Q: How often should location be sampled in FINDING? → A: At the idle-sampling interval
  (20 seconds), raised to the parking-sampling interval (5 seconds) while the phone reports it is in
  a vehicle (FR-027 row 4, FR-028).
- Decisions after `/speckit-analyze` (ledger entries AN1-*): the plan's four introduced values are
  now requirements (availability re-check interval and cone-length fraction in FR-055; the
  cone-containment measure in FR-045; the replay error model in QR-016). All permissions are
  requested in sequence at launch, each once (FR-048). Notification permission is required for the
  app to operate; a denial is explained and acknowledged, not re-prompted (FR-056). *(The
  notification-denial handling and "each once" for the required permissions are superseded by
  Session 2026-10-03.)*

### Session 2026-10-03

- Owner decision (ledger OD-1): if the user denies the location or the notification permission,
  the app explains that the permission is required and asks the user to confirm. Confirming shows
  a message that the app is closing, then the app closes. Not confirming (back, dismiss) returns to
  the system permission prompt; a dismissal is not taken as accepting the denial. This replaces the
  2026-10-02 "explain, acknowledge, stay open, do not re-prompt" handling of a notification denial,
  and means a confirmed location denial closes the app before any later permission is requested
  (FR-042 rule 1, FR-048, FR-049, FR-056; US7 scenarios 7 to 12).
- Q: Does granting only approximate location (Android 12+) count as granting location? → A: No. It
  is a denial of the required fine-location permission and gets the same FR-056 confirm-or-close
  flow. FR-047 is unchanged. Repeated-denial behavior (the platform stops showing the prompt) and
  SC-011 are accepted as written (ledger OD-1).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parking is detected and remembered automatically (Priority: P1)

A driver arrives somewhere, parks, and walks away without touching the phone. Car Finder notices
the drive has ended, watches the phone settle in one place, and saves where the car is together
with how precise that saved position is. The saved position is still there later, even if the app
was closed, the operating system reclaimed it, or the phone was restarted.

**Why this priority**: Nothing else works without a trustworthy, automatically captured parked
location.

**Independent Test**: Replay a scripted sequence of readings (smoothed speed above the driving
threshold, then at or below the parking threshold, then three readings that are pairwise within the
convergence radius). Verify the state becomes PARKED, the stored location is the centroid of those
three readings with the accuracy radius of FR-016, and both survive a restart.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the app first runs, **Then** the state is FINDING and no
   Parked Location is held.
2. **Given** the state is DRIVING, **When** smoothed speed drops to or below the parking-speed
   threshold, **Then** the state becomes PARKING and location is sampled once per parking-sampling
   interval.
3. **Given** the state is PARKING, **When** the three most recent usable readings are pairwise
   within the convergence radius (for example pairwise distances of 4 m, 6 m and 9 m), **Then** the
   state becomes PARKED and their centroid is stored as the Parked Location with its accuracy radius
   and the time of the declaration.
4. **Given** the state is PARKING, **When** the three most recent usable readings have pairwise
   distances of 4 m, 6 m and 11 m, **Then** the state stays PARKING and nothing is stored.
5. **Given** the state is PARKING and readings have not converged, **When** another reading
   arrives, **Then** the oldest reading leaves the window and convergence is evaluated again, with
   no timeout and no failure state.
6. **Given** the state is PARKED, **When** the app is terminated, its process is killed, or the
   phone restarts, **Then** on the next launch the state is PARKED and the same Parked Location is
   held.
7. **Given** the state is FINDING, **When** the phone is stationary or moving at or below the
   driving-speed threshold, **Then** the state stays FINDING and no Parked Location is stored.
8. **Given** any state, **When** smoothed speed is above the parking-speed threshold and at or
   below the driving-speed threshold, **Then** no state transition occurs.
9. **Given** fewer speed readings than the speed-filter window size have arrived, **When** a
   reading far above the driving-speed threshold arrives, **Then** no state transition occurs.

---

### User Story 2 - Guided back to the car with honest direction and distance (Priority: P1)

Later, the user opens the app and sees which way to walk and how far it is. The guidance is a cone,
not an arrow: when the saved position or the user's own position is imprecise, the cone widens to
say so. As the user walks and turns, the cone follows the phone's heading and the distance counts
down. The whole walk back happens while the state stays PARKED.

**Why this priority**: This is the payoff of the product. With Story 1 it is the MVP.

**Independent Test**: With a known Parked Location, live position, accuracy radii and heading,
verify the cone direction, the cone half-angle and the distance text (including the feet-to-miles
switch) against FR-030 to FR-037.

**Acceptance Scenarios**:

1. **Given** the state is PARKED with a fresh fix and a heading, **When** the app is opened,
   **Then** the guidance cone is shown with no user action, within the time-to-guidance target.
2. **Given** guidance is shown, **When** the user's position or the phone's heading changes,
   **Then** the cone direction and distance text update to match.
3. **Given** either accuracy radius grows, **When** the display updates, **Then** the cone widens.
4. **Given** the distance to the car is at or below the distance-unit threshold, **Then** it is
   shown in feet; **Given** it is above the threshold, **Then** it is shown in miles.
5. **Given** the phone is rotated between portrait and landscape, **Then** the cone keeps the same
   size and proportions, stays centered, and still points at the car.
6. **Given** guidance is shown, **When** no heading update arrives for the heading-staleness
   timeout, or the phone reports its orientation sensor as unreliable, **Then** "Location
   unavailable" replaces the cone, the state stays PARKED and the Parked Location is kept.
7. **Given** guidance is shown, **When** the live fix becomes older than the fix-staleness timeout,
   **Then** "Location unavailable" replaces the cone, the state stays PARKED and the Parked Location
   is kept.
8. **Given** "Location unavailable" is shown while PARKED, **When** a fresh fix and a reliable,
   fresh heading are both available again, **Then** guidance is shown again.

---

### User Story 3 - A premature "parked" is corrected (Priority: P2)

A driver stops briefly before the real parking space, for example to check cross traffic at a lot
entrance. The stop is long enough to be taken for parking. The driver then creeps through the lot
at low speed, never fast enough to count as driving again, and parks for real. Car Finder notices
the phone has settled somewhere new shortly after it declared PARKED and quietly moves the saved
location to the real space. This only happens for a short, fixed time after the declaration, so
that a driver who has really parked and walked off is not followed.

**Why this priority**: Found in real-world driving in two independent builds. Without it the saved
location can be confidently wrong and nothing ever clears it.

**Independent Test**: Replay a drive, a converging brief stop, slow spread-out movement and a
second converging stop inside the recovery window, and verify the second location is stored.
Replay the same with the second stop after the window, and verify the first location is kept.

**Acceptance Scenarios**:

1. **Given** PARKED was declared and the recovery window is open, **When** three usable readings at
   least one parking-sampling interval apart converge at a place more than the convergence radius
   from the stored Parked Location, **Then** the stored location is replaced by the new centroid and
   accuracy radius, the state stays PARKED, and no message or prompt is shown for the change.
2. **Given** a correction happened, **Then** the Parked Location still carries the time of the
   original PARKED declaration, and the recovery window still ends at the same moment.
3. **Given** the recovery window has closed, **When** readings converge somewhere else, **Then** the
   Parked Location is unchanged.
4. **Given** the recovery window is open, **When** readings converge within the convergence radius
   of the stored Parked Location, **Then** the Parked Location is unchanged.
5. **Given** the recovery window is open and readings arrive faster than one per parking-sampling
   interval, **Then** only readings at least one parking-sampling interval apart are used for
   recovery.
6. **Given** the recovery window is open, **When** smoothed speed exceeds the driving-speed
   threshold, **Then** the state becomes DRIVING and the Parked Location is deleted.

---

### User Story 4 - Arrival is recognized (Priority: P2)

As the user gets close, the remaining distance becomes no larger than the uncertainty, and the cone
can no longer honestly point anywhere. The app says "You have arrived" and asks "Do you see your
car?"

**Why this priority**: Prevents misleading guidance at the end of the walk.

**Independent Test**: Raise the computed half-angle to the arrival threshold and verify the cone is
replaced by the arrival message and the prompt. Answer either way and verify the prompt is no longer
on screen, the state is PARKED and the Parked Location is unchanged.

**Acceptance Scenarios**:

1. **Given** guidance is shown, **When** the cone half-angle reaches the arrival half-angle,
   **Then** the cone is replaced by "You have arrived" and the prompt "Do you see your car?" appears.
2. **Given** the prompt is shown, **When** the user answers Yes or No, **Then** the prompt is no
   longer shown, the state stays PARKED, and the Parked Location is not cleared.
3. **Given** the prompt was dismissed and the arrival condition still holds, **When** the phone is
   rotated or the display updates, **Then** the prompt stays dismissed.
4. **Given** the prompt was dismissed, **When** the half-angle drops below the arrival half-angle
   and later reaches it again, **Then** the prompt is shown again.

---

### User Story 5 - The screen always says something true (Priority: P2)

Whenever the app is open but guidance is not possible, it says so plainly: the user is driving, the
app senses they are about to park, or the location is unavailable.

**Why this priority**: The default view must never be blank, and must never show guidance it
cannot back up.

**Independent Test**: Put the system into every combination of lifecycle state, stored-location
presence, live-fix presence and freshness, heading presence and permission state, and verify
exactly one view is shown, following the priority order in FR-042.

**Acceptance Scenarios**:

1. **Given** location permission is not granted and no permission-required confirmation is pending
   (FR-056), **Then** "Location unavailable" is shown, whatever
   the lifecycle state.
2. **Given** the state is DRIVING, **Then** the driving message is shown.
3. **Given** the state is FINDING, **Then** "Location unavailable" is shown.
4. **Given** the state is PARKING, **Then** "Sensing you will be parking soon" is shown.
5. **Given** the state is PARKED and there is no live fix, a stale fix, or no usable heading,
   **Then** "Location unavailable" is shown.
6. **Given** the state is PARKED with a fresh fix and a usable heading, **Then** guidance is shown.
7. **Given** the app is launched for the first time, **Then** the default view is shown with no
   interaction.

---

### User Story 6 - Driving away clears the old location (Priority: P2)

When the user gets back in the car and drives off, the old parked location is discarded, so a later
"find my car" never points at yesterday's space.

**Why this priority**: A stale location is worse than none.

**Independent Test**: From PARKED, replay smoothed speed above the driving threshold and verify the
state is DRIVING, no Parked Location is held, and the same is true after a restart.

**Acceptance Scenarios**:

1. **Given** the state is PARKED, **When** smoothed speed exceeds the driving-speed threshold,
   **Then** the Parked Location is deleted and the state becomes DRIVING.
2. **Given** a Parked Location was deleted by driving away, **When** the app is restarted, **Then**
   no Parked Location is held and the stored state is not PARKED.
3. **Given** the state is PARKED, **When** one speed reading far above the driving threshold arrives
   among slow ones, **Then** the state stays PARKED and the Parked Location is kept.

---

### User Story 7 - Detection keeps running, within what the user allowed (Priority: P2)

Detection runs in the background behind a visible notification, so parking and drive-away are
caught without opening the app. The app asks for each permission one at a time, in a fixed
order, when it is launched. Location and notifications are required: if the user denies either,
the app explains that it cannot run without it and asks the user to confirm; confirming closes
the app, and backing out asks for the permission again. If a permission is missing or is taken away
later, the app never crashes and never starts sensing it was not allowed to do.

**Why this priority**: Without background detection Story 1 only works with the app open, and a
permission mistake here crashes the app or silently stops detection.

**Independent Test**: With test doubles for the permission system, walk through every combination
of granted, denied and revoked permissions, including a restart after reboot, and verify there is
no crash, no sensing without permission, the right notification text, and the right view.

**Acceptance Scenarios**:

1. **Given** no permission has been requested, **When** the app is launched, **Then** it asks in the
   order location, background location, activity recognition, notifications, one per prompt.
2. **Given** location and notification permissions are granted, **Then** detection runs in the
   background with a persistent notification titled "Car Finder" with the text "Monitoring for parking".
3. **Given** background ("Allow all the time") location is granted, **When** the phone restarts,
   **Then** detection resumes and the notification returns without the app being opened.
4. **Given** only "While using the app" location is granted, **When** the phone restarts, **Then**
   no background start is attempted, detection resumes the next time the app is opened, and the
   notification asks the user to grant "Allow all the time".
5. **Given** a required permission is revoked while detection is running, **When** the system
   restarts or re-enters the background component, **Then** the component checks its own
   permission, does not start sensing, and the app does not crash.
6. **Given** any combination of granted, denied or revoked permissions, **Then** the app does not
   crash or become unresponsive.
7. **Given** the user denies the location prompt (or grants only approximate location) or denies
   the notification prompt, **Then** the
   permission-required confirmation for that permission is shown, and detection does not start.
8. **Given** the permission-required confirmation is shown, **When** the user confirms, **Then** the
   closing message is shown for the shutdown-notice duration, the app then closes without a crash,
   detection is not running, and no later permission in the sequence is requested.
9. **Given** the permission-required confirmation is shown, **When** the user leaves it without
   confirming (back, tapping outside it, or its non-confirm action), **Then** the system prompt for
   the same permission is shown again, and the denial is not treated as accepted.
10. **Given** a required permission was denied and the denial confirmed at an earlier launch,
    **When** the app is launched again, **Then** that permission is requested again, and scenarios 7
    to 9 apply to the answer.
11. **Given** background location or activity recognition was answered at an earlier launch, or a
    required permission is granted, **When** the app is launched again, **Then** that permission is
    not requested again.
12. **Given** a required permission is turned off in system settings, **When** the app next comes
    to the foreground, **Then** that permission is requested again, and scenarios 7 to 9 apply.

---

### Edge Cases

- **Two of three pairs within the radius, the third pair outside** (4 m, 6 m, 11 m): not converged.
- **Never converging** (idling, circling for a space): the window keeps sliding in PARKING with no
  timeout and no failure state.
- **Speed rises above the driving threshold while PARKING** (a long red light, then moving off): the
  state returns to DRIVING and nothing is stored.
- **Slow or stationary phone in FINDING**: no transition. PARKING is reachable only from DRIVING.
- **Reading with no accuracy value**: not used for convergence or recovery, and not used for
  guidance. It is never treated as accuracy zero.
- **Reading with no speed value**: not used for any speed-threshold check and not added to the speed
  filter. It is never treated as speed zero. Its position is still usable if it has an accuracy
  value.
- **Speed filter not yet full** (app start, after a restart): no speed-based transition fires, even
  on a very fast or very slow reading.
- **One anomalous speed reading** in an otherwise steady stream: no transition.
- **Brief stop before the real space**: PARKED may be declared at the brief stop. A second
  convergence inside the recovery window moves the location. Guidance opened in between points at
  the brief-stop position.
- **Real space reached after the recovery window closes** (a very long search, waiting for another
  car to back out): the early location is kept. This is the accepted limit of a fixed window.
- **Parking, then walking away and settling after the window closes**: the location is unchanged.
- **Parking, then walking away and standing still inside the window, more than the convergence
  radius from the car**: the location moves to where the user stood. The window bounds how long this
  exposure lasts; removing it is deferred to production.
- **Guidance opened while the recovery window is open**: position is sampled at the parking
  interval, so the cone and distance follow the user's movement every 5 seconds until the window
  closes, then every second. Heading updates are not affected.
- **Several corrections in one window**: each is allowed, and none moves the end of the window.
- **App or phone restarted while the recovery window is open**: the window still ends at the same
  moment, because it is measured from the stored declaration time.
- **Distance to the car is zero**: the half-angle is treated as 90°, so arrival is shown.
- **Exactly at the distance-unit threshold**: shown in feet. **Exactly at the arrival half-angle**:
  arrival is shown. **Fix age exactly at the fix-staleness timeout**: still fresh.
- **Fix or heading stops updating with no new event**: staleness is detected by the passage of time,
  not only when a new reading arrives.
- **Stored record unreadable**: logged, the state is FINDING with no location, and storage is reset
  so the next write succeeds.
- **Stored record says PARKED but has no location**, or **has a location but is not PARKED**: the
  state becomes FINDING with no location, or the location is dropped, so FR-017 holds.
- **Permission revoked while DRIVING or PARKING**: "Location unavailable" is shown (FR-042 rule 2)
  while the permission is asked for again on return to the foreground (FR-048); a denial then
  follows FR-056.
- **Location denied at launch**: the permission-required confirmation is shown (FR-056). If it is
  confirmed, the app closes and no later permission is requested; if not, location is asked for
  again. Background location is never requested while location is not granted.
- **Required-permission prompt that the platform no longer shows** (after repeated denials the
  platform may answer "denied" at once, without a prompt): the confirmation is shown again
  immediately. The user can still confirm and close, or allow the permission in system settings.
  Accepted by the owner as-is (ledger OD-1).
- **Only approximate location granted** (Android 12+): treated as a location denial; the FR-056
  confirmation is shown.
- **App killed, or its screen swiped away, while the confirmation or the closing message is
  shown**: nothing about the denial is remembered as confirmed; at the next launch the permission
  is requested again, whether or not the process survived.
- **App sent to the background during the closing message**: the app closes as soon as its
  screen is shown again.
- **Screen rotated while a permission prompt is open**: the answer is still received and the
  sequence continues.
- **Closing after a confirmed denial while PARKED**: the lifecycle state and the Parked Location are
  kept in storage unchanged; only the app and detection stop.
- **Activity recognition denied**: detection runs; only the in-vehicle sampling hint is absent.
- **App closed part-way through the launch prompts**: at the next launch the sequence continues
  with the capabilities not yet answered.
- **A capability that needs no runtime grant on this Android version**: treated as granted and not
  requested.
- **Arrival prompt dismissed, then guidance becomes unavailable and returns still within the arrival
  threshold**: the prompt stays dismissed, because the arrival condition never stopped holding from
  the user's point of view. See Assumptions.

## Requirements *(mandatory)*

### Functional Requirements

**Lifecycle**

- **FR-001**: The system MUST hold exactly one lifecycle state at a time, from FINDING, DRIVING,
  PARKING and PARKED. *(Seed §2)*
- **FR-002**: FINDING MUST mean "no Parked Location is currently held" and nothing else. A fresh
  install MUST start in FINDING, with no other flag for "never parked". Loss of the live fix or the
  heading while a location is held MUST NOT change the lifecycle state. PARKED MUST NOT move to
  FINDING through normal use; it leaves only to DRIVING (FR-004), and falls back to FINDING only
  when the stored record is unusable (FR-020). *(Seed §2, R1-I1)*
- **FR-003**: The system MUST infer parking from the phone's motion and location alone, with no
  manual "I parked" action. *(Seed §1)*
- **FR-004**: The system MUST move to DRIVING from any other state when smoothed speed (FR-007)
  exceeds the driving-speed threshold. *(Seed §2)*
- **FR-005**: The system MUST move from DRIVING to PARKING when smoothed speed is at or below the
  parking-speed threshold. PARKING MUST NOT be entered from any other state. *(REQ-PARK-01)*
- **FR-006**: Smoothed speed above the parking-speed threshold and at or below the driving-speed
  threshold MUST NOT cause any state transition (dead zone). *(Seed §2)*
- **FR-007**: Every speed-threshold check (FR-004, FR-005, FR-014, FR-019) MUST use a smoothed
  speed, never a raw reading. The smoothed speed MUST be the median of the most recent
  speed-filter-window-size readings that carry a speed value. *(REQ-PARK-10; filter type
  confirmed in Clarifications)*
- **FR-008**: Until the speed filter holds a full window of readings, it MUST report that no
  smoothed speed is available, and no speed-based transition may fire. The system MUST hold its
  current state until the window is full. It MUST NOT fall back to a raw reading. *(REQ-PARK-10)*
- **FR-009**: A reading with no reported speed MUST be left out of the speed filter and MUST NOT
  cause or contribute to a speed-based transition. It MUST NOT be treated as speed zero or as
  "stationary". *(REQ-PARK-08)*
- **FR-010**: A reading with no reported accuracy MUST be left out of every convergence evaluation
  (FR-011, FR-021). It MUST NOT be treated as accuracy zero or as "perfectly accurate".
  *(REQ-PARK-08)*
- **FR-011**: Convergence MUST be pairwise across all samples in the window: samples S1, S2, S3
  converge if and only if `distance(S1,S2) ≤ R`, `distance(S2,S3) ≤ R` and `distance(S1,S3) ≤ R`,
  where R is the convergence radius. Pairwise distances of 4 m, 6 m and 9 m converge. Pairwise
  distances of 4 m, 6 m and 11 m do not. *(REQ-PARK-02)*
- **FR-012**: The system MUST move from PARKING to PARKED when the most recent
  convergence-sample-count consecutive usable readings converge (FR-011). *(REQ-PARK-02)*
- **FR-013**: If readings in PARKING do not converge, the system MUST keep sliding the window
  (drop the oldest, add the newest) and MUST NOT time out or declare failure. *(Seed §5)*
- **FR-014**: The system MUST move from PARKING to DRIVING, storing nothing and discarding the
  partial window, when smoothed speed exceeds the driving-speed threshold before convergence.
  *(Seed §2)*

**Parked Location and persistence**

- **FR-015**: On the PARKED declaration the system MUST store the centroid of the converging
  samples as the Parked Location, with its accuracy radius (FR-016) and the time of the
  declaration. *(REQ-PARK-03)*
- **FR-016**: The accuracy radius of a stored or corrected Parked Location MUST be the maximum,
  over the converging samples, of that sample's own accuracy radius plus that sample's distance
  from the centroid. A mean of the samples' accuracy values MUST NOT be used. *(REQ-UNC-02)*
- **FR-017**: The system MUST hold at most one Parked Location, and MUST hold one if and only if
  the state is PARKED. *(REQ-PARK-04, REQ-PARK-09)*
- **FR-018**: The Parked Location, including its declaration time, and the lifecycle state MUST
  survive app termination, process death and phone restart, and MUST be restored on the next
  launch. *(REQ-PARK-04)*
- **FR-019**: On the move from PARKED to DRIVING the system MUST delete the current Parked Location
  and MUST store DRIVING as the lifecycle state in the same step, so that no stale PARKED state or
  stale location remains, including across restarts. *(REQ-PARK-05)*
- **FR-020**: If the stored Parked Location or lifecycle state cannot be read back because of
  corruption or a decoding failure, the system MUST log the failure, continue in FINDING with no
  Parked Location, and reset the stored record to its empty default so that later writes succeed
  and the failure does not repeat on every launch. A stored record that breaks FR-017 MUST be
  corrected the same way. *(REQ-PARK-09)*

**Re-convergence recovery**

- **FR-021**: While the state is PARKED and no more than the parked-recovery window has passed since
  the PARKED declaration, the system MUST keep evaluating incoming readings for convergence
  (FR-011). This evaluation MUST NOT require a preceding DRIVING state. *(REQ-PARK-06)*
- **FR-022**: If, within the window, convergence-sample-count readings converge and their centroid
  is more than the convergence radius from the stored Parked Location, the system MUST discard the
  stored location, store the new centroid and accuracy radius (FR-015, FR-016), persist it
  (FR-018), and remain PARKED. No message or prompt may be shown for the correction. A convergence
  whose centroid is within the convergence radius of the stored location MUST NOT change it.
  *(REQ-PARK-06)*
- **FR-023**: The recovery window MUST be measured from the original PARKED declaration. A
  correction MUST NOT reset or extend it, and a corrected Parked Location MUST keep the original
  declaration time. *(REQ-PARK-06)*
- **FR-024**: Once the window has closed, the system MUST stop recovery evaluation, MUST discard
  any partly collected recovery readings, and MUST NOT change the Parked Location except to delete
  it on drive-away (FR-019). *(REQ-PARK-06)*
- **FR-025**: Readings used for recovery convergence MUST be at least one parking-sampling interval
  apart. Readings arriving faster MUST be thinned, not used. *(REQ-PARK-06)*
- **FR-026**: Drive-away (FR-004, FR-019) MUST take precedence over recovery. Recovery MUST NOT
  depend on activity recognition. *(REQ-PARK-06, Seed §5)*

**Sampling**

- **FR-027**: The location-sampling interval MUST depend on lifecycle state and display visibility,
  chosen by the first matching row: *(REQ-PARK-07)*

  | # | Condition | Interval |
  |---|-----------|----------|
  | 1 | DRIVING or PARKING | parking-sampling interval |
  | 2 | PARKED with the recovery window open, whether or not guidance is visible | parking-sampling interval |
  | 3 | PARKED with the guidance display visible | guidance-sampling interval |
  | 4 | PARKED otherwise, and FINDING | idle-sampling interval |

  The sampling actually requested from the platform MUST match this table at all times; a build
  that requests one fixed rate does not satisfy this requirement.
- **FR-028**: While sampling at the idle interval (FR-027 row 4), if the platform reports that the
  phone is in a vehicle, the system MUST raise sampling to the parking-sampling interval. This hint
  MUST NOT change the lifecycle state and MUST NOT pause sampling. *(REQ-PARK-07)*
- **FR-029**: The system MUST use one shared location stream for detection and guidance, and MUST
  NOT start a second precision location service while one that can provide the needed readings is
  already running. *(Seed §3.4)*

**Uncertainty and guidance**

- **FR-030**: Guidance uncertainty MUST be the sum of the stored Parked Location's accuracy radius
  and the live fix's accuracy radius. *(REQ-UNC-01)*
- **FR-031**: The guidance cone's half-angle MUST equal
  `atan(uncertainty_radius / distance_to_parked_location)`. A distance of zero MUST give 90°.
  *(REQ-GUIDE-01)*
- **FR-032**: The cone MUST be centered on
  `display_bearing = (360 − device_heading + bearing_to_car) mod 360`, in the range 0 to 360
  degrees, where `bearing_to_car` is the true bearing from the live fix to the Parked Location.
  *(REQ-GUIDE-01)*
- **FR-033**: `device_heading` MUST be the phone's heading first remapped for the current display
  rotation and then corrected for magnetic declination to true north. A raw, uncorrected magnetic
  heading, or one not remapped for display rotation, MUST NOT be used. *(REQ-GUIDE-01)*
- **FR-034**: `device_heading` MUST come from a smoothed orientation source, not from the
  instantaneous raw compass reading, so that ordinary sensor noise does not make the cone jitter.
  *(REQ-GUIDE-01, CR-17)*
- **FR-035**: A person icon MUST be anchored at the user's end of the cone and a car icon at the
  opposite end, along the centerline. The centerline itself MUST NOT be drawn. *(REQ-GUIDE-01)*
- **FR-036**: Cone geometry MUST be computed and drawn relative to the minimum display dimension
  and centered within it, with the cone's length equal to the cone-length fraction of that dimension, so the cone looks the same in portrait and landscape. *(REQ-GUIDE-01)*
- **FR-037**: The distance to the Parked Location MUST be shown as text in the middle of the
  guidance display and MUST update as the user moves. It MUST be in feet at or below the
  distance-unit threshold and in miles above it. *(REQ-GUIDE-04)*
- **FR-038**: When the cone half-angle is at or above the arrival half-angle, the system MUST
  replace the cone display with the message "You have arrived". *(REQ-GUIDE-02)*
- **FR-039**: On reaching the arrival threshold the system MUST show the prompt "Do you see your
  car?" with Yes and No answers. Either answer MUST only dismiss the prompt: the prompt is no
  longer shown, the state stays PARKED, and the Parked Location is kept. The prompt MUST stay
  dismissed, including across display rotation, for as long as the arrival condition holds, and
  MUST be shown again the next time the arrival condition is reached after it has stopped holding.
  *(REQ-GUIDE-03, REQ-UI-04, Seed §2)*
- **FR-040**: A live fix older than the fix-staleness timeout MUST NOT be used for guidance. The
  display MUST fall back to the same "Location unavailable" view used when no fix has ever been
  received. Fix age and heading age (FR-041) MUST be re-checked at least once per availability re-check
  interval, not only when a new reading arrives.
  *(REQ-GUIDE-05)*
- **FR-041**: A heading MUST be treated as absent when no heading update has arrived within the
  heading-staleness timeout, and for as long as the platform reports the orientation sensor as
  unreliable. Normal use MUST resume as soon as a fresh, reliable heading arrives. *(REQ-GUIDE-07)*
- **FR-042**: The default view MUST be chosen by the first matching rule below, and every reachable
  combination of permission state, lifecycle state, stored location, live fix and heading MUST fall
  under exactly one rule: *(REQ-GUIDE-06, REQ-PERM-03)*
  1. A denial of a required permission (FR-049) is awaiting the user's FR-056 answer → the
     permission-required confirmation for that permission; or such a denial has been confirmed
     and the app has not yet closed → the closing message (FR-056). A denial is awaiting an answer
     from the moment its request returns "not granted" until the user confirms or dismisses.
  2. A required permission (FR-049) is not granted → "Location unavailable". This is display-only:
     it does not change the lifecycle state or the Parked Location.
  3. State is DRIVING → the driving message.
  4. State is FINDING → "Location unavailable".
  5. State is PARKING → "Sensing you will be parking soon".
  6. State is PARKED and there is no live fix, or the fix is stale (FR-040), or the fix has no
     accuracy value (FR-010), or the heading is absent (FR-041) → "Location unavailable".
  7. State is PARKED with a fresh fix that has an accuracy value and a usable heading → guidance
     (FR-030 to FR-039).
- **FR-043**: The default view MUST be shown on launch, including first launch, with no user
  interaction. *(REQ-TEST-04 d)*
- **FR-044**: When the state is PARKED and a fresh fix and usable heading are already available, the
  guidance cone MUST be drawn within the time-to-guidance target of the guidance screen becoming
  visible. The binding measurement is on a device (the field check in the validation guide);
  scripted replay additionally checks that the shared logic adds no wait before the first guidance
  state. This target is an owner-revisable placeholder. *(REQ-GUIDE-08)*
- **FR-045**: In scripted replay (QR-016), the true parked location MUST fall inside the drawn
  uncertainty cone in at least the cone-containment target share of guidance frames. A guidance
  frame is one in which a cone is drawn; arrival and unavailable frames are not counted. The true
  parked location is inside the cone when the true bearing from the true user position to the true
  parked location, taken relative to the true heading, differs from the drawn display bearing by no
  more than the drawn half-angle. This target is an owner-revisable placeholder. *(REQ-GUIDE-09)*
- **FR-046**: The guidance display MUST update on every location and heading update without visible
  stutter, and MUST keep the FR-036 geometry across display rotation. *(REQ-UI-03)*

**Permissions and background execution**

- **FR-047**: The system depends on exactly these runtime-gated capabilities: fine location,
  background location, activity recognition, and notifications. *(REQ-PERM-01)*
- **FR-048**: At launch the system MUST request, in sequence and one capability per prompt, every
  capability that is not granted and, for background location and activity recognition, has not
  yet been answered, in this fixed order: location, background location,
  activity recognition, notifications. They MUST NOT be requested as one bulk request. A denial of
  a required permission (FR-049) MUST be handled by FR-056 before the sequence continues; a
  confirmed denial ends the sequence, so no later capability is requested. Background location and
  activity recognition, once answered, MUST NOT be requested again by the app. A required permission
  that is not granted MUST be requested at every launch and whenever the app returns to the
  foreground. Other changes the user makes in system settings MUST be picked up when the app returns
  to the foreground. Background location MUST NOT be requested while location is not granted. A
  capability that needs no runtime grant on the running platform version MUST be treated as granted
  and not requested. *(REQ-PERM-02; timing decided 2026-10-02; denial handling decided 2026-10-03)*
- **FR-049**: Under no combination of granted, denied or revoked permissions may the system crash
  or become unresponsive. Location and notifications are the *required* permissions: while either
  is not granted, detection MUST NOT run and the default view MUST follow FR-042 rules 1 and 2; a
  denial of either MUST be handled by FR-056, so the app does not stay in use without them. Closing
  the app under FR-056 is not a crash. Without background location the system MUST behave as FR-054
  states. Without activity recognition detection MUST still run, without the in-vehicle sampling
  hint (FR-028). Execution MUST NOT continue on an unmet precondition. *(REQ-PERM-03; denial
  handling decided 2026-10-03)*
- **FR-050**: The background detection component MUST verify its own required permissions (FR-049)
  at every point where the operating system can start, restart or re-enter it, not only where the
  app first started it. *(REQ-PERM-04)*
- **FR-051**: Once the required permissions (FR-049) are granted, detection MUST run in the
  background in every lifecycle state, whether or not the app is open, behind a persistent
  notification with the title "Car Finder" and the text "Monitoring for parking". The one exception
  is after a reboot with only foreground location granted (FR-054). *(REQ-PERM-05)*
- **FR-052**: No location- or sensor-consuming background component may start before its runtime
  permission is confirmed granted, including on boot and automatic-restart paths. *(REQ-PERM-06)*
- **FR-053**: With background location granted, detection MUST restart after the phone reboots
  without the app being opened. *(REQ-PERM-07)*
- **FR-054**: With only foreground location granted, the system MUST NOT attempt to start detection
  from a boot or other background-only entry point. Detection MUST resume the next time the user
  opens the app, and the persistent notification MUST ask the user to grant "Allow all the time";
  tapping it MUST open the app's permission settings. *(REQ-PERM-07)*
- **FR-056**: When the user denies a required permission (location or notifications, FR-049) at
  its system prompt, the system MUST show a permission-required confirmation that names the
  permission, says Car Finder cannot run without it, and asks the user to confirm. A grant of only
  approximate location, where the platform offers that choice, is a denial of location (FR-047) and
  MUST be handled the same way. Then:
  (a) if the user confirms, the system MUST show a closing message for the shutdown-notice duration
  and then close the app: every screen closed, detection stopped, no crash. No later capability in
  the FR-048 sequence may be requested. The stored lifecycle state and Parked Location MUST NOT be
  changed by the closing.
  (b) if the user leaves the confirmation in any other way (back, tapping outside it, or a
  non-confirm action), the system MUST show the system prompt for the same permission again. Such a
  dismissal MUST NOT be treated as accepting the denial.
  A confirmed denial MUST NOT be remembered as final: the permission is requested again at the next
  launch (FR-048). A pending confirmation or closing MUST NOT carry over to the next launch, even
  when the operating system keeps the app's process alive. A confirmed closing MUST still happen
  if the app is sent to the background during the closing message; it then takes effect the next
  time the app's screen is shown. Here a *launch* is a start after the app's screen was closed by
  the user or by FR-056 (or after the process ended); a return from the background, or a screen the
  system re-created, is not a launch. *(Owner decision 2026-10-03, ledger OD-1; replaces the
  2026-10-02 decision)*

**Named constants**

- **FR-055**: Each value below MUST be defined once, changeable in one place, and referred to by
  name wherever it is used or tested. *(Seed §4)*

  | Constant | Value |
  |----------|-------|
  | Parking-speed threshold | 5 mph |
  | Driving-speed threshold | 25 mph |
  | Convergence radius | 10 m |
  | Convergence sample count | 3 |
  | Parking-sampling interval | 5 s |
  | Guidance-sampling interval | 1 s |
  | Idle-sampling interval | 20 s |
  | Arrival cone half-angle | 45° |
  | Distance-unit threshold | 500 ft |
  | Fix-staleness timeout | 30 s |
  | Heading-staleness timeout | 2 s |
  | Parked-recovery window | 180 s |
  | Time-to-guidance target | 2 s |
  | Cone-containment target | 90% |
  | Speed-filter window size | 3 readings |
  | Availability re-check interval | 500 ms |
  | Cone-length fraction | 0.65 of the minimum display dimension |
  | Shutdown-notice duration | 2 s (owner-revisable placeholder; ledger OD-1) |

### Lifecycle Transition Summary

| From | Condition | To | Side effect |
|------|-----------|----|-------------|
| FINDING (initial) | smoothed speed > driving threshold | DRIVING | — |
| DRIVING | smoothed speed ≤ parking threshold | PARKING | sample at the parking interval |
| PARKING | last 3 usable readings converge pairwise | PARKED | store centroid, accuracy radius, declaration time |
| PARKING | smoothed speed > driving threshold | DRIVING | discard partial readings |
| PARKED | within the recovery window, thinned readings converge > radius away | PARKED | replace location, keep declaration time |
| PARKED | smoothed speed > driving threshold | DRIVING | delete Parked Location |
| any | dead-zone speed, filter not full, reading without speed, lost fix, lost heading, arrival answer | unchanged | display-only effects |
| (restore) | stored record unreadable or inconsistent | FINDING | log, reset stored record |

### Quality & Engineering Requirements

The seed document makes these binding and names their technology, and the project constitution
(Principles I to V) requires them. They are the only place this spec names implementation
technology.

- **QR-001**: All shared domain logic MUST have automated unit tests: the state machine,
  convergence, both uncertainty calculations (FR-016, FR-030), the recovery mechanism and its
  sampling rule (FR-021 to FR-028), and the bearing, distance and cone-geometry math. *(REQ-TEST-01)*
- **QR-002**: Every FR and QR in this spec MUST have at least one automated test that names the ID
  it verifies. A requirement with no linked test blocks the build. *(REQ-TEST-02)*
- **QR-003**: Platform adapters (location, heading, activity recognition, storage, permissions)
  MUST be tested with test doubles, not real hardware. Any adapter a test needs to replace MUST be
  an interface with a platform implementation class, never a Kotlin Multiplatform `expect class`.
  `expect`/`actual` is reserved for small stateless platform functions that no test replaces.
  *(REQ-TEST-03)*
- **QR-004**: The guidance display MUST have automated UI tests, using semantics-based testing,
  covering at least: (a) the drawn half-angle matches FR-031 for a given uncertainty and distance;
  (b) the feet-to-miles switch at the distance-unit threshold; (c) the arrival prompt appears at the
  arrival half-angle; (d) the default view is drawn on first launch with no interaction; (e) the
  icons are anchored at the two ends of the cone and no centerline is drawn. *(REQ-TEST-04)*
- **QR-005**: Tests MUST refer to the FR-055 constants by name and MUST NOT repeat their values,
  so changing a constant does not require rewriting tests. *(REQ-TEST-05)*
- **QR-006**: A requirement that describes something the user can observe MUST have a test that
  asserts the observable effect (the element is no longer drawn, the shown state changed), not only
  an internal field. *(REQ-TEST-06)*
- **QR-007**: Every numeric threshold, named constant, formula, and timing or sampling behavior
  MUST be a numbered requirement or success criterion in this spec. None may exist only in
  planning or research notes, and none may be invented during implementation. *(REQ-TEST-07)*
- **QR-008**: Every requirement with corresponding code MUST be traceable from that code by an
  in-code annotation naming the requirement ID. *(REQ-DOC-01)*
- **QR-009**: A traceability report MUST be producible, by hand or by a simple script, listing for
  each requirement ID its implementing code and its verifying tests. The report's own parsing MUST
  be validated against a real sample of this spec and of the source formats before it is relied
  on. *(REQ-DOC-02)*
- **QR-010**: The report MUST flag, as build-blocking: a requirement with no code annotation; an
  annotation naming an ID that is not in this spec; and a requirement with code but no linked test.
  A requirement that by its nature has no code (a rule about tests or process) is exempt from the
  first check only if it is declared, with its reason, in a list the report prints in full; it
  still needs a linked test. *(REQ-DOC-03)*
- **QR-011**: Findings from any automated cross-artifact consistency check MUST be recorded in an
  append-only ledger keyed by finding ID. A later run MUST NOT drop, downgrade or contradict a
  finding without a recorded reason. *(REQ-DOC-05)*
- **QR-012**: The guidance display (cone, icons, distance text, arrival prompt) MUST be built in
  Jetpack Compose, with no legacy Android View components. The arrival prompt MUST be a Compose
  dialog or overlay. *(REQ-UI-01, REQ-UI-04)*
- **QR-013**: Everything the default view shows MUST be computed in the shared domain layer and
  exposed to the UI as state. The UI MUST be a pure function of that state. *(REQ-UI-02)*
- **QR-014**: The state machine, Parked Location model, uncertainty, distance, bearing and guidance
  math, and the persistence model MUST live in the shared cross-platform core with no dependency on
  platform APIs. Platform services MUST be native adapters. *(Seed §7)*
- **QR-015**: The Android release MUST support Android 8.0 (API level 26) as its minimum version.
  *(Seed §7)*
- **QR-016**: Requirements stated as a percentage or time target (FR-044, FR-045) MUST be checked
  by scripted replay: a recorded or synthetic sequence of location, heading and speed readings
  played through the shared domain logic exactly as a live stream would arrive. Synthetic sequences
  MUST carry ground truth and MUST displace each reported position from the true position by a
  repeatable, seeded random error no larger than that reading's reported accuracy radius. Scripted
  replay MUST NOT replace real-device testing of operating-system behavior (permission flows,
  background execution, process death, reboot). *(Seed §9)*

### Key Entities

- **Lifecycle State**: One of FINDING (initial; no Parked Location held), DRIVING, PARKING, PARKED
  (the only state in which a Parked Location is held). Persisted.
- **Location Reading**: A position sample with time, optional speed and optional accuracy radius.
  Without accuracy it is unusable for convergence and guidance; without speed it is unusable for
  speed checks.
- **Smoothed Speed**: The median of the most recent readings that carry a speed; unavailable until
  the window is full.
- **Convergence Window**: The most recent readings under evaluation, in PARKING or in the recovery
  window. Converged when every pair is within the convergence radius.
- **Parked Location**: The single saved car position: centroid, accuracy radius, and the time of
  the PARKED declaration. Created on entering PARKED, replaced by a recovery correction (keeping its
  declaration time), deleted on drive-away. Distinct from any future map pin or parking-history
  record.
- **Recovery Window**: The fixed period after the PARKED declaration during which a correction is
  allowed. Derived from the stored declaration time, not stored separately.
- **Heading**: The phone's pointing direction, smoothed, remapped for display rotation and corrected
  to true north; absent when stale or unreliable.
- **Guidance State**: Derived, not persisted: uncertainty, distance, bearing to car, display
  bearing, cone half-angle, distance text and unit, arrival status, prompt visibility.
- **Default View**: Which view is shown, derived by FR-042.
- **Permission State**: For each of the four capabilities, one of: not yet requested, granted, or
  denied. Remembered across restarts, and re-read when the app returns to the foreground rather than
  assumed. A denied required permission is requested again at the next launch (FR-048).
- **Denial Confirmation**: Transient, not persisted: which required permission's denial is awaiting
  the user's answer, or has been confirmed with the app not yet closed (FR-056). It ends when the
  user dismisses it, when the app has closed, or when the app's screen is closed by other means;
  the next launch starts without one.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In scripted replay of drive-and-park sessions, 100% of sessions with a stop that
  converges end with a stored Parked Location, with no manual action, by the time the third
  converging reading has been processed.
- **SC-002**: In scripted replay, 0% of sessions made only of dead-zone speeds change state, 0% of
  sessions with no drive store a Parked Location, and 0% of sessions change state before the speed
  filter is full.
- **SC-003**: In scripted replay, 100% of sessions with a brief converging stop followed by a second
  convergence elsewhere inside the recovery window end with the second location stored, and 0% of
  convergences after the window closes change the stored location.
- **SC-004**: 100% of Parked Locations are still available after each of: app force-close, process
  kill, and phone restart.
- **SC-005**: 0 stale Parked Locations remain after a drive-away, in replay and in field tests.
- **SC-006**: A user who opens the app while parked, with a fix and heading already available, sees
  guidance within 2 seconds and with no taps (owner-revisable target).
- **SC-007**: In scripted replay, the true parked location is inside the drawn cone in at least 90%
  of guidance frames (owner-revisable target).
- **SC-008**: Direction and distance shown update within 1 second of the user moving or turning
  while the guidance-sampling interval applies, with no visible stutter.
- **SC-009**: Arrival is shown in 100% of test cases where uncertainty is at least the distance, and
  in 0% where it is less.
- **SC-010**: In 100% of test cases where the fix goes stale, the heading goes stale, or the
  orientation sensor is reported unreliable while parked, "Location unavailable" is shown within
  1 second of that moment, no guidance is shown, and the Parked Location is kept.
- **SC-011**: Across every tested combination of granted, denied and revoked permissions, including
  restart after reboot, there are 0 crashes and 0 cases of sensing without permission.
- **SC-012**: In background operation, the sampling interval actually in effect matches FR-027 in
  100% of tested state and visibility combinations.
- **SC-013**: 100% of numbered requirements appear in the traceability report with implementing
  code (where applicable) and at least one verifying test.

## Assumptions

Choices made where the seed document was silent or inconsistent. Five were confirmed by the owner
(see Clarifications) and are marked so; the rest remain assumptions.

- **FINDING and the lifecycle diagram.** Confirmed in Clarifications (2026-10-02): FINDING means
  only "no Parked Location is currently held". The seed's diagram ("PARKED → FINDING →
  (confirmation) → DRIVING") and its "PARKED or FINDING" wording in REQ-PARK-04, REQ-PARK-05 and
  REQ-PERM-07 are read as "PARKED" (FR-002, FR-019).
- **Speed filter type.** Confirmed in Clarifications (2026-10-02): a median. The seed names the
  filter and its window size but not the formula (FR-007).
- **Sampling in FINDING.** Confirmed in Clarifications (2026-10-02): the idle interval, with the
  in-vehicle hint applying. REQ-PARK-07 gives FINDING no interval (FR-027 row 4, FR-028).
- **Recovery window against guidance visibility.** Confirmed in Clarifications (2026-10-02): while
  the recovery window is open, sampling is at the parking interval even with guidance visible
  (FR-027 row 2). Guidance therefore updates every 5 seconds for the first 180 seconds after the
  PARKED declaration, and SC-008 is scoped accordingly.
- **Permission branch in the view order.** Confirmed in Clarifications (2026-10-02): REQ-GUIDE-06's
  five branches are preceded by two permission rules (FR-042 rules 1 and 2). REQ-PERM-03 requires
  the second, which can occur in any lifecycle state; FR-056 adds the first (reworded for the
  2026-10-03 confirm-then-close decision).
- **A fix without accuracy while PARKED** is treated like no fix (FR-042 rule 6), because FR-010
  forbids treating missing accuracy as zero and FR-030 cannot be computed without it.
- **"Outside the convergence radius of the stored location"** (REQ-PARK-06) is measured from the new
  centroid to the stored Parked Location (FR-022).
- **Recovery window boundary.** A convergence completed exactly at the end of the window counts.
- **Resume on app open under foreground-only permission** applies in every lifecycle state, not only
  PARKED or FINDING as REQ-PERM-07 words it, since the seed gives no reason to leave DRIVING or
  PARKING without detection (FR-054).
- **Status wording.** The seed gives "You have arrived", "Do you see your car?", "location
  unavailable" and "sensing you will be parking soon", and no text for the driving message. Assumed
  texts: "Location unavailable", "Sensing you will be parking soon", and "Driving" for the driving
  message. All are owner-revisable.
- **Distance text format**: whole feet at or below the threshold; miles to two decimal places above.
- **Arrival prompt across an unavailable spell.** If guidance becomes unavailable and returns while
  still within the arrival threshold, the prompt stays dismissed.
- **Boundaries**: "reaches 45°" means at or above; "exceeds the timeout" means strictly older.
- **Adapter boundary wording.** The constitution (Principle V) says platform services sit behind an
  `expect`/`actual` boundary; the seed (REQ-TEST-03) says fakeable adapters are interfaces and never
  `expect class`. These are read as compatible: interfaces for adapters, with `expect`/`actual` used
  only for a small stateless function that supplies the platform implementations (QR-003).
- **Confirmation and closing wording** (FR-056): "Car Finder needs location permission to run.
  Close Car Finder?" (or "notification permission"), with the confirm action "Close" and the
  non-confirm action "Allow", which returns to the system prompt. Closing message: "Car Finder is
  closing." Owner-revisable.
- The user is assumed to grant all four permissions for full function. Speed comes from the phone's
  location readings; there is no vehicle integration. One vehicle, one current Parked Location.
  Detection, storage and guidance work offline. Bearing and distance math may be reused from the
  owner's prior implementation. Android is the only deliverable; the shared core must not preclude
  iOS.

### Out of Scope (not to be architecturally foreclosed)

- Timeout or failure handling for a PARKING window that never converges.
- Gating recovery on activity recognition staying in-vehicle; walking detection; longer dwell
  handling; a vehicle head-unit companion that reports ignition-off.
- Preventing the early PARKED declaration itself at a brief stop.
- A full arbitration policy between independent concurrent location consumers.
- Permission rationale screens shown before a prompt (the after-denial confirmation of FR-056 is in
  scope), Settings deep-links for permanently denied permissions (see ledger OD-1), and a separate
  signal-loss message.
- Detecting notifications turned off in system settings on Android 8.0 to 12L (API 26 to 32).
  There, notification permission has no runtime grant and is treated as granted (FR-048), so the
  FR-056 flow does not run for it and detection still runs; only the persistent notification is
  hidden (ledger AN2-C1).
- Rotation-aware or elliptical cone rendering.
- Map view, parking history, breadcrumbs, advertising, telemetry, Phone Finder.
- Deriving traceability from commit diffs (REQ-DOC-04).
- Support for Android versions below 8.0.
