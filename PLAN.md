# MiniC Agent 执行计划

当前开发阶段：`0.3.0-SNAPSHOT`。

下一步任务：`C030：实现 VS Code 风格 Shell`。

`0.1.0` 编译闭环总结见 [version/0.1.0.md](version/0.1.0.md)。
`0.2.0` 结构化观测阶段记录见 [version/0.2.0.md](version/0.2.0.md)。

## 执行规则

- 每次 agent 只执行一个任务编号，除非用户明确批准合并。
- 不允许跳过依赖。
- 每个非文档任务必须运行验证命令。
- 如果无法验证，停止并汇报阻塞点。
- 每个任务完成后汇报：任务编号、修改文件、验证命令、验证结果、已知限制。
- 文档、计划、验收标准和汇报使用中文。
- Java 包名、类名、方法名、测试方法名使用英文。
- 代码注释优先中文，必要时可使用简短英文术语。
- 修改已有文件前应先查看当前内容，避免覆盖用户或其他 agent 的未提交改动。
- 不提交构建产物、IDE 私有配置、临时文件、日志文件和本地环境文件。

## 0.3 目标边界

`0.3.0` 目标是实现 JavaFX 版 MiniC Visual Workbench。首版 UI 是编译全流程可视化工作台，不是完整 IDE，也不是运行时 debugger。

UI 风格参考本机原型：

```text
C:\Users\Administrator\Desktop\styleOfMiniC\index.html
```

风格约束：

- VS Code 风格深色工作台。
- 高信息密度、清晰分栏、工具型界面。
- 不做 landing page、营销页、浅色主题或大卡片 dashboard。
- 主背景 `#1e1e1e`，面板背景 `#252526`，顶栏背景 `#3c3c3c`，主强调色 `#007acc`。
- UI 字体优先 `Segoe UI`、`Microsoft YaHei`；代码和结构化输出使用 `Consolas`。
- 布局采用 titlebar、activity bar、sidebar、editor、visual pane、bottom panel、inspector、status bar 的工作台结构。

架构约束：

- JavaFX UI 放在独立 UI 包或应用层。
- UI 只依赖 `minic.uiapi.*`。
- UI 不直接依赖 `minic.compiler.*`、`minic.runtime.step.*` 或 `minic.session.*`。
- 首版只实现正向 `next`、`play`、`playFast`、`pause` 和 JavaFX 定时 `tick`。
- `previous` 和 `reversePlay` 只显示为预留禁用能力，不实现。
- 首版按现有 `Ui*Dto` 的字符串摘要展示数据，不为了图形化而穿透访问 AST、IR 或 stepper 内部对象。

## Phase C：JavaFX UI

### C000：定义 JavaFX UI 阶段目标和边界

依赖：`0.2.0` 已完成。

目标：把 `0.3.0` UI 阶段的产品定位、视觉风格、架构边界和首版不做事项写入文档。

允许修改：

- `PLAN.md`
- `README.md`
- 可选 `SPEC.md`
- 可选 `version/0.3.0.md`

验收：

- 明确首版 UI 是 VS Code 风格 MiniC Visual Workbench。
- 明确 UI 只绑定 `minic.uiapi` 门面和 DTO。
- 明确不实现 debugger、真实进程调试、反向步进、自动倒放和完整 AST/IR 富对象图。
- 明确参考 `styleOfMiniC/index.html` 的布局和视觉规则。
- README 当前状态指向 `C010`。

验证：文档任务无需运行 Gradle；需人工检查文档。

### C010：接入 JavaFX 构建依赖和 UI 入口

依赖：`C000`。

目标：让项目具备可启动的 JavaFX UI 应用入口。

允许修改：

- `build.gradle`
- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- Gradle 接入 JavaFX。
- 新增 JavaFX Application 入口。
- 保留现有 CLI 入口。
- 能启动一个空的 MiniC Visual Workbench 窗口。
- 不引入 compiler/runtime/session 内部依赖到 UI 层。

验证：`./gradlew test`，并手工启动 UI。

### C020：实现 UI 状态模型适配层

依赖：`C010`。

目标：封装 `MiniCObservationApi`，把 UI API DTO 转成 JavaFX 可绑定状态。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- ViewModel 持有 `MiniCObservationApi`。
- 支持加载源码、开始会话、下一步、播放、两倍速播放、暂停。
- 当前状态、阶段数据、全局数据可被 JavaFX 控件绑定。
- 控制能力字段能驱动按钮启用/禁用。
- ViewModel 不暴露 compiler/runtime/session 对象。

验证：`./gradlew test`

### C030：实现 VS Code 风格 Shell

依赖：`C020`。

目标：搭建参考原型中的工作台骨架。

允许修改：

- `src/main/java/minic/ui/**`
- `src/main/resources/**`
- `src/test/java/minic/ui/**`

验收：

- 实现 titlebar、activity bar、sidebar、editor area、inspector 和 status bar。
- 深色配色、字体、边框和控件密度接近参考原型。
- 不使用大圆角卡片、装饰渐变或营销页布局。
- 窗口缩放时主要区域不重叠、不溢出。

验证：`./gradlew test`，并手工检查 UI。

### C031：实现 Sidebar 与 Pipeline 阶段列表

依赖：`C030`。

目标：展示 workspace 文件区和编译阶段时间线。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- 阶段列表覆盖 Source、Lexer、Parser、Semantic、IR、Codegen、Toolchain。
- 支持 queued、running、done、error 等视觉状态。
- 阶段进度绑定 `UiStageDataDto` 和 `UiGlobalDataDto`。
- 当前阶段突出显示。

验证：`./gradlew test`

### C032：实现源码视图

依赖：`C031`。

目标：提供带行号和当前范围高亮的源码查看区。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- 使用等宽字体展示源码。
- 显示行号。
- 根据 `UiCurrentStateDto.sourceRange` 高亮当前源码范围或所在行。
- 支持中文诊断和 MiniC 源码混排显示。

验证：`./gradlew test`

### C033：实现 Visual Pane

依赖：`C032`。

目标：在源码右侧展示当前阶段的结构化可视化区域。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- 首版可展示当前阶段摘要、当前项、累计输出和占位结构视图。
- Lexer、Parser、Semantic、IR、Codegen 至少有可区分的展示模式。
- 不穿透访问 compiler 内部 AST/IR 对象。
- 图形区域保持深色网格或等价工作台视觉风格。

验证：`./gradlew test`，并手工检查 UI。

### C034：实现 Bottom Panel

依赖：`C033`。

目标：实现 Problems、Output、Terminal 风格底部面板。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- Problems 展示当前和全局 diagnostics。
- Output 展示阶段输出摘要。
- Terminal 展示类似命令行的观测日志文本。
- tab 切换不丢失当前观测状态。

验证：`./gradlew test`

### C035：实现 Inspector

依赖：`C034`。

目标：实现右侧观测详情面板和控制按钮。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- 控制按钮包含 Next、Play、2x、Pause。
- Previous 和 Reverse Play 可显示为禁用预留项，或暂不放入首版控制栏。
- Current State 展示阶段、全局步数、阶段步数、帧间隔和诊断数量。
- Current Item 展示当前项和说明。
- Accumulated Output 展示 token、AST、semantic、IR、assembly、artifact 摘要数量或文本。

验证：`./gradlew test`

### C040：实现源码加载和会话启动

依赖：`C035`。

目标：让 UI 可以从示例或用户输入创建观测会话。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`
- 可选 `samples/**`

验收：

- 支持选择内置样例。
- 支持编辑或粘贴源码文本。
- 点击开始后调用 `loadSource` 和 `startSession`。
- 会话启动后刷新当前状态、阶段数据和全局数据。

验证：`./gradlew test`，并手工启动 UI 验证。

### C050：实现单步刷新闭环

依赖：`C040`。

目标：打通 UI 的 `next` 控制和所有面板刷新。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- 点击 Next 后调用 `MiniCObservationApi.next()`。
- 当前状态、源码高亮、阶段列表、当前阶段区、底部面板和 inspector 同步刷新。
- 到达末尾后 Next 自动禁用或显示无法继续。

验证：`./gradlew test`，并手工启动 UI 验证。

### C060：实现播放、两倍速和暂停

依赖：`C050`。

目标：用 JavaFX 定时器驱动 `tick` 播放。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- Play 使用 `UiCurrentStateDto.frameIntervalMillis` 驱动 tick。
- 2x 使用调度层两倍速播放状态。
- Pause 可停止 UI 定时器并同步 API 状态。
- 到达编译末尾自动暂停。
- 不真实等待的逻辑部分有测试覆盖。

验证：`./gradlew test`，并手工启动 UI 验证。

### C070：实现 diagnostics 定位体验

依赖：`C060`。

目标：让诊断列表能定位源码范围。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- diagnostics 按 severity 视觉区分。
- 点击 diagnostic 后源码视图定位并高亮对应 range。
- 无 range 或跨文件诊断有合理降级显示。
- 中文错误信息显示正常。

验证：`./gradlew test`，并手工启动 UI 验证。

### C080：UI 阶段验收和文档

依赖：`C070`。

目标：收口 `0.3.0` JavaFX UI 首版。

允许修改：

- `README.md`
- `PLAN.md`
- `SPEC.md`
- `version/0.3.0.md`

验收：

- README 记录 JavaFX UI 启动方式。
- SPEC 记录 UI 层依赖边界和首版能力。
- `version/0.3.0.md` 记录已完成能力和未实现项。
- `./gradlew test` 通过。
- 手工 UI 验收记录清楚。

验证：`./gradlew test`，并手工启动 UI 验证。
