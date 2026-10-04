param([Parameter(Mandatory=$true)][ValidatePattern('^C[0-9]{2}$')][string]$Stage)
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
Push-Location -LiteralPath $repoRoot
try {
    & .\gradlew.bat --gradle-user-home .gradle-home --console=plain --rerun-tasks "-PvisualizationStage=$Stage" "-PvisualizationRunId=$runId" visualizationCheck
    if ($LASTEXITCODE -ne 0) { throw "Visualization acceptance failed: $Stage / $runId" }
    & git diff --check
    if ($LASTEXITCODE -ne 0) { throw 'Working tree whitespace check failed' }
    & git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'Index whitespace check failed' }
    Write-Output "Accepted $Stage; reports: build/test-results/visualization/$runId"
} finally {
    Pop-Location
}
