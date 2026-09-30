# Contract: Requirement Traceability Tags & Report

This is a development-time tool only. It has no dependency on, and is not shipped with, `:shared` or `:app`
(Constitution II, QR-005 to QR-007, research R11).

## Tag grammar

A KDoc (`/** … */`) block in any `*.kt` file may contain one or more lines of the form:

```text
@requirement <ID>[, <ID>]*
ID := (FR|QR)-\d{3}
```

- **Implementation tag**: placed on the implementing declaration (class, object, function or property) in a
  production source set.
- **Verification tag**: placed on the test function (preferred) or on the test class, which then applies to
  every test in it, in a test source set.
- **Classification by path**:
  - A file under `src/test/`, `src/androidTest/`, or any `src/<name>Test/` (`commonTest`, `androidHostTest` and
    so on) is a **test** file.
  - Any other file is an **implementation** file.
  - `tools/traceability/tests/fixtures/` is excluded by default.
- Test names may also embed the ID, for example `fun fr007_threeReadings9mApartInLine_doNotConverge()`. The KDoc
  tag is the authoritative link, and the name is informational.

Example:

```kotlin
/**
 * Pairwise convergence over the most recent readings.
 * @requirement FR-007
 */
class ConvergenceWindow(...)

/** @requirement FR-007 */
@Test fun readingsWithinCentroidButNotPairwise_doNotConverge() { ... }
```

## CLI

```text
tools/traceability/Get-TraceabilityReport.ps1
    [-SpecPath specs/002-park-detect-guidance/spec.md]
    [-SourcePaths shared/src, app/src]
    [-OutputPath specs/002-park-detect-guidance/traceability.md]
    [-FailOnGaps]
```

- Requirement IDs are read from the spec as the `**FR-###**` and `**QR-###**` definition markers.
- The exit code is 0 by default. With `-FailOnGaps`, the exit code is 1 if the report contains any untraced
  requirement or any orphaned tag.

## Report format (`traceability.md`, generated and not hand-edited)

```markdown
# Traceability Report — <spec path> — <UTC timestamp>

Summary: N requirements · T fully traced · U untraced · O orphaned tags

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| FR-007 | shared/src/commonMain/.../ConvergenceWindow.kt:ConvergenceWindow | shared/src/commonTest/.../ConvergenceWindowTest.kt:readingsWithinCentroidButNotPairwise_doNotConverge | TRACED |
| FR-017 | app/src/main/.../MainActivity.kt:MainActivity | — | UNTESTED |

## Untraced
- FR-017 — no verifying test

## Orphaned tags
- app/src/main/.../Old.kt:12 → FR-099 (not in spec)
```

A requirement's **status** is one of the following:

- `TRACED`: at least one implementation tag and at least one test tag.
- `UNTESTED`: implementation only.
- `UNIMPLEMENTED`: tests only. This is allowed for process requirements such as QR-005 to QR-007, whose
  "implementation" is this tool itself. Those IDs carry implementation tags in the script's comment header,
  written as `# @requirement QR-007`, and the script scans `tools/traceability/*.ps1` for them.
- `UNTRACED`: neither an implementation tag nor a test tag.

## Tests for the tool

`tools/traceability/tests/Get-TraceabilityReport.Tests.ps1` (Pester) runs against fixture trees and checks four
things:

1. The FR and QR parsing counts.
2. Test versus implementation classification by path.
3. Detection of untraced IDs and orphaned tags.
4. That the `-FailOnGaps` exit code is 1 when there are gaps and 0 when the fixture is clean.
