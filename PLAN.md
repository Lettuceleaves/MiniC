# MiniC Agent 执行计划

当前开发阶段：`0.3.1-SNAPSHOT`。

下一步任务：`C101：在 UI API 中暴露当前阶段 visual data`。

`0.1.0` 编译闭环总结见 [version/0.1.0.md](version/0.1.0.md)。
`0.2.0` 结构化观测阶段记录见 [version/0.2.0.md](version/0.2.0.md)。
`0.3.0` JavaFX UI 首版记录见 [version/0.3.0.md](version/0.3.0.md)。

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

## 0.3.1 目标边界

`0.3.1` 目标是增强 JavaFX UI 的阶段专属图形化表达，把 `0.3.0` 的摘要型 Visual Pane 升级为更贴近编译过程的图形视图。

本阶段仍沿用 `C` 任务编号，从 `C100` 开始。

核心需求：

- Lexer 阶段：源码文本上使用半透明彩色遮罩覆盖当前 token，遮罩位置必须和源码字符位置对齐，并保留换行、tab、空格。
- Parser/AST 阶段：显示 AST 树图形。
- Semantic 阶段：显示作用域树，`global scope` 在顶部，树向下展开，但箭头方向反向，即从子作用域指向父作用域。
- Codegen/Assembly 阶段：以文本行显示汇编，每生成一行就追加一行，并高亮当前行。

架构约束：

- UI 仍只依赖 `minic.uiapi` 门面和 DTO，不直接访问 compiler、runtime stepper 或 session 内部对象。
- 如需更丰富图形数据，应扩展 UI 专用 visual DTO，不暴露 AST、Scope、IR、Stepper 等内部对象。
- JavaFX 代码不得进入 compiler、runtime、session 或 uiapi 以外的职责边界。
- `previous` 和 `reversePlay` 继续保持预留，不在本阶段实现。

## Phase C 0.3.1：阶段图形化增强

### C100：定义阶段图形化数据契约

依赖：`0.3.0` 已完成。

目标：为 Lexer、AST、Semantic 和 Codegen 定义 UI 专用 visual DTO。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/test/java/minic/uiapi/**`
- 可选 `src/main/java/minic/ui/**`
- 可选 `src/test/java/minic/ui/**`

验收：

- 定义统一的 `UiStageVisualDto` 或等价聚合模型。
- Lexer visual DTO 能表达 token 文本、kind、source range、起止行列、当前 token。
- AST visual DTO 能表达树节点 label、kind、children、source range、当前节点。
- Semantic visual DTO 能表达作用域节点、符号摘要、父子关系、当前作用域和反向箭头语义。
- Assembly visual DTO 能表达已生成汇编行、当前行、行号、section/label 元信息。
- DTO 只包含字符串、数字、布尔、range、只读列表等 UI 友好字段，不暴露 compiler/runtime/session 内部对象。

验证：`./gradlew test`

### C101：在 UI API 中暴露当前阶段 visual data

依赖：`C100`。

目标：让 UI 能从 `MiniCObservationApi` 查询当前阶段图形化数据。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/test/java/minic/uiapi/**`
- 必要时 `src/main/java/minic/runtime/step/**`
- 必要时 `src/test/java/minic/runtime/step/**`

验收：

- `MiniCObservationApi` 提供 `currentStageVisualData()` 或等价方法。
- Lexer、Parser、Semantic、Codegen 阶段返回对应 visual 数据。
- 其他阶段返回空 visual 或 generic visual。
- 现有 UI API 方法保持兼容。
- 新增 API 不暴露 compiler/runtime/session 内部类型。

验证：`./gradlew test`

### C110：实现 Lexer Token 遮罩数据生成

依赖：`C101`。

目标：基于 token range 和源码文本生成可对齐的 token overlay 数据。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 当前 token 标记为 active。
- token visual 包含 `startOffset`、`endOffset`、起止行列、token kind、token text。
- UI 能根据 range 生成覆盖整个 token 的半透明彩色遮罩。
- 空格、tab、换行不被压缩。
- 测试覆盖空格、tab、换行、多字符 token、同一行多个 token。

验证：`./gradlew test`

### C111：实现 Lexer 源码对齐遮罩视图

依赖：`C110`。

目标：在 JavaFX UI 中显示源码文本，并使用半透明彩色遮罩覆盖当前 token。

允许修改：

- `src/main/java/minic/ui/**`
- `src/main/resources/**`
- `src/test/java/minic/ui/**`

验收：

- 源码视图使用等宽布局。
- 保留空格、tab、换行。
- 当前 token 使用半透明彩色遮罩覆盖完整 token 文本区域。
- 遮罩随 `next`、`play`、`tick` 推进。
- 不压缩空白字符，不因 token 长度变化导致布局跳动。

验证：`./gradlew test`，并手工启动 UI 检查。

### C120：实现 AST 树 visual data

依赖：`C101`。

目标：Parser 阶段生成 AST 树 DTO。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 程序根节点、函数、block、statement、expression 能形成树。
- 当前 parser 节点可标记 active。
- 节点保留 source range。
- 节点 label 简洁可读。
- 不暴露真实 AST 对象。

验证：`./gradlew test`

### C121：实现 AST 树视图

依赖：`C120`。

目标：Parser 阶段显示 AST 树图形。

允许修改：

- `src/main/java/minic/ui/**`
- `src/main/resources/**`
- `src/test/java/minic/ui/**`

验收：

- AST 节点以树形布局展示。
- active 节点高亮。
- 树区域可滚动或自适应。
- 点击节点可定位源码 range，若该 range 存在。
- 大程序不会导致 UI 元素重叠。

验证：`./gradlew test`，并手工启动 UI 检查。

### C130：实现 Semantic 作用域树 visual data

依赖：`C101`。

目标：Semantic 阶段生成作用域树 DTO，根节点为 `global scope`。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 根节点是 `global scope`。
- 数据结构表达父子作用域关系。
- 每条边带方向语义：child -> parent。
- 每个作用域节点包含符号摘要。
- 当前语义动作关联的作用域高亮。
- 不暴露真实 Scope 或 Symbol 内部对象。

验证：`./gradlew test`

### C131：实现 Semantic 反向箭头作用域树视图

依赖：`C130`。

目标：Semantic 阶段显示作用域树，布局向下展开但箭头反向。

允许修改：

- `src/main/java/minic/ui/**`
- `src/main/resources/**`
- `src/test/java/minic/ui/**`

验收：

- `global scope` 显示在顶部。
- 子作用域向下展开。
- 箭头方向从子节点指向父节点，视觉上箭头朝上。
- 当前作用域节点高亮。
- 当前路径可用强调色显示。
- 节点显示变量、函数、结构体等符号摘要。
- 可定位源码 range 时联动源码视图。

验证：`./gradlew test`，并手工启动 UI 检查。

### C140：实现 Assembly 行增量 visual data

依赖：`C101`。

目标：Codegen 阶段提供已生成汇编行和当前行。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/runtime/step/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/runtime/step/**`

验收：

- 每次 codegen step 后 assembly 行数递增或当前结构行变化。
- 当前行标记 active。
- 行号稳定。
- section、label 可作为行 metadata。
- 不暴露 codegen 内部 emitter 或 frame layout 对象。

验证：`./gradlew test`

### C141：实现 Assembly 行文本视图

依赖：`C140`。

目标：Codegen 阶段以文本方式显示汇编生成过程。

允许修改：

- `src/main/java/minic/ui/**`
- `src/main/resources/**`
- `src/test/java/minic/ui/**`

验收：

- 汇编按行显示。
- 每生成一行追加一行。
- 当前行高亮。
- 使用 `Consolas` 等宽字体。
- 支持滚动到最新行。

验证：`./gradlew test`，并手工启动 UI 检查。

### C150：整合阶段 Visual Pane 自动切换

依赖：`C111`、`C121`、`C131`、`C141`。

目标：根据当前阶段自动切换 Visual Pane 类型。

允许修改：

- `src/main/java/minic/ui/**`
- `src/main/resources/**`
- `src/test/java/minic/ui/**`

验收：

- Lexer 显示 token 半透明遮罩视图。
- Parser 显示 AST 树。
- Semantic 显示 global scope 在顶部、箭头反向的作用域树。
- Codegen 显示 Assembly 行视图。
- 其他阶段显示摘要 fallback。
- `next`、`play`、`playFast`、`tick` 都能驱动 Visual Pane 刷新。

验证：`./gradlew test`，并手工启动 UI 检查。

### C160：0.3.1 文档与验收

依赖：`C150`。

目标：收口 `0.3.1` 阶段。

允许修改：

- `README.md`
- `PLAN.md`
- `SPEC.md`
- `version/0.3.1.md`

验收：

- README 记录 `0.3.1` 图形化能力。
- PLAN 标记 `C100-C150` 完成。
- 新增 `version/0.3.1.md`。
- `./gradlew test` 通过。
- 手工 UI 验收记录包含 Lexer、AST、Semantic、Assembly 四个阶段结果。

验证：`./gradlew test`，并手工启动 UI 检查。
