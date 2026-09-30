# Car Finder Constitution

Car Finder is a mobile application that detects when a user has parked and guides them back to
their vehicle. This constitution governs how that system is designed, built, tested, and amended.
It supersedes all other engineering practices for this project.

## Core Principles

### I. Test-First Coverage of Shared Domain Logic (NON-NEGOTIABLE)

All shared domain logic in the Kotlin Multiplatform common module MUST have automated unit test
coverage. This explicitly includes, at minimum:

- the parking state machine,
- the convergence calculation,
- the uncertainty calculation,
- the bearing, distance, and cone-geometry math.

Tests MUST reference named constants rather than hardcoding the numeric values of those constants,
so that changing a constant does not require rewriting otherwise-passing tests. A test that
duplicates a constant's literal value is a defect in the test.

The Compose guidance display MUST have automated UI tests written against Compose's
semantics-based testing APIs, covering at minimum:

- cone geometry rendered for a given uncertainty/distance input,
- the feet↔miles distance-unit switch,
- the arrival-confirmation prompt appearing at the arrival threshold.

**Rationale**: This logic is shared across platforms. A defect in it affects both the current
Android build and any future iOS build, so it cannot be validated by manual inspection of one
platform's UI. The named-constant rule keeps tuning work (thresholds, radii, timeouts) cheap
instead of turning every parameter change into a test-rewrite.

### II. Forward Requirement Traceability (NON-NEGOTIABLE)

Every numbered requirement in a feature spec MUST have at least one automated test, and each such
test MUST identify the requirement ID it verifies — by test name or by an explicit
annotation/tag.

Every requirement that has corresponding code MUST be traceable from that code via an in-code
annotation referencing the requirement ID (for example, a KDoc tag such as
`@requirement REQ-PARK-02`).

It MUST be possible to generate a traceability report listing, per requirement ID:

- the implementing file(s) and function(s),
- the verifying test(s),
- untraced requirements (a requirement with no test or no implementing code),
- orphaned code annotations (an annotation referencing a requirement ID that no longer exists).

That report MAY be produced manually or by a simple script. CI automation of the report is NOT
required at MVP.

**Out of scope (deliberately)**: *backward* traceability — deriving traceability updates
automatically from commit diffs — is a recognized future direction for this project and MUST NOT
be treated as a requirement under this constitution. Work toward it is permitted; depending on it
is not.

**Rationale**: Forward traceability is what makes "is this requirement actually built and actually
verified?" a mechanical question rather than a judgment call, and it is achievable by hand at MVP
scale. Baking in the backward mechanism now would impose tooling cost before the project can carry
it.

### III. Platform Adapters Tested via Fakes (NON-NEGOTIABLE)

Platform-specific adapters — Android sensors, location, and activity recognition today; iOS Core
Location and Core Motion when that target is built — MUST be covered by automated tests that use
platform test doubles/fakes, consistent with the shared/platform boundary defined in Principle V.

Real-device testing remains valuable for integration validation and SHOULD be performed, but it
MUST NOT be offered as a substitute for automated adapter coverage. "It was verified on a handset"
does not satisfy this principle.

**Rationale**: Hardware-dependent tests are slow, non-deterministic, and unavailable in the loop
where most defects are introduced. Fakes make adapter behavior — including error, permission-denied,
and degraded-accuracy paths that are hard to provoke on real hardware — reproducible.

### IV. Compose-Only UI Driven by Hoisted State (NON-NEGOTIABLE)

The Android guidance display — cone, person and car icons, distance text, arrival confirmation —
MUST be built entirely in Jetpack Compose. No legacy Android View-system component may appear
anywhere in that screen.

Rendering MUST be driven by state hoisted from the shared domain layer (for example, exposed as a
`StateFlow`). Guidance state MUST NOT be owned or computed inside a Composable; each Composable in
this screen MUST be a pure function of the state passed to it.

The display MUST recompose on every location and heading update without visible jank — that is,
without visible stuttering or dropped frames during animation — and MUST continue to honor the
cone-geometry rule of Principle V (geometry computed in the shared module, never recomputed in the
Composable) across device rotation.

**Rationale**: A Composable that is a pure function of hoisted state is testable through Principle
I's semantics-based UI tests and survives rotation and process recreation without a separate
save/restore path. Mixing in the View system or local state reintroduces exactly the
configuration-change bugs this structure removes.

### V. Shared Core, Adapted at the Edges (NON-NEGOTIABLE)

Domain logic MUST live in the Kotlin Multiplatform shared module with zero compile-time and zero
runtime dependency on either platform's APIs. This includes:

- the parking state machine,
- the Parked Location model,
- location-quality and uncertainty calculations,
- distance, bearing, and guidance math (including cone geometry),
- the persistence model.

Platform-specific services — sensors, location, activity recognition, permissions, background
execution, battery management — MUST sit behind an `expect`/`actual` boundary as native adapters.

A `PermissionController` abstraction MUST normalize each platform's runtime permission flow into a
single suspend function per platform. This normalization MUST NOT be pushed into artificial
symmetry where the platforms genuinely differ: for example, iOS motion APIs that require no
explicit permission request SHALL expose observable status rather than being forced into a
request/response shape that has no meaning on that platform.

**Rationale**: The shared core is the asset that makes a future iOS release cheap. Any platform
type that leaks across the boundary erodes that, and forcing false symmetry at the boundary
produces adapters that lie about what the platform actually does.

## Technology & Platform Constraints

These constraints bind technology choices. They are not principles, and they are amendable on the
PATCH/MINOR path as the platform landscape changes.

- **On-device first.** The system operates primarily on-device. Core capabilities — parking
  detection, storing the parked location, and guidance back to the vehicle — MUST NOT depend on
  network connectivity. Network-dependent behavior is permitted only as enhancement, and its
  absence MUST degrade gracefully.
- **Android first, iOS not precluded.** The initial release targets Android. The architecture MUST
  NOT foreclose a later iOS release that shares most of the domain logic; Principle V is the
  mechanism by which this is enforced.
- **Proportionate sensor use.** Use of location, motion, and compass sensors SHOULD be limited to
  what each capability actually needs — in duty cycle, accuracy class, and duration — with
  reasonable battery consumption as an explicit design consideration rather than an afterthought.
- **Do not foreclose known future directions.** None of the following are MVP features, but
  architectural decisions MUST NOT make them unreasonably expensive to add later:
  - map visualization,
  - parking history,
  - a "Phone Finder" companion capability sharing this app's location infrastructure,
  - telemetry.

## Development Workflow & Quality Gates

- **Clarify before planning.** Ambiguity in a feature spec MUST be resolved with
  `/speckit.clarify` before `/speckit.plan` is run against that spec.
- **Analyze before implementing.** `/speckit.analyze` MUST be run before `/speckit.implement` is
  allowed to generate code for a feature. Any issue it surfaces MUST be fixed at its source — in
  the spec, plan, or tasks artifact that caused it, not by patching a downstream artifact — and
  `/speckit.analyze` MUST then be re-run until it is clean.
- **Traceability is part of "done."** A feature is not complete until every numbered requirement in
  its spec satisfies Principle II.

## Governance

This constitution supersedes all other development practices for Car Finder. Where a tool default,
a template, or a habit conflicts with it, this document wins.

**Amendment procedure**: Amendments MUST be proposed as a change to this file, MUST state the
rationale for the change, and MUST be reviewed and approved before merge. An amendment that
changes how existing code or specs must be structured MUST include a migration note describing what
existing work has to change and by when.

**Versioning policy**: This constitution is versioned with semantic versioning.

- **MAJOR** — a principle is removed, or redefined in a way that is backward incompatible with
  work built under the prior version.
- **MINOR** — a principle or section is added, or existing guidance is materially expanded.
- **PATCH** — clarification, wording, or typo fixes that do not change meaning.

**Compliance review**: Every pull request review MUST verify compliance with these principles.
Complexity that appears to violate a principle MUST be justified explicitly in the PR description,
and an unjustified violation is a blocking review comment. Reviewers SHOULD treat a requested
exemption as a signal that either the design or this constitution needs to change — and if the
latter, the amendment procedure above is the route, not a one-off waiver.

Runtime development guidance for agents and contributors lives in `CLAUDE.md`; where that guidance
conflicts with this constitution, this constitution governs.

**Version**: 1.0.0 | **Ratified**: 2026-09-30 | **Last Amended**: 2026-09-30
