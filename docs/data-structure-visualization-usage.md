# MiniC 数据结构可视化使用文档

这份文档面向使用 MiniC Workbench 的老师、助教和大一新生。目标是用很少的注释告诉调试器“这段 C 代码里的数据结构应该怎么看”，然后在 **数据结构** 视图里得到数组、链表、树、哈希表等图形化结果。

## 1. 启动项目

在项目根目录执行：

```powershell
.\gradlew.bat runUi
```

启动后，在 UI 中打开或粘贴 MiniC 程序，点击 **启动** 进入调试，再切换到 **数据结构** 标签查看可视化结果。

如果只是验证功能是否正常，可以运行：

```powershell
.\gradlew.bat test
```

## 2. 最小使用流程

1. 在 C 源码顶部或变量附近写一行 `// @visual ...` 注释。
2. `root` 写要观察的根变量名。
3. `kind` 写希望使用的可视化模板。
4. 启动调试，运行到变量已经构建完成的位置。
5. 打开 **数据结构** 视图。

示例：

```c
// @visual root=head kind=singly-list label=value
struct Node {
    int value;
    struct Node *next;
};

int main() {
    struct Node a;
    struct Node b;
    struct Node *head;
    a.value = 1;
    b.value = 2;
    a.next = &b;
    b.next = NULL;
    head = &a;
    return head->value + head->next->value;
}
```

这行注释的含义是：

- `root=head`：从 `head` 这个变量开始看。
- `kind=singly-list`：按单链表模板画。
- `label=value`：每个节点上显示 `value` 字段。

## 3. 注释语法

基础格式：

```c
// @visual root=变量名 kind=模板名 其他选项...
```

常用选项：

| 选项 | 作用 | 示例 |
|---|---|---|
| `root` | 可视化从哪个变量开始，必须是变量名 | `root=head` |
| `kind` | 选择可视化模板 | `kind=binary-tree` |
| `name` | 给图起一个显示名称，默认等于 `root` | `name=tree` |
| `label` | 节点显示哪个字段 | `label=value` |
| `fields` | 结构体只显示哪些字段 | `fields=x,y,next` |
| `next` | 链式结构的后继字段，默认 `next` | `next=next` |
| `prev` | 双向链表的前驱字段，默认 `prev` | `prev=prev` |
| `left` | 二叉树左孩子字段，默认 `left` | `left=left` |
| `right` | 二叉树右孩子字段，默认 `right` | `right=right` |
| `rows` | 矩阵行数 | `rows=2` |
| `columns` | 矩阵列数 | `columns=3` |
| `max-depth` | 链/树最多展开多少层 | `max-depth=32` |

`kind` 大小写和连接符比较宽松，下面写法等价：

```c
// @visual root=head kind=doubly-list
// @visual root=head kind=doubly_list
// @visual root=head kind=DOUBLY_LIST
```

## 4. 模板速查

### 基础值和数组

| 模板 | 适合的 C 结构 | 默认效果 |
|---|---|---|
| `scalar` | `int x;`、`char c;` | 单个值 |
| `pointer` | `int *p;`、`struct Node *p;` | 指针箭头 |
| `pointer-chain` | `int **pp;` | 多级指针链 |
| `array` | `int a[10];` | 一维格子 |
| `pointer-array` | `struct Node *nexts[4];` | 每格是一个指针 |
| `matrix` | `int dp[2][3];` 或线性数组配 `rows/columns` | 二维表格 |
| `heap` | 用数组实现的堆 | 数组格子 + 父子下标 metadata |
| `fenwick-tree` | 树状数组 `bit[]` | 数组格子 + lowbit 覆盖区间 |
| `stack` | 顺序栈底层数组 | 数组模板，保留 `top` 语义 |
| `queue` | 顺序队列底层数组 | 数组模板，保留 `front/rear` 语义 |
| `deque` | 双端队列底层数组 | 数组模板，保留双端语义 |
| `circular-queue` | 循环队列底层数组 | 数组模板，保留环绕语义 |

### 结构体

| 模板 | 适合的 C 结构 | 默认效果 |
|---|---|---|
| `struct` | `struct Point p;` | 字段列表 |
| `struct-pointer` | `struct Node *p;` | 指向结构体的箭头 |
| `struct-pointer-chain` | `head->next->next` 这类结构 | 结构体指针链 |
| `struct-array` | `struct Point points[3];` | 每个数组元素是结构体 |
| `struct-matrix` | 结构体二维表 | 二维结构体格子 |
| `record-table` | 表格式结构体数组 | 结构体数组别名 |

### 链式结构

| 模板 | 适合的 C 结构 | 默认字段 |
|---|---|---|
| `singly-list` | 单链表 | `next=next` |
| `struct-list` | 单链表旧名称，兼容保留 | `next=next` |
| `doubly-list` | 双向链表 | `next=next`，`prev=prev` |
| `lru-list` | LRU 的双向链表部分 | `next/prev`，自动标记 `head/tail` |

### 树和图

| 模板 | 适合的 C 结构 | 默认字段/布局 |
|---|---|---|
| `binary-tree` | 二叉树 | `left=left`，`right=right`，层级布局 |
| `general-tree` | 多叉树 | 当前先保留模板语义 |
| `trie` | 字典树 | 当前先保留模板语义 |
| `segment-tree` | 线段树 | 当前先保留模板语义 |
| `hash-chain-table` | 桶数组 + 拉链法 | 桶横排，链向下挂 |
| `adjacency-list` | 邻接表 | 桶挂链布局 |
| `graph` | 通用图 | 显式节点/边或运行时事件 |
| `persistent-tree` | 可持久化树 | 当前先保留模板语义 |

## 5. 常见结构示例

### 5.1 一维数组

```c
// @visual root=values kind=array
int main() {
    int values[3];
    values[0] = 10;
    values[1] = 20;
    values[2] = 30;
    return values[1];
}
```

图上会看到 3 个格子，下标分别是 `[0]`、`[1]`、`[2]`。

### 5.2 结构体

```c
// @visual root=point kind=struct fields=x,y
struct Point {
    int x;
    int y;
};

int main() {
    struct Point point;
    point.x = 3;
    point.y = 4;
    return point.x + point.y;
}
```

`fields=x,y` 表示只显示 `x` 和 `y` 两个字段。

### 5.3 单链表

```c
// @visual root=head kind=singly-list label=value
struct Node {
    int value;
    struct Node *next;
};

int main() {
    struct Node a;
    struct Node b;
    struct Node c;
    struct Node *head;
    a.value = 1;
    b.value = 2;
    c.value = 3;
    a.next = &b;
    b.next = &c;
    c.next = NULL;
    head = &a;
    return head->value;
}
```

不写 `next=next` 也可以，因为单链表模板默认使用 `next` 字段。

### 5.4 双向链表 / LRU 链

```c
// @visual root=head kind=lru-list label=value
struct Node {
    int value;
    struct Node *prev;
    struct Node *next;
};

int main() {
    struct Node a;
    struct Node b;
    struct Node c;
    struct Node *head;
    a.value = 10;
    b.value = 20;
    c.value = 30;
    a.prev = NULL;
    a.next = &b;
    b.prev = &a;
    b.next = &c;
    c.prev = &b;
    c.next = NULL;
    head = &a;
    return head->value;
}
```

`lru-list` 会把 `next` 看作主边，把 `prev` 看作辅助边，并自动标出头尾节点。

### 5.5 二叉树

```c
// @visual root=root kind=binary-tree label=value
struct Node {
    int value;
    struct Node *left;
    struct Node *right;
};

int main() {
    struct Node n1;
    struct Node n2;
    struct Node n3;
    struct Node *root;
    n1.value = 10;
    n2.value = 5;
    n3.value = 15;
    n1.left = &n2;
    n1.right = &n3;
    n2.left = NULL;
    n2.right = NULL;
    n3.left = NULL;
    n3.right = NULL;
    root = &n1;
    return root->value;
}
```

`binary-tree` 默认使用 `left` 和 `right` 字段，不需要每次手写边映射。

### 5.6 哈希表：桶数组 + 拉链法

```c
// @visual root=buckets kind=hash-chain-table label=key
struct Entry {
    int key;
    struct Entry *next;
};

int main() {
    struct Entry *buckets[3];
    struct Entry e0;
    struct Entry e1;
    struct Entry e2;
    buckets[0] = &e0;
    buckets[1] = NULL;
    buckets[2] = &e2;
    e0.key = 10;
    e0.next = &e1;
    e1.key = 20;
    e1.next = NULL;
    e2.key = 30;
    e2.next = NULL;
    return e0.key + e1.key + e2.key;
}
```

`hash-chain-table` 的视觉效果是：

- 桶数组横向排列。
- 每个桶下面垂直挂自己的链。
- 每个链节点显示 `label=key` 指定的 `key` 字段。

### 5.7 堆数组

```c
// @visual root=heap kind=heap
int main() {
    int heap[4];
    heap[0] = 50;
    heap[1] = 30;
    heap[2] = 40;
    heap[3] = 10;
    return heap[0];
}
```

当前堆模板会保留数组视图，并给每个格子补充父子关系 metadata：

- `parentIndex`
- `leftIndex`
- `rightIndex`

### 5.8 树状数组

```c
// @visual root=bit kind=fenwick-tree
int main() {
    int bit[4];
    bit[0] = 1;
    bit[1] = 3;
    bit[2] = 2;
    bit[3] = 8;
    return bit[3];
}
```

`fenwick-tree` 会给每个格子补充：

- `fenwickIndex`：从 1 开始的树状数组下标。
- `rangeStart` / `rangeEnd`：这一格覆盖的区间。

## 6. 运行时状态

下面这些不是单独的模板，而是程序运行时自然形成的状态。系统会根据节点和边自动标记 metadata。

| 状态 | 什么时候出现 | 怎么理解 |
|---|---|---|
| `null-edge` | 指针指向 `NULL` | 链、树或桶到这里结束 |
| `self-loop` | `node->next = node` | 节点指向自己 |
| `cycle` | 链或图形成环 | 不需要提前声明“环形链表” |
| `shared-node` | 多条边指向同一节点 | 树里出现共享子结构，或多个指针别名 |
| `rewire` | 同一条逻辑边被改到新目标 | 链表插入、删除、移动时常见 |
| `primary-edge` | 模板主边 | 例如链表的 `next`、树的 `left/right` |
| `auxiliary-edge` | 辅助边 | 例如双向链表的 `prev` |

例子：环形链表不需要写 `kind=cycle-list`。先按链表写：

```c
// @visual root=head kind=singly-list label=value
```

当程序执行到：

```c
tail->next = head;
```

系统发现边指回已有节点，就会把这条边标记为 `cycle`。

## 7. 运行时图事件：构建过程可视化

如果希望观察“构建过程”，可以使用运行时 graph 注释。它适合递归建树、插入哈希表、链表重连等过程。

基本格式：

```c
// @visual graph name=tree kind=tree root=root mode=runtime function=build
// @visual-map node graph=tree id=node label=node->value
// @visual-map edge graph=tree key=left from=node to=node->left
// @visual-map edge graph=tree key=right from=node to=node->right
```

含义：

- `mode=runtime`：这个图不是一次性静态扫描，而是随调试事件变化。
- `function=build`：只关注 `build` 函数中的构建动作。
- `@visual-map node`：告诉系统哪个运行时值代表节点。
- `@visual-map edge`：告诉系统哪个字段代表边。

普通数据结构展示优先用 `// @visual root=... kind=...`，只有需要过程动画或递归构建轨迹时再用 runtime graph。

## 8. 示例文件

当前仓库里有这些可直接打开的样例：

| 文件 | 说明 |
|---|---|
| `samples/visual_scalar.mc` | 标量 |
| `samples/visual_pointer.mc` | 指针 |
| `samples/visual_pointer_chain.mc` | 多级指针 |
| `samples/visual_array.mc` | 一维数组 |
| `samples/visual_pointer_array.mc` | 指针数组 |
| `samples/visual_matrix.mc` | 矩阵 |
| `samples/visual_struct.mc` | 结构体 |
| `samples/visual_struct_pointer.mc` | 结构体指针 |
| `samples/visual_struct_pointer_chain.mc` | 结构体指针链 |
| `samples/visual_struct_array.mc` | 结构体数组 |
| `samples/visual_struct_matrix.mc` | 结构体矩阵 |
| `samples/visual_struct_list.mc` | 结构体链表 |
| `samples/visual_lru_list.mc` | LRU/双向链表 |
| `samples/visual_binary_tree.mc` | 二叉树 |
| `samples/visual_hash_chain_table.mc` | 拉链法哈希表 |
| `samples/visual_heap.mc` | 堆数组 |
| `samples/visual_fenwick_tree.mc` | 树状数组 |

## 9. 建议写法

### 推荐

尽量使用简单、常见的字段名：

```c
struct Node {
    int value;
    struct Node *next;
};
```

对应注释：

```c
// @visual root=head kind=singly-list label=value
```

### 需要自定义时

如果你的字段名不是默认名，就显式告诉系统：

```c
// @visual root=head kind=singly-list next=link label=data
struct Node {
    int data;
    struct Node *link;
};
```

二叉树同理：

```c
// @visual root=root kind=binary-tree left=lson right=rson label=key
```

## 10. 常见问题

### 为什么没有看到图？

常见原因：

1. `root` 写错了，必须是当前作用域里能看到的变量名。
2. 运行还没到数据结构构建完成的位置。
3. 指针还没有赋值，仍然是未初始化或 `NULL`。
4. 注释里的 `kind` 拼错了。

建议先在构建完成后的位置打断点，再打开 **数据结构** 视图。

### 为什么哈希表链没有显示预期节点？

`hash-chain-table` 最稳定的写法是让桶数组指向可寻址的结构体变量：

```c
struct Entry *buckets[3];
struct Entry e0;
buckets[0] = &e0;
```

当前调试内存图对“桶指向结构体数组元素”的支持还不够细：

```c
struct Entry entries[3];
buckets[0] = &entries[0];
```

这种写法有时会把目标解析成整个 `entries` 数组，而不是单个 `entries[0]` 节点。教学样例建议先使用独立结构体变量，或者使用项目中已经验证过的样例作为参考。

### 为什么环形链表不用单独模板？

因为环是在执行过程中形成的，程序运行前不一定能预判。应该先声明它是链表：

```c
// @visual root=head kind=singly-list label=value
```

当某条 `next` 指回已有节点时，系统会自动标记 `cycle` 状态。

### 数据结构视图为什么文字不多？

数据结构 tab 现在以图形为主，避免把大量重复解释直接铺在界面里。详细解释会保留在元素 metadata/tooltip 和说明模板中。课堂讲解时，建议先让学生看图形关系，再点击或查看对应元素说明。

## 11. 开发者验证命令

修改可视化相关代码后，建议至少跑：

```powershell
.\gradlew.bat test --tests minic.runtime.debug.visual.VisualAnnotationParserTest --tests minic.runtime.debug.visual.VisualProjectionBuilderTest --tests minic.uiapi.UiDebugDataStructureViewBuilderTest --tests minic.ui.MiniCDebugPaneTest --tests minic.uiapi.UiDebugDataStructureEndToEndTest
```

提交前跑完整测试：

```powershell
.\gradlew.bat test
```
