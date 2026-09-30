# Contract: Home Screen & Guidance Display (Jetpack Compose)

The UI is a pure function of `HomeScreenState`, which is defined in [../data-model.md](../data-model.md). It
contains no `AndroidView`, no XML layout, and no `remember { mutableStateOf }` holding domain data
(Constitution IV, QR-008, QR-009).

## Composables

```kotlin
@Composable fun HomeScreen(state: HomeScreenState, onArrivalAnswered: (sawCar: Boolean) -> Unit)
    // when (state): Driving | Unavailable | Parking → StatusMessage(text); Guidance → GuidanceDisplay

@Composable fun StatusMessage(text: String)

@Composable fun GuidanceDisplay(state: HomeScreenState.Guidance, onArrivalAnswered: (Boolean) -> Unit)
```

No Composable collects flows, holds domain state, or runs side effects (Constitution IV). `MainActivity` owns
those jobs outside composition:

- **State**: inside `lifecycleScope.launch { repeatOnLifecycle(STARTED) { presenter.state.collect { uiState = it
  } } }`, the presenter's state is copied into an Activity-held `mutableStateOf<HomeScreenState>`.
  `setContent { CarFinderTheme { HomeScreen(uiState, presenter::onArrivalAnswered) } }` only reads it.
- **Sensors**: `GuidanceSessionObserver : DefaultLifecycleObserver` calls `engine.setGuidanceVisible(true)` and
  `heading.start()` in `onStart`, and reverses both in `onStop`.

## Rendering rules

| Element | Rule | Req |
|---|---|---|
| Status texts | Exactly `"Driving - Waiting to Park."`, `"Parked location unavailable."` and `"Sensing you will be Parking Soon."`, held as string resources with no reformatting | FR-016 |
| Cone | Canvas sector built from `cone.sweepStartDegrees`/`sweepDegrees`, radius `CONE_LENGTH_FRACTION × minDim`, with the apex at `center + apex × minDim`. The y-axis is flipped for screen coordinates. That scale-and-translate is the UI's only arithmetic. | FR-020, FR-021, FR-024 |
| Centerline | Never drawn | FR-023 |
| Person icon | Centered on `cone.apex` | FR-022 |
| Car icon | Centered on `cone.carAnchor` | FR-022 |
| Distance text | `distance.text`, centered on screen and not rotated. Hidden when `isArrived`. | FR-025, FR-026, FR-028 |
| Arrival | When `isArrived`, the cone and icons are not composed and `"You have arrived"` is shown. When `isArrivalPromptVisible`, the prompt `"Do you see your car?"` appears with Yes and No buttons, each of which calls `onArrivalAnswered`. | FR-028, FR-029 |
| Layout | `BoxWithConstraints`, where `minDim = min(maxWidth, maxHeight)` and the cone drawing area is a centered `minDim × minDim` square | FR-024 |

## Test hooks (semantics)

| Tag / property | Node | Value |
|---|---|---|
| `TestTags.STATUS_MESSAGE` | the status `Text` | its text |
| `TestTags.GUIDANCE_CONE` | the cone Canvas | `ConeHalfAngleDegrees` and `ConeDisplayBearingDegrees` (custom `SemanticsPropertyKey<Double>`), and `ConeDrawSize` (`Dp`) |
| `TestTags.PERSON_ICON`, `TestTags.CAR_ICON` | the icons | `contentDescription` of `"You"` and `"Your car"` |
| `TestTags.DISTANCE_TEXT` | the distance `Text` | its text, such as `"412 ft"` or `"0.37 mi"` |
| `TestTags.ARRIVAL_MESSAGE`, `TestTags.ARRIVAL_PROMPT`, `TestTags.ARRIVAL_YES`, `TestTags.ARRIVAL_NO` | the arrival elements | its text |

## Required UI tests

The UI tests run under Robolectric in `app/src/test`. Each carries KDoc `@requirement`.

1. **Cone geometry from inputs** (QR-003, FR-020, FR-021, FR-024). Build `Guidance` by running an uncertainty
   and distance fixture through `GuidanceCalculator` and `ConeGeometryCalculator`, with the fixture values
   derived from constants (Constitution I: "for a given uncertainty/distance input"). Assert that
   `ConeHalfAngleDegrees` and `ConeDisplayBearingDegrees` equal the inputs. Render at a portrait and a landscape
   size, and assert that `ConeDrawSize` is equal in both.
2. **Feet↔miles switch** (QR-003, FR-026). For distances just below, exactly at, and just above
   `DISTANCE_UNIT_THRESHOLD_FEET`, assert that the `DISTANCE_TEXT` unit suffix is `ft`, `ft` and `mi`.
3. **Arrival prompt at threshold** (QR-003, FR-028, FR-029). At a half-angle of `ARRIVAL_HALF_ANGLE_DEGREES`,
   the cone does not exist and the arrival message and prompt are displayed. Clicking Yes invokes the callback
   with `true`, and clicking No invokes it with `false`. Just below the threshold, the cone exists and the
   prompt does not.
4. **Status messages** (QR-003, FR-016). For each of `Driving`, `Unavailable` and `Parking`, `STATUS_MESSAGE`
   has the exact text and `GUIDANCE_CONE` does not exist.
5. **No centerline** (FR-023). The cone node has no child and no semantics for a line. The rule is also backed
   by code review, since pixels are not asserted.
6. **Recomposition scope** (FR-027). 100 successive `Guidance` states that differ only in the display bearing
   recompose the cone once per state and never recompose the status or arrival composables. Frame timing is
   covered separately by the `:benchmark` Macrobenchmark.
7. **Compose-only** (QR-008). A source-scan unit test asserts that `app/src/main` contains no `AndroidView(`,
   `setContentView(` or `res/layout/`.
