param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ConsoleJar,
    [ValidateSet('All', 'Baseline', 'Cpp', 'Benchmark')][string]$Suite = 'All',
    [string]$SelectClass,
    [switch]$CompileOnly
)

# Socket-free verification for the compiler; UI work has its own test suite.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
if (-not $JavaHome) {
    $bundledJdk = Join-Path $projectRoot '.local/tools/jdk-21.0.10+7'
    if (Test-Path -LiteralPath $bundledJdk) { $JavaHome = $bundledJdk }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) {
    throw 'Pass -JavaHome pointing to JDK 21, or set JAVA_HOME.'
}
if (-not $ConsoleJar) {
    $cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-console-standalone/1.11.4'
    if (Test-Path -LiteralPath $cache) {
        $ConsoleJar = Get-ChildItem -LiteralPath $cache -Recurse -File -Filter '*.jar' |
            Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $ConsoleJar -or -not (Test-Path -LiteralPath $ConsoleJar)) {
    throw 'Pass -ConsoleJar pointing to junit-platform-console-standalone-1.11.4.jar.'
}
$ConsoleJar = (Resolve-Path -LiteralPath $ConsoleJar).Path
$java = Join-Path $JavaHome 'bin/java.exe'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$outputRoot = Join-Path $projectRoot 'build/compiler-verification'
$mainOutput = Join-Path $outputRoot 'main'
$testOutput = Join-Path $outputRoot 'test'
foreach ($directory in @($mainOutput, $testOutput)) {
    $absolute = [IO.Path]::GetFullPath($directory)
    if (-not $absolute.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Verification output escapes the project: $absolute"
    }
    if (Test-Path -LiteralPath $absolute) { Remove-Item -LiteralPath $absolute -Recurse -Force }
    New-Item -ItemType Directory -Path $absolute -Force | Out-Null
}
$mainSources = @(Get-ChildItem -LiteralPath 'src/main/java/minic' -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -notmatch '[\\/]minic[\\/]ui[\\/]' } | ForEach-Object FullName)
& $javac '-J-Duser.language=en' --release 21 -encoding UTF-8 -d $mainOutput @mainSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$testSources = @(Get-ChildItem -LiteralPath 'src/test/java/minic' -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -notmatch '[\\/]minic[\\/]ui[\\/]' } |
    Where-Object { $Suite -ne 'Baseline' -or $_.FullName -notmatch '[\\/]minic[\\/](cpp|benchmark)[\\/]' } |
    ForEach-Object FullName)
& $javac '-J-Duser.language=en' --release 21 -encoding UTF-8 -classpath "$mainOutput;$ConsoleJar" -d $testOutput @testSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($CompileOnly) { exit 0 }
$classpath = "$mainOutput;$testOutput;$projectRoot/src/main/resources;$projectRoot/src/test/resources;$ConsoleJar"
$selection = @('--scan-class-path')
if ($SelectClass) { $selection = @('--select-class', $SelectClass) }
elseif ($Suite -eq 'Cpp') { $selection = @('--select-package', 'minic.cpp') }
elseif ($Suite -eq 'Benchmark') { $selection = @('--select-package', 'minic.benchmark') }
& $java '-Dfile.encoding=UTF-8' '-Duser.language=en' -classpath $classpath org.junit.platform.console.ConsoleLauncher execute @selection --details=summary --disable-banner --fail-if-no-tests
exit $LASTEXITCODE
