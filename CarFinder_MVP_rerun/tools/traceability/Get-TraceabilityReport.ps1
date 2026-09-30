#Requires -Version 5.1
# @requirement QR-005, QR-006, QR-007

<#
.SYNOPSIS
Generates the forward-traceability report required by Constitution Principle II.

.DESCRIPTION
Reads requirement IDs (**FR-###** and **QR-###** definition markers) from the spec, scans Kotlin sources for
KDoc "@requirement <ID>[, <ID>]*" tags and tool scripts for "# @requirement" comments, classifies each tag as
implementation or test by its path, and writes a markdown report listing, per requirement, the implementing
file:declaration entries, the verifying file:test entries, untraced requirements and orphaned tags.

Grammar and report format: specs/002-park-detect-guidance/contracts/traceability.md

.PARAMETER SpecPath
Spec file holding the requirement definitions.

.PARAMETER SourcePaths
Directories scanned recursively for *.kt files.

.PARAMETER ToolPaths
Directories scanned (non-recursively) for *.ps1 files carrying "# @requirement" comments.

.PARAMETER ExcludePaths
Path prefixes (relative to the current directory) that are never scanned.

.PARAMETER OutputPath
Where the generated markdown report is written.

.PARAMETER FailOnGaps
Exit with code 1 when any requirement is UNTESTED or UNTRACED, or any tag is orphaned.

.EXAMPLE
.\tools\traceability\Get-TraceabilityReport.ps1 -FailOnGaps
#>

param(
    [string]$SpecPath = "specs/002-park-detect-guidance/spec.md",
    [string[]]$SourcePaths = @("shared/src", "shared-testing/src", "app/src", "benchmark/src"),
    [string[]]$ToolPaths = @("tools/traceability", "tools/traceability/tests", "tools/benchmark"),
    [string[]]$ExcludePaths = @("tools/traceability/tests/fixtures"),
    [string]$OutputPath = "specs/002-park-detect-guidance/traceability.md",
    [switch]$FailOnGaps
)

$ErrorActionPreference = "Stop"

# Non-ASCII report glyphs are built from code points: PowerShell 5.1 reads BOM-less scripts as ANSI.
$EmDash = [string][char]0x2014
$MidDot = [string][char]0x00B7
$Arrow = [string][char]0x2192

$IdPattern = '(?:FR|QR)-\d{3}'
$TagPattern = "@requirement\s+($IdPattern(?:\s*,\s*$IdPattern)*)"
$DeclarationPattern = '\b(?:fun|class|object|interface|val|var)\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?([A-Za-z_]\w*)'

function ConvertTo-RelativePath {
    param([string]$FullPath)
    $relative = Resolve-Path -LiteralPath $FullPath -Relative
    return ($relative -replace '^\.[\\/]', '') -replace '\\', '/'
}

function Test-IsExcluded {
    param([string]$RelativePath)
    foreach ($prefix in $ExcludePaths) {
        $normalized = ($prefix -replace '\\', '/').TrimEnd('/')
        if ($RelativePath -eq $normalized -or $RelativePath.StartsWith("$normalized/")) { return $true }
    }
    return $false
}

function Test-IsTestPath {
    param([string]$RelativePath)
    # src/test/, src/androidTest/, any src/<name>Test/ source set (commonTest, androidHostTest, ...), the tool's own
    # tests/ directory (Pester tests for this script), and the benchmark module and its assertion script.
    return ($RelativePath -match '(^|/)src/(test|[A-Za-z]*Test)/') -or ($RelativePath -match '(^|/)tests/') -or
        ($RelativePath -match '^(benchmark|tools/benchmark)/')
}

function Get-SpecRequirementIds {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { throw "Spec file not found: $Path" }
    $text = Get-Content -LiteralPath $Path -Raw
    return [regex]::Matches($text, "\*\*($IdPattern)\*\*") | ForEach-Object { $_.Groups[1].Value } |
        Sort-Object -Unique
}

function Get-DeclarationName {
    param([string[]]$Lines, [int]$StartIndex)
    for ($i = $StartIndex; $i -lt [Math]::Min($Lines.Count, $StartIndex + 40); $i++) {
        $line = $Lines[$i].Trim()
        if ($line.StartsWith('*') -or $line.StartsWith('/*') -or $line.StartsWith('//') -or $line.StartsWith('@')) {
            if ($line -notmatch '^@\w+\s+(fun|class|object|val|var)\b') { continue }
        }
        if ($line -match $DeclarationPattern) { return $Matches[1] }
    }
    return $null
}

function Get-TagsFromFile {
    param([System.IO.FileInfo]$File, [string]$RelativePath)
    $lines = @(Get-Content -LiteralPath $File.FullName)
    $tags = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $match = [regex]::Match($lines[$i], $TagPattern)
        if (-not $match.Success) { continue }
        $declaration = if ($File.Extension -eq '.kt') { Get-DeclarationName $lines ($i + 1) } else { $File.BaseName }
        foreach ($id in ([regex]::Matches($match.Groups[1].Value, $IdPattern) | ForEach-Object { $_.Value })) {
            $tags += [pscustomobject]@{
                Id          = $id
                Path        = $RelativePath
                Line        = $i + 1
                Declaration = $declaration
                IsTest      = Test-IsTestPath $RelativePath
            }
        }
    }
    return $tags
}

# ----------------------------------------------------------------------------------------------------------------

$specIds = @(Get-SpecRequirementIds $SpecPath)

$files = @()
foreach ($path in $SourcePaths) {
    if (Test-Path -LiteralPath $path) { $files += Get-ChildItem -LiteralPath $path -Filter "*.kt" -Recurse -File }
}
foreach ($path in $ToolPaths) {
    if (Test-Path -LiteralPath $path) { $files += Get-ChildItem -LiteralPath $path -Filter "*.ps1" -File }
}

$tags = @()
foreach ($file in $files) {
    $relative = ConvertTo-RelativePath $file.FullName
    if (Test-IsExcluded $relative) { continue }
    $tags += Get-TagsFromFile $file $relative
}

$orphans = @($tags | Where-Object { $_.Id -notin $specIds })
$rows = foreach ($id in $specIds) {
    $idTags = @($tags | Where-Object { $_.Id -eq $id })
    $impl = @($idTags | Where-Object { -not $_.IsTest } | ForEach-Object { "$($_.Path):$($_.Declaration)" } |
        Sort-Object -Unique)
    $test = @($idTags | Where-Object { $_.IsTest } | ForEach-Object { "$($_.Path):$($_.Declaration)" } |
        Sort-Object -Unique)
    $status = if ($impl.Count -and $test.Count) { 'TRACED' }
              elseif ($impl.Count) { 'UNTESTED' }
              elseif ($test.Count) { 'UNIMPLEMENTED' }
              else { 'UNTRACED' }
    [pscustomobject]@{ Id = $id; Implementation = $impl; Tests = $test; Status = $status }
}
$rows = @($rows)

$traced = @($rows | Where-Object { $_.Status -eq 'TRACED' }).Count
$gaps = @($rows | Where-Object { $_.Status -in @('UNTESTED', 'UNTRACED') })

$report = @()
$report += "# Traceability Report $EmDash $SpecPath $EmDash $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))"
$report += ""
$report += "Summary: $($rows.Count) requirements $MidDot $traced fully traced $MidDot $($gaps.Count) untraced $MidDot " +
    "$($orphans.Count) orphaned tags"
$report += ""
$report += "| Requirement | Implementation | Tests | Status |"
$report += "|---|---|---|---|"
foreach ($row in $rows) {
    $impl = if ($row.Implementation.Count) { $row.Implementation -join '<br>' } else { $EmDash }
    $test = if ($row.Tests.Count) { $row.Tests -join '<br>' } else { $EmDash }
    $report += "| $($row.Id) | $impl | $test | $($row.Status) |"
}
$report += ""
$report += "## Untraced"
if ($gaps.Count) {
    foreach ($row in $gaps) {
        $reason = if ($row.Status -eq 'UNTESTED') { 'no verifying test' } else { 'no implementation and no test' }
        $report += "- $($row.Id) $EmDash $reason"
    }
} else { $report += "- none" }
$report += ""
$report += "## Orphaned tags"
if ($orphans.Count) {
    foreach ($orphan in $orphans) { $report += "- $($orphan.Path):$($orphan.Line) $Arrow $($orphan.Id) (not in spec)" }
} else { $report += "- none" }

$outputDir = Split-Path -Parent $OutputPath
if ($outputDir -and -not (Test-Path -LiteralPath $outputDir)) { New-Item -ItemType Directory -Path $outputDir | Out-Null }
$fullOutputPath = if ([System.IO.Path]::IsPathRooted($OutputPath)) { $OutputPath } else { Join-Path (Get-Location).Path $OutputPath }
[System.IO.File]::WriteAllLines($fullOutputPath, [string[]]$report,
    (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Report written to: $OutputPath"
Write-Host "  Requirements: $($rows.Count)  Traced: $traced  Untraced: $($gaps.Count)  Orphaned tags: $($orphans.Count)"

if ($FailOnGaps -and ($gaps.Count -gt 0 -or $orphans.Count -gt 0)) { exit 1 }
exit 0
