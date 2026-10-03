# Contract: Home Screen UI (`:app`, Jetpack Compose)

The UI is a pure function of `HomeScreenState` (QR-013). It computes no geometry, holds no guidance
state and collects no flows inside a Composable. It uses no Android View component (QR-012).

```kotlin
@Composable fun HomeScreen(
    state: HomeScreenState,
    onArrivalAnswered: () -> Unit,
    onDenialConfirmed: () -> Unit,
    onDenialDismissed: () -> Unit,
)
```

| State | Shown | Requirement |
|---|---|---|
| `PermissionRequired(capability)` | A Compose dialog, nothing behind it: "Car Finder needs location permission to run. Close Car Finder?" (or "notification permission" for `NOTIFICATIONS`), a "Close" button calling `onDenialConfirmed` and an "Allow" button calling `onDenialDismissed`. Its dismiss request (back, tap outside) also calls `onDenialDismissed`. | FR-042 rule 1, FR-056 |
| `Closing` | Text "Car Finder is closing." and no action | FR-042 rule 1, FR-056 |
| `Unavailable` | Text "Location unavailable" | FR-042 rules 2, 4, 6 |
| `Driving` | Text "Driving" | FR-042 rule 3 |
| `Parking` | Text "Sensing you will be parking soon" | FR-042 rule 5 |
| `Guidance` | Cone sector, person icon at `cone.apex`, car icon at `cone.carAnchor`, `distanceText` centered. No centerline. | FR-031 to FR-037 |
| `Arrived` | Text "You have arrived". If `isPromptVisible`, a dialog "Do you see your car?" with Yes and No, both calling `onArrivalAnswered`. No cone, icons or distance. | FR-038, FR-039 |

Drawing: the Canvas maps normalized coordinates with one scale (the minimum of width and height) and
one translation (to the center). Nothing else is computed in the UI (FR-036).

## Semantics (test hooks)

| Node tag | Properties |
|---|---|
| `status-message` | text |
| `guidance-cone` | `HalfAngleDegrees`, `DisplayBearingDegrees`, `ApexAnchor`, `CarAnchor`, `IsCenterlineDrawn` (always false) |
| `distance-text` | text |
| `arrival-message` | text |
| `arrival-prompt`, `arrival-yes`, `arrival-no` | presence, click |
| `permission-required`, `permission-required-close`, `permission-required-allow` | text, click, dismiss |
| `closing-message` | text |

## Required UI tests (QR-004, QR-006)

| # | Test | Requirement |
|---|---|---|
| a | For a given uncertainty and distance, `HalfAngleDegrees` equals `GuidanceCalculator.coneHalfAngleDegrees` | FR-031 |
| b | Distance text is in feet at the distance-unit threshold and in miles just above it | FR-037 |
| c | At the arrival half-angle the arrival message and prompt are shown and the cone is not | FR-038, FR-039 |
| d | First launch shows the default view with no interaction | FR-043 |
| e | Icons sit at the two anchors and `IsCenterlineDrawn` is false | FR-035 |
| f | Tapping Yes, and tapping No, removes the prompt node while the arrival message stays | FR-039 |
| g | Each status state shows exactly its text | FR-042 |
| h | The cone's size and center are the same in portrait and landscape | FR-036 |
| i | A guidance update recomposes the guidance display only | FR-046 |
| j | No file under the UI package imports the Android View system | QR-012 |
| k | `PermissionRequired` shows exactly its text for each required capability, with "Close" and "Allow"; "Close" calls `onDenialConfirmed` once; "Allow" and a back press each call `onDenialDismissed` once; `Closing` shows exactly its text and no button | FR-056 |
