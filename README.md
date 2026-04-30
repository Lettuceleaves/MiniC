# MiniC

MiniC 是一个用于学习编译原理的 Java 版 C 语言子集编译器与 debugger。当前执行顺序优先补齐从源码到真实可执行文件的完整编译链路，再扩展语言能力，随后进入 debugger 和可视化教学能力。

## 文档入口

本项目只维护三个根目录文档：

- [SPEC.md](SPEC.md)：项目规范、语言范围、架构约束、代码和测试要求
- [PLAN.md](PLAN.md)：agent 小步执行计划，每次只做一个可验收任务
- [README.md](README.md)：项目入口

## 本地编译为 exe

Windows x64 可执行文件生成依赖 Visual Studio 2022 Build Tools 的 C++ 工具链，必须能使用 `ml64.exe` 和 `link.exe`。

本机安装命令：

```powershell
winget install --id Microsoft.VisualStudio.2022.BuildTools --source winget --accept-package-agreements --accept-source-agreements --override "--quiet --wait --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
```

在普通 PowerShell 中可通过 `VsDevCmd.bat` 临时启用 x64 工具链环境：

```powershell
cmd /c "`"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat`" -arch=x64 && where ml64 && where link"
```

编译样例并生成可执行文件。下面命令显式传入本机已验证的 `ml64.exe` 和 `link.exe` 路径，并使用 `--no-daemon` 避免 Gradle daemon 复用未加载 MSVC 环境的旧进程：

```powershell
cmd /c '"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 && set "JAVA_HOME=E:\projects\MiniC\.local\tools\jdk-21.0.10+7" && set "PATH=E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;%PATH%" && set "GRADLE_USER_HOME=E:\projects\MiniC\.gradle-home" && .\gradlew --no-daemon run --args="compile samples\main.mc --out-dir build\minic --emit-asm --ml64 \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\ml64.exe\" --link \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\link.exe\""'
```

成功后产物位于：

```text
build\minic\main.asm
build\minic\main.obj
build\minic\main.exe
```

运行可执行文件并查看退出码：

```powershell
.\build\minic\main.exe
$LASTEXITCODE
```

`samples\main.mc` 当前返回 `add(1, 2)` 的结果，期望退出码是 `3`。

已验证的样例：

```text
samples\return_constant.mc      -> 退出码 7
samples\arithmetic.mc           -> 退出码 7
samples\multi_function.mc       -> 退出码 12
samples\local_assignment.mc     -> 退出码 3
samples\divide_by_zero.mc       -> 退出码 102
samples\uninitialized_read.mc   -> 退出码 101
samples\nested_call.mc          -> 退出码 10
samples\stack_arguments.mc      -> 退出码 11
samples\declaration_call.mc     -> 退出码 12
samples\comparison.mc           -> 退出码 5
samples\if_else.mc              -> 退出码 7
samples\if_no_else.mc           -> 退出码 8
samples\nested_if.mc            -> 退出码 6
samples\else_if.mc              -> 退出码 2
samples\else_if_fallback.mc     -> 退出码 3
samples\while_zero.mc           -> 退出码 4
samples\while_count.mc          -> 退出码 5
samples\for_count.mc            -> 退出码 10
samples\for_omitted.mc          -> 退出码 4
samples\break_while.mc          -> 退出码 3
samples\continue_for.mc         -> 退出码 8
samples\printf.mc               -> 输出 value=42，退出码 42
```

其中 `102` 是除零运行时检查 trap，`101` 是未初始化局部变量读取 trap。

## 协作规则

- 全程使用中文沟通、记录需求、编写文档和汇报结果。
- Java 包名、类名、方法名、测试方法名使用英文。
- 代码注释优先中文，必要时可使用简短英文术语。
- 每次 agent 只能执行 [PLAN.md](PLAN.md) 中的一个任务编号，除非用户明确批准合并，完成任务之后修改下方的当前状态。
- 每个非文档任务必须有测试或明确说明无法测试的原因。

## 当前状态

当前已完成项目文档整理、git 仓库初始化、Git 规范与验收要求补充、Java Gradle 骨架、Gradle Wrapper、临时文件清理验收要求、源码位置模型、诊断模型、Token 模型、v0.1 lexer、Program 和声明 AST、基础函数解析、语句 AST 和解析、表达式 AST 和解析、符号模型、函数和变量解析、v0.1 语义规则、IR 模型、基础 AST 到 IR lowering、变量、赋值和运行时检查插桩、AST/IR 包结构整理、目标平台和汇编输出模型、完整 v0.1 目标汇编生成、编译管线入口和产物结果模型、CLI 编译入口和阶段化观测输出、函数命名和签名规则、函数声明和定义分离、用户函数调用代码生成完善、比较表达式、语法扩展前的核心职责拆分、if/else 控制流、else if 链式分支、while 循环、for 循环、break/continue 循环控制、外部函数声明模型、字符串字面量，以及动态链接 printf。执行计划已调整为先完成从前端到真实可执行文件生成的编译链路，再扩展语言能力，随后进入 debugger。

下一步应执行 `A103：添加编译运行反馈闭环`，之后按计划补充指针、数组和结构体。
