# Prompt for /speckit.plan

Paste the text below as the argument to `/speckit.plan` (after `/speckit.specify`
and, if needed, `/speckit.clarify` have run) so Spec Kit generates the
technical `plan.md` itself, from its own template.

---

Plan the implementation of Car Finder around a Kotlin Multiplatform (KMP)
shared-core / native-adapter architecture, Android-first.

**Module split.** Put all domain logic in a KMP shared/common module with no
compile-time or runtime dependency on either platform's APIs: the parking
state machine (DRIVING/PARKING/PARKED/FINDING), the Parked Location model,
convergence calculation, uncertainty calculation, distance/bearing/cone
guidance math, and the persistence model (interfaces only — the actual
storage mechanism is a platform concern). Put everything platform-specific —
sensors, location, activity recognition, permissions, background execution,
battery management — behind an `expect`/`actual` boundary as native
adapters, so the shared module stays portable to a future iOS build without
rework.

**Android sensing.** Use Android's location and motion/activity APIs to
derive speed and position for the state machine, and the device's magnetic
compass for heading in the guidance display. Evaluate whether an existing
always-on location service can be reused before starting a new
high-frequency one during PARKING-state sampling (5-second interval) —
this affects battery consumption and is worth a concrete recommendation
(e.g. Fused Location Provider) rather than leaving it open. Persistence for
the Parked Location needs to survive app termination, phone restart, and OS
process death — pick a concrete on-device storage mechanism appropriate for
a small, frequently-read/written single record (not a full history table
yet, but don't choose something that would make adding one later awkward).

**Permissions.** Design a `PermissionController` abstraction that normalizes
Android's `Activity`-based runtime permission flow into a single suspend
function, with an equivalent (but not artificially identical) shape planned
for iOS later — note specifically that iOS raw accelerometer/gyroscope data
via `CMMotionManager` needs no permission, while only
`CMMotionActivityManager` and `CMPedometer` are gated behind
`NSMotionUsageDescription` and requested implicitly on first use. Don't
force a symmetric explicit-request API across both platforms for motion;
expose observable permission/availability status instead where a platform
doesn't have an explicit request to make.

**UI.** Build the Android guidance display (cone, person/car icons, distance
text, arrival confirmation) entirely in Jetpack Compose — no legacy Android
View system. Rendering must be driven by state hoisted from the shared KMP
domain layer, exposed as something like a `StateFlow` of a guidance-display
state object (bearing, distance, cone half-angle, arrival flag), with the
Composable as a pure function of that state so it recomposes cleanly on
location/heading updates and device rotation without owning any of the
geometry calculation itself.

**Testing strategy.** Shared-module logic (state machine, convergence,
uncertainty, guidance math) gets plain Kotlin unit tests, referencing named
constants rather than hardcoded literals. Platform adapters get tested
against fakes/test doubles for sensors and location, not real hardware. The
Compose guidance display gets Compose's semantics-based UI testing APIs,
covering cone-geometry-from-inputs, the feet/miles distance-unit switch, and
the arrival-confirmation prompt appearing at threshold. Plan for how test
names or annotations will carry the requirement ID they verify, since that
linkage is a constitution-level requirement, not an afterthought to bolt on
later.

**Traceability tooling.** Plan a lightweight mechanism (a script, not a full
CI pipeline at MVP) that scans source and test code for requirement-ID
annotations/tags and produces a report of which requirements have
implementing code and verifying tests, flagging any requirement with neither
and any code annotation pointing at a requirement ID that doesn't exist.
Keep this decoupled from the app's runtime code — it's a development-time
tool, not a shipped feature.


