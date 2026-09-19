# Prompt for /speckit.constitution

Paste the text below as the argument to `/speckit.constitution` (or pass it via
`--files`) so Spec Kit generates `constitution.md` itself, from its own
template, rather than being handed a finished document.

---

Establish the constitution for Car Finder, a mobile app that detects when a
user has parked and guides them back to their vehicle. 

Set these as core, non-negotiable principles:

1. **Test-first coverage of shared domain logic.** All shared domain logic in
   the Kotlin Multiplatform common module — the parking state machine,
   convergence calculation, uncertainty calculation, and the
   bearing/distance/cone-geometry math — must have automated unit test
   coverage, because this logic is shared across platforms and a defect here
   affects both the current Android build and any future iOS build. Tests
   must reference named constants rather than hardcoding their values, so
   changing a constant doesn't require rewriting passing tests. The Compose
   guidance display must have automated UI tests (using Compose's
   semantics-based testing APIs) covering at minimum: cone geometry for a
   given uncertainty/distance input, the feet/miles distance-unit switch, and
   the arrival-confirmation prompt appearing at the arrival threshold.

2. **Requirement traceability, forward.** Every numbered requirement in a
   feature spec must have at least one automated test, and each test must
   identify the requirement ID it verifies (by name or an explicit
   annotation/tag). Every requirement with corresponding code must be
   traceable from that code via an in-code annotation referencing the
   requirement ID (e.g. a KDoc tag like `@requirement REQ-PARK-02`). It must
   be possible to generate a traceability report — manually or via a simple
   script, CI automation not required at MVP — listing, per requirement ID,
   the implementing file(s)/function(s) and verifying test(s), and flagging
   both untraced requirements and orphaned code annotations. Note that a
   *backward* traceability mechanism — deriving traceability updates
   automatically from commit diffs — is a real future direction for this
   project but is explicitly out of scope for the MVP constitution; don't let
   it get baked in as a requirement here.

3. **Platform adapters tested via fakes, not hardware.** Platform-specific
   adapters (Android sensors/location/activity recognition; iOS Core
   Location/Core Motion, when built) must be tested using platform test
   doubles/fakes, consistent with the shared/platform boundary in principle 5
   below. Real-device testing is still valuable for integration validation
   but must not substitute for automated adapter coverage.

4. **Compose-only UI, driven by hoisted state.** The Android guidance display
   (cone, person/car icons, distance text, arrival confirmation) must be
   built entirely in Jetpack Compose — no legacy Android View system anywhere
   in that screen. Rendering must be driven by state hoisted from the shared
   domain layer (e.g. a `StateFlow`), never owned or computed inside the
   Composable; the Composable must be a pure function of that state. The
   display must recompose on every location/heading update without visible
   jank (visible stuttering or dropped frames during animation), respecting the cone-geometry rule from principle 5 across device
   rotation.

5. **Shared core, adapted at the edges.** Domain logic — the parking state
   machine, Parked Location model, location-quality/uncertainty
   calculations, distance/bearing/guidance math, and the persistence model —
   lives in the Kotlin Multiplatform shared module, with zero compile-time or
   runtime dependency on either platform's APIs. Platform-specific services
   (sensors, location, activity recognition, permissions, background
   execution, battery management) sit behind an `expect`/`actual` boundary as
   native adapters. A `PermissionController` abstraction normalizes each
   platform's runtime permission flow into a single suspend function per
   platform — but don't force artificial symmetry where the platforms
   genuinely differ (for example, iOS motion APIs that require no explicit
   permission request should expose observable status rather than a forced
   request/response shape).

Also capture these as technology/platform constraints, separate from the
principles above: the system operates primarily on-device and core
capabilities must not depend on network connectivity; the initial release
targets Android with an architecture that doesn't preclude a later iOS
release sharing most of the domain logic; sensor use (location, motion,
compass) should be limited to what each capability actually needs, with
reasonable battery consumption in mind; and architectural decisions must not
foreclose known future directions — map visualization, parking history, a
"Phone Finder" companion capability sharing this app's location
infrastructure, and future telemetry — even though none of them are MVP
features.

Finally, capture a development-workflow/quality-gate expectation: ambiguity
in a feature spec should be resolved with `/speckit.clarify` before
`/speckit.plan` runs against it, and `/speckit.analyze` should be run — with
any issue it surfaces fixed at its source and re-checked — before
`/speckit.implement` is allowed to generate code for that feature.
