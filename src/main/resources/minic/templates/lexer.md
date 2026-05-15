<!-- 词法分析阶段说明模板 -->
<!-- 本文件定义 lexer 阶段各 token 类型的解释文本 -->
<!-- 格式: ## token类别 下面是该类别的解释正文 -->
<!-- 加载时忽略 HTML 注释行 -->

## header
词法阶段把源码字符流切成 token。当前 token 是 ${kind}，文本为 `${text}`，源码位置是 ${startLine}:${startColumn} 到 ${endLine}:${endColumn}，offset 范围是 ${startOffset}..${endOffset}。

## footer
用途: token 是 parser 的输入。只要看清当前 token 的类别、文本和位置，就能理解 AST 为什么会在这里创建某个节点，或者为什么语法错误会落在这一段源码上。

## type_keyword
这是类型关键字，用来声明变量、函数返回值或形参类型。parser 会把它放进声明节点，语义分析会用它检查赋值、返回值和函数调用是否类型匹配。

## control_keyword
这是控制流关键字，决定 parser 构造哪类语句节点。例如 return 产生返回语句，if/else 产生分支，for/while 产生循环，break/continue 约束在循环作用域内。

## EXTERN
extern 表示外部声明。它告诉编译器该函数或符号由外部目标文件/运行库提供，语义分析登记签名，代码生成只产生调用引用。

## IDENTIFIER
标识符是用户定义的名字，后续语义分析会把它解析为变量、函数或类型，并检查是否已经声明、是否在当前作用域可见。

## literal
字面量是源码中直接写出的值，会在 AST 中形成常量表达式，后续 IR 通常把它变成立即数、常量地址或空指针值。

## STRING_LITERAL
字符串字面量会作为连续字符数据保存，调用 printf 这类外部函数时通常作为格式串地址传入。

## operator
这是运算符 token，描述表达式或语句之间的动作。parser 会依据优先级和结合性组织表达式树，语义分析再检查操作数类型。

## delimiter
这是结构/分隔符 token，用来限定参数列表、语句块、数组/下标或语句边界。它主要决定 AST 的层级和边界。

## EOF
EOF 是源码结束标记，不对应真实字符，用来告诉 parser 输入已经耗尽。

## default
该 token 是 lexer 从字符流中切分出的最小语法单元，parser 不再直接看原始字符，而是消费这些 token。
