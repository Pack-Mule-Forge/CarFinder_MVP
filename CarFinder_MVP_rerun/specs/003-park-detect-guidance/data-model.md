# Data Model: Automatic Park Detection & Guidance (Run 2)

**Feature**: `specs/003-park-detect-guidance` | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

Every type lives in `:shared` `commonMain` under `com.packmuleforge.carfindermvp.shared`.

## Constants

### `CarFinderConstants` (FR-055)

Each value is defined only here. Code and tests use the name (QR-005).

| Name | Value | Spec name |
|---|---|---|
| `PARKING_SPEED_THRESHOLD_MPH` | 5.0 | Parking-speed threshold |
| `DRIVING_SPEED_THRESHOLD_MPH` | 25.0 | Driving-speed threshold |
| `CONVERGENCE_RADIUS_METERS` | 10.0 | Convergence radius |
| `CONVERGENCE_SAMPLE_COUNT` | 3 | Convergence sample count |
| `SAMPLING_INTERVAL_PARKING_MILLIS` | 5_000 | Parking-sampling interval |
| `SAMPLING_INTERVAL_GUIDANCE_MILLIS` | 1_000 | Guidance-sampling interval |
| `SAMPLING_INTERVAL_IDLE_MILLIS` | 20_000 | Idle-sampling interval |
| `ARRIVAL_CONE_HALF_ANGLE_DEGREES` | 45.0 | Arrival cone half-angle |
| `DISTANCE_UNIT_THRESHOLD_FEET` | 500.0 | Distance-unit threshold |
| `FIX_STALENESS_TIMEOUT_MILLIS` | 30_000 | Fix-staleness timeout |
| `HEADING_STALENESS_TIMEOUT_MILLIS` | 2_000 | Heading-staleness timeout |
| `PARKED_RECOVERY_WINDOW_MILLIS` | 180_000 | Parked-recovery window |
| `TIME_TO_GUIDANCE_VISIBLE_TARGET_MILLIS` | 2_000 | Time-to-guidance target |
| `CONE_CONTAINMENT_TARGET` | 0.90 | Cone-containment target |
| `SPEED_FILTER_WINDOW_SIZE` | 3 | Speed-filter window size |
| `AVAILABILITY_RECHECK_INTERVAL_MILLIS` | 500 | Availability re-check interval |
| `CONE_LENGTH_FRACTION` | 0.65 | Cone-length fraction |
| `SHUTDOWN_NOTICE_DURATION_MILLIS` | 2_000 | Shutdown-notice duration |

Unit conversions (not spec constants): meters per second to mph, meters to feet, feet per mile.

## Entities

### LifecycleState *(persisted)*

`FINDING`, `DRIVING`, `PARKING`, `PARKED`. Exactly one is current (FR-001). Initial value `FINDING`
(FR-002). Invariant: `state == PARKED` ⇔ a Parked Location is held (FR-017).

### LocationReading *(transient)*

| Field | Type | Notes |
|---|---|---|
| `latitude`, `longitude` | `Double` | degrees; out-of-range readings never reach shared code (research R4) |
| `accuracyMeters` | `Double?` | `null` when the platform reported none. Never `0.0` for "absent" (FR-010). |
| `speedMetersPerSecond` | `Double?` | `null` when the platform reported none. Never `0.0` for "absent" (FR-009). |
| `receivedElapsedMillis` | `Long` | monotonic receipt time; used for fix age (FR-040) and recovery thinning (FR-025) |

### SpeedFilter *(transient)*

Immutable. Holds the last `SPEED_FILTER_WINDOW_SIZE` speeds, in mph, from readings that carry one.
`smoothed: Double?` is their median when full, `null` otherwise (FR-007, FR-008). Not cleared on
transitions. Empty after a process start.

### ConvergenceWindow *(transient)*

Immutable. Holds the last `CONVERGENCE_SAMPLE_COUNT` readings that have accuracy.

- `isConverged`: full, and every pair is at most `CONVERGENCE_RADIUS_METERS` apart (FR-011).
- `centroid`: mean latitude and longitude.
- `accuracyRadiusMeters`: `max over readings (accuracy + distance to centroid)` (FR-016).
- `lastReceivedElapsedMillis: Long?`: for thinning.

One instance serves PARKING (FR-012, FR-013) and PARKED recovery (FR-021).

### ParkedLocation *(persisted)*

| Field | Type | Notes |
|---|---|---|
| `latitude`, `longitude` | `Double` | centroid |
| `accuracyMeters` | `Double` | FR-016; finite and > 0 |
| `declaredAtEpochMillis` | `Long` | wall-clock time of the PARKED declaration. A correction keeps it (FR-023). |

`isRecoveryOpen(nowEpochMillis)`: `0 ≤ now − declaredAtEpochMillis ≤ PARKED_RECOVERY_WINDOW_MILLIS`.

### PersistedParkingRecord *(persisted)*

```json
{ "schemaVersion": 1, "state": "PARKED",
  "parkedLocation": { "latitude": 0.0, "longitude": 0.0, "accuracyMeters": 11.3,
                      "declaredAtEpochMillis": 1790000000000 } }
```

- Default: `{schemaVersion: 1, state: FINDING, parkedLocation: null}`.
- `normalized()`: PARKED without a location becomes FINDING; a location without PARKED is dropped
  (FR-017, FR-020). `normalized() != this` means the record is logged and rewritten.
- Written as one unit on every change of state or location (FR-018, FR-019).
- Unreadable or unknown-version data is replaced by the default and logged (FR-020).

### HeadingReading *(transient)*

`trueHeadingDegrees: Double` in [0, 360), smoothed, remapped for display rotation, corrected to true
north (FR-033, FR-034). `receivedElapsedMillis: Long`. The source emits `null` when the sensor is
unreliable or missing (FR-041).

### Capability, PermissionStatus and PermissionState

`Capability`: `FINE_LOCATION`, `BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`, `NOTIFICATIONS`, in
request order (FR-047, FR-048).

`PermissionStatus`: `NOT_REQUESTED`, `GRANTED`, `DENIED`.

`PermissionState`: `Map<Capability, PermissionStatus>`, observable, remembered across restarts by the
platform implementation.

- `areRequiredGranted`: `FINE_LOCATION` and `NOTIFICATIONS` are both `GRANTED` (FR-049).
- `FINE_LOCATION` is `GRANTED` only when precise location is granted; approximate location alone
  is `DENIED` (FR-056).
- A capability with no runtime grant on the running platform version is `GRANTED` (FR-048).
- `DENIED` is never final for a required capability: it is requested again at every launch and
  return to the foreground (FR-048). Any status becomes `GRANTED` when a refresh finds the grant.

### DenialConfirmation *(transient, in presenter; never persisted)*

`DenialConfirmation(capability: Capability, isClosing: Boolean)`: the required capability whose
denial awaits the user's answer, and whether the user has confirmed closing (FR-056). `null` when
none is pending.

- Created with `isClosing = false` when a required request returns `false`.
- Set to `null` by `onDenialDismissed` (the same capability is then requested again).
- Set to `isClosing = true` by `onDenialConfirmed`.
- Set to `null` by `onClosed` and by `cancelPermissionSequence`. The presenter is
  application-scoped and can outlive the Activity, so it must not rely on process death to clear
  this (FR-056).

`isClosePending: Boolean` (presenter, transient): becomes `true`
`SHUTDOWN_NOTICE_DURATION_MILLIS` after `onDenialConfirmed`, and stays `true` until `onClosed`. It
is state, not a one-off event, so an Activity that was stopped when it became `true` still sees it
on its next start.

### EngineState *(transient, `StateFlow`)*

`lifecycle`, `parkedLocation`, `latestFix: LocationReading?`, `samplingIntervalMillis`.

### GuidanceState *(derived)*

| Field | Rule |
|---|---|
| `uncertaintyMeters` | `parked.accuracyMeters + fix.accuracyMeters` (FR-030) |
| `distanceMeters` | haversine, fix to parked |
| `bearingToCarDegrees` | initial bearing, true north, [0, 360) |
| `displayBearingDegrees` | `(360 − heading + bearingToCar) mod 360` (FR-032) |
| `coneHalfAngleDegrees` | `degrees(atan2(uncertainty, distance))` (FR-031) |
| `isArrived` | `coneHalfAngleDegrees ≥ ARRIVAL_CONE_HALF_ANGLE_DEGREES` (FR-038) |
| `distanceText` | whole feet when `distance_ft ≤ DISTANCE_UNIT_THRESHOLD_FEET`, otherwise miles to two decimals (FR-037) |
| `cone` | `ConeGeometry` |

### ConeGeometry *(derived)*

Normalized: origin at screen center, +y up, 1.0 = minimum display dimension (FR-036).
`L = CONE_LENGTH_FRACTION`.

| Field | Meaning |
|---|---|
| `apex` | person-icon anchor, `−L/2` along the display bearing (FR-035) |
| `carAnchor` | car-icon anchor, `+L/2` along the display bearing (FR-035) |
| `sweepStartDegrees`, `sweepDegrees` | sector from `displayBearing − halfAngle`, spanning `2 × halfAngle` |
| `halfAngleDegrees`, `displayBearingDegrees` | echoed for UI semantics |

No centerline (FR-035).

### ArrivalPrompt *(transient, in presenter)*

`ARMED → PROMPTING` when `isArrived` becomes true. `PROMPTING → DISMISSED` on Yes or No.
`DISMISSED → ARMED` only when a guidance state with `isArrived == false` is computed. Unavailable
spells and rotation do not change it (FR-039). No effect on lifecycle or Parked Location.

### HomeScreenState *(derived; the UI's only input)*

```kotlin
sealed interface HomeScreenState {
    data class PermissionRequired(val capability: Capability) : HomeScreenState  // FR-056 confirmation
    data object Closing : HomeScreenState                    // "Car Finder is closing."
    data object Unavailable : HomeScreenState   // "Location unavailable"
    data object Driving : HomeScreenState       // "Driving"
    data object Parking : HomeScreenState       // "Sensing you will be parking soon"
    data class Guidance(val cone: ConeGeometry, val distanceText: String) : HomeScreenState
    data class Arrived(val isPromptVisible: Boolean) : HomeScreenState  // "You have arrived"
}
```

Selection, first match (FR-042):

1. `DenialConfirmation` is not `null` → `Closing` if `isClosing`, else `PermissionRequired(capability)`
2. `areRequiredGranted` is false → `Unavailable`
3. DRIVING → `Driving`
4. FINDING → `Unavailable`
5. PARKING → `Parking`
6. PARKED and (no fix, or fix older than `FIX_STALENESS_TIMEOUT_MILLIS`, or fix without accuracy, or
   heading `null`, or heading older than `HEADING_STALENESS_TIMEOUT_MILLIS`) → `Unavailable`
7. PARKED otherwise → `Arrived` if `isArrived`, else `Guidance`

## Lifecycle transitions

`v` is the smoothed speed, evaluated only on a reading that itself carries a speed; otherwise no
speed rule applies to that reading. Rules are tried in order.

| # | From | Guard | To | Effects |
|---|---|---|---|---|
| 1 | FINDING, PARKING, PARKED | `v > DRIVING_SPEED_THRESHOLD_MPH` | DRIVING | Delete location, empty window, persist (FR-004, FR-014, FR-019) |
| 2 | DRIVING | `v ≤ PARKING_SPEED_THRESHOLD_MPH` | PARKING | Empty window, persist (FR-005) |
| 3 | PARKING | reading has accuracy; added; window converged | PARKED | Store centroid, accuracy, `declaredAt = now`; empty window; persist; schedule window-end timer (FR-012, FR-015) |
| 4 | PARKING | otherwise | PARKING | Window slides (FR-013) |
| 5 | PARKED | recovery closed | PARKED | Empty window (FR-024) |
| 6 | PARKED | recovery open; reading has accuracy; received ≥ `SAMPLING_INTERVAL_PARKING_MILLIS` after the last accepted (or window empty); added; converged; centroid > `CONVERGENCE_RADIUS_METERS` from stored | PARKED | Replace location, keep `declaredAt`; empty window; persist; no transition event (FR-022, FR-023, FR-025) |
| 7 | PARKED | recovery open; anything else | PARKED | Window slides if the reading was accepted (FR-021, FR-022) |
| 8 | any | none of the above | unchanged | (FR-006, FR-008, FR-009) |
| — | (restore) | record normalized or defaulted | per record | Log and rewrite if changed (FR-020) |

## Sampling interval (FR-027, FR-028)

`SamplingPolicy.intervalFor(lifecycle, isRecoveryOpen, isGuidanceVisible, isInVehicle)`, first match:

| # | Condition | Interval |
|---|---|---|
| 1 | DRIVING or PARKING | `SAMPLING_INTERVAL_PARKING_MILLIS` |
| 2 | PARKED and recovery open | `SAMPLING_INTERVAL_PARKING_MILLIS` |
| 3 | PARKED and guidance visible | `SAMPLING_INTERVAL_GUIDANCE_MILLIS` |
| 4 | PARKED or FINDING, in vehicle | `SAMPLING_INTERVAL_PARKING_MILLIS` |
| 5 | PARKED or FINDING | `SAMPLING_INTERVAL_IDLE_MILLIS` |

## Relationships

```text
PersistedParkingRecord 1 ── 0..1 ParkedLocation
EngineState     ← PersistedParkingRecord + SpeedFilter + ConvergenceWindow + SamplingPolicy
GuidanceState   ← ParkedLocation + latest LocationReading + HeadingReading
HomeScreenState ← DenialConfirmation + PermissionState + EngineState + GuidanceState + ArrivalPrompt
                  + monotonic clock
```

Not built, not foreclosed: a parking-history store appending each `ParkedLocation`; a map view
reading coordinates; telemetry collecting the engine's transition stream.
