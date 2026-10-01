# C++ 算法题组合覆盖审计

本文记录 `6d6441d` 后新增的功能验收，区别于已经通过的 API 验收和独立性能矩阵。下述 18 个模式参数已全部实际执行通过，验收 revision 与范围见文末。

当前能力表仍是 Windows x64 LLP64 算法 profile 的 **84 项 API：83 项 supported，1 项 legacy-supported（random_shuffle）**。逐 API 历史证据见 [coverage.md](../benchmarks/validation/2026-10-02-cpp17/coverage.md) 和 [mapping.json](../benchmarks/validation/2026-10-02-cpp17/mapping.json)。已有证据覆盖所选接口，但不表示完整 ISO C++17，也不表示所有容器、值类型和操作组合均已验证。历史 4545 项统一验收及其固定 revision 不因本次增补而改写。

## 发现的缺口

| 领域 | 现有证据 | 尚缺的组合深度 | 本次补充 |
|---|---|---|---|
| 算法题整体 | `CppOjProgramsTest` 的 5 个程序，每题 1 组输入 | 不连通图、重复优先级、退化输入及跨结构操作 | 8 个批量题目程序，独立参考算法 |
| 容器随机操作 | `CppLibraryRandomizedTest` 的 3 个 seed，vector<int>、deque<int>、有序整数容器 | 非平凡元素、别名插入、多次复制移动、清空后复用；原 resize 小于 18 | 1 个 Record 操作程序，3 组序列，包含跨分段长度 |
| 图及优先队列 | 一个小型最短路正例 | 64 位距离、过时队列项、零权环、不可达节点 | Bellman–Ford 对照 priority_queue Dijkstra |
| deque 与算法 | 端点和分段契约、短整数序列 | 滑窗两端过期、相等及单调序列 | 每个窗口独立枚举最大值 |
| 有序容器 | 单接口查找、复制、擦除及小型随机操作 | 最优调度、抵消删除、大键值、字符串键排序 | 子集穷举调度；独立 Java 映射 |
| bitset | 字边界、移位和 count 的专项测试 | 完整动态规划中旧状态与移位状态的合并 | 布尔数组 0/1 背包 |
| queue 与嵌套容器 | 一个网格 BFS | 起点障碍、孤岛、长条网格、多条最短路 | 不使用队列的整张网格反复松弛 |
| 性能 | 原 11 主负载和 4 bulk 配置；已有比较/分配等次数上界 | OJ 组合的多种数据形态及规模 | 由独立性能矩阵补充；本测试不测耗时 |

已有 `CppLibrarySourcesTest` 的正负库契约、对象生命周期、SSO、比较次数及复杂度上界等验收保留。本次不把 seed、优化开关或后端数量记成新的独立功能。

## 新增程序与独立模型

统一入口为 `CppOjCombinationCoverageTest#realCombinationsMatchIndependentModels`，源文件位于 `src/test/resources/cpp/oj-combinations/`。每个程序批量处理小型、固定的边界输入和固定 seed 输入，重复构造及销毁容器。

| 源文件 | 输入/序列数 | 关键组合与边界 | 独立 Java 模型 |
|---|---:|---|---|
| `dijkstra.cpp` | 7 | vector<vector<pair<long long,int>>>、priority_queue；平行边、过时项、零权环、不可达、超过 32 位的距离、稀疏与稠密图 | Bellman–Ford |
| `sliding-maximum.cpp` | 8 | vector<long long>、deque<int>；窗口 1/全长、全相等、单调、负数、长度 129 | 逐窗口全扫描 |
| `coordinate-compression.cpp` | 8 | vector、sort、unique、erase、lower_bound；空、重复、逆序、大正负键 | TreeSet 排序加独立 rank 映射 |
| `interval-scheduling.cpp` | 7 | sort、pair、multiset、upper_bound、erase；0 台机器、相接端点、重复区间、大时间值 | 穷举全部子集，半开区间重叠数验证最优值 |
| `sparse-accumulation.cpp` | 6 | map<long long,long long>；抵消为 0 后擦除并重新插入、范围和、空/反向区间 | TreeMap 更新，范围查询全扫描 |
| `word-ranking.cpp` | 7 | iostream、map<string,int>、vector<pair<string,int>>、自定义排序；频率相同、大小写、15/16/23/24/63/64 字节字符串 | HashMap 统计后独立排序 |
| `bitset-knapsack.cpp` | 9 | bitset<129>、移位及合并；0、63/64/128、超过容量、重复权值、稠密可达状态 | 布尔数组倒序 0/1 DP，比较全部 129 位与 count |
| `grid-bfs.cpp` | 8 | queue<pair<int,int>>、vector<string>、嵌套 vector；障碍起点、孤岛、长行/列、随机障碍 | 整张网格反复松弛，比较所有距离 |
| `tracked-sequences.cpp` | 3 | vector<Record>、deque<Record>；元素别名插入、resize、擦除、复制/移动/赋值/swap、保留端点引用、reserve(capacity)、clear 后复用 | ArrayList 模型加全序列校验和；self 地址及存活数不变量 |

合计 **9 个程序、63 组输入或操作序列、18 个优化模式参数**。每个参数运行 MiniC native、MiniC debug、G++ 系统 STL 和 G++ 自研 .mh 四路，逐一与 Java 模型比较。debug 执行源 IR；优化模式只改变 native 生成路径。除较长 Record 操作序列使用有序校验和外，题目输出完整逻辑结果；Record 额外要求地址不变量和最终零存活/零错误。

测试不依赖未指定的 capacity 增长、移动次数、等价元素顺序或已失效的引用。固定 seed 为 44117、95171，另一个 Record 序列为 104729，均与性能生成器的数据独立。

## 验收状态

新增 Java 测试编译成功；18 个模式参数全部通过，没有失败、错误、中止或跳过。每个参数的四路 stdout 都与 Java 独立模型一致。此批新增覆盖首次完整执行即通过，没有伪造 RED，也没有为其改动产品实现。

| 验收项 | 实际记录 |
|---|---|
| 编译器 | `7d656ca781b52808c562a36353ddb17e7562719d`：`6d6441d` 加最终 AdjacentResultForwarding |
| 库与源快照基线 | `6d6441d633cf3beb3824177fd60aa1a36c8bf51f` |
| 平台与参考工具 | Windows x64；JDK 21；本机 MinGW G++ 8.1，C++17 |
| 新功能计数 | 9 程序、63 输入/操作序列、18 模式参数；每参数 4 后端 |
| JUnit | 18 成功，0 失败/错误/跳过；正常退出 0 |
| 本地证据 | `build/oj-functional-expansion/first-run.txt`、`xml/TEST-junit-jupiter.xml`、`results/<程序>-<模式>/` 下的输入、独立期望及四路结果 |

root 后续统一新快照的复验应另记 revision，不将其结果倒写为此轮证据。将来出现真实失败须保留原程序、输入、独立期望及各后端输出，先定位实现问题，不能通过削弱程序语义绕过。

本次不更改 84 项能力状态，也不扩大到完整 ISO C++、通用异常展开、自定义 allocator、完整 locale 或 long double。功能一致性与新增性能矩阵每项 OPT/system ≤ 1.2 的目标分别验收。
