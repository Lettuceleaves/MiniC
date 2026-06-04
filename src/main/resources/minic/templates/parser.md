<!-- 语法分析阶段说明模板 -->
<!-- 本文件定义 Parser/AST 阶段各节点类型的解释文本 -->
<!-- 格式: ## 节点kind 下面是该节点的解释正文 -->
<!-- 加载时忽略 HTML 注释行 -->

## header
Parser 走到这里时，已经把线性的 token 串整理成一棵可以上下展开的 AST；当前节点是 ${kind}，界面标签是 `${label}`，id 是 `${id}`，它下面接着 ${childCount} 个子节点，是否正处在处理焦点中由 ${active} 标出。你可以把 AST 想成把一句 C 代码画成句法树：每个节点都说明这一小段代码在整句里扮演什么角色。后续的语义分析和 IR lowering 会顺着这棵树继续走，先确认名字、类型和作用域，再把确认过的结构翻译成更接近机器执行的中间表示。

## footer
AST 是 Parser 留给后面阶段的结构化记录，它把源码从“一串字符和 token”变成“谁包含谁、谁先执行、谁依赖谁”的树。对大一同学来说，可以先记住：Parser 不负责判断变量有没有声明对，也不负责生成机器代码，它主要负责把语法形状搭清楚。语义分析和 IR lowering 会沿着这些父子关系继续工作，一个负责检查含义是否合理，另一个负责把合理的结构降到可执行路径、临时值和跳转。

## Program
当前节点标签是 `${label}`，它覆盖的源码片段是 `${source}`。Program 是整棵 AST 的根节点，对应一个完整的 C 源文件，而不是某一行具体语句。比如文件里有 `int add(int a, int b) { return a + b; }` 和 `struct Point { int x; int y; };`，这些顶层声明都会挂在 Program 下面。Parser 建这个节点，是为了给所有顶层内容一个共同入口；语义分析从这里建立全局符号表，IR lowering 也从这里找到每个需要生成的函数和全局结构。

## StructDecl
当前节点标签是 `${label}`，它对应的 C 源码片段是 `${source}`。StructDecl 对应结构体类型声明，例如 `struct Point { int x; int y; };`。Parser 会把结构体名字 `Point` 和花括号里的字段列表收进同一个节点，这样后面不会把字段误当成普通局部变量。语义分析会登记这个结构体类型、检查字段名是否重复，IR lowering 或后端计算 `point.x`、`point->x` 时会依赖它给出的字段顺序和内存偏移。

## StructField
当前节点标签是 `${label}`，源码片段是 `${source}`。StructField 对应结构体里面的一条字段声明，例如 `struct Point { int x; int y; };` 里的 `int x;` 或 `int y;`。Parser 单独建立字段节点，是因为字段虽然长得像变量声明，但它属于结构体布局，不是在当前语句位置执行。语义分析会把这些字段收集成 `Point` 的成员表，后续 IR lowering 处理 `point.x = 7;` 时才能知道 `x` 是哪个字段、应该写到结构体内存的哪个位置。

## FunctionDecl
界面标签是 `${label}`，当前源码片段是 `${source}`。FunctionDecl 对应函数声明或函数定义，例如 `int add(int a, int b) { return a + b; }`。这个节点会把返回类型、函数名、参数列表 和函数体放在一起，让 Parser 明确“这是一个可被调用的函数单元”。语义分析会把 `add` 登记到符号表并检查调用签名，IR lowering 会把函数体降成一个独立的 IR 函数，参数成为入口处可使用的值。

## Parameter
当前节点标签是 `${label}`，源码片段是 `${source}`。Parameter 对应函数形参，例如 `int add(int a, int b)` 里的 `int a` 和 `int b`。Parser 为每个形参建节点，是为了保留它的类型、名字和在参数列表中的顺序，因为调用函数时这些信息都不能丢。语义分析会把形参加入函数体的局部作用域，并检查实参能不能一一对应；IR lowering 会把形参看成函数入口已经准备好的值，供 `return a + b;` 这样的表达式读取。

## BlockStmt
当前节点标签是 `${label}`，这段块级源码是 `${source}`。BlockStmt 对应一对花括号包住的语句块，例如函数体 `{ return a + b; }`，或者 `if (1 < 2) { return 7; }` 里的 then 块。它很像一间小教室：里面可以有自己的局部声明，语句也按黑板上的顺序一条条执行。Parser 建这个节点是为了保存块的边界；语义分析通常会在这里打开新的作用域，IR lowering 则按块内语句顺序生成控制流。

## VarDeclStmt
当前节点标签是 `${label}`，它对应的声明源码是 `${source}`。VarDeclStmt 对应变量声明语句，例如 `int x = 1;`，也包括 `for (int i = 0; i < 5; i = i + 1)` 里的初始化声明 `int i = 0`。Parser 建这个节点，是为了把变量名、声明类型和可选初始值绑在一起，而不是把 `int`、`x`、`1` 当作互不相关的碎片。语义分析会检查同一作用域里是否重名、初始值能不能放进变量，IR lowering 会为这个变量分配临时值或存储位置，并把初始化表达式写进去。

## ReturnStmt
当前节点标签是 `${label}`，当前返回语句源码是 `${source}`。ReturnStmt 对应 `return` 语句，例如 `return a + b;`、`return 7;` 或 `return value ? value : sizeof value;`。Parser 建这个节点，是为了告诉后续阶段“当前函数的这条执行路径在这里结束，并且可能带回一个值”。语义分析会把返回表达式和当前函数的返回类型对照，IR lowering 会把它翻译成设置返回值并跳到函数出口的控制流。

## BreakStmt
当前节点标签是 `${label}`，当前源码片段是 `${source}`。BreakStmt 对应 `break;`，常见于循环或 `switch (value) { case 5: ... default: ... }` 的分支里。Parser 把它单独做成节点，是因为它不是普通顺序执行语句，而是要求立刻跳出最近的循环或 switch。语义分析会检查它是否真的处在允许 `break` 的结构内部，IR lowering 会把它接到当前结构的出口基本块。

## ContinueStmt
当前节点标签是 `${label}`，当前源码片段是 `${source}`。ContinueStmt 对应 `continue;`，通常出现在 `while (x < 5)` 或 `for (int i = 0; i < 5; i = i + 1)` 的循环体里。Parser 建这个节点，是为了保留“跳过本轮剩余语句，进入下一轮循环”的控制意图。语义分析会确认它在循环内部；IR lowering 会根据循环类型，把它跳到 while 的条件判断处，或 for 的步进表达式再回到条件判断处。

## IfStmt
当前节点标签是 `${label}`，它对应的分支源码是 `${source}`。IfStmt 对应条件分支，例如 `if (1 < 2) { return 7; } else { return 9; }`。Parser 会把条件表达式 `1 < 2`、then 分支和 else 分支放进同一个节点，这样树上能清楚看出哪段代码属于哪个分支。语义分析会检查条件是否能当真假值使用，IR lowering 会把它展开成条件跳转、then 基本块、else 基本块以及分支结束后的汇合位置。

## ForStmt
当前节点标签是 `${label}`，当前 for 循环源码是 `${source}`。ForStmt 对应 for 循环，例如 `for (int i = 0; i < 5; i = i + 1)`。Parser 建这个节点，是为了把初始化、循环条件、每轮后的步进表达式和循环体固定在正确位置，因为这四块虽然写在一行里，执行时却分布在不同阶段。语义分析会分别检查 `i` 的作用域、条件类型和赋值是否合法，IR lowering 会把它排成初始化、条件判断、循环体、步进和退出跳转。

## WhileStmt
当前节点标签是 `${label}`，当前 while 循环源码是 `${source}`。WhileStmt 对应先判断再执行的循环，例如 `while (x < 5)` 后面接一个循环体。Parser 把条件和循环体配成一个节点，是为了说明这个条件控制的是哪一段重复执行的代码。语义分析会检查 `x < 5` 这样的条件能否作为真假判断，IR lowering 会生成条件基本块、循环体基本块，以及执行完循环体后回到条件处的跳转。

## DoWhileStmt
当前节点标签是 `${label}`，当前 do-while 源码是 `${source}`。DoWhileStmt 对应 `do { ... } while (condition);` 这种先执行、再判断的循环。Parser 需要把它和普通 while 区分开，因为 do-while 的循环体至少会执行一次，这个顺序对程序行为很重要。语义分析仍会检查条件表达式是否可用作真假判断，IR lowering 则会先生成循环体，再在末尾根据条件决定是否跳回下一轮。

## SwitchStmt
当前节点标签是 `${label}`，当前 switch 源码是 `${source}`。SwitchStmt 对应多分支选择，例如 `switch (value) { case 5: ... default: ... }`。Parser 建这个节点，是为了把待匹配的表达式 `value` 和所有 `case`、`default` 分支收在同一个结构里，避免它们像普通标签一样散落。语义分析会检查选择表达式和 case 值是否适合比较、是否有重复 case，IR lowering 会把它们整理成一组跳转目标。

## SwitchCase
当前节点标签是 `${label}`，当前 case/default 源码是 `${source}`。SwitchCase 对应 switch 里的一个入口，例如 `case 5:` 或 `default:`。Parser 把每个分支入口做成节点，是为了记录“匹配到哪个值时从这里开始执行”，以及这个入口下面跟着哪些语句。语义分析会用它检查重复的 case 值并确认 default 的位置含义，IR lowering 会把每个 SwitchCase 变成 switch 跳转表或条件跳转链里的目标块。

## ExprStmt
当前节点标签是 `${label}`，当前表达式语句源码是 `${source}`。ExprStmt 对应把表达式当语句执行的情况，例如 `point.x = 7;`、`point->x = 6;`、`values[0] = 2;` 或一次函数调用。Parser 建这个节点，是为了说明这里的表达式不是拿来继续参与更大计算，而是作为一条完整语句独立执行。语义分析仍会检查表达式本身是否合法，IR lowering 会保留赋值、调用、更新等副作用，即使表达式算出的值之后没人使用。

## AssignmentExpr
当前节点标签是 `${label}`，当前赋值源码是 `${source}`。AssignmentExpr 对应赋值表达式，例如 `int x = 1;` 初始化里的写入，或者 `point.x = 7;`、`point->x = 6;`、`values[0] = 2;`。Parser 会把左侧目标、赋值运算符和右侧值分开挂在树上，因为左侧必须是“能被写入的位置”，右侧才是要计算出的值。语义分析会检查左侧是否可写、右侧类型能不能转换过去，IR lowering 会先算右侧，再把结果写回变量、字段、指针指向位置或数组元素。

## BinaryExpr
当前节点标签是 `${label}`，当前二元表达式源码是 `${source}`。BinaryExpr 对应二元运算，例如 `a + b`、`1 < 2`、`x < 5` 或 `i + 1`。Parser 建这个节点，是为了把“左边表达式、运算符、右边表达式”的结合关系固定下来，这样后面阶段不用重新猜优先级和结合方向。语义分析会检查两侧类型是否能做加法、比较或逻辑运算，IR lowering 会根据运算符生成加法、比较、逻辑判断等对应指令。

## UnaryExpr
当前节点标签是 `${label}`，当前一元表达式源码是 `${source}`。UnaryExpr 对应只作用在一个表达式上的运算，例如 `-x`、`&x`、`*p`、`!flag`，以及前缀形式的 `++i` 或 `--i`。Parser 把它单独成节点，是因为一元运算会改变一个表达式的含义：有时得到数值，有时得到地址，有时要求目标可写。语义分析会按运算符检查类型和可写性，IR lowering 会生成取地址、解引用、取负、逻辑取反或更新写回等相应步骤。

## PostfixUpdateExpr
当前节点标签是 `${label}`，当前后缀更新源码是 `${source}`。PostfixUpdateExpr 对应后缀自增或自减，例如 `i++`、`i--`，也可以出现在 for 循环的步进位置。Parser 需要把后缀更新和普通赋值区分开，因为它的表达式值通常是更新前的旧值，但执行后又要把新值写回去。语义分析会确认目标是可写的整数或指针一类对象，IR lowering 会保留“先取旧值、再加减、最后写回”的顺序。

## ConditionalExpr
当前节点标签是 `${label}`，当前三目表达式源码是 `${source}`。ConditionalExpr 对应三目条件表达式，例如 `return value ? value : sizeof value;` 里的 `value ? value : sizeof value`。Parser 把条件、真分支表达式和假分支表达式放在同一节点里，是为了说明这不是 if 语句，而是会产生一个表达式结果的选择结构。语义分析会检查条件能否当真假值使用，并协调两个结果表达式的类型；IR lowering 会把它拆成条件跳转和一个合并后的结果值。

## CallExpr
当前节点标签是 `${label}`，当前调用源码是 `${source}`。CallExpr 对应函数调用，例如 `add(1, 2)` 或在更大程序里调用已经声明的函数。Parser 建这个节点，是为了把“要调用谁”和“传入哪些实参”固定下来，实参本身也可能是复杂表达式。语义分析会查找被调用函数、检查实参数量和类型是否匹配参数列表，IR lowering 会按调用约定准备参数、发出调用，并把返回值交给后续表达式使用。

## NameExpr
当前节点标签是 `${label}`，当前名字源码是 `${source}`。NameExpr 对应源码里出现的名字，例如 `a`、`b`、`x`、`value`、`point` 或 `values`。Parser 现在只知道它是一个名字，还不知道它最终指的是局部变量、形参、函数，还是某个全局符号。语义分析会沿着作用域链把名字绑定到具体声明，IR lowering 之后才知道这里应该读取一个值、取得一个地址，还是作为函数调用目标来处理。

## FieldAccessExpr
当前节点标签是 `${label}`，当前字段访问源码是 `${source}`。FieldAccessExpr 对应结构体字段访问，例如 `point.x = 7;` 里的 `point.x`，或 `point->x = 6;` 里的 `point->x`。Parser 建这个节点，是为了保存点号和箭头访问的形状：左边是结构体值或结构体指针，右边是字段名。语义分析会检查目标类型是否真的包含字段 `x`，并查出字段类型和偏移；IR lowering 会把它变成“基址加字段偏移”的地址计算，再按上下文读取或写入。

## IndexExpr
当前节点标签是 `${label}`，当前下标访问源码是 `${source}`。IndexExpr 对应下标访问，例如 `values[0] = 2;`。Parser 把数组或指针表达式 `values` 与下标表达式 `0` 配对，是为了说明这里访问的是某个连续存储区域中的第几个元素。语义分析会检查目标是否能按下标访问、下标是否为整数，IR lowering 会把它降成基址加 `下标 * 元素大小` 的地址计算，再完成读取或写入。

## GroupingExpr
当前节点标签是 `${label}`，当前括号表达式源码是 `${source}`。GroupingExpr 对应源码中显式写出的括号，例如 `(a + b)` 或 `(1 < 2)`。Parser 建这个节点，是为了尊重程序员用括号指定的组合方式，让树的形状和源码想表达的优先级一致。语义分析通常会继续检查括号里的表达式本身，IR lowering 多数情况下不会为括号生成额外指令，而是按照括号已经确定的树形顺序继续生成代码。

## SizeofExpr
当前节点标签是 `${label}`，当前 sizeof 源码是 `${source}`。SizeofExpr 对应 `sizeof` 表达式，例如 `return value ? value : sizeof value;` 里的 `sizeof value`。Parser 单独建立这个节点，是因为 `sizeof` 看起来像运算，但它关心的是类型或表达式的大小，而不是普通运行时计算结果。语义分析会确定被查询对象的类型和布局，IR lowering 通常可以把它变成编译期常量，直接放进后续表达式里。

## StructInitExpr
当前节点标签是 `${label}`，当前结构体初始化源码是 `${source}`。StructInitExpr 对应结构体聚合初始化，例如给 `struct Point` 的两个字段按顺序提供初始值。Parser 建这个节点，是为了把一组初始化表达式视为一个结构体值，而不是几条彼此独立的表达式。语义分析会检查初始值数量、顺序和字段类型是否匹配，IR lowering 会把它展开成逐字段写入，或者生成一段结构体内存的初始化过程。

## IntegerLiteralExpr
当前节点标签是 `${label}`，当前整数字面量源码是 `${source}`。IntegerLiteralExpr 对应源码里的普通整数常量，例如 `int x = 1;` 中的 `1`、`if (1 < 2)` 中的 `1` 和 `2`、`return 7;` 中的 `7`。Parser 为字面量建节点，是为了让数字也成为表达式树中的叶子，而不是只停留在词法 token 里。语义分析会确定它参与表达式时的整数类型，IR lowering 通常会把它直接变成立即数，用在赋值、比较或返回值里。

## LongLiteralExpr
当前节点标签是 `${label}`，当前 long 字面量源码是 `${source}`。LongLiteralExpr 对应 long 类型整数常量，例如带有 long 后缀或被解析为更宽整数的字面量。Parser 把它和普通 IntegerLiteralExpr 区分开，是为了从 AST 阶段就保留“这个数需要更宽整数类型”的信息。语义分析会按 long 的规则参与类型检查，IR lowering 会生成对应位宽的常量，避免后面把它误当成普通 int。

## FloatLiteralExpr
当前节点标签是 `${label}`，当前 float 字面量源码是 `${source}`。FloatLiteralExpr 对应 float 浮点常量，例如单精度形式的 `1.0f`。Parser 建这个节点，是为了保留源码中浮点数的文本和值，并让它作为表达式树的叶子参与更大计算。语义分析会把它按 float 类型检查，IR lowering 和后端会用适合单精度浮点的常量表示来传递这个值。

## DoubleLiteralExpr
当前节点标签是 `${label}`，当前 double 字面量源码是 `${source}`。DoubleLiteralExpr 对应 double 浮点常量，例如没有 `f` 后缀的 `1.0`。Parser 把它记录成独立节点，是因为 double 和 float 在类型、精度、常量表示上都不同，不能只当作普通整数处理。语义分析会让它参与浮点表达式的类型判断，IR lowering 会生成双精度常量或把它放进合适的常量数据区域。

## CharLiteralExpr
当前节点标签是 `${label}`，当前字符字面量源码是 `${source}`。CharLiteralExpr 对应字符常量，例如 `'a'` 或 `'\n'`。Parser 建这个节点，是为了把源码里的字符写法保存为一个明确的表达式值，同时保留它最终会对应到某个字符编码。语义分析会确认它的字符类型含义，IR lowering 通常会把它变成一个小整数常量，供赋值、比较或函数调用使用。

## BoolLiteralExpr
当前节点标签是 `${label}`，当前布尔字面量源码是 `${source}`。BoolLiteralExpr 对应布尔常量，例如 `true` 或 `false`。Parser 单独建立布尔字面量节点，是为了让条件、逻辑表达式和普通整数在语义上更容易区分。语义分析会确认它是布尔类型，IR lowering 通常会把它转成 1 或 0 这样的条件值，交给 if、while 或逻辑运算使用。

## NullLiteralExpr
当前节点标签是 `${label}`，当前空指针字面量源码是 `${source}`。NullLiteralExpr 对应空指针常量，也就是表示“这里没有指向任何对象”的值。Parser 把它和普通数字区分开，是因为它主要用于指针上下文，而不是用来参加普通算术。语义分析会检查它是否被放在指针赋值、比较或返回等合适位置，IR lowering 会把它变成目标平台约定的空地址值。

## StringLiteralExpr
当前节点标签是 `${label}`，当前字符串字面量源码是 `${source}`。StringLiteralExpr 对应字符串常量，例如 `"hello"` 这样的一串字符。Parser 建这个节点，是为了把整段字符串当成一个表达式值，而不是很多分散的字符 token。语义分析会确定它在当前语言规则下的字符串或字符数组含义，IR lowering 和后端通常会把实际内容放到只读数据区，再把地址交给调用、赋值或其他表达式使用。

## default
当前节点标签是 `${label}`，当前源码片段是 `${source}`，节点类型是 `${kind}`。default 是兜底说明，用在某个 AST 节点暂时没有专门文案时。即使走到这里，它仍然代表源码中的一个具体语法结构，只是当前模板还没有为这个 kind 写出更细的教学解释。读这类节点时，可以先观察它的父节点、子节点和标签；语义分析和 IR lowering 也会依靠这些树形关系继续检查和翻译程序。
