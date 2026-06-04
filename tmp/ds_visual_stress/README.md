# MiniC Data Structure Visual Stress Samples

这些文件是临时测试样例，用来压测 MiniC 调试器的数据结构可视化能力。每个 `.mc` 文件都是独立程序，可以在 Workbench 中单独打开。

运行建议：

1. 用 `.\gradlew.bat runUi` 启动 Workbench。
2. 打开本目录中的某个 `.mc` 文件。
3. 启动调试。
4. 运行到标有 `// @break` 的 `return` 行。
5. 切换到 **数据结构** 视图观察结构。

样例列表：

| 文件 | 内容 |
|---|---|
| `01_lru_hash.mc` | 带哈希表优化的 LRU，数组桶 + 拉链 + 双向链 |
| `02_min_heap.mc` | 小根堆，多次插入、删除堆顶、修改值 |
| `03_red_black_tree.mc` | 红黑树插入、旋转、查找、删除标记 |
| `04_b_tree.mc` | B 树插入、分裂、查找、删除标记 |
| `05_trie.mc` | 字典树插入、查询、前缀统计、删除标记 |
| `06_dijkstra_graph.mc` | 带权无向图的 Dijkstra |
| `07_open_address_hash_resize.mc` | 开放地址法哈希表，线性探测和扩容 |
| `08_cycle_linked_list.mc` | 构建链表并判断是否成环 |
| `09_three_dim_array.mc` | 三维数组写入、更新、查询 |
| `10_segment_tree.mc` | 线段树构建、区间查询、单点修改 |

注意：

- MiniC 当前没有真正的 `malloc/free`，样例统一使用结构体数组作为节点池。
- 若要观察“构建过程”，可以单步运行；若只看最终形态，直接运行到 `// @break`。
- 哈希拉链和 LRU 同时使用 `hash-chain-table`、`lru-list` 和 `struct-array` 注释，方便从多个角度看同一份内存。
