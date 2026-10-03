# @requirement QR-002, QR-008, QR-009, QR-010

BeforeAll {
    $script:Script = Join-Path $PSScriptRoot '..\Get-TraceabilityReport.ps1'
    $script:Mixed = Join-Path $PSScriptRoot 'fixtures\mixed'
    $script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path

    function Invoke-Report {
        param([string]$Root, [string[]]$SourcePaths, [string]$NoCode, [switch]$FailOnGaps)
        $out = Join-Path $TestDrive ('report-' + [guid]::NewGuid() + '.md')
        $params = @{
            RootPath    = $Root
            SpecPath    = (Join-Path $Root 'spec.md')
            SourcePaths = $SourcePaths
            NoCodePath  = $NoCode
            OutputPath  = $out
            PassThru    = $true
        }
        if ($FailOnGaps) { $params.FailOnGaps = $true }
        $result = & $script:Script @params 6>$null
        [pscustomobject]@{ Result = $result; ExitCode = $LASTEXITCODE; Report = (Get-Content $out -Raw) }
    }

    function New-Tree {
        param([hashtable]$Files, [string]$NoCode = "@{}")
        $root = Join-Path $TestDrive ([guid]::NewGuid())
        foreach ($path in $Files.Keys) {
            $full = Join-Path $root $path
            New-Item -ItemType Directory -Force (Split-Path $full) | Out-Null
            Set-Content -Path $full -Value $Files[$path] -Encoding UTF8
        }
        Set-Content -Path (Join-Path $root 'no-code.psd1') -Value $NoCode -Encoding UTF8
        $root
    }
}

Describe 'Get-TraceabilityReport on the mixed fixture' {
    BeforeAll {
        $script:Run = Invoke-Report -Root $Mixed -SourcePaths @('shared', 'app') `
            -NoCode (Join-Path $Mixed 'no-code-requirements.psd1')
        $script:Status = @{}
        foreach ($row in $Run.Result.Requirements) { $Status[$row.Id] = $row.Status }
    }

    It 'parses exactly the bold FR and QR definitions' {
        @($Run.Result.Requirements.Id) | Should -Be @('FR-001', 'FR-002', 'FR-003', 'QR-001', 'QR-002')
    }

    It 'marks a requirement with a code tag and a test tag TRACED' {
        $Status['FR-001'] | Should -Be 'TRACED'
    }

    It 'classifies by path: a tag under src/commonTest is a test tag' {
        $row = $Run.Result.Requirements | Where-Object Id -eq 'FR-001'
        $row.Tests | Should -Match 'commonTest'
        $row.Implementation | Should -Match 'commonMain'
    }

    It 'marks code without a test UNTESTED' {
        $Status['FR-002'] | Should -Be 'UNTESTED'
    }

    It 'marks a requirement with no code tag NO-ANNOTATION, ignoring Kotlin line comments' {
        $Status['FR-003'] | Should -Be 'NO-ANNOTATION'
    }

    It 'marks a declared no-code requirement with a test NO-CODE' {
        $Status['QR-001'] | Should -Be 'NO-CODE'
    }

    It 'counts a // @requirement line in a .gradle.kts file as an implementation tag' {
        $Status['QR-002'] | Should -Be 'TRACED'
        ($Run.Result.Requirements | Where-Object Id -eq 'QR-002').Implementation | Should -Match 'build\.gradle\.kts'
    }

    It 'reports a tag naming an ID not in the spec as orphaned' {
        @($Run.Result.Orphans.Id) | Should -Be @('FR-099')
    }

    It 'prints the declared no-code list in the report' {
        $Run.Report | Should -Match '## Requirements with no code \(declared\)'
        $Run.Report | Should -Match 'QR-001.*A rule about tests'
    }

    It 'exits 0 without -FailOnGaps even when there are gaps' {
        $Run.ExitCode | Should -Be 0
    }
}

Describe 'Get-TraceabilityReport -FailOnGaps' {
    BeforeAll {
        $script:Spec = "- **FR-001**: One.`n- **QR-001**: Two.`n"
        $script:Impl = "/** @requirement FR-001 */`nfun a() = Unit`n"
        $script:Test = "/** @requirement FR-001, QR-001 */`nclass T`n"
        $script:NoCodeQr = "@{ 'QR-001' = 'Process rule.' }"
    }

    It 'exits 0 on a clean tree' {
        $root = New-Tree -NoCode $NoCodeQr -Files @{
            'spec.md' = $Spec; 'm/src/commonMain/A.kt' = $Impl; 'm/src/commonTest/T.kt' = $Test
        }
        (Invoke-Report -Root $root -SourcePaths @('m') -NoCode "$root\no-code.psd1" -FailOnGaps).ExitCode |
            Should -Be 0
    }

    It 'exits 1 on a requirement with no code annotation' {
        $root = New-Tree -Files @{ 'spec.md' = $Spec; 'm/src/commonMain/A.kt' = $Impl; 'm/src/commonTest/T.kt' = $Test }
        (Invoke-Report -Root $root -SourcePaths @('m') -NoCode "$root\no-code.psd1" -FailOnGaps).ExitCode |
            Should -Be 1
    }

    It 'exits 1 on an orphaned tag' {
        $root = New-Tree -NoCode $NoCodeQr -Files @{
            'spec.md' = $Spec; 'm/src/commonMain/A.kt' = "$Impl/** @requirement FR-777 */`nfun b() = Unit`n"
            'm/src/commonTest/T.kt' = $Test
        }
        (Invoke-Report -Root $root -SourcePaths @('m') -NoCode "$root\no-code.psd1" -FailOnGaps).ExitCode |
            Should -Be 1
    }

    It 'exits 1 on code without a test' {
        $root = New-Tree -NoCode $NoCodeQr -Files @{
            'spec.md' = $Spec; 'm/src/commonMain/A.kt' = $Impl
            'm/src/commonTest/T.kt' = "/** @requirement QR-001 */`nclass T`n"
        }
        (Invoke-Report -Root $root -SourcePaths @('m') -NoCode "$root\no-code.psd1" -FailOnGaps).ExitCode |
            Should -Be 1
    }

    It 'still blocks a declared no-code requirement that has no test tag' {
        $root = New-Tree -NoCode $NoCodeQr -Files @{
            'spec.md' = $Spec; 'm/src/commonMain/A.kt' = $Impl
            'm/src/commonTest/T.kt' = "/** @requirement FR-001 */`nclass T`n"
        }
        $run = Invoke-Report -Root $root -SourcePaths @('m') -NoCode "$root\no-code.psd1" -FailOnGaps
        ($run.Result.Requirements | Where-Object Id -eq 'QR-001').Status | Should -Be 'UNTESTED'
        $run.ExitCode | Should -Be 1
    }

    It 'does not scan build directories' {
        $root = New-Tree -NoCode $NoCodeQr -Files @{
            'spec.md' = $Spec; 'm/src/commonMain/A.kt' = $Impl; 'm/src/commonTest/T.kt' = $Test
            'm/build/generated/B.kt' = "/** @requirement FR-888 */`nfun b() = Unit`n"
        }
        $run = Invoke-Report -Root $root -SourcePaths @('m') -NoCode "$root\no-code.psd1" -FailOnGaps
        @($run.Result.Orphans).Count | Should -Be 0
        $run.ExitCode | Should -Be 0
    }

    It 'treats tools/traceability/tests as test files and skips its fixtures' {
        $root = New-Tree -NoCode $NoCodeQr -Files @{
            'spec.md' = $Spec; 'm/src/commonMain/A.kt' = $Impl
            'tools/traceability/tests/X.Tests.ps1' = "# @requirement FR-001, QR-001`n"
            'tools/traceability/tests/fixtures/f/Y.kt' = "/** @requirement FR-555 */`nfun y() = Unit`n"
        }
        $run = Invoke-Report -Root $root -SourcePaths @('m', 'tools/traceability') -NoCode "$root\no-code.psd1" -FailOnGaps
        ($run.Result.Requirements | Where-Object Id -eq 'FR-001').Status | Should -Be 'TRACED'
        @($run.Result.Orphans).Count | Should -Be 0
        $run.ExitCode | Should -Be 0
    }
}

Describe 'Get-TraceabilityReport against the real spec' {
    It 'finds exactly FR-001 to FR-056 and QR-001 to QR-016' {
        $expected = @(1..56 | ForEach-Object { 'FR-{0:D3}' -f $_ }) + @(1..16 | ForEach-Object { 'QR-{0:D3}' -f $_ })
        $out = Join-Path $TestDrive 'real.md'
        $result = & $Script -RootPath $RepoRoot -SourcePaths @('shared') -OutputPath $out -PassThru 6>$null
        @($result.Requirements.Id | Sort-Object) | Should -Be $expected
    }
}
