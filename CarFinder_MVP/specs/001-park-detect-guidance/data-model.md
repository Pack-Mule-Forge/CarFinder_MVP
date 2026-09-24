# Phase 1 Data Model: Automatic Park Detection and Return Guidance

**Date**: 2026-09-18 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

All types live in `shared/src/commonMain/` and are platform-free (Principle V). Units are SI
internally; conversion happens only at the presentation boundary and at constant declaration.

---

## Unit policy

**Decision**: every internal value is SI — meters, meters/second, radians for computation, degrees
for bearings at API boundaries. The eight named constants are declared in the units the spec states
them in (mph, feet, degrees) and converted once, at the declaration site.

**Why it matters**: FR-034 forbids duplicating literal constant values, and FR-037 requires tests to
reference the constants. If a test needs `2.2352` to express "5 mph in m/s", the literal has escaped.
Declaring `PARKING_SPEED_THRESHOLD_MPH = 5.0` alongside a derived `PARKING_SPEED_THRESHOLD_MPS`
keeps both the spec-facing and compute-facing forms single-sourced.

---

## `ParkingConstants` *(FR-034)*

The single source of truth. No other file may contain these literals; no test may inline them.

| Constant | Spec value | Derived SI form | Used by |
|----------|-----------|-----------------|---------|
| `PARKING_SPEED_THRESHOLD` | 5 mph | 2.2352 m/s | FR-003, FR-004 |
| `DRIVING_SPEED_THRESHOLD` | 25 mph | 11.176 m/s | FR-002, FR-004, FR-010 |
| `CONVERGENCE_RADIUS` | 10 m | 10.0 m | FR-006 |
| `CONVERGENCE_SAMPLE_COUNT` | 3 | — (count) | FR-006, FR-008 |
| `PARKING_SAMPLE_INTERVAL` | 5 s | 5000 ms | FR-005 |
| `ARRIVAL_CONE_HALF_ANGLE` | 45° | π/4 rad | FR-031 |
| `DISTANCE_UNIT_THRESHOLD` | 500 ft | 152.4 m | FR-029 |
| `FIX_STALENESS_TIMEOUT` | 30 s | 30000 ms | FR-030, FR-043 |

**Validation**: thresholds must satisfy `PARKING_SPEED_THRESHOLD < DRIVING_SPEED_THRESHOLD` — the
dead zone of FR-004 is only well-defined if they are ordered. Assert this in a test.
`FIX_STALENESS_TIMEOUT` must be positive; assert that too.

---

## `GeoPoint`

A position with its reported confidence.

| Field | Type | Notes |
|-------|------|-------|
| `latitudeDegrees` | `Double` | −90.0…90.0 |
| `longitudeDegrees` | `Double` | −180.0…180.0 |
| `accuracyRadiusMeters` | `Double` | ≥ 0. The provider's reported horizontal accuracy; feeds FR-015 |

**Validation**: latitude and longitude in range; accuracy non-negative. Reject rather than clamp —
constitution's input-validation standard.

---

## `LocationSample`

One observation from the location provider. Consumed by the state machine and the convergence window.

| Field | Type | Notes |
|-------|------|-------|
| `point` | `GeoPoint` | |
| `speedMetersPerSecond` | `Double` | ≥ 0. Compared against the thresholds *after* smoothing (R-07) |
| `timestampEpochMillis` | `Long` | Monotonic ordering within a window |

---

## `ParkedLocation` *(FR-007, FR-011, FR-012)*

Where the vehicle is believed to be. At most one exists at any time.

| Field | Type | Notes |
|-------|------|-------|
| `point` | `GeoPoint` | Centroid of the converging samples (FR-007) |
| `capturedAtEpochMillis` | `Long` | |

**Derivation**: the centroid is the mean of the `CONVERGENCE_SAMPLE_COUNT` converging samples'
coordinates. The stored `accuracyRadiusMeters` is the mean of the contributing samples' accuracy
radii — this is the value FR-015 later sums with the live fix's accuracy.

> **Centroid caveat worth a test**: averaging raw latitude/longitude is correct for points 10 m apart
> at any latitude except across the ±180° antimeridian, where naive averaging of −179.99 and +179.99
> yields 0 — the opposite side of the planet. A guard belongs in `ConvergenceWindow`.

**Lifecycle**: created on PARKING → PARKED (FR-007); deleted on drive-away from PARKED or FINDING
(FR-010); after deletion no read may return it (FR-013).

---

## `ParkingState` *(FR-001, FR-009)*

```
DRIVING | PARKING | PARKED | FINDING
```

**FINDING is the default and initial state** (FR-009) — the state in which no `ParkedLocation` is
held, which in this version means the app was just installed and no parking cycle has completed. A
newly installed system starts in FINDING. PARKED is the state in which a location is held, and it
covers walking back to the vehicle. Loss of position signal or compass **never changes the state**;
it changes only what is displayed (FR-030, `GuidanceViewState`).

### Invariants

| Invariant | Requirement |
|-----------|-------------|
| A `ParkedLocation` exists **iff** the state is PARKED | FR-009, FR-012, FR-022 |
| FINDING never holds a `ParkedLocation` | FR-009 |
| Exactly one state is current at any time | FR-001 |
| State and location persist and restore together | FR-011 |
| Loss of live position or heading never changes `ParkingState` or the stored `ParkedLocation` | FR-009, FR-030 |

### Transitions

| From | Trigger | To | Requirement |
|------|---------|-----|-------------|
| *any* | smoothed speed > `DRIVING_SPEED_THRESHOLD` | DRIVING | FR-002 |
| DRIVING | smoothed speed ≤ `PARKING_SPEED_THRESHOLD` | PARKING | FR-003 |
| *any* | `PARKING_SPEED_THRESHOLD` < speed ≤ `DRIVING_SPEED_THRESHOLD` | *(no change)* | FR-004 |
| PARKING | `CONVERGENCE_SAMPLE_COUNT` consecutive samples within `CONVERGENCE_RADIUS` | PARKED *(stores location)* | FR-006, FR-007 |
| PARKING | samples do not converge | PARKING *(slide window, forever)* | FR-008 |
| PARKED | speed > `DRIVING_SPEED_THRESHOLD` | DRIVING *(deletes location)* | FR-010 |
| FINDING | speed > `DRIVING_SPEED_THRESHOLD` | DRIVING *(no-op delete)* | FR-010 |

There is deliberately no transition for signal loss or a missing compass: those conditions are
handled in the view (FR-030), not in the state machine.

**Boundary semantics to encode as tests** — these are the spec's edge cases, and each is an
off-by-one waiting to happen:

- speed *exactly* `PARKING_SPEED_THRESHOLD` → enters PARKING (rule is "at or below")
- speed *exactly* `DRIVING_SPEED_THRESHOLD` → does **not** enter DRIVING (rule is "above"); falls in
  the dead zone
- distance *exactly* `DISTANCE_UNIT_THRESHOLD` → renders in feet
- half-angle *exactly* `ARRIVAL_CONE_HALF_ANGLE` → arrival

---

## `ConvergenceWindow` *(FR-006, FR-008)*

A sliding buffer of the last `CONVERGENCE_SAMPLE_COUNT` samples.

| Behavior | Requirement |
|----------|-------------|
| Holds at most `CONVERGENCE_SAMPLE_COUNT` samples, discarding oldest | FR-006 |
| Converged **iff** all pairwise distances ≤ `CONVERGENCE_RADIUS` (each sample to every other) | FR-006 |
| On non-convergence, slides by one and re-evaluates — no timeout, no failure state | FR-008 |
| Cleared on exit from PARKING | — |

**Convergence definition (FR-006)**: Convergence uses the *all-pairwise* criterion, meaning every
sample's distance to every other sample in the window must be at most `CONVERGENCE_RADIUS` meters.
This is the stricter interpretation. Three samples in a line 9 m apart each pass an "within 10m of
the first sample" test but fail the pairwise test (max distance 18 m between first and third).
Pairwise is the more defensible reading and is what the implementation uses. See FR-006 in spec.md
for the explicit requirement wording.

---

## `UncertaintyRadius` *(FR-015, FR-016)*

Derived, not stored:

```
uncertaintyRadiusMeters = parkedLocation.point.accuracyRadiusMeters
                        + currentFix.accuracyRadiusMeters
```

Recomputed on every position update. Never rounded, clamped, or substituted (FR-016).

---

## `GuidanceViewState` *(FR-018–FR-022, FR-030, FR-042)*

The complete, displayable description of the current moment — computed in `:shared`, consumed by
Compose as a pure input. A sealed hierarchy makes the four-way selection total by construction, so
FR-030's "no undefined display condition can arise" is guaranteed by the type system rather than by
a defensive branch.

| Variant | Shown when | Requirement |
|---------|-----------|-------------|
| `Driving` | state is DRIVING | FR-019 |
| `NoParkedLocation` | state is FINDING, **or** state is PARKED but guidance is unavailable (no current fix, no heading) — the display fallback | FR-020, FR-030 |
| `ParkingSoon` | state is PARKING | FR-021 |
| `Guidance` | state is PARKED, a location exists, and a current fix and heading are available | FR-022, FR-030 |

### `Guidance` payload

| Field | Type | Notes | Requirement |
|-------|------|-------|-------------|
| `distanceMeters` | `Double` | | FR-028 |
| `formattedDistance` | `String` | feet at or below threshold, else miles | FR-029 |
| `displayBearingDegrees` | `Double` | `(360 − deviceHeadingTrueNorth + bearingToCar) mod 360`, where `deviceHeadingTrueNorth` is magnetic heading corrected via `GeomagneticField.getDeclination()` | FR-024 |
| `coneHalfAngleRadians` | `Double` | `atan(uncertaintyRadius / distance)`; returns π/2 (90°) at zero distance | FR-023 |
| `hasArrived` | `Boolean` | half-angle ≥ `ARRIVAL_CONE_HALF_ANGLE` | FR-031 |

**Selection order is strict** (FR-018): Driving → NoParkedLocation → ParkingSoon → Guidance. The
DRIVING check outranks the no-location check, so a brand-new install mid-drive shows the driving
message (FR-019, spec US4 scenario 1). When the state is PARKED but a current fix or a heading is
missing, the result is `NoParkedLocation`; the calculator receives the fix *age* as an input so that
it stays pure, with no clock (FR-042, FR-043).

**Division-by-zero guard**: `coneHalfAngleRadians` divides by `distanceMeters`. At zero distance the
result is undefined; the spec's edge case requires this be treated as arrival, so the calculator
returns `π/2` with `hasArrived = true` rather than propagating a NaN into the renderer.

---

## `ParkedLocationRepository` *(interface only — FR-011, FR-013)*

Declared in `commonMain`, implemented only on the platform side (R-04). Persists the
`ParkedLocation` and `ParkingState` **together**, since FR-011 requires both to survive and the
invariant above couples them.

| Operation | Contract |
|-----------|----------|
| `observe(): Flow<PersistedParkingData>` | Emits current state + location; the UI's slow path |
| `save(state, location?)` | Atomic write of both — a torn write violates FR-011 |
| `clearLocation()` | Deletes the location; subsequent reads must not return it (FR-013) |

Full signatures in [contracts/shared-domain-api.md](./contracts/shared-domain-api.md).

---

## Entity relationships

```
ParkingConstants ──governs──> ParkingStateMachine ──owns──> ConvergenceWindow
                                     │                            │
                                     │ produces                   │ consumes
                                     v                            v
                              ParkingState                  LocationSample
                                     │                            │
                                     └──────persisted with────┐   │ centroid of N
                                                              v   v
                                                       ParkedLocation
                                                              │
                          current fix ──> UncertaintyRadius <─┘
                                                │
                            device heading ──> GuidanceViewStateCalculator
                                                │
                                                v
                                        GuidanceViewState ──> Compose (pure render)
```
