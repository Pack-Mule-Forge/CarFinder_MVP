# @requirement QR-008, QR-009, QR-010
<#
.SYNOPSIS
    Builds the requirement traceability report (contracts/traceability.md).

.DESCRIPTION
    Reads the **FR-###** and **QR-###** definitions from the spec, scans Kotlin KDoc, PowerShell comment lines and
    Gradle Kotlin build-file comment lines for "@requirement <ID>[, <ID>]" tags, classifies each tag as
    implementation or test by its path, and writes a Markdown report. With -FailOnGaps it exits 1 when a
    requirement has no code annotation (and is not declared no-code), when a tag names an ID not in the spec, or
    when a requirement has no test tag.
#>
[CmdletBinding()]
param(
    [string]$RootPath = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$SpecPath,
    [string[]]$SourcePaths = @('shared', 'shared-testing', 'app', 'tools/traceability'),
    [string]$NoCodePath,
    [string]$OutputPath,
    [switch]$FailOnGaps,
    [switch]$PassThru
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $SpecPath) { $SpecPath = Join-Path $RootPath 'specs\003-park-detect-guidance\spec.md' }
if (-not $NoCodePath) { $NoCodePath = Join-Path $RootPath 'tools\traceability\no-code-requirements.psd1' }
if (-not $OutputPath) { $OutputPath = Join-Path $RootPath 'specs\003-park-detect-guidance\traceability.md' }

$idPattern = '(?:FR|QR)-\d{3}'
$tagList = "@requirement\s+($idPattern(?:\s*,\s*$idPattern)*)"
$tagPatterns = @{
    '.kt'  = "(?:/\*\*|^\s*\*)\s*$tagList"
    '.ps1' = "^\s*#\s*$tagList"
    '.kts' = "^\s*//\s*$tagList"
}

function Get-RelativePath([string]$Path) {
    $rootFull = [IO.Path]::GetFullPath($RootPath).TrimEnd('\', '/') + '\'
    $full = [IO.Path]::GetFullPath($Path)
    if ($full.StartsWith($rootFull, [StringComparison]::OrdinalIgnoreCase)) { $full = $full.Substring($rootFull.Length) }
    $full -replace '\\', '/'
}

function Test-IsTestPath([string]$RelativePath) {
    $RelativePath -match '(^|/)src/test/' -or
    $RelativePath -match '(^|/)src/[^/]*Test/' -or
    $RelativePath -match '(^|/)tools/traceability/tests/'
}

function Test-IsExcluded([string]$RelativePath) {
    $RelativePath -match '(^|/)build/' -or $RelativePath -match '(^|/)tools/traceability/tests/fixtures/'
}

# Requirement IDs, in spec order, from bold definition markers only.
$specText = Get-Content -Path $SpecPath -Raw
$requirementIds = @([regex]::Matches($specText, "\*\*($idPattern)\*\*") | ForEach-Object { $_.Groups[1].Value } |
    Select-Object -Unique)
$known = @{}
foreach ($id in $requirementIds) { $known[$id] = $true }

$noCode = @{}
if (Test-Path $NoCodePath) { $noCode = Import-PowerShellDataFile -Path $NoCodePath }

# Collect tags.
$tags = New-Object System.Collections.Generic.List[object]
foreach ($sourcePath in $SourcePaths) {
    $dir = Join-Path $RootPath $sourcePath
    if (-not (Test-Path $dir)) { continue }
    $files = Get-ChildItem -Path $dir -Recurse -File |
        Where-Object { $_.Name -like '*.kt' -or $_.Name -like '*.ps1' -or $_.Name -like '*.gradle.kts' }
    foreach ($file in $files) {
        $relative = Get-RelativePath $file.FullName
        if (Test-IsExcluded $relative) { continue }
        $pattern = $tagPatterns[$file.Extension]
        if (-not $pattern) { continue }
        $kind = if (Test-IsTestPath $relative) { 'test' } else { 'implementation' }
        $lineNumber = 0
        foreach ($line in [IO.File]::ReadAllLines($file.FullName)) {
            $lineNumber++
            $match = [regex]::Match($line, $pattern)
            if (-not $match.Success) { continue }
            foreach ($idMatch in [regex]::Matches($match.Groups[1].Value, $idPattern)) {
                $tags.Add([pscustomobject]@{
                    Id = $idMatch.Value; Kind = $kind; Path = $relative; Line = $lineNumber
                })
            }
        }
    }
}

# Classify each requirement.
$requirements = foreach ($id in $requirementIds) {
    $implementation = @($tags | Where-Object { $_.Id -eq $id -and $_.Kind -eq 'implementation' } |
        Select-Object -ExpandProperty Path -Unique)
    $tests = @($tags | Where-Object { $_.Id -eq $id -and $_.Kind -eq 'test' } |
        Select-Object -ExpandProperty Path -Unique)
    $status =
        if ($implementation.Count -gt 0 -and $tests.Count -gt 0) { 'TRACED' }
        elseif ($implementation.Count -gt 0) { 'UNTESTED' }
        elseif ($noCode.ContainsKey($id) -and $tests.Count -gt 0) { 'NO-CODE' }
        elseif ($noCode.ContainsKey($id)) { 'UNTESTED' }
        else { 'NO-ANNOTATION' }
    [pscustomobject]@{
        Id             = $id
        Implementation = ($implementation -join ', ')
        Tests          = ($tests -join ', ')
        Status         = $status
    }
}
$requirements = @($requirements)

$orphans = @($tags | Where-Object { -not $known.ContainsKey($_.Id) } |
    ForEach-Object { [pscustomobject]@{ Id = $_.Id; Location = "$($_.Path):$($_.Line)" } })
$gaps = @($requirements | Where-Object { $_.Status -in 'UNTESTED', 'NO-ANNOTATION' })
$traced = @($requirements | Where-Object { $_.Status -in 'TRACED', 'NO-CODE' })

# Report.
$builder = New-Object System.Text.StringBuilder
$specRelative = Get-RelativePath $SpecPath
$utc = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss') + 'Z'
$dash = [char]0x2014
$dot = [char]0x00B7
[void]$builder.AppendLine("# Traceability Report $dash $specRelative $dash $utc")
[void]$builder.AppendLine()
[void]$builder.AppendLine(("Summary: {0} requirements {4} {1} traced {4} {2} gaps {4} {3} orphaned tags" -f
    $requirements.Count, $traced.Count, $gaps.Count, $orphans.Count, $dot))
[void]$builder.AppendLine()
[void]$builder.AppendLine('| Requirement | Implementation | Tests | Status |')
[void]$builder.AppendLine('|---|---|---|---|')
foreach ($row in $requirements) {
    [void]$builder.AppendLine("| $($row.Id) | $($row.Implementation) | $($row.Tests) | $($row.Status) |")
}
[void]$builder.AppendLine()
[void]$builder.AppendLine('## Gaps')
[void]$builder.AppendLine()
if ($gaps.Count -eq 0) { [void]$builder.AppendLine('None.') }
foreach ($gap in $gaps) { [void]$builder.AppendLine("- $($gap.Id): $($gap.Status)") }
[void]$builder.AppendLine()
[void]$builder.AppendLine('## Orphaned tags')
[void]$builder.AppendLine()
if ($orphans.Count -eq 0) { [void]$builder.AppendLine('None.') }
foreach ($orphan in $orphans) { [void]$builder.AppendLine("- $($orphan.Id) at $($orphan.Location)") }
[void]$builder.AppendLine()
[void]$builder.AppendLine('## Requirements with no code (declared)')
[void]$builder.AppendLine()
if ($noCode.Count -eq 0) { [void]$builder.AppendLine('None.') }
foreach ($id in ($noCode.Keys | Sort-Object)) { [void]$builder.AppendLine("- $($id): $($noCode[$id])") }

$outputDir = Split-Path -Parent $OutputPath
if ($outputDir -and -not (Test-Path $outputDir)) { New-Item -ItemType Directory -Force $outputDir | Out-Null }
[IO.File]::WriteAllText($OutputPath, $builder.ToString(), (New-Object System.Text.UTF8Encoding($false)))

Write-Information ("Traceability: {0} requirements, {1} traced, {2} gaps, {3} orphaned tags. Report: {4}" -f
    $requirements.Count, $traced.Count, $gaps.Count, $orphans.Count, $OutputPath) -InformationAction Continue
foreach ($gap in $gaps) { Write-Information "  GAP $($gap.Id): $($gap.Status)" -InformationAction Continue }
foreach ($orphan in $orphans) { Write-Information "  ORPHAN $($orphan.Id) at $($orphan.Location)" -InformationAction Continue }

if ($PassThru) {
    [pscustomobject]@{ Requirements = $requirements; Orphans = $orphans; Gaps = $gaps }
}

$hasGaps = $gaps.Count -gt 0 -or $orphans.Count -gt 0
if ($FailOnGaps -and $hasGaps) { exit 1 }
exit 0
