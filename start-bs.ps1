param(
    [string]$HostName = "127.0.0.1",
    [int]$UiApiPort = 18080,
    [int]$UiWebPort = 5173,
    [int]$TimeoutSeconds = 120,
    [switch]$NoOpen
)

$ErrorActionPreference = "Stop"

$RepoRoot = $PSScriptRoot
$UiWebRoot = Join-Path $RepoRoot "uiweb"
$LogRoot = Join-Path $RepoRoot "temp\tools\bs"
$UiApiBaseUrl = "http://${HostName}:$UiApiPort"
$UiWebBaseUrl = "http://${HostName}:$UiWebPort"

New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

$UiApiStdout = Join-Path $LogRoot "uiapi.out.log"
$UiApiStderr = Join-Path $LogRoot "uiapi.err.log"
$UiWebStdout = Join-Path $LogRoot "uiweb.out.log"
$UiWebStderr = Join-Path $LogRoot "uiweb.err.log"

function Test-HttpOk {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
    } catch {
        return $false
    }
}

function Test-PortAvailable {
    param([string]$Address, [int]$Port)
    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse($Address), $Port)
        $listener.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $listener) {
            $listener.Stop()
        }
    }
}

function Get-LogTail {
    param([string]$Path)
    if (Test-Path $Path) {
        return (Get-Content -Path $Path -Tail 80 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
    }
    return ""
}

function Wait-HttpReady {
    param(
        [string]$Name,
        [string]$Url,
        [System.Diagnostics.Process]$Process,
        [string]$StdoutLog,
        [string]$StderrLog
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne $Process -and $Process.HasExited) {
            throw "$Name exited before becoming ready.`nSTDOUT:`n$(Get-LogTail $StdoutLog)`nSTDERR:`n$(Get-LogTail $StderrLog)"
        }
        if (Test-HttpOk $Url) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Name at $Url.`nSTDOUT:`n$(Get-LogTail $StdoutLog)`nSTDERR:`n$(Get-LogTail $StderrLog)"
}

function Start-LoggedProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [string]$StdoutLog,
        [string]$StderrLog
    )

    Remove-Item -Force -ErrorAction SilentlyContinue -Path $StdoutLog, $StderrLog
    Write-Host "Starting $Name..."
    return Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $StdoutLog `
        -RedirectStandardError $StderrLog `
        -WindowStyle Hidden `
        -PassThru
}

function Stop-ProcessTree {
    param([System.Diagnostics.Process]$Process, [string]$Name)
    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    Write-Host "Stopping $Name..."
    if ($IsWindows -or $env:OS -eq "Windows_NT") {
        & taskkill.exe /PID $Process.Id /T /F | Out-Null
    } else {
        $Process.Kill($true)
    }
}

if (-not (Test-Path $UiWebRoot)) {
    throw "Cannot find uiweb directory: $UiWebRoot"
}

$GradlePath = if ($IsWindows -or $env:OS -eq "Windows_NT") {
    Join-Path $RepoRoot "gradlew.bat"
} else {
    Join-Path $RepoRoot "gradlew"
}
if (-not (Test-Path $GradlePath)) {
    throw "Cannot find Gradle wrapper: $GradlePath"
}

$startedUiApi = $null
$startedUiWeb = $null

try {
    if (Test-HttpOk "$UiApiBaseUrl/api/health") {
        Write-Host "UIAPI already healthy: $UiApiBaseUrl"
    } elseif (-not (Test-PortAvailable $HostName $UiApiPort)) {
        throw "Port $UiApiPort is already in use, but $UiApiBaseUrl/api/health is not healthy."
    } else {
        if ($IsWindows -or $env:OS -eq "Windows_NT") {
            $gradleCommand = "`"$GradlePath`" --no-daemon -q -PuiApiPort=$UiApiPort runUiApi"
            $startedUiApi = Start-LoggedProcess "UIAPI" "cmd.exe" @("/c", $gradleCommand) $RepoRoot $UiApiStdout $UiApiStderr
        } else {
            $startedUiApi = Start-LoggedProcess "UIAPI" $GradlePath @("--no-daemon", "-q", "-PuiApiPort=$UiApiPort", "runUiApi") $RepoRoot $UiApiStdout $UiApiStderr
        }
        Wait-HttpReady "UIAPI" "$UiApiBaseUrl/api/health" $startedUiApi $UiApiStdout $UiApiStderr
        Write-Host "UIAPI ready: $UiApiBaseUrl"
    }

    if (Test-HttpOk $UiWebBaseUrl) {
        Write-Host "UIWeb already healthy: $UiWebBaseUrl"
    } elseif (-not (Test-PortAvailable $HostName $UiWebPort)) {
        throw "Port $UiWebPort is already in use, but $UiWebBaseUrl is not healthy."
    } else {
        if (-not (Test-Path (Join-Path $UiWebRoot "node_modules"))) {
            throw "Cannot find uiweb\node_modules. Run: cd $UiWebRoot; npm install"
        }
        if ($IsWindows -or $env:OS -eq "Windows_NT") {
            $viteCommand = "set `"VITE_MINIC_UIAPI_BASE_URL=$UiApiBaseUrl`" && npm run dev -- --host $HostName --port=$UiWebPort --strictPort"
            $startedUiWeb = Start-LoggedProcess "UIWeb" "cmd.exe" @("/c", $viteCommand) $UiWebRoot $UiWebStdout $UiWebStderr
        } else {
            $viteCommand = "VITE_MINIC_UIAPI_BASE_URL='$UiApiBaseUrl' npm run dev -- --host $HostName --port=$UiWebPort --strictPort"
            $startedUiWeb = Start-LoggedProcess "UIWeb" "sh" @("-lc", $viteCommand) $UiWebRoot $UiWebStdout $UiWebStderr
        }
        Wait-HttpReady "UIWeb" $UiWebBaseUrl $startedUiWeb $UiWebStdout $UiWebStderr
        Write-Host "UIWeb ready: $UiWebBaseUrl"
    }

    Write-Host ""
    Write-Host "MiniC BS architecture is running."
    Write-Host "Frontend: $UiWebBaseUrl"
    Write-Host "Backend : $UiApiBaseUrl"
    Write-Host "Logs    : $LogRoot"
    Write-Host "Press Ctrl+C to stop processes started by this script."

    if (-not $NoOpen) {
        Start-Process $UiWebBaseUrl | Out-Null
    }

    while ($true) {
        if ($null -ne $startedUiApi -and $startedUiApi.HasExited) {
            throw "UIAPI exited unexpectedly. See $UiApiStderr"
        }
        if ($null -ne $startedUiWeb -and $startedUiWeb.HasExited) {
            throw "UIWeb exited unexpectedly. See $UiWebStderr"
        }
        Start-Sleep -Seconds 1
    }
} finally {
    Stop-ProcessTree $startedUiWeb "UIWeb"
    Stop-ProcessTree $startedUiApi "UIAPI"
}
