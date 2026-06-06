param(
    [string]$OutputRoot = ""
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path (Join-Path $scriptRoot "..\..")

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $projectRoot "uiweb-render-check\parity-report\uilocal"
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputRoot)
$gradle = Join-Path $projectRoot "gradlew.bat"

& $gradle "captureUiLocalScreenshots" "-PuiLocalScreenshotOutput=$resolvedOutput"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "JavaFX screenshots captured at $resolvedOutput"
