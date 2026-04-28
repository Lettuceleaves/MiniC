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
- `return`
- 函数定义
- 函数参数
- block
- 局部变量声明
- 赋值
- 整数字面量
- 标识符
- `+ - * /`
- 函数调用
- `//` 行注释

暂不支持：

- 预处理器和头文件
- 指针、数组、struct、union、enum
- 浮点数
- `if`、`while`、`for`
- 多目标平台代码生成
- 优化 pass

## v0.1 语法

```ebnf
program        ::= functionDecl* EOF ;
functionDecl   ::= "int" identifier "(" parameterList? ")" block ;
parameterList  ::= parameter ("," parameter)* ;
parameter      ::= "int" identifier ;
block          ::= "{" statement* "}" ;
statement      ::= varDecl | returnStmt | exprStmt | block ;
varDecl        ::= "int" identifier ("=" expression)? ";" ;
returnStmt     ::= "return" expression? ";" ;
exprStmt       ::= expression ";" ;
expression     ::= assignment ;
assignment     ::= identifier "=" assignment | additive ;
additive       ::= multiplicative (("+" | "-") multiplicative)* ;
multiplicative ::= primary (("*" | "/") primary)* ;
primary        ::= integerLiteral | identifier | callExpr | "(" expression ")" ;
callExpr       ::= identifier "(" argumentList? ")" ;
argumentList   ::= expression ("," expression)* ;
```

## v0.1 语义规则

- 可执行程序必须包含 `main`，且 `main` 返回 `int`。
- 重复函数名是错误。
- 同一作用域内重复局部变量名是错误。
- 变量使用前必须声明。
- 函数调用目标必须存在。
- 实参个数必须匹配形参个数。
- v0.1 中所有表达式类型都是 `int`。
- 未初始化局部变量被读取是运行时错误，生成产物必须保留该检查。
- 除零是运行时错误，生成产物必须保留该检查。

## v0.1 产物约定

- 合法输入最终应生成真实可执行文件；v0.1 最低目标是单平台 `.exe` 产物。
- `main` 的返回值作为进程退出码。
- 后端可以先生成文本汇编，再调用本地工具链完成汇编和链接；v0.1 不要求直接手写 PE/COFF 二进制。
- 编译、汇编、链接和运行时检查相关失败都必须转换为结构化 diagnostics，不能只打印 console。

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
  - `minic.compiler.ir.lowering`：AST 到 IR 的 lowering、后续 IR 构造辅助逻辑。
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
