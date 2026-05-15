<!-- 语法分析阶段说明模板 -->
<!-- 本文件定义 parser/AST 阶段各节点类型的解释文本 -->
<!-- 格式: ## 节点kind 下面是该节点的解释正文 -->
<!-- 加载时忽略 HTML 注释行 -->

## header
AST 阶段把 token 串组织成树。当前节点是 ${kind}，显示标签为 `${label}`，id 为 `${id}`，子节点数 ${childCount}，当前是否正在处理: ${active}。

## footer
用途: AST 是语义分析和 IR lowering 的共同输入。看这个节点的类型、标签、子节点和源码遮罩，就能知道编译器当前把哪一段源码理解成了什么语法结构。

## Program
Program 是整棵语法树的根，收集所有顶层声明。后续语义分析和 IR lowering 都从这里开始遍历。

## FunctionDecl
FunctionDecl 表示函数声明或定义，包含返回类型、函数名、参数列表和可选函数体。它会进入符号表，并在 IR 阶段变成一个函数单元。

## Parameter
Parameter 表示函数形参。语义分析会把它加入函数体作用域，函数调用检查也会用它验证实参与形参是否匹配。

## BlockStmt
BlockStmt 表示一对花括号包围的语句块。它通常创建新的作用域，局部变量只在这个块及其子块内可见。

## VarDeclStmt
VarDeclStmt 表示局部变量声明。语义分析会检查重名和初始化表达式类型，IR 阶段会为变量分配临时值或栈位置。

## ReturnStmt
ReturnStmt 表示函数返回。语义分析会检查返回值类型是否匹配函数返回类型，IR/ASM 会生成返回值传递和退出序列。

## IfStmt
IfStmt 表示条件分支。IR 阶段会把它降成条件跳转和基本块。

## ForStmt
ForStmt 表示 for 循环，包含初始化、条件、步进和循环体。IR 阶段会把它拆成循环入口、条件判断、循环体、步进和退出跳转。

## WhileStmt
WhileStmt 表示 while 循环，IR 阶段会生成条件判断和回边跳转。

## ExprStmt
ExprStmt 表示把表达式当作语句执行，例如函数调用或赋值。它通常关注副作用而不是最终值。

## BinaryExpr
BinaryExpr 表示二元表达式，左右子节点分别是操作数。语义分析会检查两侧类型，IR 阶段会生成对应计算或比较指令。

## UnaryExpr
UnaryExpr 表示单目表达式，例如取负、取地址、解引用或自增自减。它会影响值类别和后续代码生成方式。

## CallExpr
CallExpr 表示函数调用。语义分析会解析被调用函数并检查参数数量/类型，代码生成会按调用约定传参。

## NameExpr
NameExpr 表示一个名字引用。语义分析会在作用域链里查找它对应的符号，IR 阶段再读取或写入该符号。

## IntegerLiteralExpr
IntegerLiteralExpr 表示整数常量，通常直接降低为 IR 立即数。

## StringLiteralExpr
StringLiteralExpr 表示字符串常量，后端会把它放入数据区，并把地址传给使用它的表达式。

## default
该节点表示源码中的一个语法结构。父子关系说明 parser 如何把线性的 token 串组织成可遍历的树。
