# MiniC 规范

## 项目目标

MiniC 是一个基于 Java 的 C 语言子集编译器与 debugger，用于个人学习编译原理，并为后续“编译全流程可视化教学”和“可视化运行调试”打基础。

项目需要让以下阶段可观察：

- 源码、token 流、AST、诊断信息
- 符号表、语义信息、IR 或解释执行模型
- 调用栈、栈帧、变量状态、debugger 快照

## 技术选型

- Java 21
- Gradle
- JUnit 5
- AssertJ
- 手写 lexer
- 手写递归下降 parser，表达式复杂后使用 Pratt parsing
- 初期先做 CLI 和结构化调试数据，核心稳定后再做 JavaFX 可视化

早期不使用 ANTLR，不接 LLVM，不做完整 C 标准兼容。

## 语言与文档要求

- 项目文档、执行计划、验收标准、agent 汇报必须使用中文。
- Java 标识符使用英文。
- 测试方法名可以使用英文。
- 代码注释优先中文。
- 用户可见错误信息、CLI 输出、UI 文案优先中文。
- 诊断 code 使用稳定英文编号，例如 `LEX001`、`PAR001`、`SEM001`、`RUN001`。

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
- 原生机器码生成
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
- 未初始化局部变量被读取是运行时错误。
- 除零是运行时错误。

## 架构约束

目标包结构：

```text
minic.source
minic.diagnostics
minic.compiler.lexer
minic.compiler.parser
minic.compiler.ast
minic.compiler.semantic
minic.compiler.ir
minic.runtime.vm
minic.runtime.debug
minic.cli
```

依赖规则：

- compiler 可以依赖 `source` 和 `diagnostics`。
- runtime 可以依赖 AST 或 IR、`source` 和 `diagnostics`。
- CLI/UI 可以依赖 compiler 和 runtime。
- compiler 不能依赖 JavaFX。
- parser 不能依赖 runtime。
- 普通用户代码错误必须转为 diagnostics，不能只打印 console。

核心流水线：

```text
SourceFile
  -> Lexer
  -> Tokens
  -> Parser
  -> AST
  -> SemanticAnalyzer
  -> SemanticResult
  -> Interpreter / IR
  -> RuntimeState / DebuggerSnapshot
```

## 代码风格

- 使用 Java 21。
- AST 和值对象优先使用 records 和 sealed interfaces。
- tokens、AST、diagnostics、source ranges 尽量不可变。
- 避免全局可变状态。
- 每个 public 顶层类型一个文件。
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
- 已更新 `README.md` 中的当前状态，指向下一步任务。
- 已按 Git 规范提交本任务改动。
- 适用时保留源码 range。
- 适用时错误转为 diagnostics。
