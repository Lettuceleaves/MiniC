# STL 兼容与范围

## OJ / STL 兼容补齐

标准头文件由 `lib/stl/*.mh` 实现；算法程序可以直接使用 `#include <vector>`、`#include <unordered_map>` 或 `#include <bits/stdc++.h>`，并通过 `main`、标准输入和标准输出运行。

| 本轮补齐项 | 支持范围 |
|---|---|
| 成员多声明器 | `struct E { int u, v, w; };`；同一声明中的指针、数组、默认成员初始化独立生效；模板实参逗号、括号内逗号和 lambda 默认值不会截断声明。 |
| 分配和所有权 | 单对象及动态数组 `new/delete/delete[]`、多维固定内层数组、值初始化、超对齐、空指针删除、数组逆序析构；全局分配／释放重载；`unique_ptr`、`shared_ptr`、`make_unique<T>`、`make_unique<T[]>`、`make_shared`、移动所有权、自定义删除器与共享别名指针。 |
| 七个头文件 | `<numeric>`、`<unordered_map>`、`<unordered_set>`、`<list>`、`<array>`、`<tuple>`、`<sstream>`，同时由 `<bits/stdc++.h>` 引入。 |
| `long double` | 独立的源语言类型与 `L` 字面量、重载／模板／算术类型规则、常量与全局初始化、变参、`cmath` 常用重载、流输入输出、原生和调试器的 `%Lf`。当前 Windows x64 后端使用 8 字节 binary64，精度与 `double` 相同。 |

哈希容器使用可扩容的桶和链式节点，支持自定义 hash/equal、查找、插入、删除、迭代、reserve/rehash、复制／移动，map 还支持 `try_emplace` 和 `insert_or_assign`；键不变时平均查找、插入和删除复杂度为 O(1)，最坏为 O(n)。扩容保留元素地址。`list` 使用双向链表，单节点插入、删除、splice 为 O(1)，稳定排序为 O(n log n)，并保留节点地址。

`numeric` 包含 accumulate、iota、inner_product、partial_sum、adjacent_difference、gcd、lcm 和无执行策略的 reduce；`array` 支持聚合初始化、零长度、迭代与结构化绑定；`tuple` 支持索引 get、make_tuple、tie、forward_as_tuple、比较与结构化绑定；字符串流支持数值／字符串输入输出、getline、格式控制、状态恢复和读写位置。

这里提供算法题常用接口，不宣称完整 ISO C++ 标准库覆盖。当前不包含异常展开、`nothrow`／类专用分配、`weak_ptr`／并发共享计数、自定义 allocator、unordered 多重容器／节点句柄、tuple_cat/apply、locale/streambuf。分配失败和越界检查沿用现有 `.mh` 库的终止策略。`long double` 不是 GCC x87 的 80 位扩展格式。

本轮功能补齐不代表 Hot100 全题覆盖、大数据复杂度对照或每项性能不超过系统 STL 1.2 倍的目标已经达成。
