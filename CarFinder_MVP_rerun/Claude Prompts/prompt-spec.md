# Prompt for /speckit.specify

Paste the text below as the argument to `/speckit.specify` so Spec Kit
generates `spec.md` itself, from its own template, rather than being handed a
finished document.

---

Build Car Finder, a mobile app (Android-first, with a shared core intended to
support a future iOS release) that determines when a user has parked their
vehicle and then guides them back to it. The app observes the phone's motion
and location to infer parking without any manual "I parked" action, stores
that location together with how uncertain it is, and shows the user a
directional guidance display that leads them back, honestly conveying both
direction and distance rather than implying false precision.

The core lifecycle is a state machine: DRIVING → PARKING → PARKED → FINDING →
(arrival confirmation) → back to DRIVING. Speed above 25 mph is DRIVING.
Speed at or below 5 mph following a DRIVING state enters PARKING, where
location sampling increases to once every 5 seconds. Speeds between 5 and 25
mph are an intentional dead zone with no state transition, so biking,
jogging, or stop-and-go traffic don't falsely trigger a change. The system
declares PARKED once 3 consecutive samples converge within a 10-meter
radius of each other, and stores the centroid of those samples as the Parked
Location. That location must survive app termination, phone restart, and OS
process death — it should still be there, and the app should still know it's
in state PARKED or FINDING, after any of those interruptions. When the user
later drives away (speed back above 25 mph) from PARKED or FINDING, the
current Parked Location is deleted and the cycle restarts; this must not
leave a stale location around to mislead a later "find my car" request. An
open, consciously-deferred question: what to do if 3 samples never converge
(user idling, or circling for a spot) — for MVP, keep sliding the 3-sample
window rather than declaring a timeout or failure.

Alongside the Parked Location, the system tracks uncertainty: the combined
positional confidence of the stored parked fix and the user's current live
fix, calculated as the sum of each fix's reported accuracy radius. This
uncertainty is not just an internal number — it directly shapes what the
guidance display shows the user, so it should never be quietly dropped or
approximated away.

The guidance display is the app's default view, not something the user has
to request — whenever the app is open, it shows one of four things
depending on current state, checked in this priority order:

1. State is DRIVING — show "Driving - Waiting to Park." This takes priority
   even if no parked location has ever been stored (e.g. brand new install,
   first drive before any parking cycle has completed).
2. No parked location has ever been stored, and the state is not DRIVING —
   show "No parked Location yet."
3. State is PARKING (elevated sampling, not yet converged) — show "Sensing
   you will be Parking Soon."
4. Otherwise — state is PARKED or FINDING, and a parked location exists —
   show the directional guidance display described below.

That guidance display is a cone whose half-angle is
`atan(uncertainty_radius / distance_to_parked_location)`,
centered on a line pointing toward the Parked Location and adjusted for the
phone's compass heading (`display_bearing = (360 − device_heading +
bearing_to_car) mod 360`). A person icon anchors at the user's current
position (the base of the cone); a car icon anchors at the opposite end.
The cone's centerline itself isn't drawn. Because the phone's screen isn't
round, compute and render the cone geometry relative to the minimum display
dimension (portrait width), centered within it, so the cone looks consistent
regardless of device rotation — a fuller rotation-aware/elliptical rendering
that uses the whole screen in any orientation is a deliberately deferred
refinement, not required now. Show the distance to the Parked Location as
text in the middle of the display, updating as the user moves — in feet when
the distance is 500 feet or less, in miles beyond that.

As the user closes in, recognize arrival rather than continuing to imply
more walking is needed: when the computed cone half-angle reaches 45
degrees, replace the cone with a "You have arrived" message and show a
confirmation prompt, "Do you see your car?" Either answer (yes or no) simply
dismisses the prompt for MVP — it introduces no new state, and answering it
does not clear the Parked Location.

Cross-cutting expectations that apply across all of this (call these out as
their own requirement areas, not folded silently into the pieces above):
automated test coverage for the shared logic above (state machine,
convergence, uncertainty, guidance math, and the four-way default-view
selection logic) and automated Compose UI tests for the guidance display and
the three non-guidance states; the ability to trace every requirement
forward to the code that implements it and the tests that verify it, with a
way to generate a report of that traceability and flag gaps; and the default
view itself — guidance display and all three status messages — being built
entirely in Jetpack Compose, driven by state exposed from the shared domain
layer rather than computed in the UI layer.

Named constants this system depends on, which should be treated as
named/configurable rather than hardcoded wherever they're used or tested:
parking-speed threshold 5 mph, driving-speed threshold 25 mph, convergence
radius 10 meters, convergence sample count 3, parking-sampling interval 5
seconds, arrival cone half-angle 45 degrees, distance-unit threshold 500
feet.

Explicitly out of scope for this version, but not to be architecturally
foreclosed: a map-based view of the parked location, a history of past
parking events (this MVP tracks only the single current parked location),
advertising, server-side telemetry, and a future "Phone Finder" companion
capability that would let a paired smartwatch use this same
location/guidance infrastructure to help find the phone itself.

Assume: the user grants the location and motion permissions the app needs;
speed is derived from the phone's location provider rather than a separate
vehicle integration; a single current parked location is tracked at a time
(no multi-vehicle support); and the bearing/distance math needed for
guidance can reuse an existing prior implementation rather than being
designed from scratch.
