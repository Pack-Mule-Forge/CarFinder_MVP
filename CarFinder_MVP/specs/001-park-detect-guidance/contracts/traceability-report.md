# Contract: Traceability Report

**Tool**: `tools/traceability/Get-TraceabilityReport.ps1` | **Requirements**: FR-038, FR-039, FR-040
| **Principle**: II

A development-time tool, deliberately outside both Gradle modules so it is never compiled into the
APK. CI automation is **not** required at MVP (Principle II), but the exit-code contract below means
it can become a gate later without modification.

---

## Inputs

| Input | Default | Notes |
|-------|---------|-------|
| `-SpecPath` | `specs/001-park-detect-guidance/spec.md` | Authoritative list of requirement IDs |
| `-SourcePaths` | `shared/src`, `app/src` | Scanned recursively for `*.kt` |
| `-OutputPath` | `specs/001-park-detect-guidance/traceability.md` | Generated report |
| `-FailOnGaps` | `$false` | When set, exit non-zero on any gap |

---

## Scanning rules

**Requirement IDs** are parsed from `spec.md` by matching `**FR-###**` at the start of a list item.
The spec is the authority — an ID that is not in the spec does not exist.

**Annotations** are collected from two forms, both recognized:

```kotlin
@Requirement("FR-001", "FR-002")        // primary convention
/** @requirement FR-001 */              // KDoc, also accepted (constitution's example form)
```

**Implementation vs. test** is decided by path, not by annotation: a file under a test source set
(`commonTest`, `androidTest`, `androidHostTest`, `androidDeviceTest`, `src/test`) is a *verifying
test*; anything else is *implementing code*. This keeps the annotation itself uniform.

---

## Output format

```markdown
# Traceability Report
**Generated**: <ISO-8601>   **Spec**: specs/001-park-detect-guidance/spec.md
**Requirements**: 42   **Fully traced**: 40   **Gaps**: 2   **Orphans**: 1

## Fully traced
| Requirement | Implementation | Verifying tests |
|-------------|----------------|-----------------|
| FR-006 | shared/…/ConvergenceWindow.kt:24 | shared/…/ConvergenceWindowTest.kt:31, :47 |

## Implemented but untested          ← violates FR-039
| Requirement | Implementation |

## Specified but unimplemented       ← violates FR-038
| Requirement | Spec line |

## Orphaned annotations              ← ID not present in spec.md
| Annotation | Location |
```

---

## Categories and exit codes

| Category | Meaning | Violates | Exit contribution |
|----------|---------|----------|-------------------|
| Fully traced | ≥1 implementation **and** ≥1 test | — | 0 |
| Implemented but untested | Code annotated, no test | FR-039 | non-zero |
| Specified but unimplemented | In spec, no annotation anywhere | FR-038 | non-zero |
| Orphaned annotation | Annotation ID absent from spec | FR-040 | non-zero |

Exit `0` only when every requirement is fully traced and there are no orphans. Without `-FailOnGaps`
the script always exits `0` and reports — the MVP default, since Principle II does not require a
gate.

---

## Known and accepted limitation

Requirements that are **constraints rather than behavior** have no single implementing declaration
to annotate. Four in this feature:

| Requirement | Why it has no natural annotation site |
|-------------|---------------------------------------|
| FR-034 | "no duplicated literals" is a property of the whole codebase |
| FR-037 | "tests reference constants" is a property of every test |
| FR-041 | "no legacy View system anywhere" is an absence, not a presence |
| FR-042 | "no computation in the UI layer" is likewise an absence |

**Resolution**: annotate the closest meaningful artifact — `ParkingConstants` for FR-034/FR-037, the
`DefaultView` Composable for FR-041/FR-042 — and verify the *absence* properties with dedicated
tests (a lint-style test asserting no `android.view` import in the UI package; a test asserting
`ParkingConstants` values are not duplicated in test sources). The report cannot prove a negative,
and pretending otherwise would make it less trustworthy, not more. This limitation belongs in the
report's own header so a reader is not misled by a green result.

---

## Out of scope

**Backward traceability** — deriving traceability from commit diffs — is explicitly excluded by
Principle II and by the spec's Out of Scope section. This tool scans the working tree only; it does
not read git history, and nothing in its design should come to depend on it.
