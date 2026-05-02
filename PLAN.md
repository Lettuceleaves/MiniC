# MiniC Agent 执行计划

当前开发阶段：`0.2.0`。

`0.1.0` 已完成从 MiniC 源码到 Windows x64 可执行文件的相对完整编译链路，并具备 Lexer、Parser、Semantic、IR、Codegen、Toolchain 和 CLI。当前进入 `0.2.0` 开发阶段，本阶段不继续扩展 C 语言能力，转向项目结构化、编译层可步进改造，以及面向 UI 的统一调度接口。

`0.2.0` 的目标分为四层：

- UI 层：只负责展示。UI tab、侧边栏和自动切换策略留到 UI 阶段讨论。
- 调度层：提供全局控制动作和统一数据访问 API，包括下一步、自动播放、两倍速播放、暂停；上一步和自动倒放只预留能力，不在本阶段实现。
- 兼容层：为每个编译大阶段暴露统一 API，负责把各阶段内部数据整理成格式统一、信息完整的阶段数据。
- 编译层：改造现有一次性执行模型，让各阶段具备细粒度正向步进能力。

## 执行规则

- 每次 agent 只执行一个 `B` 编号任务，除非用户明确批准合并。
- 不允许跳过依赖。
- 每个非文档任务必须运行验证命令。
- 如果无法验证，停止并汇报阻塞点。
- 每个任务完成后汇报：任务编号、修改文件、验证命令、验证结果、已知限制。
- 文档、计划、验收标准和汇报使用中文。
- `0.2.0` 阶段不新增 C 语言语法能力，不引入 JavaFX UI，不实现编译板块断点架构。

## 0.2 关键边界

- 编译层正向步进必须真实执行，不采用“完整编译后回放 trace”替代。
- 步进粒度一步到位：
  - Lexer：每步产出一个 token 或词法 diagnostic。
  - Parser：每步产出一个完成的 AST 节点。
  - Semantic：每步执行一个语义动作。
  - IR lowering：每步产出一条 IR 指令或一个关键 IR 结构动作。
  - Codegen：每步产出一行汇编或一个汇编结构行。
- 上一步和自动倒放只做接口与能力预留，当前返回 unsupported 或 `canPrevious=false`、`canReversePlay=false`。
- 调度控制是全局的：下一步、自动播放、两倍速播放、暂停作用于全局编译观测游标；UI 是否自动切 tab 留到 UI 阶段。
- 数据访问分为三类：
  - 当前状态数据：当前阶段、当前步骤、播放状态、源码范围、标题、诊断摘要。
  - 当前阶段数据：当前阶段的输入摘要、当前项、累计输出、阶段诊断和阶段进度。
  - 全局数据：源码、所有阶段摘要、全量 diagnostics、token/AST/semantic/IR/assembly/artifact 摘要。

## Phase B0：测试基线收敛预处理

### B000：盘点测试基线并定义收敛原则

依赖：无。

目标：进入结构化改造前，先明确测试收敛策略，降低后续重构时的维护成本。

允许修改：

- `PLAN.md`
- 可选 `README.md`

验收：

- 明确测试从“开发期细粒度防护”切换为“重构期综合行为防护”。
- 明确保留基础模型、类型布局、pipeline、CLI、toolchain 等低成本高价值测试。
- 明确收敛重点为 Lexer、Parser、Semantic、IR lowering、Windows x64 Codegen。
- 明确删除或弱化只验证 record getter、重复 source range、单语法单错误、过细汇编片段的测试。

验证：文档任务无需运行 Gradle；需人工检查文档。

### B001：收敛 Lexer 和 Parser 测试

依赖：B000。

目标：减少词法和语法阶段的重复细粒度测试，用综合用例覆盖已完成语法能力。

允许修改：

- `src/test/java/minic/compiler/lexer/**`
- `src/test/java/minic/compiler/parser/**`

验收：

- Lexer 保留代表性 token、关键字/标识符边界、字面量、注释和非法字符诊断测试。
- Parser 将声明、类型、语句、控制流、表达式、指针、数组、结构体、函数指针等测试合并为少量综合用例。
- 删除重复的 getter、完整 range、单语法单测试断言。
- Parser 仍保留代表性语法错误诊断。

验证：`./gradlew test`

### B002：收敛 Semantic 测试

依赖：B001。

目标：把语义阶段大量单规则测试收敛为综合合法程序和代表性错误测试。

允许修改：

- `src/test/java/minic/compiler/semantic/**`

验收：

- 保留作用域、结构体布局等基础契约测试。
- 合并函数、变量、类型、指针、数组、结构体、函数指针、浮点转换等合法路径测试。
- 保留代表性 name resolution、类型不兼容、非法 return、函数指针返回值限制等 diagnostics。
- 删除一语法一错误的重复用例。

验证：`./gradlew test`

### B003：收敛 IR lowering 测试

依赖：B002。

目标：降低 IR lowering 测试对内部顺序、临时编号和单语法路径的绑定。

允许修改：

- `src/test/java/minic/compiler/ir/lowering/**`

验收：

- 用综合程序覆盖算术、控制流、函数调用、指针、数组、结构体、函数指针、标量和浮点。
- 保留关键 IR 指令类型和数据流契约断言。
- 删除对自动标签编号、过细局部变量顺序、重复语法路径的脆弱断言。

验证：`./gradlew test`

### B004：收敛 Windows x64 Codegen 测试

依赖：B003。

目标：把汇编测试从大量单语法片段收敛为关键能力综合片段测试。

允许修改：

- `src/test/java/minic/compiler/codegen/windows/**`

验收：

- 保留目标平台、入口符号、调用约定、栈参数、运行时 trap、浮点、指针/数组/结构体、函数指针关键片段。
- 删除重复的单语法汇编片段测试。
- 避免把完整汇编文本作为黄金文件。

验证：`./gradlew test`

### B005：收敛 AST 纯模型测试

依赖：B004。

目标：删除只验证 Java record getter 的 AST 纯模型测试，保留真正的不变量测试。

允许修改：

- `src/test/java/minic/compiler/ast/**`

验收：

- 删除只验证字段访问、空 Optional、简单 getter 的测试。
- 保留不可变集合、非法参数、关键结构不变量测试。
- 不降低 Parser 综合测试对 AST 结构的覆盖。

验证：`./gradlew test`

### B006：建立回归样例清单

依赖：B005。

目标：明确后续结构化改造的功能健全基线，避免继续为已完成语法补大量细粒度测试。

允许修改：

- `README.md`
- `PLAN.md`
- 可选 `src/test/java/minic/compiler/pipeline/**`

验收：

- README 或 PLAN 明确 samples 和 pipeline 测试是 0.1 功能健全基线。
- 保留代表性合法程序、代表性 diagnostics 和编译管线失败路径。
- 记录后续新增结构化能力时优先测新结构化契约，不重复补旧语法细节。

验证：`./gradlew test`

### B007：B0 验收

依赖：B006。

目标：确认测试基线已经收敛，可以进入编译层结构化改造。

允许修改：

- `README.md`
- `PLAN.md`

验收：

- `./gradlew test` 通过。
- Parser、Semantic、IR lowering、Codegen 的测试数量和重复断言明显下降。
- 保留完整编译链路回归、代表性错误诊断和关键后端能力检查。
- README 当前状态指向 B1 编译层数据管理改造。

验证：`./gradlew test`

## Phase B1：结构化数据契约

### B010：定义阶段标识、步骤结果和能力模型

依赖：B007。

目标：建立调度层、兼容层和编译层共享的基础结构化契约。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 定义编译阶段标识：source、lexer、parser、semantic、ir、codegen、toolchain。
- 定义步骤结果，能表达成功推进、阶段完成、无法推进、unsupported 和失败 diagnostics。
- 定义能力模型，明确 `next/play/playFast/pause` 当前可用，`previous/reversePlay` 当前预留。
- 测试覆盖不可变性和 unsupported 能力表达。

验证：`./gradlew test`

### B011：定义当前状态数据模型

依赖：B010。

目标：提供 UI 可直接消费的全局当前状态数据。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 当前状态包含源码名、当前阶段、全局步骤下标、阶段步骤下标、播放模式、帧间隔、源码范围、标题、说明和当前 diagnostics。
- 当前状态包含 `canNext`、`canPrevious`、`canPlay`、`canPlayFast`、`canPause`、`canReversePlay`。
- `canPrevious` 和 `canReversePlay` 在本阶段默认为 false。

验证：`./gradlew test`

### B012：定义阶段数据和全局数据模型

依赖：B011。

目标：建立统一的数据区设计，隔离 UI 与编译器内部实现。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 阶段数据包含阶段、阶段进度、输入摘要、当前项、累计输出、阶段 diagnostics。
- 全局数据包含源码、所有阶段摘要、全量 diagnostics、token/AST/semantic/IR/assembly/artifact 摘要。
- 数据模型使用不可变集合，不暴露编译层可变工作状态。

验证：`./gradlew test`

## Phase B2：编译层数据管理改造

### B020：定义编译阶段执行状态模式

依赖：B012。

目标：为编译层引入输入数据、工作数据、输出数据的统一改造模式。

允许修改：

- `src/main/java/minic/compiler/stage/**`
- `src/test/java/minic/compiler/stage/**`

验收：

- 定义阶段进度、阶段状态、阶段输入/工作/输出的基础抽象或约定类。
- 明确工作数据仅供编译层内部使用，输出数据可被兼容层读取。
- 明确最终结果仍能构建出现有 `LexResult`、`ParseResult`、`SemanticResult`、`IrModule`、`AssemblySource`。

验证：`./gradlew test`

### B021：改造 Lexer 为可步进状态

依赖：B020。

目标：让 Lexer 支持每次正向推进一个 token 或词法 diagnostic。

允许修改：

- `src/main/java/minic/compiler/lexer/**`
- `src/test/java/minic/compiler/lexer/**`
- 必要时 `src/main/java/minic/compiler/stage/**`

验收：

- Lexer state 保存 source、offset、已产出 tokens、已产出 diagnostics 和当前 token。
- `next` 每次产出一个 token 或 diagnostic。
- 完成后可构建与现有 lexer API 等价的 `LexResult`。
- 现有一次性 lexer 调用可基于 step 循环实现或保持兼容包装。

验证：`./gradlew test`

### B022：改造 Parser 为可步进状态

依赖：B021。

目标：让 Parser 支持每次正向推进并产出一个完成的 AST 节点。

允许修改：

- `src/main/java/minic/compiler/parser/**`
- `src/test/java/minic/compiler/parser/**`
- 必要时 `src/main/java/minic/compiler/stage/**`

验收：

- Parser state 保存 token 输入、游标、解析上下文、已完成 AST 节点、diagnostics 和当前节点。
- `next` 按“节点完成”计步，不使用 enter/exit 双事件。
- 完成后可构建与现有 parser API 等价的 `ParseResult`。
- 现有一次性 parser 调用保持兼容。

验证：`./gradlew test`

### B023：改造 Semantic 为可步进状态

依赖：B022。

目标：让语义分析支持每次正向执行一个语义动作。

允许修改：

- `src/main/java/minic/compiler/semantic/**`
- `src/test/java/minic/compiler/semantic/**`
- 必要时 `src/main/java/minic/compiler/stage/**`

验收：

- 语义动作至少覆盖定义符号、解析符号、检查类型、类型转换、注册结构体/函数、报告 diagnostic。
- Semantic state 保存 AST 输入、当前遍历位置、scope/registry 工作数据、已完成动作、diagnostics 和当前动作。
- 完成后可构建与现有 semantic API 等价的 `SemanticResult`。
- 现有一次性 semantic 调用保持兼容。

验证：`./gradlew test`

### B024：改造 IR lowering 为可步进状态

依赖：B023。

目标：让 IR lowering 支持每次正向产出一条 IR 指令或关键 IR 结构动作。

允许修改：

- `src/main/java/minic/compiler/ir/**`
- `src/test/java/minic/compiler/ir/**`
- 必要时 `src/main/java/minic/compiler/stage/**`

验收：

- IR 动作覆盖函数、基本块、局部变量、临时值和指令产生。
- IR state 保存 AST/Semantic 输入、当前函数 builder、当前 block、计数器、控制流上下文和已产出 IR。
- 完成后可构建与现有 lowering API 等价的 `IrModule`。
- 现有一次性 IR lowering 调用保持兼容。

验证：`./gradlew test`

### B025：改造 Codegen 为可步进状态

依赖：B024。

目标：让 Windows x64 codegen 支持每次正向产出一行汇编或汇编结构行。

允许修改：

- `src/main/java/minic/compiler/codegen/**`
- `src/test/java/minic/compiler/codegen/**`
- 必要时 `src/main/java/minic/compiler/stage/**`

验收：

- Codegen state 保存 IR 输入、当前函数 emitter、frame layout、section/label、已产出汇编行和当前行。
- `next` 每次产出一行汇编或一个结构行。
- 完成后可构建与现有 codegen API 等价的 `AssemblySource`。
- 现有一次性 codegen 调用保持兼容。

验证：`./gradlew test`

## Phase B3：兼容层阶段 API

### B030：定义阶段 Stepper 接口

依赖：B025。

目标：为每个编译大阶段暴露统一兼容层 API。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 接口支持 `next`、`previous` 预留、`snapshot`、`data`、`canNext`、`canPrevious`。
- `previous` 当前返回 unsupported，`canPrevious=false`。
- 接口不暴露编译层内部可变工作数据。

验证：`./gradlew test`

### B031：实现 Lexer 阶段兼容层

依赖：B030。

目标：把 Lexer 可步进状态适配为统一阶段 API。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/main/java/minic/compiler/lexer/**`
- `src/test/java/minic/runtime/step/**`

验收：

- `next` 推进一个 token 或词法 diagnostic。
- 阶段数据包含源码摘要、当前 token、已产出 tokens、diagnostics。
- 当前状态标题和详情适合 UI 展示。

验证：`./gradlew test`

### B032：实现 Parser 阶段兼容层

依赖：B031。

目标：把 Parser 可步进状态适配为统一阶段 API。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/main/java/minic/compiler/parser/**`
- `src/test/java/minic/runtime/step/**`

验收：

- `next` 推进一个 AST 节点。
- 阶段数据包含 token 输入摘要、当前 AST 节点、已完成节点、AST 摘要和 diagnostics。
- 当前状态能定位源码范围。

验证：`./gradlew test`

### B033：实现 Semantic 阶段兼容层

依赖：B032。

目标：把 Semantic 可步进状态适配为统一阶段 API。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/main/java/minic/compiler/semantic/**`
- `src/test/java/minic/runtime/step/**`

验收：

- `next` 推进一个语义动作。
- 阶段数据包含 AST 输入摘要、当前动作、符号/类型摘要、diagnostics。
- 语义动作使用稳定动作类型，便于 UI 分组显示。

验证：`./gradlew test`

### B034：实现 IR 阶段兼容层

依赖：B033。

目标：把 IR lowering 可步进状态适配为统一阶段 API。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/main/java/minic/compiler/ir/**`
- `src/test/java/minic/runtime/step/**`

验收：

- `next` 推进一个 IR 指令或结构动作。
- 阶段数据包含当前函数、当前 block、当前 IR 项、已产出 IR 摘要。
- 当前状态能关联源码范围时必须保留。

验证：`./gradlew test`

### B035：实现 Codegen 阶段兼容层

依赖：B034。

目标：把 Codegen 可步进状态适配为统一阶段 API。

允许修改：

- `src/main/java/minic/runtime/step/**`
- `src/main/java/minic/compiler/codegen/**`
- `src/test/java/minic/runtime/step/**`

验收：

- `next` 推进一行汇编或结构行。
- 阶段数据包含当前 section、label、汇编行、累计汇编摘要。
- 当前状态能关联 IR 或源码范围时应保留。

验证：`./gradlew test`

## Phase B4：调度层全局控制

### B040：添加编译观测会话

依赖：B035。

目标：建立全局编译观测会话，串联各阶段 stepper。

允许修改：

- `src/main/java/minic/session/**`
- `src/test/java/minic/session/**`

验收：

- 会话可从源码创建。
- 会话持有阶段顺序、当前阶段、当前阶段 stepper 和全局步骤计数。
- 下游阶段在需要时可自动准备上游完整结果，但只把当前全局游标对应步骤作为当前状态。

验证：`./gradlew test`

### B041：实现全局下一步

依赖：B040。

目标：调度层提供全局 `next`，按阶段顺序推进最小粒度步骤。

允许修改：

- `src/main/java/minic/session/**`
- `src/test/java/minic/session/**`

验收：

- `next` 推进当前阶段一步。
- 当前阶段完成后，下一次 `next` 进入下一个阶段。
- 所有阶段完成后返回无法继续或 completed 状态。
- 当前状态数据、当前阶段数据、全局数据同步更新。

验证：`./gradlew test`

### B042：实现播放状态和暂停

依赖：B041。

目标：调度层支持自动播放所需状态管理。

允许修改：

- `src/main/java/minic/session/**`
- `src/test/java/minic/session/**`

验收：

- 播放状态支持 paused、playing、fastPlaying。
- `pause` 可从任意播放状态回到 paused。
- 状态数据能返回当前帧间隔。
- 上一步和自动倒放能力仍显示为预留不可用。

验证：`./gradlew test`

### B043：实现自动播放和两倍速播放

依赖：B042。

目标：提供不依赖 UI 框架的播放控制能力。

允许修改：

- `src/main/java/minic/session/**`
- `src/test/java/minic/session/**`

验收：

- 自动播放按 `1000ms/帧` 推进。
- 两倍速播放按 `500ms/帧` 推进。
- 单元测试使用手动 tick 或可控时钟，不真实等待。
- 到达编译末尾自动暂停。

验证：`./gradlew test`

### B044：预留上一步和自动倒放接口

依赖：B043。

目标：在调度层 API 中保留未来反向能力入口，但当前不实现。

允许修改：

- `src/main/java/minic/session/**`
- `src/test/java/minic/session/**`

验收：

- `previous` 返回 unsupported 或等价结果。
- `reversePlay` 返回 unsupported 或等价结果。
- `currentState.canPrevious=false`。
- `currentState.canReversePlay=false`。
- 文档或 JavaDoc 明确这是未来扩展点。

验证：`./gradlew test`

## Phase B5：UI API 门面

### B050：添加 UI 编译控制门面

依赖：B044。

目标：提供 UI 层使用的简单 API，不暴露内部 stepper 和编译层状态。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/test/java/minic/uiapi/**`

验收：

- API 支持加载源码文本或 `SourceFile`。
- API 支持开始编译观测会话。
- API 支持下一步、自动播放、两倍速播放、暂停。
- API 保留上一步、自动倒放方法或能力字段，但当前返回 unsupported。
- API 支持查询当前状态数据、当前阶段数据、全局数据。
- `uiapi` 不依赖 JavaFX。

验证：`./gradlew test`

### B051：添加 UI 状态 DTO

依赖：B050。

目标：定义 UI 层可稳定绑定的数据对象，隔离内部模型变化。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/test/java/minic/uiapi/**`

验收：

- DTO 覆盖当前状态、当前阶段数据和全局数据。
- DTO 使用不可变数据结构。
- DTO 不暴露 compiler 内部可变对象。
- DTO 字段命名适合 UI 绑定。

验证：`./gradlew test`

### B052：添加 UI 门面端到端测试

依赖：B051。

目标：用一个 MiniC 样例覆盖 UI 门面从加载源码到正向播放控制的基础流程。

允许修改：

- `src/test/java/minic/uiapi/**`
- 必要时可补充 `samples/**`

验收：

- 测试覆盖加载源码、开始会话、下一步、播放 tick、两倍速 tick、暂停。
- 测试覆盖 previous/reversePlay 当前 unsupported。
- 测试断言当前阶段和当前数据会随游标推进变化。
- 不依赖真实 MSVC 工具链。

验证：`./gradlew test`

## Phase B6：文档收口

### B060：补充结构化接口文档

依赖：B052。

目标：记录 `0.2.0` 四层结构、数据区设计和 UI API 使用方式。

允许修改：

- `README.md`
- `SPEC.md`
- `version/0.2.0.md`

验收：

- README 增加 UI API 的最小使用示例。
- SPEC 记录 UI 层、调度层、兼容层、编译层的依赖规则。
- `version/0.2.0.md` 记录已完成能力和仍未实现项。

验证：文档任务无需运行 Gradle；需人工检查文档。

### B061：0.2.0 阶段验收

依赖：B060。

目标：完成 `0.2.0` 阶段收口，确认结构化观测和 UI 接口达到可用状态。

允许修改：

- `README.md`
- `PLAN.md`
- `version/0.2.0.md`

验收：

- `PLAN.md` 标记 `B000-B060` 完成。
- README 当前状态更新为 `0.2.0`。
- `version/0.2.0.md` 有最终阶段总结。
- 明确下一阶段候选：JavaFX UI、运行时 debugger、真实进程调试、反向 rr debug。

验证：`./gradlew test`
