# MiniC macOS / Linux 兼容技术调查与设计

调查日期：2026-06-05

本文基于当前仓库只读调查，目标是给 MiniC 提供 macOS、Linux 兼容的步骤、选型和设计建议。本文只描述现状与方案，不代表当前代码已经具备完整跨平台能力。

## 结论摘要

MiniC 的 Java、Gradle、JavaFX 基础设施具备跨平台潜力，但“源码到真实可执行文件”的闭环目前是 Windows 专属。当前阻塞点不在 lexer/parser/semantic/IR，也不主要在 JavaFX，而在后端目标平台、ABI、汇编方言、工具链、CLI 默认行为和观测流水线默认实现。

建议把兼容目标拆成四层推进：

| 层级 | 目标 | 当前可行性 | 推荐优先级 |
|------|------|------------|------------|
| L0 | macOS/Linux 可构建、可跑非原生工具链测试 | 高，主要是 JDK/Gradle/CI 文档与配置问题 | 最高 |
| L1 | JavaFX Workbench 与 IR Debugger 跨平台运行 | 较高，但需处理配置目录、快捷键、字体、JavaFX headless 验证 | 最高 |
| L2 | Linux x86_64 生成 ELF 可执行文件 | 中，需要 SysV ABI + ELF/clang 工具链后端 | 高 |
| L3 | macOS 原生可执行文件 | 中低，x86_64 可复用部分 SysV 设计；Apple Silicon 需要 AArch64 后端 | 中 |

短期不要把 macOS/Linux 兼容理解为“一次性补齐所有原生产物”。更稳妥的路线是先让跨平台用户能打开 Workbench、运行编译观察到 ASM 阶段、使用 IR Debugger；再补 Linux x86_64 原生目标；最后处理 macOS Mach-O 与 Apple Silicon。

## 当前项目事实

### 构建与运行

- `build.gradle` 使用 Java plugin、application plugin、OpenJFX Gradle plugin `0.1.0`，Java toolchain 指定 Java 21，JavaFX 版本为 `21.0.2`，模块为 `javafx.controls`。
- CLI 入口是 `minic.Main`，Gradle `application.mainClass` 指向该类。
- UI 入口是 `runUi` 任务，主类为 `minic.ui.MiniCWorkbenchLauncher`，再由它调用 `Application.launch(MiniCWorkbenchApp.class, args)`。
- Gradle Wrapper 使用 Gradle `8.7`。
- 仓库同时有 `gradlew` 和 `gradlew.bat`，但当前 git 索引中 `gradlew` 是 `100644`，在 macOS/Linux 上直接 `./gradlew` 可能因缺少可执行位失败。
- `gradle.properties` 写死 `org.gradle.java.installations.paths=.local/tools/jdk-21.0.10+7`，这是当前机器本地 JDK 路径，不是跨平台可复现路径。
- README 当前启动、测试、编译命令均以 PowerShell / Windows 路径为主。

### 编译器与工具链

- `TargetPlatform` 当前只有 `WINDOWS_X86_64("windows-x86_64")`。
- `MiniCompiler()` 默认构造 `WindowsX64AssemblyEmitter`。
- `MiniCli` 默认构造 `WindowsMsvcToolchain`，CLI 参数只暴露 `--ml64` 和 `--link`。
- `CodegenStageStepper` 直接持有 `WindowsX64CodegenStepState`。
- `ToolchainStageStepper` 默认构造 `WindowsMsvcToolchain`，并固定输出到 `build/minic`。
- `WindowsMsvcToolchain` 写出 `.asm`，调用 `ml64 /c /Fo` 生成 `.obj`，再调用 `link /ENTRY:<entry> /SUBSYSTEM:CONSOLE /OUT:<exe>` 生成 `.exe`。
- `WindowsX64CodegenStepState` 输出 MASM 风格汇编：`PUBLIC`、`EXTERN ...:PROC`、`.const`、`.code`、`PROC`、`ENDP`、`END`。
- Windows 入口为 `minic$entry`，手动 `call main`，再调用 `ExitProcess`。这不是 Linux/macOS 的 CRT `main` 启动模型。
- Windows x64 调用约定固定使用 `rcx/rdx/r8/r9` 与 32 字节 shadow space；Linux/macOS x86_64 需要 SysV ABI。

### UI 与资源

- classpath 资源加载较健康：CSS、解释模板、默认快捷键从 `src/main/resources` 读取。
- 用户设置、快捷键覆盖、主题目录当前使用相对进程工作目录的 `config/`，包括 `config/settings.json`、`config/keybindings.json`、`config/themes`。从非项目根目录启动、打包后启动或 CI 并发测试时会有路径和污染风险。
- 默认快捷键资源使用 `Ctrl`，虽然解析器支持 `Meta` / `Command`，但默认绑定没有面向 macOS 的 Command 版本。
- CSS 默认字体偏 Windows：`Segoe UI`、`Microsoft YaHei`、`Consolas`、`Courier New`。macOS/Linux 会 fallback，但中文字体、等宽宽度、行高和图形布局需要实机验证。
- 图形缩放和平移依赖 JavaFX 鼠标事件、滚轮 `deltaY` 和右键拖拽；macOS 触控板自然滚动、惯性滚动和双指右键需要单独验收。

### Debugger 与可视化 ASM

- Debugger 第一版执行 IR Interpreter，不调试真实 exe，不接 Windows Debug API。这个设计本身利于跨平台。
- 但 ASM 映射展示当前也直接使用 Windows 后端：`DebugMappingIndex.collectAsm()` 和 `UiDebugAsmViewBuilder` 都构造 `WindowsX64CodegenStepState`。
- 因此 L1 阶段可以跨平台运行 Debugger，但 ASM 视图必须明确“展示默认目标汇编”，或后续通过目标平台选择切换。

### 发布形态

- `release/launcher/MiniCLauncher.cs` 是 Windows 启动器，固定 `java.exe`、MSVC `ml64.exe/link.exe`、Windows Kits 路径和 `.exe` 生态。
- macOS/Linux 发布不应复用该启动器。短期可提供 shell 启动脚本；正式分发建议使用每个平台本地构建的 `jpackage` 或平台专属 launcher。

## 兼容目标定义

### L0：构建和测试跨平台

目标是在 macOS/Linux 上完成：

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$PWD/.gradle-home"
sh ./gradlew test
```

如果修正 `gradlew` 可执行位，也可以使用：

```bash
chmod +x ./gradlew
./gradlew test
```

L0 不要求生成 Linux/macOS 原生可执行文件，只要求 Java 层编译、单元测试、非 MSVC 依赖路径可控。

关键设计：

- 不再把 `.local/tools/jdk-21.0.10+7` 作为跨平台默认说明。
- CI 使用 `actions/setup-java` 或等价机制安装 JDK 21。
- 测试中涉及真实 MSVC 的部分应拆成 host/工具链条件测试，或用 fake toolchain 验证命令构造。
- JavaFX UI 测试在 Linux CI 使用 `xvfb-run` 或改造为 headless 可测的 UI API / view model 测试。

### L1：Workbench 与 IR Debugger 跨平台

目标是在 macOS/Linux 上完成：

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$PWD/.gradle-home"
sh ./gradlew runUi
```

L1 允许编译观察流水线在没有目标工具链时停在 ASM 或 Toolchain 诊断阶段，但 UI 不应因为默认 MSVC 不存在而失去基本可用性。IR Debugger 应继续使用解释器路径，不依赖真实 exe。

关键设计：

- Workbench 目标平台选择必须从“默认 Windows 工具链”中解耦。
- 非 Windows host 默认进入 `NoOpToolchain`、`--emit-asm-only` 或明确的“未配置工具链”状态，而不是尝试发现 `ml64.exe/link.exe`。
- 配置目录应支持注入，例如系统属性 `minic.config.dir` 或环境变量 `MINIC_CONFIG_DIR`；默认再落回项目 `config/`。
- 默认快捷键应支持平台感知：macOS 使用 `Meta` / `Command`，Windows/Linux 使用 `Ctrl`。
- 字体栈应增加跨平台候选，例如 UI 字体加入 `Inter`、`Noto Sans CJK SC`、`PingFang SC`、`Arial`，等宽字体加入 `JetBrains Mono`、`Menlo`、`Monaco`、`DejaVu Sans Mono`、`Consolas`。

### L2：Linux x86_64 原生可执行文件

目标是在 Linux x86_64 上完成：

```bash
sh ./gradlew run --args="compile samples/main.mc --target linux-x86_64 --out-dir build/minic --emit-asm"
./build/minic/main
echo $?
```

推荐选型：

- 汇编方言：优先生成 GNU as / clang integrated assembler 可消费的 AT&T 或 Intel 语法。为降低学习成本，可选择 `.intel_syntax noprefix` 的 GNU as 风格。
- 工具链：优先 `clang`，由 clang 负责汇编、链接、CRT 启动文件和 libc 连接；避免第一版直接手写 `as` + `ld` 参数。
- ABI：System V AMD64 ABI。
- 产物：汇编 `.s`，目标文件 `.o`，可执行文件无扩展名。
- 入口：优先直接导出 `main`，让 C runtime 调用 `main` 并处理退出码。不要在第一版 Linux 后端手写 `_start`。

Linux x86_64 关键差异：

| 项目 | Windows x64 当前实现 | Linux x86_64 设计 |
|------|----------------------|-------------------|
| 整数/指针参数 | `rcx`, `rdx`, `r8`, `r9` | `rdi`, `rsi`, `rdx`, `rcx`, `r8`, `r9` |
| 浮点参数 | `xmm0`-`xmm3` | `xmm0`-`xmm7` |
| shadow space | 调用方保留 32 字节 | 无 |
| 栈对齐 | 当前按 Windows 规则 | call 前 16 字节对齐 |
| 外部退出 | `ExitProcess` | `main` return 交给 CRT |
| 汇编声明 | `PUBLIC` / `EXTERN ...:PROC` | `.globl` / 外部符号直接引用 |
| 段 | `.const` / `.code` | `.section .rodata` / `.text` |
| 工具链 | `ml64` + `link` | `clang` |

可变参数 `printf` 需要单独验收。SysV AMD64 对 variadic call 有 `%al` 表示向量寄存器参数数量的规则；若 MiniC 第一版只传整数和指针给 `printf`，可先设置 `eax=0` 并补覆盖测试。若支持浮点传入 `printf`，需要实现更完整的 varargs ABI。

### L3：macOS 原生可执行文件

macOS 应拆成两个目标：

- `macos-x86_64`：Mach-O + x86_64 SysV 派生 ABI + clang。
- `macos-aarch64`：Mach-O + Apple arm64 ABI + clang。

推荐顺序：

1. 先做 `macos-x86_64` 的设计验证，因为它可复用 Linux x86_64 的一部分寄存器、栈对齐和 IR 指令映射。
2. 再做 `macos-aarch64`，不要试图从 x64 emitter 小修小补得到 ARM64 后端。

macOS x86_64 关键差异：

- Mach-O 汇编通常需要符号下划线，例如 `_main`、`_printf`。
- 段声明不同，例如 `__TEXT,__text`、`__TEXT,__cstring` 或由 clang assembler 接受的简化形式。
- 工具链仍建议用 `clang` 负责汇编链接。
- Apple Silicon 上运行 x86_64 产物需要 Rosetta，不应作为长期原生兼容方案。

macOS arm64 关键差异：

- 整数/指针参数使用 `x0`-`x7`。
- 浮点参数使用 `v0`-`v7` / `d0` / `s0`。
- 指令、寻址、栈帧、调用约定全部不同。
- 当前 `WindowsX64ValueEmitter`、`WindowsX64InstructionEmitter`、`WindowsX64FrameLayout` 不能复用为 arm64 后端，只能复用更高层 IR。

## 架构设计

### 目标平台抽象

建议把目标平台从单一 enum 扩展为可描述目标特性的模型：

```text
TargetPlatform
  id: windows-x86_64 | linux-x86_64 | macos-x86_64 | macos-aarch64
  hostOs: windows | linux | macos
  architecture: x86_64 | aarch64
  objectFormat: coff | elf | macho
  assemblySyntax: masm | gas-intel | macho-clang | arm64-clang
  objectExtension: .obj | .o
  executableExtension: .exe | ""
```

现有 `AssemblySource` 已携带 `targetPlatform` 和 `entrySymbol`，可继续保留，但 `entrySymbol` 对 POSIX/clang 工具链应允许“用于展示，不一定传给 linker”。Windows 可以继续使用 `/ENTRY:minic$entry`，Linux/macOS 可以让 clang 默认找 `main`。

### BackendFactory

引入后端工厂，集中替代 scattered `new WindowsX64...`：

```text
BackendFactory
  createAssemblyEmitter(TargetPlatform)
  createCodegenStepState(TargetPlatform, IrModule)
  createToolchain(TargetPlatform, ToolchainOptions)
  defaultTargetForHost()
```

应被替换的装配点：

- `MiniCompiler()` 默认后端。
- `MiniCli` 根据 `--target` 创建 emitter 和 toolchain。
- `CodegenStageStepper` 通过 target 创建 codegen state。
- `ToolchainStageStepper` 不再默认 Windows MSVC。
- `DebugMappingIndex` 和 `UiDebugAsmViewBuilder` 通过 target 创建 ASM 映射视图。

### Codegen 层拆分

当前 Windows 后端已经按职责拆出 calling convention、frame layout、instruction emitter、value emitter。新增平台时，不建议硬复用 MASM 文本生成，而是抽出共同概念、各平台独立实现：

```text
minic.compiler.codegen
  AssemblyEmitter
  AssemblySource
  CodegenStepState 接口或适配器
  TargetPlatform
  TargetDescriptor

minic.compiler.codegen.windows
  WindowsX64AssemblyEmitter
  WindowsX64CodegenStepState
  WindowsX64CallingConvention
  WindowsX64FrameLayout

minic.compiler.codegen.sysv
  SysVX64AssemblyEmitter
  SysVX64CodegenStepState
  SysVX64CallingConvention
  SysVX64FrameLayout
  GasIntelDialect

minic.compiler.codegen.macos
  MacOsX64AssemblyEmitter
  MacOsSymbolMangler

minic.compiler.codegen.arm64
  AArch64AssemblyEmitter
  AArch64CallingConvention
```

第一版 Linux 可允许 `SysVX64CodegenStepState` 和 `SysVX64AssemblyEmitter` 比 Windows 实现少一些 UI 元数据，但必须保持 `UiAssemblyLineVisualDto` 所需字段可构造。

### Toolchain 层设计

保留现有 `Toolchain` 接口，新增：

```text
ToolchainOptions
  assemblerCommand / compilerCommand
  linkerCommand
  libraryPaths
  extraArgs
  runMode: asmOnly | assembleOnly | linkExecutable

NoOpToolchain
WindowsMsvcToolchain
ClangToolchain
```

`ClangToolchain` 推荐承担 Linux/macOS 第一版：

```bash
clang -x assembler -c main.s -o main.o
clang main.o -o main
```

若后续需要更稳定的链接控制，再拆 `AssemblerToolchain` 与 `LinkerToolchain`。

工具链结果应避免语义上假设 `.exe`：

- `ToolchainResult.objectPathOptional()` 继续适用。
- `ExecutableArtifact` 建议后续携带 `TargetPlatform` 或 `ExecutableKind`。
- UI 和测试展示应使用 “executable path” 而不是 “exe path”。

### CLI 设计

建议新增参数：

```text
--target <windows-x86_64|linux-x86_64|macos-x86_64|macos-aarch64>
--toolchain <auto|msvc|clang|none>
--cc <path-or-command>
--assembler <path-or-command>
--linker <path-or-command>
--emit-asm-only
--no-run
```

兼容策略：

- 保留 `--ml64` 和 `--link` 作为 Windows/MSVC 别名，避免破坏现有 README 命令。
- `compile` 默认不应强制真实链接；可考虑改成“生成到当前 target 的汇编，若工具链配置完整则链接”，或新增 `compile-native` 区分。
- `compile-run` 必须要求 target 工具链可用，否则返回结构化 `TOOL001` / `RUN001`，并在 CLI 中输出明确诊断。

### Workbench 设计

Workbench 应把“编译观察”拆成可用等级：

1. 前端到 IR：所有平台可用。
2. ASM 生成：按已实现 target 可用。
3. Toolchain：按 host 和工具链配置可用。
4. Execution：仅当 toolchain 生成了当前 host 可执行产物时可用。

UI 上不应默认假定 Windows MSVC。非 Windows 平台可以：

- 默认 target 为 `host` 对应 target，但若后端未实现，降级到 `windows-x86_64` ASM 展示并标注。
- 或默认 target 为 `windows-x86_64`，但 Toolchain/Execution 显示“未配置 Windows MSVC 工具链”。

更推荐前者：host target 优先，后端未实现时明确提示“当前平台只支持前端、IR Debugger 和默认 Windows ASM 展示”。

### 配置目录设计

新增统一配置目录解析，避免多个类各自 `Path.of("config", ...)`：

```text
MiniCPathConfig
  configRoot()
  settingsFile()
  keybindingsFile()
  themesDirectory()
```

解析顺序：

1. 系统属性 `minic.config.dir`
2. 环境变量 `MINIC_CONFIG_DIR`
3. 开发模式默认 `config/`
4. 打包模式默认用户配置目录

用户配置目录建议：

| 平台 | 默认目录 |
|------|----------|
| Windows | `%APPDATA%/MiniC` |
| macOS | `~/Library/Application Support/MiniC` |
| Linux | `${XDG_CONFIG_HOME:-~/.config}/minic` |

这一步能同时解决 CI 并发污染、从非项目根启动、打包启动找不到主题的问题。

## 分阶段实施步骤

### 阶段 1：文档和 L0/L1 最小跨平台可运行

目标：

- README 或 docs 增加 macOS/Linux JDK、Gradle、UI 启动命令。
- CI 增加 Windows / Ubuntu / macOS matrix，先跑 Java 层测试。
- Linux CI 处理 `gradlew` 可执行位：修正 git mode 或统一 `sh ./gradlew`。
- JavaFX 测试明确 headless 策略。

验收：

- Windows 现有测试仍可运行。
- Ubuntu JDK 21 下 `test` 可运行，平台专属 MSVC 测试被跳过或使用 fake toolchain。
- macOS JDK 21 下 `test` 可运行，UI 非 headless 测试不误挂。

### 阶段 2：目标平台和工具链选择抽象

目标：

- 扩展 `TargetPlatform`。
- 新增 `BackendFactory` / `ToolchainFactory`。
- CLI 支持 `--target` 和 `--toolchain none`。
- Workbench codegen/toolchain stepper 不再直接默认 Windows。
- Debug ASM 视图通过 target 获取 codegen state。

验收：

- `windows-x86_64` 行为保持不变。
- `--target windows-x86_64 --toolchain none --emit-asm` 可只生成汇编，不要求 MSVC。
- 非 Windows host 上不会无条件尝试发现 `ml64.exe/link.exe`。

### 阶段 3：Linux x86_64 后端

目标：

- 新增 `linux-x86_64` target。
- 新增 SysV x86_64 calling convention、frame layout、gas/clang 汇编 emitter。
- 新增 `ClangToolchain`。
- 支持 `main` 返回退出码、整数/指针/数组/结构体基础路径、内部函数调用、`printf` 整数/字符串调用。

验收：

- Linux 上 `samples/main.mc` 生成可执行文件并返回预期退出码。
- `samples/printf.mc` 可输出预期 stdout。
- 对应 codegen 测试只断言关键 ABI 片段，不使用整段黄金汇编。

### 阶段 4：macOS x86_64 后端

目标：

- 新增 `macos-x86_64` target。
- 在 SysV x86_64 后端基础上补 Mach-O 符号命名、段声明、clang 调用参数。
- 明确 Intel Mac 或 Rosetta 环境支持边界。

验收：

- macOS x86_64 环境下 `samples/main.mc` 可生成并运行。
- `_main`、`_printf` 等符号命名测试覆盖。
- UI/Debugger 与 target 切换不冲突。

### 阶段 5：macOS arm64 后端

目标：

- 新增 `macos-aarch64` target。
- 实现 AArch64 指令选择、调用约定、栈帧、常量和数据段输出。
- clang 负责 Mach-O arm64 汇编链接。

验收：

- Apple Silicon 上生成 arm64 原生产物。
- 常用样例退出码和 stdout 正确。
- 与 x86_64 后端共享 IR 层测试，不共享汇编实现细节断言。

### 阶段 6：发布与安装

目标：

- Windows 保留现有 C# launcher 或迁移到统一发布机制。
- macOS/Linux 提供 shell launcher。
- 使用 `jpackage` 或平台原生打包方案分别构建 Workbench。
- 原生工具链可以作为外部依赖，不建议第一版打包 clang/MSVC。

验收：

- 从非项目根目录启动可加载主题、设置、样例。
- 用户配置写入平台标准目录。
- CLI 和 Workbench 启动脚本都能定位 runtime、classpath、config。

## 测试矩阵建议

| 测试层 | Windows | Linux | macOS |
|--------|---------|-------|-------|
| Java unit/regression | 必跑 | 必跑 | 必跑 |
| JavaFX view model / UI API | 必跑 | 必跑 | 必跑 |
| JavaFX Stage smoke | 必跑 | xvfb 或专用 job | 专用 job |
| Windows MSVC toolchain | 必跑或条件跑 | 不跑 | 不跑 |
| Linux clang toolchain | 不跑 | 条件跑 | 不跑 |
| macOS clang x86_64 | 不跑 | 不跑 | 条件跑 |
| macOS arm64 | 不跑 | 不跑 | Apple Silicon runner 条件跑 |

测试设计原则：

- 保留 Windows 回归测试，避免跨平台改造时破坏既有闭环。
- 新增 target-agnostic pipeline 测试，验证前端到 IR 和 target 选择。
- 工具链发现和命令构造用 fake command / fake toolchain 测试，不依赖真实本机安装。
- 真实原生产物测试按 host 和工具可用性条件启用。
- UI 配置目录测试使用临时目录注入，避免改写仓库 `config/`。

## 风险清单

| 风险 | 影响 | 缓解 |
|------|------|------|
| 把 UI 跨平台和原生产物跨平台混为一谈 | 计划过大，验收模糊 | 使用 L0-L3 分层目标 |
| 默认 `WindowsMsvcToolchain` 在非 Windows 上硬失败 | Workbench/CLI 体验差 | 增加 target/toolchain factory 和 NoOp/none 模式 |
| MASM emitter 被强行拼成 ELF/Mach-O | 后端难维护 | 新增平台独立 emitter，共享 IR 不共享文本模板 |
| SysV varargs 处理不足 | `printf` 浮点等调用错误 | 第一版限制并测试整数/字符串，后续补 `%al` 与浮点路径 |
| macOS Apple Silicon 被忽略 | 新 Mac 用户无法原生运行产物 | 明确 `macos-aarch64` 是独立阶段 |
| `config/` 相对目录污染 | CI、打包、非根目录启动异常 | 引入配置目录解析和测试注入 |
| JavaFX headless 不稳定 | CI 偶发失败 | UI API/view model 测试为主，Stage smoke 单独 job |
| 字体/快捷键平台体验差 | Workbench 可用但不好用 | 平台默认绑定和字体 fallback |

## 推荐最终选型

- 构建：继续使用 Gradle Wrapper + Java 21。
- JDK 获取：开发文档使用系统 JDK 21；CI 使用 setup-java；后续可再引入 toolchain resolver。
- UI：继续 JavaFX + RichTextFX，补平台快捷键、字体和配置目录。
- Linux 原生后端：`linux-x86_64` + SysV AMD64 ABI + clang + `.s/.o/无扩展可执行文件`。
- macOS x86_64 后端：Mach-O + clang + 符号下划线 mangling。
- macOS arm64 后端：独立 AArch64 后端，不从 x64 emitter 派生。
- 工具链抽象：保留 `Toolchain`，新增 `ClangToolchain`、`ToolchainOptions`、`ToolchainFactory`。
- 编译器装配：新增 `BackendFactory`，移除 CLI、stepper、debug ASM 中的直接 Windows 默认构造。
- 发布：短期 shell script；长期各平台本地 `jpackage`。

## 当前不建议做的事

- 不建议把 Windows MASM 文本用字符串替换改成 Linux/macOS 汇编。
- 不建议第一版 Linux/macOS 后端直接手写 `_start` 和裸 `ld` 参数。
- 不建议在未抽象 target 前直接往 CLI 塞 `--clang`。
- 不建议把 macOS arm64 视为 macOS x86_64 的小改动。
- 不建议让 UI 层继续直接知道 Windows codegen 类型。

## 关键文件索引

| 主题 | 文件 |
|------|------|
| Gradle / JavaFX / runUi | `build.gradle` |
| Wrapper 版本 | `gradle/wrapper/gradle-wrapper.properties` |
| 本地 JDK 路径 | `gradle.properties` |
| Windows 命令文档 | `README.md` |
| 平台扩展规范依据 | `SPEC.md` |
| 当前唯一目标平台 | `src/main/java/minic/compiler/codegen/target/TargetPlatform.java` |
| 默认 Windows compiler | `src/main/java/minic/compiler/pipeline/MiniCompiler.java` |
| CLI 默认 MSVC | `src/main/java/minic/cli/MiniCli.java` |
| Windows MSVC 工具链 | `src/main/java/minic/compiler/toolchain/WindowsMsvcToolchain.java` |
| Toolchain 接口 | `src/main/java/minic/compiler/toolchain/Toolchain.java` |
| Windows codegen stepper | `src/main/java/minic/compiler/codegen/windows/WindowsX64CodegenStepState.java` |
| Windows ABI | `src/main/java/minic/compiler/codegen/windows/WindowsX64CallingConvention.java` |
| 观测 codegen stepper | `src/main/java/minic/runtime/step/CodegenStageStepper.java` |
| 观测 toolchain stepper | `src/main/java/minic/runtime/step/ToolchainStageStepper.java` |
| 可执行运行器 | `src/main/java/minic/runtime/execution/ExecutableRunner.java` |
| Debug ASM 视图 | `src/main/java/minic/uiapi/debug/UiDebugAsmViewBuilder.java` |
| Debug ASM 映射 | `src/main/java/minic/runtime/debug/DebugMappingIndex.java` |
| 设置路径 | `src/main/java/minic/settings/MiniCSettings.java` |
| 快捷键路径和解析 | `src/main/java/minic/ui/workbench/MiniCKeyBindingConfig.java` |
| 主题路径 | `src/main/java/minic/color/ThemeManager.java` |
| Windows 发布 launcher | `release/launcher/MiniCLauncher.cs` |
