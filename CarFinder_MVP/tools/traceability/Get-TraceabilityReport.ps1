#Requires -Version 5.1

<#
.SYNOPSIS
Generates a traceability report mapping requirements to implementation and test coverage.

.DESCRIPTION
Scans Kotlin source files for @Requirement annotations and KDoc @requirement tags,
maps them to requirements in spec.md, and generates a report showing fully traced,
unimplemented, untested, and orphaned requirements.

.PARAMETER SpecPath
Path to the authoritative spec.md file containing requirement IDs (FR-###).
Default: specs/001-park-detect-guidance/spec.md

.PARAMETER SourcePaths
Paths to scan for Kotlin source files containing annotations.
Default: @('shared/src', 'app/src')

.PARAMETER OutputPath
Path where the generated report markdown will be written.
Default: specs/001-park-detect-guidance/traceability.md

.PARAMETER FailOnGaps
If set, exit with non-zero status if any gaps are found (unimplemented, untested, orphaned).
Default: $false

.EXAMPLE
Get-TraceabilityReport.ps1 -FailOnGaps
#>

param(
    [string]$SpecPath = "specs/001-park-detect-guidance/spec.md",
    [string[]]$SourcePaths = @("shared/src", "app/src"),
    [string]$OutputPath = "specs/001-park-detect-guidance/traceability.md",
    [switch]$FailOnGaps
)

# ============================================================================
# Helper functions
# ============================================================================

function Get-RequirementIDsFromSpec {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Error "Spec file not found: $Path"
        exit 1
    }

    $content = Get-Content $Path -Raw
    $ids = @()

    # Match **FR-###** anywhere in the line (spec.md writes "- **FR-001**: ...", with a
    # leading list marker, so an anchor at the start of the line never matched -- CR/T083 fix).
    $pattern = '\*\*FR-(\d+a?)\*\*'
    $content -split "`n" | ForEach-Object {
        if ($_ -match $pattern) {
            $ids += "FR-$($matches[1])"
        }
    }

    return $ids | Sort-Object -Unique
}

function Get-TestSourceSets {
    return @('commonTest', 'androidTest', 'androidHostTest', 'androidDeviceTest', 'src/test', 'src/androidTest')
}

function IsTestFile {
    param([string]$FilePath)

    $testSets = Get-TestSourceSets
    foreach ($testSet in $testSets) {
        if ($FilePath -like "*/$testSet/*") {
            return $true
        }
    }
    return $false
}

function Get-AnnotationsFromFile {
    param([string]$FilePath)

    $content = Get-Content $FilePath -Raw
    $ids = @()

    # Match @Requirement("FR-001", "FR-002", ...)
    if ($content -match '@Requirement\((.*?)\)') {
        $args = $matches[1]
        $pattern = '"(FR-\d+a?)"'
        [regex]::Matches($args, $pattern) | ForEach-Object {
            $ids += $_.Groups[1].Value
        }
    }

    # Match /** @requirement FR-### */
    $pattern = '@requirement\s+(FR-\d+a?)'
    [regex]::Matches($content, $pattern) | ForEach-Object {
        $ids += $_.Groups[1].Value
    }

    return $ids | Sort-Object -Unique
}

function Get-LineNumbers {
    param([string]$FilePath, [string[]]$RequirementIDs)

    $lines = @{}
    $content = Get-Content $FilePath

    foreach ($id in $RequirementIDs) {
        for ($i = 0; $i -lt $content.Count; $i++) {
            if ($content[$i] -like "*$id*") {
                if (-not $lines[$id]) {
                    $lines[$id] = @()
                }
                $lines[$id] += ($i + 1)
            }
        }
    }

    return $lines
}

# ============================================================================
# Main script
# ============================================================================

# Get all requirement IDs from spec
$specIds = Get-RequirementIDsFromSpec $SpecPath
Write-Verbose "Found $($specIds.Count) requirements in spec"

# Scan source files for annotations
$implementations = @{}
$verifyingTests = @{}
$orphans = @()

foreach ($sourcePath in $SourcePaths) {
    if (-not (Test-Path $sourcePath)) {
        Write-Verbose "Source path not found: $sourcePath"
        continue
    }

    $files = Get-ChildItem -Path $sourcePath -Filter "*.kt" -Recurse

    foreach ($file in $files) {
        $ids = Get-AnnotationsFromFile $file.FullName
        $isTest = IsTestFile $file.FullName
        $relativePath = Resolve-Path -Relative $file.FullName

        foreach ($id in $ids) {
            # Check for orphaned annotations
            if ($id -notin $specIds) {
                $orphans += @{ Id = $id; Path = $relativePath }
                continue
            }

            if ($isTest) {
                if (-not $verifyingTests[$id]) {
                    $verifyingTests[$id] = @()
                }
                $lines = Get-LineNumbers $file.FullName @($id)
                $fileRef = "$relativePath"
                if ($lines[$id]) {
                    $fileRef += ":" + ($lines[$id] -join ", :")
                }
                $verifyingTests[$id] += $fileRef
            } else {
                if (-not $implementations[$id]) {
                    $implementations[$id] = @()
                }
                $lines = Get-LineNumbers $file.FullName @($id)
                $fileRef = "$relativePath"
                if ($lines[$id]) {
                    $fileRef += ":" + ($lines[$id] -join ", :")
                }
                $implementations[$id] += $fileRef
            }
        }
    }
}

# Categorize requirements
$fullyTraced = @()
$implementedButUntested = @()
$specifiedButUnimplemented = @()

foreach ($id in $specIds) {
    $hasImpl = $id -in $implementations.Keys
    $hasTest = $id -in $verifyingTests.Keys

    if ($hasImpl -and $hasTest) {
        $fullyTraced += $id
    } elseif ($hasImpl -and -not $hasTest) {
        $implementedButUntested += $id
    } elseif (-not $hasImpl) {
        $specifiedButUnimplemented += $id
    }
}

# Generate report
$report = @()
$report += "# Traceability Report"
$report += ""
$report += "**Generated**: $(Get-Date -Format 'o')   **Spec**: $SpecPath"
$report += "**Requirements**: $($specIds.Count)   **Fully traced**: $($fullyTraced.Count)   **Gaps**: $($implementedButUntested.Count + $specifiedButUnimplemented.Count)   **Orphans**: $($orphans.Count)"
$report += ""

if ($fullyTraced.Count -gt 0) {
    $report += "## Fully traced"
    $report += "| Requirement | Implementation | Verifying tests |"
    $report += "|-------------|----------------|-----------------|"
    foreach ($id in ($fullyTraced | Sort-Object)) {
        $impl = $implementations[$id] -join ", "
        $test = $verifyingTests[$id] -join ", "
        $report += "| $id | $impl | $test |"
    }
    $report += ""
}

if ($implementedButUntested.Count -gt 0) {
    $report += "## Implemented but untested"
    $report += "| Requirement | Implementation |"
    $report += "|-------------|----------------|"
    foreach ($id in ($implementedButUntested | Sort-Object)) {
        $impl = $implementations[$id] -join ", "
        $report += "| $id | $impl |"
    }
    $report += ""
}

if ($specifiedButUnimplemented.Count -gt 0) {
    $report += "## Specified but unimplemented"
    $report += "| Requirement |"
    $report += "|-------------|"
    foreach ($id in ($specifiedButUnimplemented | Sort-Object)) {
        $report += "| $id |"
    }
    $report += ""
}

if ($orphans.Count -gt 0) {
    $report += "## Orphaned annotations"
    $report += "| Annotation | Location |"
    $report += "|------------|----------|"
    foreach ($orphan in $orphans) {
        $report += "| $($orphan.Id) | $($orphan.Path) |"
    }
    $report += ""
}

# Write output
$report | Out-File -FilePath $OutputPath -Encoding UTF8

Write-Host "Report written to: $OutputPath"
Write-Host ""
Write-Host "Summary:"
Write-Host "  Fully traced: $($fullyTraced.Count)"
Write-Host "  Implemented but untested: $($implementedButUntested.Count)"
Write-Host "  Specified but unimplemented: $($specifiedButUnimplemented.Count)"
Write-Host "  Orphaned annotations: $($orphans.Count)"

# Exit code
$hasGaps = ($implementedButUntested.Count -gt 0) -or ($specifiedButUnimplemented.Count -gt 0) -or ($orphans.Count -gt 0)
if ($FailOnGaps -and $hasGaps) {
    exit 1
}

exit 0
