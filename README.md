# MiniC

MiniC 是一个用于学习编译原理的 Java 版 C 语言子集编译器。当前已经完成从源码到 Windows x64 可执行文件的基础编译闭环，并完成 `0.2.0` 结构化编译观测阶段。

当前版本：`0.4.0-SNAPSHOT`。

## 文档入口

- [SPEC.md](SPEC.md)：项目规范、语言范围、架构约束、代码和测试要求。
- [PLAN.md](PLAN.md)：当前 agent 小步执行计划。
- [version/0.1.0.md](version/0.1.0.md)：`0.1.0` 编译闭环阶段总结。
- [version/0.2.0.md](version/0.2.0.md)：`0.2.0` 结构化观测阶段记录。
- [version/0.3.0.md](version/0.3.0.md)：`0.3.0` JavaFX UI 首版阶段记录。
- [version/0.3.1.md](version/0.3.1.md)：`0.3.1` 阶段专属图形化增强记录。

## 当前状态

`0.3.1` 已完成，当前进入 `0.4.0-SNAPSHOT` C 子集语法和预编译扩展阶段。

下一步任务：`D131：补全表达式 parser 和 AST`。`D125/D130` 已支持 MiniC 头文件语法校验、`samples/minic_std.mh` 和 `extern int printf(char *format, ...);` 可变参数原型。

项目已经具备：

- MiniC 源码到 Windows x64 可执行文件的编译运行闭环。
- Lexer、Parser、Semantic、IR lowering 和 Windows x64 codegen 的正向可步进状态。
- `minic.runtime.step` 统一阶段 Stepper 和数据模型。
- `minic.session` 全局观测会话、下一步、播放、两倍速播放、暂停和手动 tick。
- `minic.uiapi` UI 门面和不可变 DTO，供后续界面层绑定。
- JavaFX 版 MiniC Visual Workbench。
- 阶段专属 Visual Pane：Lexer token 半透明遮罩、Parser AST 树、Semantic 顶部 `global scope` 且反向箭头作用域树、Codegen Assembly 增量行文本视图。
- `MiniCObservationApi.currentStageVisualData()` UI 专用 visual DTO，不向 UI 暴露 AST、Scope、IR、Stepper 等内部对象。

UI 交互细化暂缓，后续可继续补充 AST/Scope 节点点击定位源码、真实手工 UI 截图验收和更完整的滚动定位体验。

UI 风格继续参考：

```text
C:\Users\Administrator\Desktop\styleOfMiniC\index.html
```

首版 UI 只依赖 `minic.uiapi.*`，不直接访问 compiler、runtime stepper 或 session 内部对象。

## 启动 JavaFX UI

```powershell
$env:JAVA_HOME='E:\projects\MiniC\.local\tools\jdk-21.0.10+7'
$env:PATH='E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;' + $env:PATH
$env:GRADLE_USER_HOME='E:\projects\MiniC\.gradle-home'
.\gradlew.bat runUi
```

`runUi` 是前台 JavaFX 进程，窗口未关闭前 Gradle 会一直显示 `:runUi EXECUTING`，这是正常状态；关闭 UI 窗口后任务才会结束。

## UI API 最小示例

`0.2.0` 已提供不依赖 JavaFX 的 UI 门面：

```java
MiniCObservationApi api = new MiniCObservationApi();
api.loadSource("main.mc", "int main() { return 0; }");
api.startSession();

UiControlResultDto result = api.next();
UiCurrentStateDto state = api.currentState();
UiStageDataDto stageData = api.currentStageData();
UiStageVisualDto visualData = api.currentStageVisualData();
UiGlobalDataDto globalData = api.globalData();

api.play();
api.tick();
api.playFast();
api.tick();
api.pause();
```

当前 UI API 支持正向下一步、自动播放、两倍速播放、暂停和手动 `tick`。`previous` 与 `reversePlay` 已作为未来扩展点保留，当前返回 `UNSUPPORTED`，状态能力中 `canPrevious=false`、`canReversePlay=false`。

## 本地测试

推荐使用项目内本地 JDK 和 Gradle 缓存：

```powershell
$env:JAVA_HOME='E:\projects\MiniC\.local\tools\jdk-21.0.10+7'
$env:PATH='E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;' + $env:PATH
$env:GRADLE_USER_HOME='E:\projects\MiniC\.gradle-home'
.\gradlew.bat test
```

## 本地编译为 exe

Windows x64 可执行文件生成依赖 Visual Studio 2022 Build Tools 的 C++ 工具链，必须能使用 `ml64.exe` 和 `link.exe`。

在普通 PowerShell 中可通过 `VsDevCmd.bat` 临时启用 x64 工具链环境：

```powershell
cmd /c "`"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat`" -arch=x64 && where ml64 && where link"
```

编译样例并生成可执行文件：

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

## 协作规则摘要

- 全程使用中文沟通、记录需求、编写文档和汇报结果。
- Java 标识符使用英文。
- 每次 agent 只能执行 [PLAN.md](PLAN.md) 中的一个任务编号，除非用户明确批准合并。
- 每个非文档任务必须有测试或明确说明无法测试的原因。
- 不提交构建产物、IDE 私有配置、临时文件、日志文件和本地环境文件。
