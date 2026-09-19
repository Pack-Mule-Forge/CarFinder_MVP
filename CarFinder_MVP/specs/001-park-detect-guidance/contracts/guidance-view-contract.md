# Contract: Guidance UI

**Module**: `:app` | **Principle**: IV (Compose-only, hoisted state) | **Requirements**: FR-017–FR-033, FR-041, FR-042

The UI contract has two halves: what the Composables accept, and what they expose to
semantics-based tests. Both are contracts — the test tags are as binding as the parameters, because
FR-036's UI tests address the tree through them.

---

## Composable signatures

```kotlin
@Composable
@Requirement("FR-017", "FR-018", "FR-041")
fun DefaultView(state: GuidanceViewState, onArrivalAnswered: () -> Unit)

@Composable
@Requirement("FR-023", "FR-024", "FR-025", "FR-026", "FR-027", "FR-028")
fun GuidanceDisplay(guidance: GuidanceViewState.Guidance)

@Composable
@Requirement("FR-019", "FR-020", "FR-021")
fun StatusMessage(text: String)

@Composable
@Requirement("FR-031", "FR-032", "FR-033")
fun ArrivalPrompt(onAnswered: () -> Unit)
```

**Contract**:

- Every Composable is a **pure function of its parameters** (FR-042, Principle IV). None may hold
  `remember`ed domain state, call a repository, read a sensor, or compute geometry. `GuidanceDisplay`
  receives `coneHalfAngleRadians` and `displayBearingDegrees` already computed by `:shared`; it
  converts them to pixels and nothing more.
- No legacy View-system type appears anywhere in this screen — no `AndroidView`, no `View`, no XML
  layout (FR-041).
- `DefaultView` exhaustively `when`s over the sealed `GuidanceViewState`. Because the hierarchy is
  sealed, the compiler enforces FR-018's totality; a new variant cannot be added without handling it.
- `onArrivalAnswered` dismisses the prompt only. It must not touch state or the stored location
  (FR-033), and by FR-014 it cannot reach the state machine at all.

---

## Exact message strings *(FR-019, FR-020, FR-021, FR-031, FR-032)*

These render verbatim, including the existing capitalization and punctuation. UI tests assert on
them, so they belong in one string resource file and nowhere else.

| Constant | Text | Requirement |
|----------|------|-------------|
| `MSG_DRIVING` | `Driving - Waiting to Park` | FR-019 |
| `MSG_NO_LOCATION` | `No parked Location yet.` | FR-020 |
| `MSG_PARKING_SOON` | `Sensing you will be Parking Soon.` | FR-021 |
| `MSG_ARRIVED` | `You have arrived` | FR-031 |
| `MSG_ARRIVAL_PROMPT` | `Do you see your car?` | FR-032 |

> Note the inconsistent casing and terminal punctuation across these five — `Driving - Waiting to
> Park` has no period, `No parked Location yet.` does and capitalizes "Location" mid-sentence. This
> is carried through from the source description deliberately (spec Assumptions). If it is
> unintentional, fixing it is a spec change, not a UI change.

---

## Semantics test tags *(FR-036)*

Compose UI tests address the tree through these. Adding, renaming, or removing one is a contract
change that breaks tests.

| Tag | On | Asserted by |
|-----|-----|------------|
| `guidance_cone` | The cone canvas | cone geometry from inputs |
| `guidance_person_icon` | Person icon at cone base | FR-025 |
| `guidance_car_icon` | Car icon at cone far end | FR-025 |
| `guidance_distance_text` | Centre distance text | FR-028, FR-029 |
| `arrival_message` | "You have arrived" | FR-031 |
| `arrival_prompt` | Confirmation prompt | FR-032 |
| `arrival_answer_yes` / `arrival_answer_no` | Prompt buttons | FR-033 |
| `status_message` | Any of the three status texts | FR-019–FR-021 |

**Geometry assertion strategy**: cone half-angle is not directly readable from the semantics tree.
Expose it as a custom semantics property on `guidance_cone` (a `SemanticsPropertyKey<Float>`) so the
test asserts the *rendered* angle rather than re-deriving it — a test that recomputes
`atan(u/d)` and compares against itself verifies nothing. This is the difference between a UI test
that catches a wiring bug and one that only catches a crash.

---

## Rendering contract

**Cone geometry** (FR-023, FR-026, FR-027):

- Half-angle comes from state; the Composable never calls `atan`.
- The **centerline is not drawn** (FR-026) — only the two edges and the enclosed region.
- All geometry is computed against `min(width, height)` and centred within it (FR-027), so rotation
  changes nothing. The full-screen/elliptical rendering is explicitly deferred (spec Out of Scope).
- Person icon anchors at the cone apex (user position); car icon at the far end (FR-025).

**Orientation** (FR-024): the cone is rotated to `displayBearingDegrees`. Animate rotation with
**shortest-path wrapping** — animating 359°→1° must travel 2°, not 358°. The wrap helper lives in
`:shared` and is unit tested; the Composable only consumes it.

**Frame budget** (SC-004): heading arrives rate-capped at ~20 Hz from the adapter. Read the animated
values inside the `Canvas` draw lambda via `State<T>` rather than hoisting them into a recomposing
parameter, so heading changes trigger redraw without recomposing the tree.

---

## ViewModel wiring contract *(FR-042)*

```kotlin
class GuidanceViewModel(
    repository: ParkedLocationRepository,   // slow, persisted path
    locationProvider: LocationProvider,     // fast, ephemeral path
    headingProvider: HeadingProvider,       // fast, ephemeral path
) : ViewModel() {
    val viewState: StateFlow<GuidanceViewState>
}
```

**Contract**: the ViewModel **combines flows and nothing else**. Every value in `GuidanceViewState`
comes from `GuidanceViewStateCalculator.calculate(...)` in `:shared`. If a `when`, an `atan`, a unit
conversion, or a threshold comparison appears in this class, FR-042 is violated — that logic belongs
in `:shared` where Principle I's tests can reach it.

The two paths stay separate by construction: `repository.observe()` supplies state and parked
location, the providers supply live fix and heading, and only the calculator joins them. The
ephemeral path starts on `ON_START` and stops on `ON_STOP`, so no sensor runs for a UI nobody is
looking at — and neither path can reach the state machine (FR-014).
