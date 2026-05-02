# MiniC

MiniC 是一个用于学习编译原理的 Java 版 C 语言子集编译器。当前已经完成从源码到真实可执行文件的基础编译闭环，并进入 `0.2.0` 结构化开发阶段，重点是编译层可步进改造和面向 UI 的统一调度接口。

当前开发阶段：`0.2.0`。稳定基线版本 `0.1.0` 已完成第一阶段编译器闭环，阶段总结见 [version/0.1.0.md](version/0.1.0.md)。`0.2.0` 执行计划见 [PLAN.md](PLAN.md) 的 B 系列任务。

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

也可以使用 `compile-run` 一次完成编译、链接、运行，并捕获 stdout、stderr 和退出码：

```powershell
cmd /c '"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 && set "JAVA_HOME=E:\projects\MiniC\.local\tools\jdk-21.0.10+7" && set "PATH=E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;%PATH%" && set "GRADLE_USER_HOME=E:\projects\MiniC\.gradle-home" && .\gradlew --no-daemon run --args="compile-run samples\printf.mc --out-dir build\minic --emit-asm --ml64 \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\ml64.exe\" --link \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\link.exe\""'
```

成功时会输出类似：

```text
run.stdout=value=42\r\n
run.stderr=
run.exitCode=42
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
samples\pointer_local.mc        -> 退出码 9
samples\array_local.mc          -> 退出码 5
samples\array_argument.mc       -> 退出码 18
samples\struct_field.mc         -> 退出码 12
samples\struct_pointer_field.mc -> 退出码 13
samples\function_pointer_call.mc -> 退出码 12
samples\function_pointer_parameter.mc -> 退出码 12
```

其中 `102` 是除零运行时检查 trap，`101` 是未初始化局部变量读取 trap。
`samples\function_pointer_return_unsupported.mc` 用于覆盖当前限制：函数指针返回值暂不支持，会产出 parser diagnostic `暂不支持函数指针返回值`。

## 协作规则

- 全程使用中文沟通、记录需求、编写文档和汇报结果。
- Java 包名、类名、方法名、测试方法名使用英文。
- 代码注释优先中文，必要时可使用简短英文术语。
- 每次 agent 只能执行 [PLAN.md](PLAN.md) 中的一个任务编号，除非用户明确批准合并，完成任务之后修改下方的当前状态。
- 每个非文档任务必须有测试或明确说明无法测试的原因。

## 当前状态

第一阶段已完成，版本号为 `0.1.0`。当前已经具备从 MiniC 源码到 Windows x64 可执行文件的基础编译运行闭环，并完成函数、控制流、外部函数、字符串、指针、数组、结构体、函数指针参数、基础类型系统、浮点代码生成。函数指针返回值当前明确诊断为暂不支持。类型系统阶段确认纳入 `bool`、有符号 `char`、`int`、`long`、`float`、`double` 和 `NULL`，其中 MiniC 采用 `long` 为 8 字节。

当前 `0.2.0` 阶段不继续扩展 C 语言能力，先做结构化改造：

- B0：收敛测试基线，把开发期大量细粒度语法测试调整为综合行为回归，降低后续重构成本。
- 编译层：把当前一键执行模型改造成细粒度正向步进模型，粒度为 token、AST 节点、语义动作、IR 指令和汇编行。
- 兼容层：为每个编译大阶段提供统一 API，整理阶段数据，保证 UI 获取的信息格式统一且完整。
- 调度层：提供全局下一步、自动播放 `1s/帧`、两倍速播放 `0.5s/帧`、暂停，以及当前状态数据、当前阶段数据、全局数据查询。
- UI API：暴露不依赖 JavaFX 的简单门面。上一步和自动倒放只作为未来扩展能力预留，本阶段不实现。

B001 已完成：Lexer 和 Parser 测试已收敛为代表性词法边界、综合语法能力和代表性 parser diagnostic 回归。
B002 已完成：Semantic 测试已收敛为综合合法程序、基础契约保留和代表性语义 diagnostic 回归。

下一步编号：`B003`。
