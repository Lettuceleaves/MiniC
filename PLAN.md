# MiniC Agent 执行计划

当前开发阶段：`0.5.0`。

下一步任务：`E340`：实现数据结构视图 UI 模型。

`0.1.0` 编译闭环总结见 [version/0.1.0.md](version/0.1.0.md)。
`0.2.0` 结构化观测阶段记录见 [version/0.2.0.md](version/0.2.0.md)。
`0.3.0` JavaFX UI 首版记录见 [version/0.3.0.md](version/0.3.0.md)。
`0.3.1` 阶段专属图形化增强记录见 [version/0.3.1.md](version/0.3.1.md)。
`0.4.0` C 子集语法和预编译扩展记录见 [version/0.4.0.md](version/0.4.0.md)。

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

## 0.5.0 目标边界

本阶段目标是实现 MiniC 教学型可视化 Debugger。Debugger 是 Workbench 中的独立模式，由侧边栏 Debug 按钮进入；源码编辑器、行号、断点 gutter、诊断高亮和当前执行行高亮与现有编辑器共享。

Debugger 不调试真实 exe，不接 Windows Debug API，不承诺寄存器级、机器码级或系统进程级状态。第一版执行模型为：

```text
source -> preprocess -> lexer -> parser -> semantic -> ir -> debug interpreter
```

普通编译观察模式继续保持：

```text
source -> preprocess -> lexer -> parser -> semantic -> ir -> codegen -> toolchain -> execution
```

Debugger 基于 IR Interpreter 执行，并使用“状态快照 + 事件日志”支持正向运行、暂停、重启、关闭、单退、步退和返回调用处。外部调用副作用不纳入可回退承诺范围，第一版只通过 debug stub 记录可控输出。

## 控制语义

- `快进`：持续运行，直到程序结束、下一个断点、运行时错误、外部阻塞或用户请求暂停。
- `运行到断点`：从当前位置运行到下一个断点；如果没有断点则运行到结束或错误。
- `单步`：执行下一条源码级可见语句；函数调用整体跳过，类似 Step Over。
- `步入`：如果当前语句包含函数调用，则进入被调函数第一条可见语句；否则等同单步。
- `步返`：继续执行到当前函数返回到调用者后一条可见语句。
- `暂停`：只在 `快进` 和 `运行到断点` 这类连续运行中生效；解释器在下一条可见源码行执行前停住。
- `关闭`：销毁 DebugSession，保留编辑器源码和断点。
- `重启`：重新从 `main` 创建 DebugSession，保留断点。
- `单退`：恢复到上一个可见调试步的快照。
- `步退`：恢复到上一个断点命中的快照。
- `返回调用处`：恢复到进入当前函数调用之前的快照，也就是调用点状态。

## 右侧 Debug 视图

右侧 Debug 区域顶部使用标签栏呈现，后续支持向右拆分对比。首版视图包括：

- 元数据视图：展示状态、停止原因、当前函数、当前源码位置、调用栈、变量、stdout/stderr、断点、事件日志和 snapshot 时间线。
- 数据结构视图：展示虚拟进程空间和由进程空间投影出的图形化数据结构。
- AST 视图：高亮当前 debug 对应 AST 节点，点击节点显示节点数据、解释和源码映射。
- IR 视图：高亮当前 IR 指令，展示基本块、操作数、结果值和解释。
- ASM 视图：展示当前 IR 对应的生成汇编并高亮相关行组；这是映射展示，不代表真实 CPU 正在执行的机器指令。

## 虚拟进程空间

运行时数据按虚拟进程空间呈现，帮助用户理解程序执行时的数据位置和关系：

- code：函数、当前 IR 指令和映射汇编行。
- static/data：全局变量和字符串字面量。
- stack：调用栈、参数、局部变量、返回目标和返回值。
- heap：虚拟堆块、数组、结构体、指针目标和释放状态。
- io：stdin、stdout、stderr。

虚拟地址用于教学展示，不等同于 Windows 真实进程地址。

## 数据结构图形化基础架构

数据结构视图不为每一种高级数据结构实现独立渲染器，而是把高级数据结构映射到少量通用可视化基元：

```text
高级数据结构 = 基元结构 + 布局 + 装饰器 + 校验器 + 解释器
```

底层只定义三种基元：

- GraphStructure：节点和边，覆盖链表、树、Trie、并查集森林、普通图、DAG、状态机、跳表等。
- ArrayStructure：连续空间、表格、矩阵和网格，覆盖数组、字符串、栈/队列底层数组、矩阵、邻接矩阵、DP 表、网格、hash bucket array、bitmap 等。
- CompositeStructure：由多个图或数组组合出的结构，覆盖哈希表、邻接表、LRU、堆的数组+树双投影、复杂业务结构等。

树不是独立底层模型，而是 `GraphStructure + hierarchical layout`。红黑树后续作为 `GraphStructure + hierarchical layout + color decorator + rb validators` 扩展；第一批 descriptor 不包含红黑树。

## @visual 注释协议

第一版使用注释协议，不修改 MiniC 语法。`root` 只允许变量名，允许多个 visual，支持命名，同名结构可把多个离散 component 归并到同一个逻辑数据结构。

第一版纳入：

```c
// @visual graph name=tree kind=tree root=root node=Node left=left right=right label=value
// @visual array name=arr kind=array root=a length=n label=value
// @visual composite name=cache kind=hash_table
// @visual-node graph=network id=i label=name
// @visual-edge graph=network from=u to=v label=w directed=true
```

`@visual-node` 和 `@visual-edge` 第一版进入，但只支持简单变量名或字面值，不支持复杂表达式。若后续扩展复杂表达式，需要复用 Debugger 表达式求值能力，不能单独维护第二套表达式解释器。

## Phase E 0.5.0：教学型可视化 Debugger

执行顺序原则：

- 先写清边界和 SPEC，再实现 runtime debug 核心。
- 先做可单测的 DebugSession、虚拟进程空间、IR Interpreter 和 snapshot，再接 Workbench。
- UI 层只依赖 `minic.uiapi.*` DTO，不直接访问 runtime debug 内部对象。
- 数据结构图形化先建立 Graph/Array/Composite 基础设施，再添加高级 descriptor。
- ASM 视图只做 Source/IR/ASM 映射展示，不模拟真实 CPU 状态。

### E110：确认 Debugger 边界并更新 SPEC

依赖：`0.4.0` 已完成。

目标：把 0.5.0 Debugger 的模式、能力边界、控制语义、虚拟进程空间、数据结构图形化基元和 `@visual` 协议写入 `SPEC.md`。

允许修改：

- `README.md`
- `PLAN.md`
- `SPEC.md`

验收：

- SPEC 明确 Debugger 是独立模式，不进入普通编译观察流水线。
- SPEC 明确第一版执行 IR Interpreter，不调试真实 exe。
- SPEC 记录控制语义：快进、运行到断点、单步、步入、暂停、关闭、重启、步退、单退、步返、返回调用处。
- SPEC 明确暂停只在连续运行中生效。
- SPEC 明确使用状态快照和事件日志实现反向能力。
- SPEC 明确运行时数据按虚拟进程空间展示。
- SPEC 明确数据结构图形化三类基元：GraphStructure、ArrayStructure、CompositeStructure。
- SPEC 明确 `@visual-node` / `@visual-edge` 第一版进入，且首版只支持简单变量名或字面值。
- SPEC 明确第一批 descriptor 不包含红黑树，红黑树作为后续特殊结构扩展。
- SPEC 明确 ASM 视图是映射展示，不代表真实 CPU 执行状态。
- README 当前状态指向提交完成之后的下一步任务。

验证：文档任务，无需运行测试。

### E120：建立 Debug 基础模型

依赖：`E110`。

目标：新增 `minic.runtime.debug` 基础模型，先不执行真实 IR。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 新增 DebugSession、DebugSnapshot、DebugEvent、DebugCursor、DebugStopReason、DebugCommand、DebugExecutionState。
- DebugSession 能创建初始 paused 状态。
- DebugSnapshot 能表达当前源码范围、调用栈、进程空间引用、stdout/stderr、断点命中和停止原因。
- DebugEvent 能记录事件类型、标题、解释、源码范围和影响值引用。
- 单元测试覆盖初始状态、snapshot 追加和事件日志追加。

验证：`./gradlew test`

### E130：建立虚拟进程空间模型

依赖：`E120`。

目标：建立 code/static/stack/heap/io 五段虚拟进程空间。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 支持虚拟地址和值引用。
- 支持 code 段记录函数和当前指令。
- 支持 static/data 段记录全局变量和字符串字面量。
- 支持 stack 段记录调用帧、参数、局部变量和返回目标。
- 支持 heap 段记录虚拟堆块、数组、结构体和状态。
- 支持 io 段记录 stdin/stdout/stderr。
- 测试覆盖调用帧入栈/出栈、heap block 创建和 stdout 追加。

验证：`./gradlew test`

### E140：建立 DebugValue 模型

依赖：`E130`。

目标：建立运行时值体系。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 支持 int、long、char、bool、pointer、array、struct、null、uninitialized。
- 指针值引用虚拟地址。
- 数组和结构体值保留元素/字段元数据。
- 所有值可生成稳定摘要，供元数据视图和事件日志使用。

验证：`./gradlew test`

### E150：实现 IR Interpreter 最小执行

依赖：`E140`。

目标：从 `main` 执行到 `return`，形成最小可调试闭环。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`
- 必要时 `src/main/java/minic/compiler/ir/**`

验收：

- 能从已有 IR Module 找到 `main`。
- 支持常量、局部变量声明、load/store、move、return。
- 每个可见调试步记录 snapshot 和 event。
- 返回值写入 debug 状态。
- 测试覆盖 `int main() { int x = 1; return x; }` 的执行和状态快照。

验证：`./gradlew test`

### E160：支持表达式和控制流执行

依赖：`E150`。

目标：补齐常见 IR 运算和跳转执行。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 支持 unary、binary、cast。
- 支持 branch、jump 和 switch lowering 后的控制流。
- 支持 check initialized 和 check non-zero。
- 可执行 if、while、do while、switch 样例。
- 测试覆盖分支、循环、短路和 switch。

验证：`./gradlew test`

### E170：支持函数调用和调用栈

依赖：`E160`。

目标：支持 MiniC 内部函数调用。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 支持直接函数调用、参数传递、返回值和调用帧。
- 支持步入。
- 支持步返。
- 支持返回调用处。
- 测试覆盖嵌套调用、递归调用和调用栈快照恢复。

验证：`./gradlew test`

### E180：建立外部函数 debug stub

依赖：`E170`。

目标：建立外部函数 stub 机制，先支持 `printf`。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 外部调用通过 DebugExternalFunctionStub 分发。
- `printf` 支持最小格式化输出并写入虚拟 stdout。
- 事件日志记录外部调用。
- 文档和测试明确外部调用副作用不纳入可回退承诺。

验证：`./gradlew test`

### E190：支持断点和正向运行控制

依赖：`E180`。

目标：支持源码行断点和正向控制命令。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 支持设置/取消行断点。
- 支持不可断行吸附或拒绝，并返回解释。
- 支持快进、运行到断点、单步、暂停、关闭、重启。
- 暂停只在连续运行中生效。
- 测试覆盖断点命中、无断点运行到结束、暂停请求和重启保留断点。

验证：`./gradlew test`

### E200：支持反向调试

依赖：`E190`。

目标：基于 snapshot 实现回退能力。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/test/java/minic/runtime/debug/**`

验收：

- 支持单退。
- 支持步退到上一个断点命中快照。
- 支持返回调用处。
- 恢复 stack、heap、stdout/stderr、cursor 和停止原因。
- 测试覆盖变量、调用栈、stdout 和 heap 状态回退。

验证：`./gradlew test`

### E210：建立 DebugMappingIndex

依赖：`E160`。

目标：建立 Source/AST/IR/ASM 映射索引。

允许修改：

- `src/main/java/minic/runtime/debug/**`
- `src/main/java/minic/uiapi/**`
- `src/test/java/minic/runtime/debug/**`
- `src/test/java/minic/uiapi/**`
- 必要时 `src/main/java/minic/compiler/ast/**`
- 必要时 `src/main/java/minic/compiler/ir/**`
- 必要时 `src/main/java/minic/compiler/codegen/**`

验收：

- AST 节点具备稳定 debug id 或可由映射器稳定生成。
- IR 指令具备稳定 debug id 或可由映射器稳定生成。
- ASM 行具备稳定 debug id 或可由映射器稳定生成。
- 能从 source range 找到 AST/IR/ASM 相关项。
- ASM 映射明确是生成汇编映射展示，不代表真实 CPU 状态。

验证：`./gradlew test`

### E220：暴露 Debug UI API DTO

依赖：`E200`、`E210`。

目标：UI API 暴露 Debug 状态，不泄漏 runtime debug 内部对象。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/test/java/minic/uiapi/**`

验收：

- 新增 UiDebugStateDto、UiDebugSnapshotDto、UiDebugFrameDto、UiDebugVariableDto、UiDebugProcessSpaceDto、UiDebugEventDto、UiDebugBreakpointDto。
- MiniCObservationApi 或新的 Debug API 能启动独立 Debug 模式。
- DTO 不引用 `minic.runtime.debug.*` 类型。
- 测试覆盖启动 Debug、设置断点、运行到断点、单退和查询状态。

验证：`./gradlew test`

### E230：建立 VisualStructure 基础模型

依赖：`E130`。

目标：建立数据结构图形化基元。

允许修改：

- `src/main/java/minic/runtime/debug/visual/**`
- `src/test/java/minic/runtime/debug/visual/**`

验收：

- 建立 VisualStructure 抽象。
- 建立 GraphStructure、ArrayStructure、CompositeStructure。
- 建立 DataStructureDescriptor、VisualDecorator、VisualValidator 插槽。
- 测试覆盖三类结构的最小实例和摘要。

验证：`./gradlew test`

### E240：实现 GraphStructure

依赖：`E230`。

目标：实现节点和边基元。

允许修改：

- `src/main/java/minic/runtime/debug/visual/**`
- `src/test/java/minic/runtime/debug/visual/**`

验收：

- 支持 nodes、edges、components、layout hint。
- 支持 node/edge decorators。
- 支持点击元数据所需字段。
- 测试覆盖链表、树布局 hint 和离散 component。

验证：`./gradlew test`

### E250：实现 ArrayStructure

依赖：`E230`。

目标：实现连续空间、表格、矩阵和网格基元。

允许修改：

- `src/main/java/minic/runtime/debug/visual/**`
- `src/test/java/minic/runtime/debug/visual/**`

验收：

- 支持 1D、2D、grid、matrix、ring、bucket layout hint。
- 支持 cell decorators。
- 测试覆盖数组、矩阵和循环队列布局元数据。

验证：`./gradlew test`

### E260：实现 CompositeStructure

依赖：`E240`、`E250`。

目标：实现混合结构基元。

允许修改：

- `src/main/java/minic/runtime/debug/visual/**`
- `src/test/java/minic/runtime/debug/visual/**`

验收：

- 支持 parts、links、primaryPartId。
- 支持 component relation explanation。
- 测试覆盖 hash bucket + linked graph、heap array + tree graph。

验证：`./gradlew test`

### E270：解析 @visual 注释协议

依赖：`E230`。

目标：解析源码中的 `@visual` 注释。

允许修改：

- `src/main/java/minic/runtime/debug/visual/**`
- `src/test/java/minic/runtime/debug/visual/**`
- 必要时 `src/main/java/minic/source/**`

验收：

- 支持 `@visual graph`、`@visual array`、`@visual composite`。
- 支持 `@visual-node` 和 `@visual-edge`，首版只接受简单变量名或字面值。
- 相同 name/graph 归并到同一结构。
- root 只允许变量名，复杂表达式报 diagnostic 或 visual warning。
- 测试覆盖多个 visual、离散 component 和非法声明。

验证：`./gradlew test`

### E280：实现 Visual 投影构建器

依赖：`E260`、`E270`。

目标：从虚拟进程空间投影数据结构视图。

允许修改：

- `src/main/java/minic/runtime/debug/visual/**`
- `src/test/java/minic/runtime/debug/visual/**`

验收：

- root 扫描为主，注解事件为辅。
- 相同 name/graph 合并。
- 不连通 component 保留在同一结构中。
- 生成 GraphStructure、ArrayStructure 和 CompositeStructure。
- 测试覆盖数组、链式图、混合结构和离散子图归并。

验证：`./gradlew test`

### E290：实现首批 DataStructureDescriptor

依赖：`E280`。

目标：实现第一批高级结构 descriptor，不包含红黑树。

允许修改：

- `src/main/java/minic/runtime/debug/visual/**`
- `src/test/java/minic/runtime/debug/visual/**`

验收：

- 支持 array、matrix、list、doubly_linked_list、tree、binary_tree、bst、heap、graph、hash_table、union_find。
- descriptor 只影响默认布局、装饰器、校验器和解释，不新增底层数据模型。
- 红黑树不在第一批 descriptor 中，仅保留扩展点。

验证：`./gradlew test`

### E300：实现元数据视图 DTO/模型

依赖：`E220`。

目标：为右侧元数据视图提供 UI 模型。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/ui/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/ui/**`

验收：

- 显示当前状态、停止原因、当前行、当前函数、调用栈、变量、stdout/stderr、断点、事件日志和 snapshot timeline。
- 点击变量可定位进程空间中的值引用。
- 测试覆盖 UI 模型字段和状态转换。

验证：`./gradlew test`

### E310：实现 AST Debug 视图

依赖：`E210`、`E220`。

目标：复用现有 AST 视图并增加 debug 高亮。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/ui/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/ui/**`

验收：

- 当前 debug 节点高亮。
- 点击节点显示节点数据、解释和源码范围。
- 支持从 AST 节点关联到 IR/ASM 映射。

验证：`./gradlew test`

### E320：实现 IR Debug 视图

依赖：`E210`、`E220`。

目标：展示当前 IR 指令、基本块、操作数和运行时值。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/ui/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/ui/**`

验收：

- 当前 IR 指令高亮。
- 展示指令解释和关联源码。
- 操作数和结果能关联到 DebugValue 或值引用。

验证：`./gradlew test`

### E330：实现 ASM Debug 视图

依赖：`E210`、`E220`。

目标：展示生成汇编映射并高亮当前 IR 对应行组。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/ui/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/ui/**`

验收：

- 当前 IR 对应 ASM 行组高亮。
- 视图说明 ASM 是映射展示，不代表真实 CPU 状态。
- 点击 ASM 行能显示关联 IR 和源码范围。

验证：`./gradlew test`

### E340：实现数据结构视图 UI 模型

依赖：`E280`、`E220`。

目标：为数据结构 tab 提供 UI 模型。

允许修改：

- `src/main/java/minic/uiapi/**`
- `src/main/java/minic/ui/**`
- `src/test/java/minic/uiapi/**`
- `src/test/java/minic/ui/**`

验收：

- 显示虚拟进程空间。
- 显示多个 visual cards/tabs。
- 支持 GraphStructure、ArrayStructure、CompositeStructure。
- 节点、边、cell、part 点击后显示元数据和解释。

验证：`./gradlew test`

### E350：Workbench 接入 Debug 模式

依赖：`E300`、`E310`、`E320`、`E330`、`E340`。

目标：将 Debug 模式接入 JavaFX Workbench。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- 侧边栏 Debug 按钮进入独立 Debug 模式。
- 复用共享编辑器。
- 支持 gutter 断点。
- 支持当前执行行高亮。
- 支持 Debug 控制栏。
- 右侧 tabs 包含元数据、数据结构、AST、IR、ASM。
- UI 不直接访问 runtime debug 内部对象。

验证：`./gradlew test`

### E360：支持右侧拆分对比

依赖：`E350`。

目标：支持 AST/IR/ASM/数据结构视图向右拆分对比。

允许修改：

- `src/main/java/minic/ui/**`
- `src/test/java/minic/ui/**`

验收：

- 可将当前 tab 拆分到右侧并保留同步 debug 状态。
- 拆分视图订阅同一个 UiDebugStateDto，不复制 DebugSession。
- 关闭拆分不影响 DebugSession。

验证：`./gradlew test`

### E370：样例、文档和阶段验收

依赖：`E350`。

目标：收口 0.5.0 文档、样例和验收。

允许修改：

- `README.md`
- `PLAN.md`
- `SPEC.md`
- `samples/**`
- `version/0.5.0.md`
- 必要时测试文件

验收：

- 新增 debugger 基础样例。
- 新增数组/矩阵样例。
- 新增链式图样例。
- 新增混合结构样例。
- 新增 `version/0.5.0.md`。
- README 当前状态记录 0.5.0 Debugger 能力。
- `./gradlew test` 通过。

验证：`./gradlew test`
