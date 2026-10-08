param(
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$Version = 'v2.2_win_x86_64',
    [string]$AppVersion = '1.0.0',
    [string]$JdkPath = '.local/tools/jdk-21.0.10+7'
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Assert-Path([string]$Path, [string]$What) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "$What not found: $Path" }
}

# 前置产物：installDist 与 Graphviz 运行时由 Gradle 门禁生成。
$installLib = Join-Path $repoRoot 'build/install/Craken/lib'
$graphvizRuntime = Join-Path $repoRoot 'build/install/Craken/runtime/graphviz'
$jpackageExe = Join-Path (Join-Path $repoRoot $JdkPath) 'bin/jpackage.exe'
Assert-Path (Join-Path $installLib 'Craken-1.0.0.jar') 'Gradle installDist lib'
Assert-Path (Join-Path $graphvizRuntime 'bin/neato.exe') 'Graphviz runtime'
Assert-Path $jpackageExe 'jpackage'

# 每次打包使用独立工作目录，避免与历史构建互相覆盖，也不需要递归删除。
$workBase = Join-Path $repoRoot 'build/package-work'
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$workDir = Join-Path $workBase "run-$runId"
$inputDir = Join-Path $workDir 'input'
$modulesDir = Join-Path $workDir 'modules'
$imageDest = Join-Path $workDir 'image'
$imageDir = Join-Path $imageDest 'Craken'
New-Item -ItemType Directory -Force -Path $inputDir, $modulesDir, $imageDest | Out-Null

# 应用镜像布局：非 JavaFX 依赖走 classpath，JavaFX 四个模块 jar 走 module path。
Copy-Item -Path (Join-Path $installLib 'javafx-*.jar') -Destination $modulesDir
Copy-Item -Path (Join-Path $installLib '*.jar') -Destination $inputDir -Exclude 'javafx-*.jar'

# 图标：从应用图标 PNG 生成多尺寸 ICO（需要 Pillow；若无 Python 则报错退出）。
$iconPath = Join-Path $workDir 'Craken.ico'
$iconPng = Join-Path $repoRoot 'src/main/resources/craken/ui/icons/app-icon-rounded.png'
Assert-Path $iconPng 'application icon PNG'
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { throw 'python not found on PATH; required to render the .ico' }
& $python.Source -c "from PIL import Image; im=Image.open(r'$($iconPng.Replace('\','/'))').convert('RGBA'); im.save(r'$($iconPath.Replace('\','/'))', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $iconPath)) { throw 'Failed to render Craken.ico' }

& $jpackageExe --type app-image --name Craken --app-version $AppVersion --vendor Craken `
    --icon $iconPath `
    --input $inputDir --main-jar 'Craken-1.0.0.jar' --main-class 'craken.ui.Starter' `
    --module-path $modulesDir --add-modules 'javafx.controls,javafx.swing,java.logging' `
    --java-options '--add-exports=java.desktop/sun.font=ALL-UNNAMED' `
    --java-options '-Dfile.encoding=UTF-8' `
    --java-options '-Duser.language=en' `
    --java-options '-Duser.country=US' `
    --dest $imageDest
if ($LASTEXITCODE -ne 0) { throw "jpackage failed: $LASTEXITCODE" }
Assert-Path (Join-Path $imageDir 'Craken.exe') 'jpackage app image'

# 组装正式包目录：runtime/java 放精简 JRE，runtime/graphviz 放可视化运行时。
$outDir = Join-Path (Join-Path $repoRoot 'release') $Version
if (Test-Path -LiteralPath $outDir) { throw "release dir already exists: $outDir" }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Copy-Item -Path (Join-Path $imageDir '*') -Destination $outDir -Recurse
# jpackage 的 JRE 位于包根 runtime/；按布局移到 runtime/java/，Graphviz 随后放入 runtime/graphviz/。
Rename-Item -LiteralPath (Join-Path $outDir 'runtime') -NewName 'runtime-jre'
New-Item -ItemType Directory -Force -Path (Join-Path $outDir 'runtime') | Out-Null
Move-Item -LiteralPath (Join-Path $outDir 'runtime-jre') -Destination (Join-Path $outDir 'runtime/java')

# 运行时模块门禁：JNA（pty4j 终端依赖）在类初始化时使用 java.util.logging，
# jlink 若缺少 java.logging 会导致 "Could not initialize class com.sun.jna.Native"。
$jimageExe = Join-Path (Join-Path $repoRoot $JdkPath) 'bin/jimage.exe'
Assert-Path $jimageExe 'jimage'
$loggingPresent = & $jimageExe list (Join-Path $outDir 'runtime/java/lib/modules') |
    Select-String -SimpleMatch 'java/util/logging/Logger.class' -Quiet
if (-not $loggingPresent) {
    throw 'Packaged runtime is missing java.logging; JNA/pty4j would fail at startup'
}

# jpackage 默认 runtime 位于 $APPDIR\runtime，改指 runtime/java。
$cfgPath = Join-Path $outDir 'app/Craken.cfg'
$cfg = Get-Content -LiteralPath $cfgPath
$sectionIndex = [Array]::IndexOf($cfg, '[Application]')
if ($sectionIndex -lt 0) { throw 'Craken.cfg missing [Application] section' }
$cfg = @($cfg[0..$sectionIndex]) + @('app.runtime=$ROOTDIR\runtime\java') + @($cfg[($sectionIndex + 1)..($cfg.Count - 1)])
Set-Content -LiteralPath $cfgPath -Value $cfg -Encoding Ascii

# 自制标准库声明头，编译器从项目根的 lib 读取（-Djpackage.app-path 回退）。
Copy-Item -Path (Join-Path $repoRoot 'lib') -Destination (Join-Path $outDir 'lib') -Recurse
Copy-Item -Path $graphvizRuntime -Destination (Join-Path $outDir 'runtime/graphviz') -Recurse

# 示例、配置占位、许可证说明与用户文档。
$examplesSrc = Join-Path $repoRoot 'release/v2.1_loc_win_x86_64/examples'
if (Test-Path -LiteralPath $examplesSrc) {
    New-Item -ItemType Directory -Force -Path (Join-Path $outDir 'examples') | Out-Null
    Copy-Item -Path (Join-Path $examplesSrc '*.mc') -Destination (Join-Path $outDir 'examples')
}
Set-Content -LiteralPath (Join-Path $outDir 'settings.json') -Value '{}' -Encoding UTF8

$readme = @"
# Craken $Version

Craken 可视化编译器工作台，Windows x86_64。

- 启动：双击 `Craken.exe`
- 标准库声明头：`lib/`（含 `lib/stl/`）
- 示例源码：`examples/`
- 可视化布局运行时：`runtime/graphviz/`（Graphviz 16.1.0，内置，不依赖系统 PATH）
- 运行时配置占位：`settings.json`
- 第三方许可证：`licenses/` 与 `runtime/java/legal/`、`runtime/graphviz/licenses/`

本包内不包含本机或构建机的绝对路径；所有路径均从包根目录出发。
"@
Set-Content -LiteralPath (Join-Path $outDir 'README.md') -Value $readme -Encoding UTF8

New-Item -ItemType Directory -Force -Path (Join-Path $outDir 'docs'), (Join-Path $outDir 'licenses') | Out-Null
$guide = @"
# 使用说明

1. 双击包根目录下的 `Craken.exe` 启动工作台。
2. 打开 `examples/main.mc` 可走完编译流水线；可视化示例为
   `examples/visual_binary_tree.mc` 与 `examples/visual_red_black_tree.mc`。
3. `lib/` 是 Craken 标准库声明头（`*.mh`），用户源码用
   `#include "name.mh"` 或 `<name.h>` 引用，包内路径全部相对包根目录。
4. `settings.json` 目前为占位配置；后续版本会把用户偏好写到这里。
   若安装到受保护的 `Program Files`，请以可写权限运行或复制到用户目录。
"@
Set-Content -LiteralPath (Join-Path $outDir 'docs/使用说明.md') -Value $guide -Encoding UTF8

$notices = @"
THIRD-PARTY NOTICES

本发行包包含以下第三方组件，各自许可证副本保留在其目录内：

- OpenJDK 21 runtime（jlink 精简镜像）：GPLv2 with Classpath Exception，
  许可证位于 runtime/java/legal/。
- OpenJFX 21.0.2（javafx.controls / javafx.swing 及依赖模块）：
  GPLv2 with Classpath Exception，打包进 runtime/java。
- Graphviz 16.1.0（Eclipse Public License 2.0）：
  运行时文件与许可证位于 runtime/graphviz/，许可证副本
  runtime/graphviz/licenses/Graphviz-EPL-2.0.txt。
- RSyntaxTextArea 4.0.1：BSD-3-Clause。
- JEditerm 3.76：EPL-2.0（部分组件 LGPL/BSD）。
- Pty4J 0.13.12：EPL-1.0。
- SLF4J 2.0.13：MIT。
- JNA 5.14.0：Apache-2.0 或 LGPL-2.1。
- Kotlin stdlib 2.4.0：Apache-2.0。
"@
Set-Content -LiteralPath (Join-Path $outDir 'licenses/THIRD-PARTY-NOTICES.txt') -Value $notices -Encoding UTF8

# 打包前门禁：任何文件（含二进制）不得出现本机/构建机绝对路径。
function Invoke-AbsolutePathScan([string]$Root) {
    $machinePattern = '(?i)(E:[\\/]projects|C:[\\/]Users[\\/]Administrator|D:[\\/]python|E:[\\/]ConfyUI|C:[\\/]Users[\\/]admin)'
    $anyDrivePattern = '(?i)\b[A-Za-z]:[\\/]'
    $found = [System.Collections.Generic.List[object]]::new()
    $graphvizThirdParty = 0
    $encoding = [Text.Encoding]::GetEncoding(28591)
    foreach ($file in Get-ChildItem -LiteralPath $Root -Recurse -File) {
        $text = $encoding.GetString([IO.File]::ReadAllBytes($file.FullName))
        foreach ($match in [regex]::Matches($text, $machinePattern)) {
            $start = [Math]::Max(0, $match.Index - 20)
            $length = [Math]::Min(70, $text.Length - $start)
            $sample = ($text.Substring($start, $length) -replace '[^\x20-\x7E]', '.')
            $found.Add([pscustomobject]@{
                file = $file.FullName.Substring($Root.Length + 1)
                sample = $sample
            })
        }
        if ($file.FullName.Replace('\', '/').Contains('/runtime/graphviz/')) {
            $graphvizThirdParty += [regex]::Matches($text, $anyDrivePattern).Count
        }
    }
    return [pscustomobject]@{
        machinePathMatches = $found
        graphvizDrivePathMatches = $graphvizThirdParty
    }
}

$scan = Invoke-AbsolutePathScan $outDir
if ($scan.machinePathMatches.Count -gt 0) {
    $scan.machinePathMatches | ForEach-Object { Write-Output "  $($_.file): $($_.sample)" }
    throw 'Absolute machine paths found in release; packaging aborted'
}

$manifest = [ordered]@{
    name = 'Craken'
    version = $Version
    kind = 'windows-x86_64'
    appVersion = $AppVersion
    sourceCommit = (git -C $repoRoot rev-parse HEAD).Trim()
    generatedAt = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')
    entry = 'Craken.exe'
    mainClass = 'craken.ui.Starter'
    layout = [ordered]@{
        app = 'Craken jar 与第三方依赖 jar'
        runtime_java = 'jlink 精简的 Java 21 运行时（含 JavaFX 模块）'
        runtime_graphviz = 'Graphviz 16.1.0 可视化布局运行时'
        lib = 'Craken 标准库声明头（*.mh，含 stl/）'
        examples = '示例 MiniC 源码'
        licenses = '第三方许可证说明'
        docs = '用户文档'
    }
    pathPolicy = [ordered]@{
        root = $Version
        packageOwnedLaunchPaths = 'relative-to-root'
        parentTraversalOutsideRoot = $false
    }
    absolutePathScan = [ordered]@{
        machinePathMatches = $scan.machinePathMatches.Count
        note = '本机/构建机绝对路径为 0；graphviz 上游自带路径不计入门禁'
        graphvizUpstreamDrivePathMatches = $scan.graphvizDrivePathMatches
    }
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outDir 'release-manifest.json') -Encoding UTF8
$finalFiles = Get-ChildItem -LiteralPath $outDir -Recurse -File
$manifest.fileCount = $finalFiles.Count
$manifest.totalBytes = ($finalFiles | Measure-Object Length -Sum).Sum
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outDir 'release-manifest.json') -Encoding UTF8

# 压缩分发：优先 7z，退化为系统 Compress-Archive。
$zipPath = "$outDir.zip"
if (Test-Path -LiteralPath $zipPath) { throw "archive already exists: $zipPath" }
$sevenZip = Get-Command 7z -ErrorAction SilentlyContinue
if ($sevenZip) {
    & $sevenZip.Source a -tzip $zipPath $outDir | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '7z failed' }
} else {
    Compress-Archive -Path $outDir -DestinationPath $zipPath
}

Write-Output "Packaged $Version -> $outDir"
Write-Output "Archive -> $zipPath"
Write-Output "Absolute-path gate passed: 0 machine paths ($($scan.graphvizDrivePathMatches) upstream Graphviz drive strings)"
