# Data Model: Automatic Park Detection & Guidance

**Feature**: `specs/002-park-detect-guidance` | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

Every type on this page lives in `:shared` `commonMain` under `com.packmuleforge.carfindermvp.shared`, unless it
is marked *(android)*. Constant names refer to `CarFinderConstants`, which covers the FR-030 constants, or to
`TuningConstants`, which covers the plan-level knobs.

## Constants

### `CarFinderConstants` (FR-030)

Each value is defined only here. Tests reference the name, never the literal.

| Name | Value | Unit |
|---|---|---|
| `PARKING_SPEED_THRESHOLD_MPH` | 5.0 | mph |
| `DRIVING_SPEED_THRESHOLD_MPH` | 25.0 | mph |
| `CONVERGENCE_RADIUS_METERS` | 10.0 | m |
| `CONVERGENCE_SAMPLE_COUNT` | 3 | readings |
| `PARKING_SAMPLING_INTERVAL_MILLIS` | 5_000 | ms |
| `ARRIVAL_HALF_ANGLE_DEGREES` | 45.0 | ° |
| `DISTANCE_UNIT_THRESHOLD_FEET` | 500.0 | ft |
| `SPEED_FILTER_WINDOW_SIZE` | 3 | readings |
| `FIX_STALENESS_TIMEOUT_MILLIS` | 30_000 | ms |
| `HEADING_STALENESS_TIMEOUT_MILLIS` | 2_000 | ms |

The same object also holds the conversion factors: `METERS_PER_SECOND_TO_MPH`, `METERS_TO_FEET` and
`FEET_PER_MILE`.

### `TuningConstants` (plan-level, see research R2 and R10)

| Name | Value | Purpose |
|---|---|---|
| `IDLE_WATCH_SAMPLING_INTERVAL_MILLIS` | 20_000 | Location interval in FINDING, and in PARKED while guidance is not visible |
| `DRIVING_SAMPLING_INTERVAL_MILLIS` | 5_000 | Location interval in DRIVING |
| `GUIDANCE_SAMPLING_INTERVAL_MILLIS` | 1_000 | Location interval while guidance is visible |
| `AVAILABILITY_RECHECK_INTERVAL_MILLIS` | 500 | Presenter tick that re-checks fix and heading currency (FR-034, SC-010) |
| `CONE_LENGTH_FRACTION` | 0.8 | Cone length as a fraction of the minimum display dimension |

## Entities

### LifecycleState *(persisted)*

```kotlin
enum class LifecycleState { FINDING, DRIVING, PARKING, PARKED }
```

- There is exactly one current value (FR-001). The initial value on a fresh install is `FINDING` (FR-011).
- Invariant: `state == PARKED` ⇔ `parkedLocation != null` (FR-018).

### LocationReading *(transient)*

| Field | Type | Notes |
|---|---|---|
| `latitude` | `Double` | degrees, WGS-84 |
| `longitude` | `Double` | degrees |
| `accuracyMeters` | `Double?` | `null` if the provider gave no horizontal accuracy. A null reading is not usable for convergence or guidance. |
| `speedMetersPerSecond` | `Double?` | `null` if the provider gave no speed. A null reading does not enter the speed filter. |
| `elapsedRealtimeMillis` | `Long` | Monotonic receipt time, used for currency (FR-034) |
| `epochMillis` | `Long` | Wall-clock time of the fix |

Validation happens in the adapter mapping:
- Latitude must be in [-90, 90] and longitude in [-180, 180]. Readings outside these ranges are dropped and
  logged.
- A negative or non-finite accuracy maps to `null`.
- A negative or non-finite speed maps to `null`.

### SpeedMedianFilter *(transient)*

- An immutable value holding a FIFO of the last `SPEED_FILTER_WINDOW_SIZE` speeds in mph.
- `filtered: Double?` is the median once the window is full, and `null` before that.
- The filter is not persisted and not reset on transitions (research R8).

### ConvergenceWindow *(transient)*

- An immutable value holding a FIFO of the last `CONVERGENCE_SAMPLE_COUNT` readings that have `accuracyMeters != null`.
- `isConverged` is true iff the window is full **and** every pair of readings is within
  `CONVERGENCE_RADIUS_METERS` of each other by haversine distance (FR-007).
- `centroid()` returns the mean latitude and longitude (research R8).
- `centroidAccuracyMeters()` returns `max_i(accuracy_i + haversine(centroid, reading_i))` (FR-012).
- The window is reset to `empty()` on entering and on leaving PARKING (FR-008, FR-010).

### ParkedLocation *(persisted)*

| Field | Type | Notes |
|---|---|---|
| `latitude` | `Double` | centroid |
| `longitude` | `Double` | centroid |
| `accuracyMeters` | `Double` | always non-null and > 0 |
| `capturedAtEpochMillis` | `Long` | wall-clock time of the PARKED transition |

- Only one Parked Location exists at a time (FR-013).
- It is created on PARKING→PARKED (FR-012) and deleted on PARKED→DRIVING (FR-015).

### PersistedParkingRecord *(persisted, `@Serializable`)*

```json
{ "schemaVersion": 1, "state": "PARKED",
  "parkedLocation": { "latitude": 37.42, "longitude": -122.08, "accuracyMeters": 11.3,
                      "capturedAtEpochMillis": 1790000000000 } }
```

- The default record, also used for missing, unreadable and corrupted files, is
  `{schemaVersion: 1, state: FINDING, parkedLocation: null}`.
- A record is normalized on read. `state == PARKED && parkedLocation == null` becomes FINDING, and
  `state != PARKED && parkedLocation != null` drops the location (FR-018).
- The record is written atomically as one unit on every change to the lifecycle state or the location (FR-014).
- An unknown `schemaVersion` is treated as corrupted and falls back to the default record.

### EngineState *(transient, exposed as `StateFlow`)*

| Field | Type |
|---|---|
| `lifecycle` | `LifecycleState` |
| `parkedLocation` | `ParkedLocation?` |
| `samplingProfile` | `SamplingProfile` (`IDLE_WATCH`, `DRIVING`, `PARKING`, `GUIDANCE`) |
| `latestFix` | `LocationReading?`, the most recent reading of any quality |

### HeadingReading *(transient)*

| Field | Type |
|---|---|
| `trueHeadingDegrees` | `Double`, in [0, 360), true north, remapped to screen-up |
| `elapsedRealtimeMillis` | `Long` |

`HeadingSource` emits `null` instead of a `HeadingReading` when the heading is unavailable. That covers no
sensor, accuracy `UNRELIABLE`, or no event within `HEADING_STALENESS_TIMEOUT_MILLIS` (FR-031, research R3).

### GuidanceState *(derived, not persisted)*

The presenter computes this from `ParkedLocation`, the latest fix, the latest heading, and the clock.

| Field | Type | Rule |
|---|---|---|
| `isAvailable` | `Boolean` | The state is PARKED, a Parked Location exists, the fix is current (age ≤ `FIX_STALENESS_TIMEOUT_MILLIS`) and has accuracy, and the heading is present, reliable and not stale (FR-031, FR-034) |
| `uncertaintyMeters` | `Double` | `parked.accuracyMeters + fix.accuracyMeters` (FR-019) |
| `distanceMeters` | `Double` | haversine from the fix to the parked location |
| `bearingToCarDegrees` | `Double` | initial bearing from the fix to the car, true north, in [0, 360) |
| `displayBearingDegrees` | `Double` | `(360 − heading + bearingToCar) mod 360` (FR-021) |
| `coneHalfAngleDegrees` | `Double` | `toDegrees(atan2(uncertainty, distance))`, which is 90° at distance 0 (FR-020) |
| `isArrived` | `Boolean` | `coneHalfAngleDegrees ≥ ARRIVAL_HALF_ANGLE_DEGREES` (FR-028) |
| `distanceDisplay` | `DistanceDisplay` | `value` plus `unit` (`FEET` when `distance_ft ≤ DISTANCE_UNIT_THRESHOLD_FEET`, otherwise `MILES`) plus the formatted `text`: whole feet ("412 ft"), or miles to 2 decimals ("0.37 mi") (FR-026) |
| `cone` | `ConeGeometry` | normalized geometry (below) |

### ConeGeometry *(derived)*

All points use normalized coordinates. The origin is the screen center, +y points up the screen, and 1.0 equals
the minimum display dimension. `L = CONE_LENGTH_FRACTION`.

| Field | Meaning |
|---|---|
| `apex: Point` | Person-icon anchor, at `−L/2` along the display bearing |
| `carAnchor: Point` | At `+L/2` along the display bearing |
| `sweepStartDegrees`, `sweepDegrees` | Sector from `displayBearing − halfAngle` spanning `2·halfAngle`, with radius `L` |
| `halfAngleDegrees`, `displayBearingDegrees` | Echoed for semantics (the UI tests read these) |

No centerline is emitted (FR-023).

### ArrivalPromptTracker *(transient, in presenter)*

- It is a small state machine over `isArrived`: `Armed → Prompting → Dismissed`.
- `Armed` changes to `Prompting` when `isArrived` becomes true.
- An answer (Yes or No) changes `Prompting` to `Dismissed`.
- `Dismissed` changes back to `Armed` only when `isArrived` becomes false.
- `isPromptVisible = (state == Prompting)`. "You have arrived" stays visible while `isArrived` is true, whatever
  the tracker state is.
- Answering has **no** effect on `LifecycleState` or `ParkedLocation` (FR-029).

### HomeScreenState *(derived; the only input to the UI)*

```kotlin
sealed interface HomeScreenState {
    data object Driving : HomeScreenState      // "Driving - Waiting to Park."
    data object Unavailable : HomeScreenState  // "Parked location unavailable."
    data object Parking : HomeScreenState      // "Sensing you will be Parking Soon."
    data class Guidance(
        val cone: ConeGeometry,
        val distance: DistanceDisplay,
        val isArrived: Boolean,
        val isArrivalPromptVisible: Boolean,
    ) : HomeScreenState
}
```

`DefaultViewSelector` applies the rules in FR-016's priority order:

1. DRIVING → `Driving`
2. FINDING, or PARKED with guidance not available → `Unavailable`
3. PARKING → `Parking`
4. otherwise → `Guidance`

## Lifecycle state transitions

The input is a `LocationReading` that has been through the speed filter. `v` is the filtered speed, which is null
until the filter window is full.

| From | Guard (evaluated in this order) | To | Side effects |
|---|---|---|---|
| any | `v != null && v > DRIVING_SPEED_THRESHOLD_MPH` | DRIVING | From PARKED: delete the location (FR-015). From PARKING: reset the window to `empty()` (FR-010). Persist. Profile `DRIVING`. |
| DRIVING | `v != null && v ≤ PARKING_SPEED_THRESHOLD_MPH` | PARKING | Reset the window to `empty()`, persist, profile `PARKING` (FR-004, FR-006) |
| PARKING | the reading (if it has accuracy) is added to the window and `isConverged` | PARKED | Store the centroid and its accuracy as `ParkedLocation`, reset the window to `empty()`, persist, profile `IDLE_WATCH` or `GUIDANCE` (FR-007, FR-012) |
| PARKING | not converged | PARKING | The window slides. No timeout (FR-008). |
| FINDING / PARKED | any `v ≤ DRIVING_SPEED_THRESHOLD_MPH` | unchanged | FINDING never enters PARKING (FR-004). PARKED exits only to DRIVING (FR-009). |
| any | `v` in the dead zone, `v == null`, lost fix, lost heading, arrival answer | unchanged | Display-only effects (FR-005, FR-031, FR-029) |
| (restore) | the record is PARKED with no location, or is corrupted | FINDING | Rewrite the normalized record (FR-018) |

Within a single reading, the drive check is evaluated before the PARKING-convergence check, so a reading that is
both fast and clustered returns to DRIVING.

`SamplingProfile` is derived from the state and guidance visibility:
- PARKING → `PARKING`
- DRIVING → `DRIVING`
- PARKED with guidance visible → `GUIDANCE`
- otherwise → `IDLE_WATCH`, which an `IN_VEHICLE` activity hint upgrades to `DRIVING` until the next lifecycle
  change

## Relationships

```text
PersistedParkingRecord 1 ── 0..1 ParkedLocation
EngineState ── derived from ── PersistedParkingRecord + SpeedMedianFilter + ConvergenceWindow
GuidanceState ── derived from ── EngineState.parkedLocation + latest LocationReading + HeadingReading + clock
HomeScreenState ── derived from ── EngineState.lifecycle + GuidanceState + ArrivalPromptTracker
```

**Future extension points** (not built):
- `ParkingHistoryRepository` would append a `ParkedLocation` on each PARKED entry.
- The Map view would consume `ParkedLocation` and `LocationReading` coordinates.
- Telemetry would collect `ParkingEngine.transitions: SharedFlow<Transition>`.
