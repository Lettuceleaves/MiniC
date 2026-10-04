param(
    [string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent),
    [ValidatePattern('^[a-zA-Z0-9-]+$')][string]$StageName = 'default'
)
$ErrorActionPreference = 'Stop'
function Get-LockedHash([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $sha.Dispose() }
}
$projectPath = [IO.Path]::GetFullPath($ProjectRoot)
$manifestPath = Join-Path $projectPath 'config/visualization/graphviz-runtime.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.version -ne '16.1.0' -or $manifest.platform -ne 'windows-x64') { throw 'Unsupported Graphviz runtime lock' }
$archivePath = [IO.Path]::GetFullPath((Join-Path $projectPath $manifest.archivePath))
$runtimePath = [IO.Path]::GetFullPath((Join-Path $projectPath $manifest.runtimePath))
$licensePath = [IO.Path]::GetFullPath((Join-Path $projectPath $manifest.licensePath))
$stageBase = [IO.Path]::GetFullPath((Join-Path $projectPath 'build/graphviz-staging'))
$stagePath = [IO.Path]::GetFullPath((Join-Path (Join-Path $stageBase $StageName) 'graphviz'))
if (-not $stagePath.StartsWith($stageBase + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Graphviz staging target escaped build/graphviz-staging'
}
foreach ($file in @($archivePath, $licensePath)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing locked Graphviz artifact: $file" }
}
if ((Get-LockedHash $archivePath) -ne $manifest.archiveSha256) { throw 'Graphviz archive checksum mismatch' }
if ((Get-LockedHash $licensePath) -ne $manifest.licenseSha256) { throw 'Graphviz license checksum mismatch' }
foreach ($requiredFile in $manifest.requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $runtimePath $requiredFile) -PathType Leaf)) { throw "Missing Graphviz runtime file: $requiredFile" }
}

# Verify every extracted byte against the locked release archive before replacing a staging directory.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
$expectedFiles = @{}
try {
    foreach ($entry in $archive.Entries) {
        if (-not $entry.FullName.StartsWith($manifest.archiveRoot, [StringComparison]::Ordinal)) { throw 'Unexpected archive root' }
        if ($entry.FullName.EndsWith('/')) { continue }
        $relative = $entry.FullName.Substring($manifest.archiveRoot.Length)
        $filePath = [IO.Path]::GetFullPath((Join-Path $runtimePath $relative))
        if (-not $filePath.StartsWith($runtimePath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Archive entry escaped runtime directory' }
        if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) { throw "Incomplete runtime: $relative" }
        $stream = $entry.Open()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $expected = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '') }
        finally { $stream.Dispose(); $sha.Dispose() }
        if ((Get-LockedHash $filePath) -ne $expected) { throw "Changed runtime: $relative" }
        $expectedFiles[$filePath] = $true
    }
} finally { $archive.Dispose() }
$actualFiles = @(Get-ChildItem -LiteralPath $runtimePath -File -Recurse)
if ($actualFiles.Count -ne $expectedFiles.Count) { throw 'Graphviz runtime contains unexpected or missing files' }
foreach ($file in $actualFiles) { if (-not $expectedFiles.ContainsKey($file.FullName)) { throw "Unexpected runtime file: $($file.FullName)" } }

# The resolved delete target was checked above and is always one generated runtime stage.
if (Test-Path -LiteralPath $stagePath) { Remove-Item -LiteralPath $stagePath -Recurse -Force }
New-Item -ItemType Directory -Path (Split-Path $stagePath -Parent) -Force | Out-Null
Copy-Item -LiteralPath $runtimePath -Destination $stagePath -Recurse
New-Item -ItemType Directory -Path (Join-Path $stagePath 'licenses') -Force | Out-Null
Copy-Item -LiteralPath $licensePath -Destination (Join-Path $stagePath 'licenses/Graphviz-EPL-2.0.txt')
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $stagePath 'runtime-lock.json')
@"
Graphviz $($manifest.version), unmodified official $($manifest.platform) binary distribution.
Release archive: $($manifest.archiveUrl)
SHA-256: $($manifest.archiveSha256)
The Graphviz source code is available under the Eclipse Public License 2.0 at:
$($manifest.sourceUrl)
License: $($manifest.licenseUrl)
All runtime files from the release archive, including existing notices, are preserved.
"@ | Set-Content -LiteralPath (Join-Path $stagePath 'SOURCE.txt') -Encoding UTF8
Write-Output "Staged complete Graphviz $($manifest.version) runtime ($($expectedFiles.Count) release files): $stagePath"
