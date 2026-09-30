# C++ STL 兼容开发记录

目标是在算法题范围内保持 C++ 的源码使用习惯，包括 `vector<int>`、成员调用、迭代器、比较器、常用算法和自动资源管理。容器存储和算法实现放在自研 `lib/cpp/*.mh` 中；编译器负责通用语法、对象生命周期、静态实例化及代码生成。外部 G++ 只作为测试参考，产品编译链不依赖它。

本记录对应 `codex/cpp-stl` 分支，基于 `5e26b38` 创建。工作树带入的 UI 改动不属于这组提交。参考材料是用户提供的《STL手册》，其中过时 API 单独列为兼容扩展，具体语义以 C++17 为目标。

## 当前状态

截至 2026 年 10 月 1 日，已完成验收基础设施和显式 C++ 模式入口。**尚不能编译 `vector<int>`，尚未实现任何 STL 容器，也没有达到接近 C++ STL 性能的结论。** 能力清单中的 STL API 均为 `planned`，`random_shuffle` 为 `legacy-planned`。

| 阶段 | 提交 | 已验收内容 |
| --- | --- | --- |
| B01 | `2084304` | 修复已删除 session API 导致的测试编译失败；保留原断言；443 个原有非 UI 测试通过 |
| B02 | `201be47` | 同源码与固定 stdin 的 MiniC native、MiniC debug、G++ 差分；17 个测试通过 |
| B03 | `419248c` | API 清单 3 测试；生命周期、分配、比较和故障注入探针 7 测试 |
| B04 | `bb9ab79` | 原生基准工具 7 测试；三个工作负载均通过独立校验值检查；保存原始样本 |
| F01 入口与词法 | `95c3d0f` | 显式模式、C++ 关键字与 `::`、诊断源码位置、debug 模式及历史；差分扩展到 18 测试 |
| F01 关键字补充 | `025169b` | 拒绝把 asm、export、goto、register 当作 C++ 标识符 |
| F02 标准头映射 | `ea68b88` | 无后缀标准头精确解析至 `lib/cpp`，无实现时明确失败；14 个新增测试 |
| 调试入口补充 | `cf6b3da` | 从已验证 IR 创建独立调试会话，避免重复编译 |
| F02 有序 AST | `26fe7d8` | namespace、using、限定名及范围；11 个新增测试；恢复延迟绑定兼容 |
| 差分预算修复 | `9094fc0` | 编译和执行独立计时；差分测试扩展至 20 项 |
| 子进程清理 | `9c9b945` | 父进程正常退出后清理已观察后代及管道；差分测试扩展至 21 项 |

`95c3d0f` 对应的完整非 UI 验收结果是 **485/485 通过，无跳过**。阶段红测在实现前实际执行，只有通过后的改动进入提交。

本批最终全量回归为 **516/516 通过，无跳过、无失败**，JUnit 执行约 71.7 秒。命令为本页的 `scripts/test-compiler.ps1`，日志保存在 `build/baseline/final-regression.txt`。已检查提交范围不含 `src/main/java/minic/ui`、UI 资源或 UI 测试；原有 UI 工作区改动保持未提交状态。

F02 目前只交付头文件映射和解析结构。namespace/using 名称绑定尚未实现，含这些节点的程序在语义阶段报 `CPP002`，不能进入 IR。AST 保留源顺序和原始名称，后续需要先绑定实体再生成内部名称；只拼接或删除 `std::` 会错误处理局部遮蔽、重开和二义性。

下一子阶段应增加独立的 C++ 名称绑定和 AST 规范化过程，在建立表达式类型映射前运行。规范化后的 AST 可复用现有 IR，但需保留原节点映射和内部名到源码名的映射，避免调试器暴露内部名字。绑定必须包括形参和局部变量，例如 `int x=1; int main(){int x=2; return ::x;}` 不能把全局引用错误解析为局部 `x`。

该子阶段还需要先处理声明点、switch 各 case 的共享词法作用域、using directive 的共同祖先查找，以及显式 `IrLowerer(Program, SemanticResult)` 入口的节点身份一致性。namespace 内类型环境、重载和全局动态初始化在完成各自验收前继续明确拒绝，不能直接展开 AST 后交给现有全局预注册逻辑。

`LanguageMode.CPP17_ALGORITHM` 是增量兼容模式入口，不代表完整 C++17 实现。旧构造器仍使用 C 模式，文件后缀不会隐式改变语言。模式贯穿预处理、词法、语法和调试入口。已识别但尚未实现的 C++ 专用关键字和限定类型形式提供 `CPP001`；引用、lambda 等使用普通符号的未支持语法仍可能产生通用 `PAR001` 诊断。

## 验收方法

在仓库根目录使用 JDK 21。测试参考编译器可通过 `MINIC_CXX` 或 `GXX` 指定单个可执行文件路径。

```powershell
$env:MINIC_CXX = 'C:\mingw64\bin\g++.exe'
./scripts/test-compiler.ps1 -JavaHome '<JDK 21 目录>'
./scripts/test-compiler.ps1 -JavaHome '<JDK 21 目录>' -Suite Baseline
./scripts/test-compiler.ps1 -JavaHome '<JDK 21 目录>' -Suite Cpp
./scripts/test-compiler.ps1 -JavaHome '<JDK 21 目录>' -Suite Benchmark
```

脚本只编译和测试非 UI 模块，避免 UI 重构阻塞编译器验收；不替代 UI 验收。它需要 JUnit console standalone 1.11.4，可通过 `-ConsoleJar` 显式指定。Gradle 提供 `stlCheck`、`cppFrontend`、`cppDifferential`、`stlContract`、`nativePerfContract` 和 `nativePerf` 入口。当前机器 Gradle daemon/worker 回环通信受限，因此本次验证使用无 socket 的脚本，不宣称 Gradle 入口已实际运行。

每一阶段遵循以下提交边界：先加入可观察失败的测试；实现通用机制或库功能；运行目标测试和受影响回归；只提交绿色状态。未支持功能不得通过跳过测试或空头文件伪装完成。新语法需要解析/诊断测试；能运行的功能需要 native/debug/G++ 同源码验证。UB 不作为三后端正确性判定依据。

当前 ownership 探针的公共子集使用显式 `probe_*` 调用，证明它能捕获泄漏、重复析构、重复释放和浅复制共享资源。参考专用 `Tracked` 使用真正的 C++ 特殊成员函数和 `std::vector`；该参考测试通过不代表 MiniC 已实现自动构析或移动。

能力清单的 `evidence` 字段目前校验三类测试引用的格式；它不等同于测试执行结果。API 改成 `supported` 前，必须增加对应真实测试并让完整验收通过。

## 后续提交顺序

以下阶段仍待实现；表中验收描述是要求，不是完成声明。复杂阶段可继续拆成绿色子提交；只解析但尚不能运行的语法必须在后续阶段明确拒绝，不能静默丢弃。

| 阶段 | 实现与主要验收 |
| --- | --- |
| F02 | 标准头精确映射至 `.mh`；有序 namespace/using AST；限定名、重开、遮蔽、二义性和声明点可见性；调用、函数取址、全局读写三后端一致 |
| F03 | struct/class 数据成员及成员函数、访问控制、`this`；布局和成员调用一致 |
| F04 | `T&`、`const T&`、值类别；引用别名、不可绑定情况、修改传播 |
| F05 | 函数和成员重载、const 限定；精确匹配、转换排序、二义性拒绝 |
| F06 | 构造函数、成员初始化；初始化顺序与对象探针一致 |
| F07 | 析构和作用域清理；正常离开、return、break、continue 均恰好析构一次 |
| F08 | 复制构造、复制赋值、C++17 必需的直接构造；深复制、自赋值与返回对象 |
| F09 | 全局及数组对象生命周期；初始化和逆序析构 |
| F10 | 运算符重载；`[]`、`*`、`->`、算术、比较、自增和函数调用 |
| F11 | 所需转换规则；explicit 构造、转换函数、非法隐式转换诊断 |
| F12 | 类模板静态实例化；不同元素类型独立布局，无运行时装箱 |
| F13 | 嵌套/default/non-type 模板参数、依赖类型与 `typename`；模板上下文拆分 `>>` |
| F14 | 函数/成员模板及推导；指针、引用、const 和容器迭代器 |
| F15 | ADL；用户比较器、swap、关联命名空间与二义性 |
| F16 | 库必需的偏特化和 SFINAE；迭代器构造与数量构造不混淆 |
| F17 | `T&&` 和移动；资源转移、移动后可析构、扩容避免深复制 |
| F18 | 转发引用、参数包、`noexcept`；emplace 参数转发与移动策略 |
| F19 | 初始化列表；`vector<int>{1,2}` 与 `(1,2)` 的语义差别 |
| F20 | `auto` 推导；值、引用及 const 保留规则 |
| F21 | range-for；单次求值、隐藏对象生命周期、引用迭代 |
| F22 | lambda 捕获及调用；值/引用捕获、比较器、闭包生命周期 |
| F23 | 结构化绑定；pair 和容器遍历中的值/引用行为 |
| L01–L03 | utility/traits/pair/comparators、类型化原始存储、迭代器；非平凡对象不能按字节复制 |
| L04–L05 | vector 所有权、增长及修改器；reserve 无额外分配、摊还常数 push、移动、失效规则、自身范围操作 |
| L06 | string；连续存储、终止符、比较、插入删除、别名输入 |
| L07–L10 | reverse/unique/排列/二分、heap、priority_queue、introsort；比较器和重复键、恶意分布、复杂度计数 |
| L11–L13 | 分段 deque、端点/中间修改、queue/stack；端点操作及引用稳定性，禁止用 vector 冒充 deque |
| L14–L17 | 红黑树插入/删除、set/multiset/map；颜色及黑高不变量、重复键、遍历顺序和 erase |
| L18–L19 | bitset 与 vector<bool> 代理引用；边界位、移位、批量操作、迭代器语义 |
| L20–L22 | 算法题所需流式 I/O、shuffle 与 legacy random_shuffle、头文件打包和 include 组合 |
| O01–O04 | 独立优化流水线及 IR verifier、常量/复制传播与 DCE、内联、冗余初始化检查消除；debug 原始 IR 保留 |
| O05–O08 | 活跃性、栈槽复用、值位置、局部寄存器及全局线性扫描；调用边界和 Windows x64 ABI |
| O09–O13 | x64 指令选择、安全循环优化、平凡类型批量复制、string SSO、bitset popcount 及后备实现 |
| V01–V04 | 固定种子随机差分、debug 历史、OJ 题集、能力矩阵和最终性能报告 |

## 性能基线与后续判定

当前目标平台是 Windows x64 LLP64。参考编译器是本机 G++ 8.1.0，参数 `-std=c++17 -O2`。Linux OJ 的 ABI、系统库和性能需后续在实际运行平台单独验收，不能从这份结果推断。

已保存的开发机测量位于 `benchmarks/baselines/2026-10-01-windows-development/report.json` 和 `samples.csv`，原始运行目录是 `build/b04-baseline-2`。测量时 HEAD 是 `419248c`，同时带有后来提交的前端和基准工具改动，具体 dirty 状态保存在报告中；该次测量不对应一个干净提交。输入 size 为 65536，seed 为 1729；每个工作负载对双方同步校准 rounds，预热后测量 3 次。表中时间是进程墙钟中位数，包含启动与 I/O，不是纯算法内核或 CPU 时间。

归档报告中的旧标签 `step recording disabled` 指没有使用 debug 解释执行器。原生编译过程仍使用默认 Parser trace，不能据此推断编译时关闭了全部观察记录；工具已将后续报告的措辞改为无 debug 运行时插装的原生可执行文件。

| 工作负载 | rounds | G++ ms | MiniC ms | MiniC/G++ |
| --- | ---: | ---: | ---: | ---: |
| 数组扫描与更新 | 2048 | 108.17 | 1455.65 | 13.46 |
| 可内联函数循环 | 512 | 153.27 | 276.29 | 1.80 |
| memcpy 循环 | 16384 | 128.05 | 135.69 | 1.06 |

双方每次运行都与独立 Java oracle 的校验值一致；六组正式样本均超过 100 ms。CPU 为 i9-12900H，测量期间有并行开发活动，数组扫描 MiniC 样本 MAD 约 167.63 ms。因此这些数据用于发现优化需求，不构成发布性能门槛。memcpy 测量依赖系统实现，也不能说明生成代码或 STL 已接近 C++。

复现工具入口如下；使用新的输出目录，工具拒绝覆盖已有报告。默认报告记录编译器版本、编译参数、Git 状态、CPU、OS、源码 SHA256、实际输入、校准/预热/正式原始样本及 median/MAD。任何编译、超时或校验失败都会失败退出。

```powershell
./gradlew.bat nativePerf --args="--gxx=C:/mingw64/bin/g++.exe --output=build/native-perf-run --repetitions=5"
```

完成库后需要对同一工作负载测量四个构建：G++ 系统 STL、G++ 自研 `.mh`、MiniC 优化自研 `.mh`、MiniC 未优化自研 `.mh`。先检查算法复杂度、比较/移动/分配次数和内存峰值，再分别判断库算法与代码生成的差距。正式报告必须基于已提交源码、固定硬件与输入、足够长的样本和独立运行环境；不得用 debug 耗时代替 native 性能。
