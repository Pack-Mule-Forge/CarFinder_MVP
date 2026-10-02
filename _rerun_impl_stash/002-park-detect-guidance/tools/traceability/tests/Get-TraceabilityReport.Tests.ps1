# Pester 5 tests for Get-TraceabilityReport.ps1.
# @requirement QR-005, QR-006, QR-007
#
# The report script ends with `exit`, so each case runs it in a child PowerShell process from inside a fixture
# tree and inspects the generated markdown and the exit code.

BeforeAll {
    $script:ScriptPath = Join-Path $PSScriptRoot '..\Get-TraceabilityReport.ps1' | Resolve-Path
    $script:FixtureRoot = Join-Path $PSScriptRoot 'fixtures'
    $script:HostExe = (Get-Process -Id $PID).Path

    function Invoke-Report {
        param([string]$Fixture, [switch]$FailOnGaps)
        $dir = Join-Path $script:FixtureRoot $Fixture
        $out = Join-Path ([System.IO.Path]::GetTempPath()) "traceability-$Fixture-$([guid]::NewGuid()).md"
        $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script:ScriptPath,
            '-SpecPath', 'spec.md', '-SourcePaths', 'src', '-ToolPaths', 'none', '-ExcludePaths', 'none',
            '-OutputPath', $out)
        if ($FailOnGaps) { $arguments += '-FailOnGaps' }
        Push-Location $dir
        try { & $script:HostExe @arguments | Out-Null; $code = $LASTEXITCODE } finally { Pop-Location }
        $text = Get-Content -LiteralPath $out -Raw -Encoding UTF8
        Remove-Item -LiteralPath $out
        return [pscustomobject]@{ ExitCode = $code; Text = $text }
    }

    function Get-Row {
        param([string]$Text, [string]$Id)
        $line = ($Text -split "`r?`n") | Where-Object { $_ -like "| $Id |*" } | Select-Object -First 1
        if (-not $line) { return $null }
        $cells = $line.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
        return [pscustomobject]@{ Implementation = $cells[1]; Tests = $cells[2]; Status = $cells[3] }
    }
}

Describe 'Get-TraceabilityReport' {
    Context 'gaps fixture' {
        BeforeAll { $script:Result = Invoke-Report -Fixture 'gaps' }

        It 'counts FR and QR requirement IDs from the spec' {
            $script:Result.Text | Should -Match 'Summary: 7 requirements'
        }

        It 'marks a requirement with implementation and test as TRACED' {
            $row = Get-Row $script:Result.Text 'FR-001'
            $row.Status | Should -Be 'TRACED'
            $row.Implementation | Should -Match 'src/commonMain/kotlin/Widget.kt:Widget'
            $row.Tests | Should -Match 'src/commonTest/kotlin/WidgetTest.kt:widgetSpins'
        }

        It 'marks implementation without a test as UNTESTED' {
            (Get-Row $script:Result.Text 'FR-002').Status | Should -Be 'UNTESTED'
        }

        It 'marks a requirement with no tags as UNTRACED' {
            (Get-Row $script:Result.Text 'FR-003').Status | Should -Be 'UNTRACED'
        }

        It 'marks a test-only requirement as UNIMPLEMENTED' {
            (Get-Row $script:Result.Text 'QR-001').Status | Should -Be 'UNIMPLEMENTED'
        }

        It 'classifies src/test/, src/androidTest/ and src/*Test/ source sets as test paths' {
            (Get-Row $script:Result.Text 'FR-004').Tests | Should -Match 'src/test/kotlin/UnitTest.kt:unitPath'
            (Get-Row $script:Result.Text 'FR-005').Tests | Should -Match 'src/androidTest/kotlin/DeviceTest.kt:devicePath'
            (Get-Row $script:Result.Text 'FR-006').Tests | Should -Match 'src/androidHostTest/kotlin/HostTest.kt:hostPath'
            foreach ($id in 'FR-004', 'FR-005', 'FR-006') { (Get-Row $script:Result.Text $id).Status | Should -Be 'TRACED' }
        }

        It 'reports an orphaned tag with its file and line' {
            $script:Result.Text | Should -Match 'src/commonMain/kotlin/Widget\.kt:14 \S+ FR-099 \(not in spec\)'
        }

        It 'exits 0 without -FailOnGaps even when there are gaps' {
            $script:Result.ExitCode | Should -Be 0
        }

        It 'exits 1 with -FailOnGaps when there are gaps' {
            (Invoke-Report -Fixture 'gaps' -FailOnGaps).ExitCode | Should -Be 1
        }
    }

    Context 'clean fixture' {
        It 'exits 0 with -FailOnGaps when everything is traced' {
            $result = Invoke-Report -Fixture 'clean' -FailOnGaps
            $result.ExitCode | Should -Be 0
            (Get-Row $result.Text 'FR-001').Status | Should -Be 'TRACED'
        }
    }
}
