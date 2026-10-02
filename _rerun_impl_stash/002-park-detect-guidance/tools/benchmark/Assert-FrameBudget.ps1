#Requires -Version 5.1
# @requirement FR-027

<#
.SYNOPSIS
Fails when the guidance benchmark's p95 frame time exceeds the frame budget (FR-027, SC-008).

.DESCRIPTION
Reads the Macrobenchmark JSON report written by `.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest` and
checks the P95 of `frameDurationCpuMs` for GuidanceJankBenchmark.guidanceSession against the budget.

.PARAMETER ReportRoot
Directory searched recursively for *benchmarkData.json.

.PARAMETER BudgetMs
Frame budget in milliseconds (16.7 ms at 60 Hz).

.EXAMPLE
.\tools\benchmark\Assert-FrameBudget.ps1
#>

param(
    [string]$ReportRoot = "benchmark/build/outputs/connected_android_test_additional_output",
    [double]$BudgetMs = 16.7
)

$ErrorActionPreference = "Stop"

$report = Get-ChildItem -LiteralPath $ReportRoot -Filter "*benchmarkData.json" -Recurse -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $report) { throw "No Macrobenchmark report found under $ReportRoot. Run the benchmark first." }

$data = Get-Content -LiteralPath $report.FullName -Raw | ConvertFrom-Json
$run = $data.benchmarks | Where-Object { $_.name -eq "guidanceSession" } | Select-Object -First 1
if (-not $run) { throw "guidanceSession not found in $($report.FullName)" }

$p95 = [double]$run.sampledMetrics.frameDurationCpuMs.P95
$device = "$($data.context.build.model) (API $($data.context.build.version.sdk))"
Write-Host "Device: $device"
Write-Host "p95 frameDurationCpuMs: $p95 ms (budget $BudgetMs ms)"

if ($p95 -gt $BudgetMs) { Write-Host "FAIL: over budget"; exit 1 }
Write-Host "PASS"
exit 0
