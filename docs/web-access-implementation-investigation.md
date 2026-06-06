# MiniC 网页访问实现调查

调查日期：2026-06-05

## 结论摘要

当前项目没有真正的网页访问层。没有 HTTP server、REST API、WebSocket、WebView、浏览器前端或前端构建链。项目现在提供的访问面是：

- CLI：`minic.Main` -> `MiniCli`。
- 桌面 UI：Gradle `runUi` -> JavaFX `MiniCWorkbenchLauncher` / `MiniCWorkbenchApp`。
- 进程内 Java UI API：`MiniCObservationApi` 和 `MiniCDebugApi`，返回 `Ui*Dto`，供 JavaFX Workbench 或未来其他 UI 绑定。

所以这里的 `uiapi` 是“UI 层可消费的 Java 门面和 DTO 边界”，不是网页/HTTP API。若后续要让网页访问 MiniC，应在 `minic.uiapi` 外新增一个独立 Web Adapter，把现有 facade 的命令和 DTO 包装成 HTTP/JSON、SSE 或 WebSocket 协议，而不是让网页直接接触 compiler、runtime、session 或 JavaFX。

## 调查范围

本次只读检查了这些区域：

- 构建和启动：`build.gradle`、`settings.gradle`、`src/main/java/minic/Main.java`、`src/main/java/minic/cli/MiniCli.java`。
- 文档约束：`README.md`、`SPEC.md`、`src/main/java/minic/uiapi/README.md`。
- UI API：`src/main/java/minic/uiapi/**`。
- 会话和运行时 stepper：`src/main/java/minic/session/CompileObservationSession.java`、`src/main/java/minic/runtime/step/**`。
- JavaFX Workbench：`src/main/java/minic/ui/app/**`、`src/main/java/minic/ui/workbench/**`、`src/main/java/minic/ui/panel/**`、`src/main/java/minic/ui/visual/**`、`src/main/java/minic/ui/debug/**`。
- 网页相关关键词：`http`、`server`、`socket`、`websocket`、`REST`、`WebView`、`localhost`、`Spring`、`Javalin`、`Netty`、`Jetty`、`JSON`、`package.json` 等。

## 已确认不存在的网页能力

`build.gradle` 只声明了 Java、application、OpenJFX 插件，主依赖是 RichTextFX、JUnit 和 AssertJ；JavaFX 模块只启用 `javafx.controls`，未启用 `javafx.web`。`runUi` 任务启动 `minic.ui.MiniCWorkbenchLauncher`，没有 Web server main class。

源码中未发现：

- `HttpServer`、`ServerSocket` 或监听端口逻辑。
- Spring MVC、Servlet、Javalin、Netty、Jetty、Undertow 等 Web 框架依赖或注解。
- WebSocket/SSE 实现。
- JavaFX `WebView` 或 `javafx.web`。
- `package.json`、Vite/Webpack、React/Vue、HTML/JS/TS 前端资源。
- Jackson/Gson/ObjectMapper 这类 HTTP JSON 序列化边界。

仓库中的 JSON 主要是本地配置，例如主题、settings、keybindings；不是网络 API 的传输格式。

## 当前分层

设计文档中的核心分层仍然清楚：

```text
JavaFX UI / future UI
  -> minic.uiapi public facade + public Ui*Dto
      -> minic.session CompileObservationSession
          -> minic.runtime.step StageStepper adapters
              -> minic.compiler preprocess/lexer/parser/semantic/IR/codegen/toolchain/execution

Debug UI / future UI
  -> minic.uiapi MiniCDebugApi + debug Ui*Dto
      -> minic.runtime.debug DebugSession / IrDebugInterpreter
          -> compiler preprocess/lexer/parser/semantic/IR lowering
```

`SPEC.md` 明确写了四层结构：编译层、兼容 stepper 层、调度 session 层、UI API 层。关键约束是 UI API 不暴露 compiler、runtime stepper 或 session 内部对象，不依赖 JavaFX，只返回 `Ui*Dto`。`src/main/java/minic/uiapi/README.md` 也说明了物理目录按 `api`、`core`、`visual`、`debug` 分组，但 Java package 仍统一为 `minic.uiapi`，这是兼容性选择。

### 编译层

`minic.compiler.*` 负责真实编译动作和阶段输出：preprocess、lexer、parser、semantic、IR lowering、Windows x64 codegen、toolchain。它不依赖 JavaFX，也不依赖 `minic.uiapi`。

### 兼容层

`minic.runtime.step.*` 把各编译阶段适配为统一 stepper：`next()`、`snapshot()`、`data()`、`canNext()` 等。它可以依赖编译层公开状态，但不应把内部可变 work 对象直接暴露出去。

典型 stepper：

- `PreprocessStageStepper` 调用 `MiniCPreprocessor`。
- `LexerStageStepper` 包装 `LexerState`。
- `ParserStageStepper` 包装 `ParserStepState`，并提供 AST preview/reveal 数据。
- `SemanticStageStepper` 包装 `SemanticStepState`。
- `IrStageStepper` 包装 `IrStepState`。
- `CodegenStageStepper` 包装 `WindowsX64CodegenStepState`。
- `ToolchainStageStepper` 调用 toolchain 生成产物。
- `ExecutionStageStepper` 等输入确认后运行可执行产物。

### 调度层

`CompileObservationSession` 串联阶段顺序：

```text
source -> preprocess -> lexer -> parser -> semantic -> ir -> codegen -> toolchain -> execution
```

它维护：

- 当前阶段下标。
- 全局 step count。
- 播放模式。
- 阶段 stepper map。
- 已完成阶段输出缓存，例如 `LexResult`、`ParseResult`、`SemanticResult`、`IrModule`、`AssemblySource`。

`next()` 的主要逻辑是：

1. 当前 stepper 可推进时调用 `stepper.next()`。
2. 阶段完成后缓存当前阶段输出。
3. 当前阶段有 diagnostics 时停止后续阶段。
4. 没有阻塞 diagnostics 时调用 `prepareNextStage()`，用上游结果构造下游 stepper，再切换阶段。

`prepareNextStage()` 的数据传递链是：

```text
SourceFile
 -> PreprocessResult.sourceFile()
 -> LexResult.tokens()
 -> ParseResult.program()
 -> SemanticResult + Program
 -> IrModule
 -> AssemblySource
 -> ExecutableArtifact
```

### UI API 层

`MiniCObservationApi` 是常规编译观测门面。调用顺序是：

1. `loadSource(String, String)` 或 `loadSource(SourceFile)` 保存源码，并清空旧 session。
2. `startSession()` 创建 `CompileObservationSession.fromSource(...)`。
3. 控制方法 `next()`、`nextStage()`、`play()`、`playFast()`、`tick()`、`pause()`、`confirmExecutionInput()` 调用 session，并转换成 `UiControlResultDto`。
4. 查询方法 `currentState()`、`currentStageData()`、`globalData()` 转换成 DTO。
5. `currentStageVisualData()` 根据当前 stepper 类型生成 `UiStageVisualDto`：lexer token、parser AST、semantic AST+scope、IR AST+scope、codegen assembly+IR 或 fallback。
6. `lexerVisualData()`、`astVisualData()`、`semanticVisualData()`、`codegenVisualData()` 提供阶段专属快照，方便 UI 切换历史阶段视图。

`MiniCDebugApi` 是独立 Debug 门面。调用顺序是：

1. `loadSource(...)` 保存源码，并清空 `DebugSession` 与缓存的 lowered 结果。
2. `startDebug()` 走 preprocess -> lex -> parse -> semantic -> IR lowering，交给 `IrDebugInterpreter.runMain(...)`，再 `RESTART` 并返回 `UiDebugStateDto`。
3. 控制方法把 UI 命令映射为 `DebugCommand`，例如 `RUN_TO_BREAKPOINT`、`STEP_OVER`、`STEP_BACK`、`BACK_TO_CALL_SITE`。
4. 查询方法通过 builder/mapper 返回 `metadataView()`、`astDebugView()`、`irDebugView()`、`asmDebugView()`、`dataStructureDebugView()`。

## DTO 与映射设计

DTO 使用 Java record，构造时做空值检查和 `List.copyOf(...)`，保证 UI 看到的是不可变快照。

核心 DTO：

- `UiControlResultDto`：控制动作结果。
- `UiCurrentStateDto`：当前阶段、全局步数、播放模式、源码范围、控制能力。
- `UiStageDataDto`：当前阶段进度、输入摘要、当前项、累计输出、阶段 diagnostics。
- `UiGlobalDataDto`：源码、阶段摘要、全量 diagnostics、各阶段摘要、运行输入/输出摘要。
- `UiStageVisualDto`：聚合 visual 模型，覆盖 generic、lexer、AST、semantic scope、IR、assembly。
- `UiDebugStateDto` 及相关 debug DTO：快照、调用栈、虚拟进程空间、事件、断点、AST/IR/ASM/数据结构视图。

映射层有两类：

- 包私有 mapper/builder：`UiDebugDtoMapper`、`UiAstVisualBuilder`、`UiSemanticScopeVisualBuilder`。它们把内部对象递归转换成 DTO，但不作为主要外部入口。
- public view builder：`UiDebugAstViewBuilder`、`UiDebugIrViewBuilder`、`UiDebugAsmViewBuilder`、`UiDebugMetadataViewBuilder`、`UiDebugDataStructureViewBuilder`。它们可组装更具体的 Debug 视图 DTO。

这一层是未来网页化最可复用的边界：Web Adapter 可以把这些 `Ui*Dto` 序列化为 JSON，而不需要重新理解 AST、IR、Scope、Stepper 或 DebugSession。

## JavaFX Workbench 如何消费 UI API

JavaFX 的入口是 `MiniCWorkbenchLauncher` 和 `MiniCWorkbenchApp`：

```text
MiniCWorkbenchLauncher.main()
 -> Application.launch(MiniCWorkbenchApp.class, args)
 -> MiniCWorkbenchApp.start(Stage)
 -> new MiniCWorkbenchShell(new MiniCWorkbenchViewModel())
 -> Scene + ThemeManager + Stage.show()
```

Workbench 侧职责如下：

- `MiniCWorkbenchShell`：工作台外壳、活动栏、文档标签、源码区/可视化区/Inspector 布局、Debug/Settings 活动区切换。
- `MiniCWorkbenchViewModel`：UI API 适配层和状态中心，持有 `MiniCObservationApi`、`MiniCDebugApi` 和 JavaFX `ReadOnly...Property`。
- `MiniCWorkbenchController`：很薄的控制封装，主要用于默认样例启动和 next/nextStage 转发。
- `MiniCPlaybackController`：用 JavaFX `Timeline` 定时调用 `viewModel.tick()`。
- `MiniCInspectorView`：注册编译控制命令，监听 ViewModel 属性刷新按钮和文本。
- `MiniCVisualPane`：监听 visual DTO 属性，并按阶段渲染 source/preprocess/lexer/parser/semantic/ir/codegen/toolchain/execution。
- `MiniCDebugPane`：监听 Debug DTO，呈现 metadata、AST、IR、ASM、数据结构视图，并触发 Debug 命令。

用户操作到 API 的路径示例：

```text
Inspector button
 -> MiniCWorkbenchControlHub.execute(commandId)
 -> MiniCPlaybackController or MiniCWorkbenchViewModel
 -> MiniCObservationApi.next()/nextStage()/play()/tick()/pause()
 -> CompileObservationSession
 -> runtime stepper
 -> compiler/runtime output
 -> Ui*Dto
 -> ViewModel ReadOnlyProperty
 -> VisualPane / Inspector / Sidebar refresh
```

Debug 操作路径：

```text
DebugPane button
 -> MiniCWorkbenchViewModel.debugStepOver()/debugRunToBreakpoint()/...
 -> MiniCDebugApi
 -> DebugSession.control(DebugCommand)
 -> UiDebugStateDto + view DTO builders
 -> ViewModel debug properties
 -> DebugPane refresh
```

## 重要旁路：实时编辑分析

`MiniCRealtimeAnalyzer` 位于 `minic.ui`，用于编辑器后台实时 diagnostics 和 token 高亮。它直接调用 `Lexer`、`MiniCPreprocessor`、`Parser`、`SemanticAnalyzer`，然后生成 `UiRealtimeAnalysisDto`。

这条路径不是 HTTP/web，也不是 `MiniCObservationApi` 的一部分。它仍然返回 UI DTO，但它绕过了 `CompileObservationSession`。如果未来要做网页编辑器，需要单独决定：

- 把实时分析也收拢进 `minic.uiapi`，形成正式 facade；或
- 在 Web Adapter 中明确提供独立的 `/realtime/analyze` 能力；或
- 暂时不提供实时分析，只提供显式编译/调试步骤。

如果目标是保持“所有 UI 都只依赖 UI API”，这条旁路是后续最该整理的地方。

## 当前选型评价

### 已选方案：进程内 Java facade + DTO

优点：

- 与编译器、stepper、debugger 同进程，控制简单，没有序列化和网络并发问题。
- DTO 边界已经稳定，JavaFX 只需要绑定 `Ui*Dto`。
- 对教学型 step-by-step UI 很合适：每一步都能同步拿到状态、阶段数据和 visual 数据。
- 编译/runtime/session 不依赖 JavaFX，后续换 UI 技术有空间。

代价：

- 不能被浏览器直接访问。
- 没有跨进程会话 ID、并发隔离、权限、超时、请求取消、资源清理等 Web 服务问题的处理。
- DTO 虽然适合序列化，但当前没有 JSON schema、版本字段或兼容性策略。
- Debug 连续运行、播放 tick 和可视化更新目前是 UI 主动拉取/调用模型，不是事件推送模型。

### 未选方案：直接把 JavaFX 当网页壳

项目没有使用 `WebView`，也没有 `javafx.web` 模块。这是合理的：Workbench 是原生桌面 UI，图形和编辑器交互依赖 JavaFX/RichTextFX；用 WebView 只会把两套 UI 技术混在一起。

### 未选方案：REST/WebSocket 服务

项目没有引入 Web 框架。这让当前代码保持轻量，但也意味着“网页访问”尚未开始设计。未来不宜让 Web 层直接依赖 compiler/runtime/session，而应沿用现有 `minic.uiapi` 分层。

## 后续网页访问设计建议

### 目标边界

新增一层 `minic.web` 或独立模块，例如：

```text
Browser frontend
  -> HTTP/JSON + SSE/WebSocket
      -> Web Adapter
          -> MiniCObservationApi / MiniCDebugApi / realtime facade
              -> existing session/runtime/compiler
```

Web Adapter 的职责应限于：

- 管理网页会话 ID 到 `MiniCObservationApi` / `MiniCDebugApi` 实例的映射。
- 把请求 payload 转成 facade 调用。
- 把 `Ui*Dto` 序列化为 JSON。
- 处理生命周期、并发、取消、超时、错误响应。

它不应：

- 直接操作 AST、IR、Scope、Stepper、DebugSession。
- 复刻 JavaFX ViewModel 的状态逻辑。
- 让 compiler/runtime/session 反向依赖 web 包。

### 推荐分阶段

第一阶段：只做 REST + 轮询。

- `POST /sessions`：创建编译观测会话，传入 `sourceName` 和 `sourceText`。
- `POST /sessions/{id}/next`：下一步。
- `POST /sessions/{id}/next-stage`：下一阶段。
- `POST /sessions/{id}/play`、`POST /sessions/{id}/pause`、`POST /sessions/{id}/tick`：复用现有播放语义。
- `GET /sessions/{id}/state`：返回 `UiCurrentStateDto`。
- `GET /sessions/{id}/stage-data`：返回 `UiStageDataDto`。
- `GET /sessions/{id}/global-data`：返回 `UiGlobalDataDto`。
- `GET /sessions/{id}/visual/current`、`/visual/lexer`、`/visual/ast`、`/visual/semantic`、`/visual/codegen`：返回 `UiStageVisualDto`。

这一阶段最贴近现有 API，风险最低。

第二阶段：Debug REST。

- `POST /debug-sessions`：创建 Debug 会话。
- `POST /debug-sessions/{id}/breakpoints/{line}`、`DELETE ...`：断点。
- `POST /debug-sessions/{id}/run-to-breakpoint`、`/step-over`、`/step-into`、`/step-back`、`/back-to-call-site` 等。
- `GET /debug-sessions/{id}/state`。
- `GET /debug-sessions/{id}/views/metadata|ast|ir|asm|data-structure`。

第三阶段：事件推送。

- SSE 或 WebSocket 推送播放 tick、debug 连续运行、diagnostics、当前源码范围和 visual 更新。
- 保留 REST 命令接口，推送只做状态通知。

第四阶段：实时编辑分析。

- 若保留当前能力，新增正式 facade，例如 `MiniCRealtimeAnalysisApi`，再暴露 `POST /analysis/realtime`。
- 网页端编辑器用 debounce 调用，不应把每次键入都变成编译观测 session。

### 技术选型建议

如果目标是最少依赖：

- 可用 JDK 自带 `com.sun.net.httpserver.HttpServer` 做原型。
- 优点：无框架依赖，容易验证 DTO 边界。
- 缺点：路由、JSON、错误处理、并发和 SSE/WebSocket 都要自己补，长期维护较弱。

如果目标是长期可维护：

- 选一个轻量 Java Web 框架，例如 Javalin 或 Spark Java，再配 Jackson。
- 优点：路由、JSON、异常处理和中间件成熟，适合 REST 原型到正式服务。
- 缺点：新增依赖，需要明确 Gradle 模块和打包方式。

如果目标是更完整的应用后端：

- Spring Boot 可行，但对当前项目体量偏重。
- 除非后续需要认证、持久化、多用户管理、部署监控，否则不建议第一步就上 Spring Boot。

前端建议：

- 初期不要复刻完整 JavaFX Workbench，先做 API 验证页面：源码输入、状态面板、下一步/下一阶段、当前 visual JSON/简图。
- AST/IR/ASM/数据结构图可以逐步移植；DTO 已经给出了足够稳定的数据边界。

## 风险和待决问题

- 会话隔离：Web 层必须引入 session id，不能用单例 facade。
- 并发控制：同一个 session 的 `next()`、`tick()`、debug command 需要串行化。
- 生命周期：长期未访问的 session 要释放，Debug 会话也要关闭。
- 错误模型：当前 `MiniCDebugApi.lowerWithProgram()` 遇 diagnostics 会抛 `IllegalStateException`；Web 层需要转成结构化错误响应。
- JSON 兼容：record 字段直接序列化前，应决定字段命名、版本和 null 策略。
- 播放语义：当前播放由 JavaFX `Timeline` 驱动 `tick()`；Web 端要决定由前端轮询 tick，还是后端定时推进并推送状态。
- 实时分析旁路：`MiniCRealtimeAnalyzer` 当前在 `minic.ui`，未来网页编辑器需要正式化这条 API。
- 工具链/执行安全：Web 访问若允许 toolchain/execution，会涉及本地命令执行、输入输出、超时和资源限制。

## 推荐下一步

1. 保留现有 `minic.uiapi` 作为唯一跨 UI 边界。
2. 先写 Web Adapter 小规格：会话模型、REST 路由、错误格式、DTO JSON 策略。
3. 原型优先选择 REST + 轮询，不先做 WebSocket。
4. 把实时编辑分析是否纳入 `uiapi` 单独立项。
5. 在实现 Web 层前补一组架构测试或依赖扫描，防止 web/ui 依赖倒灌到 compiler/runtime/session。

## 证据索引

- `README.md:26-35`：当前能力包含 `minic.runtime.step`、`minic.session`、`minic.uiapi`、JavaFX Workbench 和 visual DTO。
- `README.md:49-84`：首版 UI 只依赖 `minic.uiapi.*`，并给出 `MiniCObservationApi` 最小示例。
- `SPEC.md:242-255`：四层结构和依赖规则。
- `SPEC.md:257-271`：DTO 和 visual 数据设计约束。
- `SPEC.md:273-316`：Debugger 独立模式、控制语义和虚拟进程空间边界。
- `build.gradle:16-24`：依赖和 JavaFX 模块；无 Web/JSON server 依赖。
- `build.gradle:39-52`：`runUi` 启动 JavaFX launcher。
- `src/main/java/minic/uiapi/README.md:3-8`：UI API 目录职责。
- `src/main/java/minic/uiapi/api/MiniCObservationApi.java:13-326`：编译观测 facade。
- `src/main/java/minic/uiapi/api/MiniCDebugApi.java:25-340`：Debug facade。
- `src/main/java/minic/session/CompileObservationSession.java:42-518`：阶段顺序、控制推进、缓存和下一阶段准备。
- `src/main/java/minic/ui/workbench/MiniCWorkbenchViewModel.java:29-79`：ViewModel 只持有 UI API 门面和 DTO。
- `src/main/java/minic/ui/workbench/MiniCWorkbenchViewModel.java:149-288`：编译观测控制到 `MiniCObservationApi`。
- `src/main/java/minic/ui/workbench/MiniCWorkbenchViewModel.java:373-559`：Debug 控制到 `MiniCDebugApi`。
- `src/main/java/minic/ui/visual/MiniCVisualPane.java:120-224`：监听 visual DTO 并按阶段渲染。
- `src/main/java/minic/ui/editor/MiniCRealtimeAnalyzer.java:98-125`：实时编辑分析旁路。
