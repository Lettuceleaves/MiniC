# MiniC Agent 执行计划

规则：

- 每次 agent 只执行一个任务编号，除非用户明确批准合并。
- 不允许跳过依赖。
- 每个任务必须运行验证命令。
- 如果无法验证，停止并汇报阻塞点。
- 每个任务完成后汇报：任务编号、修改文件、验证命令、验证结果、已知限制。

## Phase 0：仓库基础

### A000：初始化 Git 仓库

依赖：无。

目标：在项目根目录创建 git 仓库。

允许修改：`.git/`，可选 `.gitignore`。

验收：`git status --short --branch` 可以运行；不添加源码。

验证：

```text
git status --short --branch
```

### A001：创建 Java Gradle 骨架

依赖：A000。

目标：添加最小 Java 21 Gradle 项目。

允许修改：`settings.gradle`、`build.gradle`、`src/main/java/minic/Main.java`、`src/test/java/minic/MainTest.java`、`.gitignore`。

验收：Gradle test task 可以运行；Java toolchain 设置为 21；一个基础测试通过。

验证：

```text
./gradlew test
```

wrapper 尚未创建时，Windows fallback：

```text
gradle test
```

### A002：添加 Gradle Wrapper

依赖：A001。

目标：添加 `gradlew`、`gradlew.bat`、`gradle/wrapper/*`。

验收：`./gradlew test` 可以运行。

验证：

```text
./gradlew test
```

## Phase 1：源码位置与诊断

### A010：添加源码位置模型

依赖：A002。

目标：实现 `SourceFile`、`SourcePosition`、`SourceRange`。

验收：offset 可映射到从 1 开始的 line/column；range 使用半开区间；测试覆盖单行和多行输入。

验证：`./gradlew test`

### A011：添加诊断模型

依赖：A010。

目标：实现 `Diagnostic`、`DiagnosticSeverity`。

验收：diagnostic 保存 code、severity、message、source range；测试覆盖构造和访问。

验证：`./gradlew test`

## Phase 2：Lexer

### A020：添加 Token 模型

依赖：A011。

目标：实现 `Token`、`TokenKind`、`LexResult`。

验收：token 保存 kind、lexeme、range、可选 literal value；token kind 覆盖 v0.1。

验证：`./gradlew test`

### A021：识别空白、EOF 和单字符 token

依赖：A020。

目标：实现初始 `Lexer`，支持 whitespace、EOF、`+ - * / = ( ) { } ; ,`。

验收：whitespace 被跳过；EOF token 被产出；token range 正确。

验证：`./gradlew test`

### A022：识别标识符和关键字

依赖：A021。

目标：支持 identifiers、`int`、`return`。

验收：关键字不作为 identifier；identifier range 正确；测试覆盖下划线和首字符后的数字。

验证：`./gradlew test`

### A023：识别整数字面量

依赖：A022。

目标：支持十进制整数。

验收：保留 lexeme；保存 Java int value；测试覆盖 `0`、单数字、多数字。

验证：`./gradlew test`

### A024：识别行注释和非法字符

依赖：A023。

目标：支持 `//` 行注释和词法 diagnostics。

验收：行注释跳过到换行或 EOF；非法字符产出 `LEX001`；可行时继续 lexing。

验证：`./gradlew test`

## Phase 3：Parser 与 AST

### A030：添加 Program 和声明 AST

依赖：A024。

目标：定义 `Program`、`FunctionDecl`、`Parameter`。

验收：节点不可变并携带 source range；本任务不添加 parser 行为。

验证：`./gradlew test`

### A031：添加 Parser Result 和基础函数解析

依赖：A030。

目标：实现 `Parser`、`ParseResult`，解析空程序和函数声明。

验收：可解析 `int main() {}` 和参数；语法错误产出 diagnostics。

验证：`./gradlew test`

### A032：添加语句 AST 和解析

依赖：A031。

目标：解析 block、变量声明、return、表达式语句。

验收：可解析变量声明和 return；statement nodes 携带 source range。

验证：`./gradlew test`

### A033：添加表达式 AST 和解析

依赖：A032。

目标：解析 integer、name、assignment、binary arithmetic、括号表达式、函数调用。

验收：优先级符合 `SPEC.md`；函数调用可解析参数；测试覆盖优先级和赋值结合性。

验证：`./gradlew test`

## Phase 4：语义分析

### A040：添加符号模型

依赖：A033。

目标：实现 `Symbol`、`SymbolKind`、`Scope`。

验收：支持 define/resolve；可检测同 scope 重复定义；支持 parent scope lookup。

验证：`./gradlew test`

### A041：添加函数和变量解析

依赖：A040。

目标：实现 `SemanticAnalyzer`、`SemanticResult`。

验收：报告重复函数、重复局部变量、未解析变量、未解析函数调用。

验证：`./gradlew test`

### A042：添加 v0.1 语义规则

依赖：A041。

目标：执行 `SPEC.md` 中的 v0.1 语义规则。

验收：报告缺少 `main`、实参数量错误、int 函数中无表达式 return。

验证：`./gradlew test`

## Phase 5：解释执行

### A050：添加运行时值和状态模型

依赖：A042。

目标：实现 `Value`、`Frame`、`RuntimeState`、`ExecutionStatus`。

验收：runtime state 可表示调用栈和 locals；测试覆盖 state construction。

验证：`./gradlew test`

### A051：执行 return 字面量和算术

依赖：A050。

目标：实现最小 `Interpreter`、`ExecutionResult`。

验收：可执行 `int main() { return 1; }`；可执行算术；除零报告 `RUN001`。

验证：`./gradlew test`

### A052：执行变量和赋值

依赖：A051。

目标：支持局部变量和赋值。

验收：可执行初始化变量和赋值；读取未初始化变量报告 `RUN002`。

验证：`./gradlew test`

### A053：执行函数调用

依赖：A052。

目标：支持用户定义函数调用。

验收：实参正确绑定形参；返回值传回调用方；call stack 可观察。

验证：`./gradlew test`

## Phase 6：编译器链路闭环

### A060：添加编译管线入口和结果模型

依赖：A053。

目标：实现统一编排入口，串联 lexer、parser、semantic 和 interpreter，并返回结构化结果。

验收：单次调用即可完成从 `SourceFile` 到 `ExecutionResult` 的核心链路；结果对象保留各阶段 diagnostics；core 仍不依赖 CLI/UI。

验证：`./gradlew test`

### A061：添加 CLI 执行入口

依赖：A060。

目标：提供最小 CLI，支持加载源码文件并执行完整编译链路。

验收：合法程序可通过 CLI 执行并返回退出结果；非法程序可输出 diagnostics；CLI 不直接承载 debugger 交互。

验证：`./gradlew test`

### A062：添加阶段化观测输出

依赖：A061。

目标：让完整编译链路在无 debugger 的前提下可观测。

验收：可按需导出或展示 tokens、AST、semantic diagnostics、运行结果等结构化数据；为后续 UI 和 debugger 复用同一套核心结果模型。

验证：`./gradlew test`

## Phase 7：Debugger 模型

### A070：添加 Debugger 命令和快照模型

依赖：A062。

目标：实现 `DebuggerCommand`、`DebuggerSnapshot`、`Breakpoint`。

验收：snapshot 暴露 status、current range、call stack、locals；建立在已完成的编译链路之上；不添加 UI。

验证：`./gradlew test`

### A071：添加单步执行

依赖：A070。

目标：支持确定性 step。

验收：可 start 并 step 简单 `main`；每次 step 返回新 snapshot；测试断言 current range 变化。

验证：`./gradlew test`

### A072：添加断点和 continue

依赖：A071。

目标：支持源码断点和 continue。

验收：continue 命中断点时暂停；无断点时运行到完成；测试覆盖 breakpoint hit 和 completion。

验证：`./gradlew test`

## Phase 8：可视化原型

### A080：添加 JavaFX 应用骨架

依赖：A072。

目标：添加最小 JavaFX app shell。

验收：app 可启动；compiler core 仍不依赖 UI。

验证：`./gradlew test`

手动检查：`./gradlew run`

### A081：显示源码和 Tokens

依赖：A080。

目标：可视化 source 和 token stream。

验收：用户可加载或编辑 sample source；tokens 显示 kind 和 lexeme；lex diagnostics 可显示。

验证：`./gradlew test`

手动检查：`./gradlew run`

### A082：显示 AST 和 Diagnostics

依赖：A081。

目标：可视化 parser output。

验收：合法源码可渲染 AST tree；非法源码可渲染 parser diagnostics。

验证：`./gradlew test`

手动检查：`./gradlew run`

### A083：显示执行状态

依赖：A082。

目标：可视化执行结果和运行时状态。

验收：用户可观察程序返回值、运行状态、调用栈和 locals；不要求提供 debugger 控制。

验证：`./gradlew test`

手动检查：`./gradlew run`

### A084：显示 Debugger 状态

依赖：A083、A072。

目标：可视化 debugger snapshots。

验收：用户可 start、step、continue；当前 source range 可见；locals 和 call stack 可见。

验证：`./gradlew test`

手动检查：`./gradlew run`
