# MiniC Agent 执行计划

当前开发阶段：`0.4.0`。

下一步任务：待定。

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

## 0.4.0 目标边界

本阶段准备从现有 v0.1 C 子集继续扩展语法能力，并同步更新旧语法规范。扩展前必须先确认范围，避免一次性纳入过多 C 标准特性导致 parser、semantic、IR 和 codegen 的任务边界失控。

本阶段确认纳入：

- 常用运算符和表达式优先级：`-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`, `%`, `&`, `|`, `^`, `~`, `!`, `&&`, `||`, `<<`, `>>`, `?:`, `++`, `--`。
- pipeline 前置预编译步骤：正式 lexer/parser 前先处理 include、宏定义、条件编译，并把预编译诊断并入编译诊断链路。
- 轻量预处理宏语法：`#include "xxx.mh"`、`#define NAME value`、`#define NAME`、`#ifdef NAME`、`#ifndef NAME`、`#else`、`#endif`、`#undef NAME`。
- MiniC 头文件语法：include 进来的文件只能使用 `.mh` 后缀，允许放置类似 `.h` 的函数声明、结构体声明、宏定义和条件编译块，但不得包含函数定义或可执行语句。
- C 风格 `printf` 所需声明语法：字符串字面量作为 `char *`，函数声明支持 `...` 可变参数，样例中的旧写法 `extern int printf(int format, int value);` 应移除，改为 `extern int printf(char *format, ...);`。
- 控制流和类型查询语法：`do while`、`switch case default`、`sizeof`。

本阶段暂不纳入：

- 函数式宏、`#if/#elif` 表达式、系统头文件搜索路径、`.h` 文件 include 和完整 C 预处理器。
- 完整标准库头文件建模；`printf` 先作为外部符号声明和 Windows x64 调用约定样例处理。
- `const`、`restrict`、`unsigned`、`void` 全量语义，除非实现 `printf` 原型时确需最小支持再单独拆任务确认。

## Phase D 0.4.0：C 子集语法和预编译扩展

执行顺序原则：

- 先改规范和样例方向，再实现 pipeline 结构。
- 预编译阶段先做“直通可观测”，再分别实现 include、对象宏、条件编译和 `.mh` 头文件约束。
- 语法能力按 lexer -> parser/AST -> semantic -> IR/codegen 分层推进，避免一次任务横跨过多层。
- `printf` 修复不作为临时绕过：必须通过 `char *`、字符串字面量、`...` 可变参数声明和 Windows x64 调用约定形成可解释闭环。

### D100：更新语法扩展规范和 printf 原型方向（已完成）

依赖：`0.3.1` 已完成。

目标：更新 `SPEC.md` 和样例规划，明确本阶段语法扩展范围，并把旧 `printf` 声明方向修正为更符合 C 的可变参数外部函数原型。

允许修改：

- `README.md`
- `PLAN.md`
- `SPEC.md`
- `samples/printf.mc`

验收：

- PLAN 清除旧阶段任务，并记录 Phase D 任务拆分。
- README 当前状态指向 `D100`。
- SPEC 记录本阶段要支持的 pipeline 预编译步骤、`.mh` include 限制、宏语法、MiniC 头文件语法、运算符、`do while`、`switch case`、`sizeof`。
- SPEC 明确 `extern int printf(int format, int value);` 虽是合法 C 函数声明，但不是标准 `printf` 签名；MiniC 样例应改用 `extern int printf(char *format, ...);`。
- `samples/printf.mc` 不再使用 `int format` 伪签名。

验证：文档任务，无需运行测试。

### D110：建立预编译阶段数据模型和 pipeline 插槽（已完成）

依赖：`D100`。

目标：在正式 lexer/parser 前建立 preprocess 阶段的最小数据模型，并把 `MiniCompiler` pipeline 改为 preprocess -> lexer -> parser。

允许修改：

- `src/main/java/minic/compiler/preprocess/**`
- `src/main/java/minic/compiler/pipeline/**`
- `src/test/java/minic/compiler/preprocess/**`
- `src/test/java/minic/compiler/pipeline/**`

验收：

- 新增 `PreprocessResult` 或等价模型，包含预编译后的 `SourceFile`、diagnostics、include 摘要和宏摘要。
- 新增 `Preprocessor` 或等价入口，初始实现可直通源码。
- `MiniCompiler` 必须先调用预编译入口，再把预编译后的源码交给 lexer。
- 预编译 diagnostics 非空时 pipeline 停止在 lexer 之前，并返回结构化 diagnostics。
- 现有无宏源码行为保持兼容。

验证：`./gradlew test`

### D111：补全 Phase D lexer token（已完成）

依赖：`D110`。

目标：让 lexer 能识别新增运算符、控制流关键字、`sizeof` 和 `...`。

允许修改：

- `src/main/java/minic/compiler/lexer/**`
- `src/test/java/minic/compiler/lexer/**`

验收：

- 识别复合赋值：`-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`。
- 识别新增运算符：`%`, `&`, `|`, `^`, `~`, `!`, `&&`, `||`, `<<`, `>>`, `?`, `:`, `++`, `--`。
- 识别关键字：`do`、`switch`、`case`、`default`、`sizeof`。
- 识别 `...`，并能区分 `.`、`..` 非法或不完整省略号。
- 新增 token 测试覆盖相似标识符边界，例如 `switchValue` 仍是 identifier。

验证：`./gradlew test`

### D120：实现 .mh include 解析和文件加载（已完成）

依赖：`D110`。

目标：在预编译阶段支持 `#include "xxx.mh"`，并拒绝非 `.mh` 后缀。

允许修改：

- `src/main/java/minic/compiler/preprocess/**`
- `src/main/java/minic/compiler/pipeline/**`
- `src/test/java/minic/compiler/preprocess/**`
- 必要时 `src/main/java/minic/runtime/step/**`

验收：

- 支持 `#include "xxx.mh"`，include 目标必须是 `.mh` 后缀；`.h`、`.inc` 或其他后缀一律报 diagnostic。
- `.mh` 查找限定为源文件相邻路径或编译选项显式 include 根目录。
- 支持嵌套 include，并检测 include 循环。
- include 展开顺序稳定，重复 include 暂不自动去重；后续可由 include guard 宏控制。
- include 文件读取失败必须转为 diagnostic。
- 预编译结果记录 include 列表，包含路径、来源行和展开状态。
- 预编译后的源码保留主文件与 `.mh` 内容的可诊断来源信息。

验证：`./gradlew test`

### D121：实现对象宏定义、取消定义和替换（已完成）

依赖：`D120`。

目标：支持 `#define NAME value`、`#define NAME`、`#undef NAME` 的对象宏。

允许修改：

- `src/main/java/minic/compiler/preprocess/**`
- `src/test/java/minic/compiler/preprocess/**`

验收：

- 支持 `#define NAME value`、`#define NAME`、`#undef NAME`。
- `#define NAME` 作为空宏或 presence 宏，可用于 `#ifdef/#ifndef`。
- 宏名必须符合 MiniC identifier 规则。
- 宏替换限定为对象宏 token 序列替换，不支持函数宏。
- 替换只发生在普通源码 token 中，不替换字符串字面量内部内容。
- 递归宏、直接自引用宏必须受限并给出 diagnostic 或保持不展开，避免无限循环。

验证：`./gradlew test`

### D122：实现条件编译块（已完成）

依赖：`D121`。

目标：支持 `#ifdef/#ifndef/#else/#endif` 条件包含。

允许修改：

- `src/main/java/minic/compiler/preprocess/**`
- `src/test/java/minic/compiler/preprocess/**`

验收：

- 支持 `#ifdef/#ifndef/#else/#endif` 条件包含，并对未闭合条件块给出 diagnostic。
- 嵌套条件编译正确工作。
- 同一条件块多个 `#else` 报 diagnostic。
- 多余 `#endif` 或孤立 `#else` 报 diagnostic。
- 被排除分支不参与普通源码输出，也不触发 lexer/parser 诊断。
- 预编译后的源码必须保留足够的来源信息或 diagnostics range，后续 UI/CLI 能解释错误来自主文件还是 `.mh` 文件。

验证：`./gradlew test`

### D125：校验 MiniC 头文件语法（已完成）

依赖：`D122`。

目标：允许 `.mh` 文件承载类似 C `.h` 的声明内容，并禁止头文件中出现实现体或可执行语句。

允许修改：

- `src/main/java/minic/compiler/preprocess/**`
- `src/main/java/minic/compiler/parser/**`
- `src/main/java/minic/compiler/semantic/**`
- `src/test/java/minic/compiler/preprocess/**`
- `src/test/java/minic/compiler/parser/**`
- `samples/**`

验收：

- `.mh` 文件允许函数声明、外部函数声明、结构体声明、宏定义、条件编译块。
- `.mh` 文件禁止函数定义、顶层可执行语句和非声明内容，并给出 diagnostic。
- 主 `.mc` 文件 include `.mh` 后可使用其中的声明。
- `.mh` 中声明参与 parser/semantic，但不得生成函数体 IR。
- `printf` 等运行库声明可放入 `.mh` 文件，例如 `extern int printf(char *format, ...);`。
- 新增 `samples/minic_std.mh` 或等价样例头文件，集中放置 `printf` 这类运行库声明。

验证：`./gradlew test`

### D130：支持可变参数函数声明和 printf 原型（已完成）

依赖：`D111`、`D125`。

目标：支持 `extern int printf(char *format, ...);` 这类 C 风格可变参数外部函数声明，并更新旧 `printf` 样例。

允许修改：

- `src/main/java/minic/compiler/parser/**`
- `src/main/java/minic/compiler/ast/**`
- `src/main/java/minic/compiler/semantic/**`
- `src/main/java/minic/compiler/ir/**`
- `src/test/java/minic/compiler/parser/**`
- `src/test/java/minic/compiler/ast/**`
- `src/test/java/minic/compiler/semantic/**`
- `samples/**`

验收：

- lexer/parser 支持 `...` 出现在函数参数列表末尾。
- 函数声明 AST/符号表能表达 variadic 标记。
- variadic 函数调用允许实参数量大于等于固定参数数量。
- 固定参数继续做类型检查，额外参数按普通表达式分析并交给调用约定。
- `samples/printf.mc` 或等价样例不再使用 `extern int printf(int format, int value);`。
- 明确记录：`extern int printf(int format, int value);` 是合法 C 声明，但不是标准库 `printf` 原型，因此不能作为 MiniC 标准样例。

验证：`./gradlew test`

### D131：补全表达式 parser 和 AST（已完成）

依赖：`D111`。

目标：按 C 优先级补全新增运算符、条件表达式和 `sizeof` 的 parser/AST。

允许修改：

- `src/main/java/minic/compiler/parser/**`
- `src/main/java/minic/compiler/ast/**`
- `src/test/java/minic/compiler/parser/**`
- `src/test/java/minic/compiler/ast/**`

验收：

- 按 C 优先级解析：后缀、自增自减、一元、乘除余、加减、移位、关系、相等、按位与/异或/或、逻辑与/或、条件、赋值。
- 支持复合赋值并在 AST 中保留可解释结构。
- 支持 `sizeof expression` 和 `sizeof(type)`。
- `?:` 右结合，赋值右结合。
- parser 测试覆盖优先级组合而不是只测单个 token。

验证：`./gradlew test`

### D132：补全表达式语义规则（已完成）

依赖：`D131`。

目标：为新增表达式补充类型规则、左值规则和 diagnostics。

允许修改：

- `src/main/java/minic/compiler/semantic/**`
- `src/test/java/minic/compiler/semantic/**`

验收：

- 逻辑运算接受标量或指针，结果为 `int` 或后续规范确认的 bool/int。
- 按位运算和移位只接受整数类型。
- `!` 接受标量或指针，`~` 只接受整数类型。
- 自增自减和复合赋值要求左侧为可赋值表达式。
- `sizeof` 只接受固定布局类型，结果类型暂定为 `long`。
- 条件表达式分支类型按 MiniC 类型兼容规则合并，不兼容时报 diagnostic。

验证：`./gradlew test`

### D133：补全表达式 IR lowering 和 codegen（已完成）

依赖：`D132`。

目标：让新增表达式能生成可运行 IR/汇编。

允许修改：

- `src/main/java/minic/compiler/ir/**`
- `src/main/java/minic/compiler/codegen/**`
- `src/test/java/minic/compiler/ir/**`
- `src/test/java/minic/compiler/codegen/**`
- `samples/**`

验收：

- 新增二元/一元运算生成正确 IR 和关键汇编片段。
- `sizeof` 对固定布局类型生成常量结果。
- `&&` 和 `||` 使用控制流 lowering，保留右操作数短路副作用。
- `?:` 生成正确控制流，保留未选分支短路副作用。
- 复合赋值和自增自减生成正确 store。

验证：`./gradlew test`

### D140：补全 do while parser/semantic/IR（已完成）

依赖：`D111`、`D132`。

目标：支持 `do while` 的 AST、语义和 IR lowering。

允许修改：

- `src/main/java/minic/compiler/parser/**`
- `src/main/java/minic/compiler/ast/**`
- `src/main/java/minic/compiler/semantic/**`
- `src/main/java/minic/compiler/ir/**`
- `src/test/java/minic/compiler/parser/**`
- `src/test/java/minic/compiler/semantic/**`
- `src/test/java/minic/compiler/ir/**`

验收：

- `do while` 至少执行一次循环体。
- 条件表达式类型规则与 `while` 一致。
- `break` 和 `continue` 在 `do while` 内合法。
- `continue` 跳转到条件检查。
- IR 控制流包含 body -> condition -> body/exit 的边。

验证：`./gradlew test`

### D141：补全 switch case parser/semantic（已完成）

依赖：`D111`、`D132`。

目标：支持 `switch case default` 的 AST 和语义规则。

允许修改：

- `src/main/java/minic/compiler/parser/**`
- `src/main/java/minic/compiler/ast/**`
- `src/main/java/minic/compiler/semantic/**`
- `src/test/java/minic/compiler/parser/**`
- `src/test/java/minic/compiler/semantic/**`

验收：

- `switch` selector 必须是整数类型。
- `case` 表达式必须是整数常量表达式；若暂不实现完整常量折叠，至少支持整数字面量和可安全求值的简单常量。
- `default` 最多一个。
- `break` 在循环和 `switch` 内合法，`continue` 仍只在循环内合法。
- case 之间允许 C 风格 fallthrough。

验证：`./gradlew test`

### D142：补全 switch case IR lowering 和 codegen（已完成）

依赖：`D141`。

目标：让 `switch case default` 生成正确控制流。

允许修改：

- `src/main/java/minic/compiler/ir/**`
- `src/main/java/minic/compiler/codegen/**`
- `src/test/java/minic/compiler/ir/**`
- `src/test/java/minic/compiler/codegen/**`
- `samples/**`

验收：

- selector 只求值一次。
- 每个 case 生成比较和跳转。
- 无匹配 case 时跳转到 default；没有 default 时跳转到 switch exit。
- case fallthrough 按源码顺序执行。
- `break` 跳转到 switch exit。

验证：`./gradlew test`

### D160：端到端样例、SPEC 和阶段验收（已完成）

依赖：`D122`、`D125`、`D130`、`D133`、`D140`、`D142`。

目标：收口 Phase D 的文档、样例和端到端验证。

允许修改：

- `README.md`
- `PLAN.md`
- `SPEC.md`
- `samples/**`
- `version/0.4.0.md`
- 必要时测试文件

验收：

- README 当前状态记录 `0.4.0` 语法扩展能力。
- SPEC 更新完整语法片段、预编译阶段、`.mh` 头文件约束和 `printf` 声明说明。
- 新增 `version/0.4.0.md` 阶段记录。
- 样例覆盖 `.mh` include、宏、条件编译、`printf`、新增运算符、`do while`、`switch`、`sizeof`。
- `./gradlew test` 通过。

验证：`./gradlew test`
