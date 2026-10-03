# @requirement QR-011

Describe 'Findings ledger' {
    BeforeAll {
        $script:Ledger = Join-Path $PSScriptRoot '..\..\..\specs\003-park-detect-guidance\analysis-findings.md'
        $script:Rows = @(Get-Content $Ledger |
            Where-Object { $_ -match '^\|' -and $_ -notmatch '^\|\s*ID\s*\|' -and $_ -notmatch '^\|\s*-' } |
            ForEach-Object {
                $cells = $_.Trim().Trim('|') -split '\|' | ForEach-Object { $_.Trim() }
                [pscustomobject]@{ Line = $_; Cells = $cells }
            })
    }

    It 'exists' {
        Test-Path $Ledger | Should -BeTrue
    }

    It 'has rows' {
        $Rows.Count | Should -BeGreaterThan 0
    }

    It 'has exactly seven columns in every row' {
        foreach ($row in $Rows) { $row.Cells.Count | Should -Be 7 -Because $row.Line }
    }

    It 'has a unique ID in every row' {
        $ids = @($Rows | ForEach-Object { $_.Cells[0] })
        @($ids | Group-Object | Where-Object Count -gt 1).Count | Should -Be 0
        foreach ($id in $ids) { $id | Should -Not -BeNullOrEmpty }
    }

    It 'has a status in every row' {
        foreach ($row in $Rows) { $row.Cells[5] | Should -Not -BeNullOrEmpty -Because $row.Cells[0] }
    }

    It 'gives a reason for every status other than Open' {
        foreach ($row in $Rows | Where-Object { $_.Cells[5] -ne 'Open' }) {
            $row.Cells[6] | Should -Not -BeNullOrEmpty -Because $row.Cells[0]
        }
    }
}
