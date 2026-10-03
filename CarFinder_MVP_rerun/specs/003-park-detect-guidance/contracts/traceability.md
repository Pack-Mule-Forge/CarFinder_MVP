# Contract: Requirement Traceability and the Findings Ledger

Development-time tooling only. Nothing here ships with the app (QR-008 to QR-011, research R12).

## Tags

```text
@requirement <ID>[, <ID>]*        ID := (FR|QR)-\d{3}
```

- In Kotlin: inside a KDoc block on the implementing declaration, or on a test function or test
  class (a class tag applies to every test in it).
- In PowerShell: on a `#` comment line.
- In a Gradle Kotlin build file (`*.gradle.kts`): on a `//` comment line. This is how a requirement
  met by build configuration, such as QR-015, is annotated. Such a tag is an implementation tag.
- Ranges are not read. List each ID.
- A file under `src/test/`, any `src/*Test/`, or `tools/traceability/tests/` is a **test** file. Every
  other scanned file is an **implementation** file. `tools/traceability/tests/fixtures/` is not
  scanned.

## Script

```text
tools/traceability/Get-TraceabilityReport.ps1
    [-SpecPath specs/003-park-detect-guidance/spec.md]
    [-SourcePaths shared, shared-testing, app, tools/traceability]
    [-OutputPath specs/003-park-detect-guidance/traceability.md]
    [-FailOnGaps]
```

Requirement IDs come from the spec's `**FR-###**` and `**QR-###**` definition markers.

## Report

```markdown
# Traceability Report — <spec path> — <UTC time>

Summary: N requirements · T traced · G gaps · O orphaned tags

| Requirement | Implementation | Tests | Status |
|---|---|---|---|

## Gaps
## Orphaned tags
## Requirements with no code (declared)
```

| Status | Meaning | Blocks with `-FailOnGaps` |
|---|---|---|
| `TRACED` | code tag and test tag | no |
| `NO-CODE` | listed in `no-code-requirements.psd1`, has a test tag | no |
| `UNTESTED` | code tag, no test tag | yes (QR-010 c) |
| `NO-ANNOTATION` | no code tag and not declared no-code | yes (QR-010 a) |
| orphaned tag | tag names an ID not in the spec | yes (QR-010 b) |

`tools/traceability/no-code-requirements.psd1` maps an ID to the reason it has no code (QR-010). The
report prints it in full. Its entries are QR-001, QR-002, QR-004, QR-005, QR-006, QR-007 and QR-011:
each is a rule about tests or process. QR-016 is not on it, because the replay harness is its code.
`build/` directories are never scanned.

## Tests for the tool (QR-009)

Pester tests in `tools/traceability/tests/` cover: ID parsing; test-versus-implementation
classification; each of the three blocking conditions; the declared no-code list; exit codes; and a
run against the real spec.md that must find exactly the FR and QR IDs the spec defines.

## Findings ledger (QR-011)

`specs/003-park-detect-guidance/analysis-findings.md`, append-only:

```markdown
| ID | Date | Source | Severity | Finding | Status | Resolution / reason for any status change |
```

- A later run may change a finding's status only by adding the reason in the last column.
- No row is deleted, and no severity is lowered without a recorded reason.
- A Pester test checks that IDs are unique and every row has a status.
