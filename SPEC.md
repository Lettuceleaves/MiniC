# MiniC 规范

## 项目目标

MiniC 是一个基于 Java 的 C 语言子集编译器与 debugger，用于个人学习编译原理，并为后续“编译全流程可视化教学”和“可视化运行调试”打基础。

项目需要让以下阶段可观察：

- 源码、token 流、AST、诊断信息
- 符号表、语义信息、IR、汇编/目标文件、链接诊断
- 可执行文件、源码映射、调用栈、变量状态、debugger 快照

## 技术选型

- Java 21
- Gradle
- JUnit 5
- AssertJ
- 手写 lexer
- 手写递归下降 parser，表达式复杂后使用 Pratt parsing
- 初期先做 CLI、结构化编译产物和调试数据，核心稳定后再做 JavaFX 可视化

早期不使用 ANTLR，不接 LLVM，不做完整 C 标准兼容。

v0.1 后端先限定单目标平台，优先 `Windows x86_64`；编译器核心负责生成可汇编、可链接的目标代码，并通过本地工具链产出真实可执行文件。

## 语言与文档要求

- 项目文档、执行计划、验收标准、agent 汇报必须使用中文。
- Java 标识符使用英文。
- 测试方法名可以使用英文。
- 代码注释优先中文。
- 用户可见错误信息、CLI 输出、UI 文案优先中文。
- 诊断 code 使用稳定英文编号，例如 `LEX001`、`PAR001`、`SEM001`、`GEN001`、`TOOL001`。

## Git 规范

- `main` 分支保持可构建、可测试。
- 每个 agent 任务完成后应形成一个小而完整的提交，提交范围只包含该任务相关改动。
- 提交前必须运行对应任务的验证命令；验证失败不得提交，除非提交内容明确记录阻塞原因。
- commit message 使用中文，格式为 `任务编号：简短说明`，例如 `A001：创建 Java Gradle 骨架`。
- 不提交构建产物、IDE 私有配置、临时文件、日志文件和本地环境文件。
- 不使用 `git reset --hard`、强制推送或重写公共历史，除非用户明确要求。
- 修改已有文件前应先查看当前内容，避免覆盖用户或其他 agent 的未提交改动。
- 如果发现与当前任务无关的未提交改动，保留原状，不回滚、不格式化、不顺手修改。

## MiniC v0.1 语言范围

支持：

- `int`
- `extern`
- `return`
- 函数声明和函数定义
- 外部函数声明
- 函数参数
- block
- 局部变量声明
- 赋值
- 整数字面量
- 标识符
- `+ - * /`
- `== != < <= > >=`
- 函数调用
- `//` 行注释
- `if`、`else`
- `while`
- `for`
- `break`、`continue`

暂不支持：

- 预处理器和头文件
- 指针、数组、struct、union、enum
- 浮点数
- 多目标平台代码生成
- 优化 pass

## v0.1 语法

```ebnf
program        ::= functionDecl* EOF ;
functionDecl   ::= external? "int" identifier "(" parameterList? ")" (block | ";") ;
external       ::= "extern" ;
parameterList  ::= parameter ("," parameter)* ;
parameter      ::= "int" identifier ;
block          ::= "{" statement* "}" ;
statement      ::= varDecl | returnStmt | ifStmt | whileStmt | forStmt | breakStmt | continueStmt | exprStmt | block ;
varDecl        ::= "int" identifier ("=" expression)? ";" ;
returnStmt     ::= "return" expression? ";" ;
ifStmt         ::= "if" "(" expression ")" statement ("else" statement)? ;
whileStmt      ::= "while" "(" expression ")" statement ;
forStmt        ::= "for" "(" forInit? ";" expression? ";" expression? ")" statement ;
forInit        ::= varDeclNoSemicolon | expression ;
varDeclNoSemicolon ::= "int" identifier ("=" expression)? ;
breakStmt      ::= "break" ";" ;
continueStmt   ::= "continue" ";" ;
exprStmt       ::= expression ";" ;
expression     ::= assignment ;
assignment     ::= identifier "=" assignment | equality ;
equality       ::= relational (("==" | "!=") relational)* ;
relational     ::= additive (("<" | "<=" | ">" | ">=") additive)* ;
additive       ::= multiplicative (("+" | "-") multiplicative)* ;
multiplicative ::= primary (("*" | "/") primary)* ;
primary        ::= integerLiteral | identifier | callExpr | "(" expression ")" ;
callExpr       ::= identifier "(" argumentList? ")" ;
argumentList   ::= expression ("," expression)* ;
```

## v0.1 语义规则

- 可执行程序必须包含 `main` 定义，且 `main` 返回 `int`。
- 函数名必须匹配 `[A-Za-z][A-Za-z0-9_]*`，不得以下划线开头；保留给运行时或后端的内部符号不得作为用户函数名。
- `main` 函数签名必须是 `int main()`，不允许参数。
- v0.1 用户函数返回类型必须是 `int`，参数类型必须是 `int`。
- 函数声明以 `;` 结束，不携带函数体；函数定义携带 block 函数体。
- 外部函数使用 `extern int name(...);` 声明，进入全局函数符号表，允许无函数体且允许被调用。
- 外部函数声明不能携带函数体。
- 同名函数允许先声明后定义，重复声明必须保持参数数量一致。
- 重复函数定义是错误；v0.1 暂不支持函数重载，因此同名函数声明或定义的参数数量不一致是错误。
- 同一作用域内重复局部变量名是错误。
- 变量使用前必须声明。
- 函数调用目标必须存在。
- 非外部函数仅声明未定义时不可调用；外部函数声明允许未定义调用，后续动态链接任务负责生成可链接调用。
- 实参个数必须匹配形参个数。
- Windows x86_64 目标上，前 4 个 `int` 实参使用 `ecx`、`edx`、`r8d`、`r9d`，第 5 个及之后的 `int` 实参通过调用方栈参数区传递。
- v0.1 中所有表达式类型都是 `int`。
- 比较表达式结果是 `int`，真为 `1`，假为 `0`。
- `if` 条件按 `int` 判断，非 0 为真，0 为假；`else` 绑定最近的未匹配 `if`。
- `else if` 不引入独立语义，按 `else` 分支中的嵌套 `if` 处理。
- `while` 条件按 `int` 判断，非 0 为真，0 为假；条件为真时重复执行循环体，条件为假时退出循环。
- `for` 按 init、condition、body、step、exit 执行；condition 省略时视为恒真，init 中声明的变量作用域限于该 `for`。
- `break` 只能在循环内使用，跳转到最近一层循环的 exit。
- `continue` 只能在循环内使用；在 `while` 中跳转到最近一层循环的 condition，在 `for` 中跳转到最近一层循环的 step。
- 未初始化局部变量被读取是运行时错误，生成产物必须保留该检查。
- 除零是运行时错误，生成产物必须保留该检查。

## v0.1 产物约定

- 合法输入最终应生成真实可执行文件；v0.1 最低目标是单平台 `.exe` 产物。
- v0.1 首个目标平台是 `Windows x86_64`，早期汇编文本输出采用 MASM 风格；新增平台必须先补充目标平台抽象和产物测试。
- `main` 的返回值作为进程退出码。
- 后端可以先生成文本汇编，再调用本地工具链完成汇编和链接；v0.1 不要求直接手写 PE/COFF 二进制。
- 编译、汇编、链接和运行时检查相关失败都必须转换为结构化 diagnostics，不能只打印 console。

## 扩展类型系统目标

在进入 debugger 之前，MiniC 需要补齐基本可用的 C 子集类型系统。本阶段确认纳入：

- `bool`
- 有符号 `char`
- `int`
- `long`
- `float`
- `double`
- `NULL`

本阶段暂不纳入：

- `byte` 关键字。C 子集优先使用有符号 `char` 表达 8-bit 整数。
- `unsigned` 类型族。
- `short`、`long long`。
- `void` 普通变量类型。若函数指针或外部函数需要 `void` 返回值，应作为独立任务讨论。

类型约束初稿：

- `bool` 表示布尔值，条件表达式可接受任意标量和指针，结果按非零或非空判断。
- `char` 固定为有符号 8-bit 整数，避免依赖宿主 C 编译器的实现定义行为。
- `int` 固定为有符号 32-bit 整数。
- `long` 在 Windows x64 目标上固定为有符号 64-bit 整数，以便教学上表达宽整数；这不同于 MSVC C 的 32-bit `long`，需在目标 ABI 文档中显式记录。
- `float` 为 32-bit IEEE 754 单精度浮点数。
- `double` 为 64-bit IEEE 754 双精度浮点数。
- `NULL` 是空指针常量，可赋给任意指针类型、作为指针实参传递、参与指针相等/不等比较；不得作为结构体或浮点值使用。
- 整型算术先执行整数提升：`bool`、`char` 提升为 `int`。
- `int` 与 `long` 运算结果为 `long`。
- 浮点参与二元算术时，整数操作数转换为浮点；`float` 与 `double` 运算结果为 `double`。
- 比较表达式结果为 `bool`。
- 赋值、返回值和函数实参允许按明确规则转换；有损转换默认作为语义错误处理，后续如需 warning 级诊断再单独扩展。
- Windows x64 代码生成必须分别处理通用寄存器和 SSE 浮点寄存器的参数、返回值、load/store 和算术比较。

## 架构约束

目标包结构：

```text
minic.source
minic.diagnostics
minic.compiler.lexer
minic.compiler.parser
minic.compiler.ast
minic.compiler.ast.decl
minic.compiler.ast.expr
minic.compiler.ast.stmt
minic.compiler.semantic
minic.compiler.ir
minic.compiler.ir.model
minic.compiler.ir.value
minic.compiler.ir.instruction
minic.compiler.ir.lowering
minic.compiler.codegen
minic.compiler.toolchain
minic.runtime.debug
minic.cli
```

项目结构规范：

- 根包只放该阶段的入口、门面或少量稳定聚合类型；当同类 public 顶层类型超过约 8 个时，应按职责拆子包。
- AST 按职责拆分：
  - `minic.compiler.ast.decl`：`Program`、函数、参数、后续全局/类型声明。
  - `minic.compiler.ast.expr`：表达式基接口和表达式节点。
  - `minic.compiler.ast.stmt`：语句基接口和语句节点。
- IR 按职责拆分：
  - `minic.compiler.ir.model`：模块、函数、基本块、类型、局部变量和形参等结构模型。
  - `minic.compiler.ir.value`：常量、临时值、引用值等可作为操作数的值。
  - `minic.compiler.ir.instruction`：所有 IR 指令和指令操作符。
  - `minic.compiler.ir.lowering`：AST 到 IR 的 lowering；函数级 builder、语句 lowering、表达式 lowering 和控制流 lowering 应按职责拆分。
- Parser 按职责拆分：
  - 入口 `Parser` 只负责编排 token 流、组装 `ParseResult` 和 `Program` 范围。
  - 声明、语句、表达式解析分别放入独立 parser 类；新增语法优先扩展对应职责类。
  - token 游标、消费、诊断和错误恢复集中在共享 parser state 中。
- Semantic 按职责拆分：
  - 入口 `SemanticAnalyzer` 只负责编排符号收集和 AST 遍历。
  - 函数声明/定义、外部函数、调用解析等全局函数规则由函数注册组件维护。
  - 语句分析、表达式分析和后续类型检查应拆到独立组件。
- Windows x86_64 codegen 按职责拆分：
  - 顶层 emitter 只负责编排模块、函数和汇编段结构。
  - 调用约定、栈帧布局、IR 指令输出、IR 值装载分别放入独立组件。
- 测试包结构应尽量镜像生产代码包结构，避免测试根目录继续堆积同类文件。
- 新增语言能力时优先放入已有职责子包；只有出现新的稳定职责边界时才新增子包。

依赖规则：

- compiler 前端可以依赖 `source` 和 `diagnostics`。
- IR、codegen 和 toolchain 层可以依赖 AST 或 IR、`source` 和 `diagnostics`。
- debugger 可以依赖编译产物、源码映射、`source` 和 `diagnostics`。
- CLI/UI 可以依赖 compiler、toolchain 和 debugger。
- compiler 不能依赖 JavaFX。
- parser 不能依赖 codegen、toolchain 或 debugger。
- 普通用户代码错误、工具链失败和运行时检查失败都必须转为 diagnostics，不能只打印 console。

`0.2.0` 结构化观测分为四层：

- 编译层：`minic.compiler.*` 内部提供可步进状态，负责真实编译动作和阶段输出构建。
- 兼容层：`minic.runtime.step.*` 适配各编译阶段，统一暴露 `next`、`previous` 预留、`snapshot`、`data`、`canNext`、`canPrevious`，并只返回 UI 可消费数据。
- 调度层：`minic.session.*` 串联各阶段 stepper，维护全局步骤游标、播放状态、当前状态数据、当前阶段数据和全局数据。
- UI API 层：`minic.uiapi.*` 提供稳定门面和 DTO，不暴露 compiler、runtime stepper 或 session 内部对象，不依赖 JavaFX。

`0.2.0` 四层依赖规则：

- 编译层不得依赖兼容层、调度层或 UI API。
- 兼容层可以依赖编译层公开阶段状态，但不得向外暴露编译层内部可变 work 数据。
- 调度层可以依赖兼容层 stepper 和阶段输出缓存，但不得依赖 UI API。
- UI API 只能依赖调度层和运行时稳定 DTO 输入来源，向 UI 返回 `Ui*Dto`，不得返回 compiler、runtime stepper 或 session 类型。
- JavaFX 或其他具体 UI 框架不得进入 compiler、runtime、session、uiapi；后续 UI 应在独立包或应用层适配 `uiapi`。

`0.2.0` 数据区设计：

- 当前状态数据描述全局游标：源码名、当前阶段、全局/阶段步骤下标、播放模式、帧间隔、源码范围、标题、说明、diagnostics 和控制能力。
- 当前阶段数据描述当前 tab 或阶段面板：阶段进度、输入摘要、当前项、累计输出摘要和阶段 diagnostics。
- 全局数据描述跨阶段概览：源码文本、阶段摘要、全量 diagnostics、token/AST/semantic/IR/assembly/artifact 摘要。
- UI DTO 必须不可变，并使用字符串、数字、布尔值和只读列表等易绑定字段，避免把 AST、IR、Scope、Stepper 等内部对象暴露给 UI。

`0.3.1` 阶段图形化数据约束：

- UI API 提供 `currentStageVisualData()`，返回 `UiStageVisualDto` 聚合模型。
- Lexer visual data 使用 token range 生成 UI 可对齐字段：token kind、token text、start/end offset、1-based 起止行列和 active 标记。
- Parser visual data 使用 UI 专用 AST 树 DTO：节点 id、label、kind、source range、active 标记和 children，不暴露真实 AST 对象。
- Semantic visual data 使用 UI 专用作用域树 DTO：根节点为 `global scope`，节点包含符号摘要、children、active 标记，并用布尔字段表达 child -> parent 的反向箭头语义，不暴露真实 Scope 或 Symbol 对象。
- Codegen visual data 使用 UI 专用 assembly 行 DTO：稳定行号、文本、kind、section、label 和 active 标记，不暴露 emitter、frame layout 或 codegen work 对象。
- JavaFX Visual Pane 根据 visual type 自动切换 Lexer、AST、Semantic、Assembly 和 generic fallback 视图；UI 层仍只依赖 `minic.uiapi` DTO。

## MiniC v0.4 语言和预编译范围

`0.4.0` 在既有类型、数组、指针和结构体能力上补充以下能力：

- 预编译阶段先于 lexer/parser 执行，支持 `#include "xxx.mh"`、对象宏、`#ifdef/#ifndef/#else/#endif`、`#undef`。
- include 文件限定为 `.mh`，用于 MiniC 头文件；头文件可包含函数声明、外部函数声明、结构体声明、宏定义和条件编译块，不允许函数定义或可执行语句。
- 标准方向 `printf` 原型为 `extern int printf(char *format, ...);`。旧写法 `extern int printf(int format, int value);` 虽是合法 C 函数声明，但不是标准库 `printf` 签名，不作为 MiniC 标准样例。
- 表达式新增：`-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`, `%`, `&`, `|`, `^`, `~`, `!`, `&&`, `||`, `<<`, `>>`, `?:`, `++`, `--`, `sizeof expression`, `sizeof(type)`。
- 控制流新增：`do while`、`switch case default`。`break` 可用于循环或 `switch`，`continue` 仍只用于循环。

`0.4.0` 表达式 lowering 语义：

- `&&`、`||` 和 `?:` 使用显式控制流 lowering；右操作数或未选分支不会提前求值，保留 C 标准短路语义和副作用顺序。

核心流水线：

```text
SourceFile
  -> Lexer
  -> Tokens
  -> Parser
  -> AST
  -> SemanticAnalyzer
  -> SemanticResult
  -> IR
  -> CodeGenerator
  -> Assembly / ObjectFile
  -> Linker
  -> ExecutableArtifact
  -> DebugInfo / DebuggerSnapshot
```

## 代码风格

- 使用 Java 21。
- AST 和值对象优先使用 records；sealed interface 只在直接实现类型位于同一包或项目引入明确 Java module 边界时使用，跨子包的节点基接口使用普通 interface。
- tokens、AST、diagnostics、source ranges 尽量不可变。
- 避免全局可变状态。
- 每个 public 顶层类型一个文件。
- 源码中的 public 顶层类型、public 构造方法和 public 方法必须使用标准 JavaDoc 注释，说明用途、参数、返回值和关键不变量；测试代码可不强制。
- 4 空格缩进。
- 不使用 wildcard imports。
- 只在解释设计意图或不变量时写注释。

## 测试与验收

- 测试框架使用 JUnit 5 + AssertJ。
- 每个非文档 agent 任务必须至少包含一个测试，除非明确说明原因。
- 最低验证命令是 `./gradlew test`；Gradle wrapper 尚未存在时，按 `PLAN.md` 中的 fallback 执行。
- 每次任务完成必须汇报：任务编号、修改文件、验证命令、验证结果、git commit、已知限制。

测试策略目标：

- 测试应保护用户可见行为和跨阶段契约，避免把临时实现细节固化为大量脆弱断言。
- 新增语言能力时，优先新增该能力的聚焦测试；只有稳定契约确实变化时才修改旧测试。
- 旧测试失败时应先判断是行为回归还是测试过度绑定内部细节。若属于后者，应调整测试粒度，并在本任务中说明原因。
- 测试覆盖应随风险分层：语法能力新增至少覆盖 lexer/parser/semantic/IR 或 codegen 中直接受影响的层；只有涉及真实产物链路时才补可执行文件样例验证。

分层测试职责：

- Lexer 测试验证 token 分类、lexeme、range、diagnostics。关键字扩展使用独立小用例验证新增关键字，不要求每次修改聚合型“所有关键字”断言。
- Parser 测试验证新增语法节点的结构、关键子节点和 source range。不要在无关旧用例中断言完整 AST 细节。
- Semantic 测试验证新增语义规则、作用域边界和 diagnostics。诊断顺序只有在语义上稳定时才使用严格顺序断言，否则验证诊断集合或关键消息。
- IR lowering 测试验证控制流和数据流契约。控制流语法应断言关键 block 存在、关键跳转目标正确；只有 block 顺序本身是契约时才使用完整顺序断言。
- Codegen 测试验证汇编中的关键指令、标签和调用约定片段。避免把完整汇编文本作为黄金文件，除非该测试明确用于输出格式稳定性。
- Pipeline/CLI 测试验证阶段串联、产物路径、diagnostics 聚合和用户可见输出，不重复覆盖各子阶段内部细节。
- 样例程序用于端到端行为验收，预期结果以退出码或 diagnostics 为准；样例数量应覆盖新增能力的代表路径，不追求穷举。

断言粒度规范：

- 新增枚举值时，避免维护需要反复改动的全量枚举顺序测试；优先使用 `contains` 或新增值的独立断言。只有序列化、ABI、CLI 输出依赖枚举顺序时才断言完整顺序。
- 新增关键字时，新增该关键字与相似标识符的测试，例如 `for` 与 `forValue`；不要要求每个关键字扩展都修改一个越来越长的聚合用例。
- 新增语句或表达式时，parser 测试应聚焦该节点的类型、关键字段和 range；不应为了新增语法而改动已有语法的解析测试。
- 控制流 lowering 的测试应优先验证语义边：入口到条件、条件到 body/exit、body 到 step/condition 等。自动生成标签的数字后缀不是长期稳定契约，测试中只在当前任务需要定位时断言。
- 汇编测试应使用包含式断言验证关键片段。新增控制流不应要求更新无关旧汇编测试。
- 端到端 exe 验证属于任务验收记录，生成的 `.asm/.obj/.exe` 默认不提交；README 只记录长期样例的运行方式和预期退出码。

推荐测试新增顺序：

1. 为新增语法或语义添加最小 parser/semantic 单元测试。
2. 为跨阶段行为添加 IR 或 codegen 测试，优先选择能暴露控制流或调用约定错误的断言。
3. 为高风险用户路径添加一个样例程序，并在本地生成可执行文件验证退出码。
4. 若新增能力只影响局部组件，不强制补全所有层级测试。

通用验收清单：

- 只完成一个任务编号。
- 未扩大语言范围。
- 未修改无关文件。
- 行为变化有测试覆盖。
- 代码可编译，测试通过。
- 已清理本任务产生的临时文件、临时脚本、下载中间包和一次性构建输出；JDK、Gradle、Gradle Wrapper 分发包缓存、Maven/Gradle 依赖缓存等可复用本地工具和缓存可保留但不得提交，必须保留的生成文件需在任务汇报中说明原因。
- 已更新 `README.md` 中的当前状态，指向下一步任务。
- 已按 Git 规范提交本任务改动。
- 适用时保留源码 range。
- 适用时错误转为 diagnostics。
