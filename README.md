# MiniC

MiniC 是一个用于学习编译原理的 Java 版 C 语言子集编译器。当前已经完成从源码到 Windows x64 可执行文件的基础编译闭环，并完成 `0.2.0` 结构化编译观测阶段。

当前版本：`0.4.0`。

## 文档入口

- [SPEC.md](SPEC.md)：项目规范、语言范围、架构约束、代码和测试要求。
- [PLAN.md](PLAN.md)：当前 agent 小步执行计划。
- [version/0.1.0.md](version/0.1.0.md)：`0.1.0` 编译闭环阶段总结。
- [version/0.2.0.md](version/0.2.0.md)：`0.2.0` 结构化观测阶段记录。
- [version/0.3.0.md](version/0.3.0.md)：`0.3.0` JavaFX UI 首版阶段记录。
- [version/0.3.1.md](version/0.3.1.md)：`0.3.1` 阶段专属图形化增强记录。
- [version/0.4.0.md](version/0.4.0.md)：`0.4.0` C 子集语法和预编译扩展阶段记录。
- [version/0.5.0.md](version/0.5.0.md)：`0.5.0` 教学型可视化 Debugger 阶段记录。
- [docs/text-style-system.md](docs/text-style-system.md)：Workbench 文本样式抽象、主题配置和迁移指南。

## 当前状态

`0.5.0` 已完成，当前支持教学型可视化 Debugger 首版。下一步规划 `0.5.1` Debugger UI 细化和运行时数据结构深度投影。

本阶段新增 `.mh` include、对象宏、条件编译、MiniC 头文件校验、`extern int printf(char *format, ...);` 可变参数外部函数声明、常用表达式运算符、`sizeof`、`do while` 和 `switch case default`。

项目已经具备：

- MiniC 源码到 Windows x64 可执行文件的编译运行闭环。
- Lexer、Parser、Semantic、IR lowering 和 Windows x64 codegen 的正向可步进状态。
- `minic.runtime.step` 统一阶段 Stepper 和数据模型。
- `minic.session` 全局观测会话、下一步、播放、两倍速播放、暂停和手动 tick。
- `minic.uiapi` UI 门面和不可变 DTO，供后续界面层绑定。
- JavaFX 版 MiniC Visual Workbench。
- 阶段专属 Visual Pane：Lexer token 半透明遮罩、Parser AST 树、Semantic 顶部 `global scope` 且反向箭头作用域树、Codegen Assembly 增量行文本视图。
- `MiniCObservationApi.currentStageVisualData()` UI 专用 visual DTO，不向 UI 暴露 AST、Scope、IR、Stepper 等内部对象。
- 轻量预编译器：`.mh` include、对象宏、条件编译和头文件声明约束。
- Phase D 语法扩展：常用复合赋值/位运算/逻辑运算/移位/条件表达式/`sizeof`、`do while`、`switch case default`。

UI 交互细化暂缓，后续可继续补充 AST/Scope 节点点击定位源码、真实手工 UI 截图验收和更完整的滚动定位体验。

当前 Debugger 作为 Workbench 独立模式进入，执行模型以 IR Interpreter 为核心，配合状态快照、事件日志、虚拟进程空间和 Graph/Array/Composite 三类数据结构图形化基元；真实 exe 调试、Windows Debug API 和寄存器级状态不属于第一版范围。

UI 风格继续参考：

```text
C:\Users\Administrator\Desktop\styleOfMiniC\index.html
```

首版 UI 只依赖 `minic.uiapi.*`，不直接访问 compiler、runtime stepper 或 session 内部对象。

## 启动 JavaFX UI

```powershell
$env:JAVA_HOME='E:\projects\MiniC\.local\tools\jdk-21.0.10+7'
$env:PATH='E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;' + $env:PATH
$env:GRADLE_USER_HOME='E:\projects\MiniC\.gradle-home'
.\gradlew.bat runUi
```

`runUi` 是前台 JavaFX 进程，窗口未关闭前 Gradle 会一直显示 `:runUi EXECUTING`，这是正常状态；关闭 UI 窗口后任务才会结束。

## UI API 最小示例

`0.2.0` 已提供不依赖 JavaFX 的 UI 门面：

```java
MiniCObservationApi api = new MiniCObservationApi();
api.loadSource("main.mc", "int main() { return 0; }");
api.startSession();

UiControlResultDto result = api.next();
UiCurrentStateDto state = api.currentState();
UiStageDataDto stageData = api.currentStageData();
UiStageVisualDto visualData = api.currentStageVisualData();
UiGlobalDataDto globalData = api.globalData();

api.play();
api.tick();
api.playFast();
api.tick();
api.pause();
```

当前 UI API 支持正向下一步、自动播放、两倍速播放、暂停和手动 `tick`。`previous` 与 `reversePlay` 已作为未来扩展点保留，当前返回 `UNSUPPORTED`，状态能力中 `canPrevious=false`、`canReversePlay=false`。

## 本地测试

推荐使用项目内本地 JDK 和 Gradle 缓存：

```powershell
$env:JAVA_HOME='E:\projects\MiniC\.local\tools\jdk-21.0.10+7'
$env:PATH='E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;' + $env:PATH
$env:GRADLE_USER_HOME='E:\projects\MiniC\.gradle-home'
.\gradlew.bat test
```

## 本地编译为 exe

Windows x64 可执行文件生成依赖 Visual Studio 2022 Build Tools 的 C++ 工具链，必须能使用 `ml64.exe` 和 `link.exe`。

在普通 PowerShell 中可通过 `VsDevCmd.bat` 临时启用 x64 工具链环境：

```powershell
cmd /c "`"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat`" -arch=x64 && where ml64 && where link"
```

编译样例并生成可执行文件：

```powershell
cmd /c '"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 && set "JAVA_HOME=E:\projects\MiniC\.local\tools\jdk-21.0.10+7" && set "PATH=E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;%PATH%" && set "GRADLE_USER_HOME=E:\projects\MiniC\.gradle-home" && .\gradlew --no-daemon run --args="compile samples\main.mc --out-dir build\minic --emit-asm --ml64 \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\ml64.exe\" --link \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\link.exe\""'
```

成功后产物位于：

```text
build\minic\main.asm
build\minic\main.obj
build\minic\main.exe
```

运行可执行文件并查看退出码：

```powershell
.\build\minic\main.exe
$LASTEXITCODE
```

也可以使用 `compile-run` 一次完成编译、链接、运行，并捕获 stdout、stderr 和退出码：

```powershell
cmd /c '"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 && set "JAVA_HOME=E:\projects\MiniC\.local\tools\jdk-21.0.10+7" && set "PATH=E:\projects\MiniC\.local\tools\jdk-21.0.10+7\bin;%PATH%" && set "GRADLE_USER_HOME=E:\projects\MiniC\.gradle-home" && .\gradlew --no-daemon run --args="compile-run samples\printf.mc --out-dir build\minic --emit-asm --ml64 \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\ml64.exe\" --link \"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\Hostx64\x64\link.exe\""'
```

## 协作规则摘要

- 全程使用中文沟通、记录需求、编写文档和汇报结果。
- Java 标识符使用英文。
- 每次 agent 只能执行 [PLAN.md](PLAN.md) 中的一个任务编号，除非用户明确批准合并。
- 每个非文档任务必须有测试或明确说明无法测试的原因。
- 不提交构建产物、IDE 私有配置、临时文件、日志文件和本地环境文件。

## MiniC 语法参考

### 类型系统

| 类型 | 大小 | 说明 |
|------|------|------|
| `bool` | 1 字节 | 布尔值 `true` / `false` |
| `char` | 1 字节 | 有符号 8 位整数 |
| `int` | 4 字节 | 有符号 32 位整数 |
| `long` | 8 字节 | 有符号 64 位整数 |
| `float` | 4 字节 | IEEE 754 单精度浮点 |
| `double` | 8 字节 | IEEE 754 双精度浮点 |

复合类型：

- 指针：`int *p;`
- 数组：`int arr[10];`
- 结构体：`struct Point { int x; int y; };`
- 函数指针：`int (*fp)(int, int);`
- 空指针：`null`

### 字面量

```c
42          // int
100L        // long
3.14f       // float
3.14        // double
'A'         // char
"hello"     // string
true false  // bool
null        // null pointer
```

### 运算符（按优先级从高到低）

| 优先级 | 运算符 | 说明 |
|--------|--------|------|
| 1 | `[]` `.` `->` `()` `++` `--`（后缀） | 下标、成员访问、调用、后缀自增减 |
| 2 | `++` `--` `+` `-` `!` `~` `*` `&` `sizeof`（前缀） | 前缀自增减、正负号、逻辑非、按位取反、解引用、取址 |
| 3 | `*` `/` `%` | 乘除取模 |
| 4 | `+` `-` | 加减 |
| 5 | `<<` `>>` | 位移 |
| 6 | `<` `<=` `>` `>=` | 关系比较 |
| 7 | `==` `!=` | 相等比较 |
| 8 | `&` | 按位与 |
| 9 | `^` | 按位异或 |
| 10 | `\|` | 按位或 |
| 11 | `&&` | 逻辑与 |
| 12 | `\|\|` | 逻辑或 |
| 13 | `? :` | 条件（三元） |
| 14 | `=` `+=` `-=` `*=` `/=` `%=` `&=` `\|=` `^=` `<<=` `>>=` | 赋值 |

### 控制流

```c
// if-else
if (condition) { ... } else { ... }

// while
while (condition) { ... }

// do-while
do { ... } while (condition);

// for
for (int i = 0; i < n; i = i + 1) { ... }

// switch
switch (value) {
    case 1: ...; break;
    case 2: ...; break;
    default: ...;
}

// break / continue
break;
continue;
```

### 函数

```c
// 声明
int add(int a, int b);

// 定义
int add(int a, int b) {
    return a + b;
}

// 外部函数（可变参数）
extern int printf(char *format, ...);

// 程序入口
int main() { return 0; }
```

### 结构体

```c
struct Point {
    int x;
    int y;
};

struct Point p;
p.x = 10;

struct Point *pp;
pp->y = 20;
```

### 指针与数组

```c
int a = 42;
int *p = &a;    // 取址
int b = *p;     // 解引用

int arr[5];
arr[0] = 1;
int *q = arr;   // 数组退化为指针
```

### 预处理器

```c
#include "header.mh"       // 引入头文件（仅 .mh）

#define PI 3
#define MAX 100
#undef MAX

#ifdef DEBUG
    // 条件编译
#endif

#ifndef GUARD
    // ...
#else
    // ...
#endif
```

### 注释

```c
// 单行注释（不支持 /* */ 块注释）
```

### 标识符规则

- 以字母或下划线开头，后跟字母、数字或下划线
- 用户函数不能以下划线开头（保留给运行时）
