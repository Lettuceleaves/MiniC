# STL 原生性能实测

候选提交：`e56d6d149c02432bc51f01bc06ef4f2de786f6c4`；历史提交：`b6464790fd3748edfe2d02981f0f6a421a133d6b`。

所有组通过完整性、源码与产物哈希、JSON/CSV 一致性和独立输出 oracle 检查。每组 8 次测量；四构建与历史/候选使用相同 size、rounds、seed、输入及输出哈希。

时间为目标进程墙钟的中位数 ± MAD（毫秒），包含启动、输入、工作负载、校验输出；MAD 是中位绝对偏差，不是置信区间。构建时间与运行时间分开。

环境：Windows 11 10.0 amd64；HKEY_LOCAL_MACHINE\HARDWARE\DESCRIPTION\System\CentralProcessor\0     ProcessorNameString    REG_SZ    12th Gen Intel(R) Core(TM) i9-12900H；g++.exe (x86_64-posix-seh-rev0, Built by MinGW-W64 project) 8.1.0 Copyright (C) 2018 Free Software Foundation, Inc. This is free software; see the source for copying conditions.  There is NO warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.。

采样条件：Windows desktop; artifacts/executables on D:; own build/test jobs finished before sampling; other user/OS activity not controlled; no power-policy or priority changes. Same input for baseline and candidate; revisions measured in separate serial batches.

## 候选四构建时间

| 负载 | size × rounds | MiniC BASELINE | MiniC OPTIMIZED | G++ + 自研库 | G++ + 系统 STL |
|---|---:|---:|---:|---:|---:|
| vector-sort | 4096 × 512 | 659.582 ± 5.607 | 394.765 ± 7.028 | 135.694 ± 3.589 | 139.207 ± 4.601 |
| binary-search | 4096 × 256 | 695.342 ± 6.810 | 448.449 ± 5.307 | 154.539 ± 3.401 | 162.904 ± 2.532 |
| priority-queue | 4096 × 256 | 488.235 ± 3.793 | 299.496 ± 4.326 | 115.865 ± 5.881 | 115.939 ± 3.160 |
| ordered-map | 4096 × 256 | 808.759 ± 4.233 | 489.712 ± 2.269 | 156.972 ± 3.464 | 193.278 ± 2.574 |
| deque | 4096 × 4096 | 4482.377 ± 8.696 | 2121.037 ± 13.843 | 190.820 ± 2.788 | 131.531 ± 6.181 |
| string | 4096 × 4096 | 935.951 ± 5.170 | 568.526 ± 6.550 | 126.871 ± 2.994 | 115.851 ± 5.393 |
| bitset | 4096 × 8192 | 829.752 ± 3.635 | 572.428 ± 4.067 | 97.377 ± 5.724 | 97.403 ± 4.590 |
| string-short | 4096 × 256 | 1165.804 ± 1.574 | 611.953 ± 5.186 | 105.607 ± 4.587 | 97.036 ± 3.883 |
| bitset-count | 4096 × 1024 | 2688.914 ± 2.647 | 931.907 ± 3.820 | 166.335 ± 2.112 | 628.444 ± 2.110 |
| bitset-count-sparse | 4096 × 1024 | 2083.913 ± 1.716 | 920.641 ± 5.290 | 179.077 ± 5.324 | 641.517 ± 3.540 |
| bitset-count-dense | 4096 × 512 | 1864.586 ± 5.750 | 621.923 ± 3.964 | 127.826 ± 3.490 | 335.899 ± 5.655 |

## 候选比值

比值均为耗时相除：小于 1 表示分子耗时更少。

| 负载 | OPT / 系统 STL | 自研库 G++ / 系统 STL | OPT / 自研库 G++ |
|---|---:|---:|---:|
| vector-sort | 2.836 | 0.975 | 2.909 |
| binary-search | 2.753 | 0.949 | 2.902 |
| priority-queue | 2.583 | 0.999 | 2.585 |
| ordered-map | 2.534 | 0.812 | 3.120 |
| deque | 16.126 | 1.451 | 11.115 |
| string | 4.907 | 1.095 | 4.481 |
| bitset | 5.877 | 1.000 | 5.878 |
| string-short | 6.306 | 1.088 | 5.795 |
| bitset-count | 1.483 | 0.265 | 5.603 |
| bitset-count-sparse | 1.435 | 0.279 | 5.141 |
| bitset-count-dense | 1.852 | 0.381 | 4.865 |

## 前后变化

四个比值均为候选 / 历史耗时。前后为不同串行批次；系统 STL 的比值反映同一工作负载的批次波动，不能把全部差异归因于代码。各历史组的完整 median/MAD 保存在 summary.json。

| 负载 | 历史 OPT ms ± MAD | MiniC BASELINE 比值 | MiniC OPTIMIZED 比值 | 自研库 G++ 比值 | 系统 STL 比值 |
|---|---:|---:|---:|---:|---:|
| vector-sort | 403.026 ± 5.384 | 0.987 | 0.980 | 1.022 | 0.962 |
| binary-search | 436.826 ± 5.132 | 1.016 | 1.027 | 1.000 | 1.024 |
| priority-queue | 294.133 ± 2.466 | 0.993 | 1.018 | 1.055 | 0.996 |
| ordered-map | 485.005 ± 3.701 | 1.018 | 1.010 | 1.028 | 1.037 |
| deque | 2133.473 ± 15.099 | 0.995 | 0.994 | 1.008 | 0.982 |
| string | 561.068 ± 6.202 | 1.014 | 1.013 | 1.060 | 1.096 |
| bitset | 593.851 ± 2.803 | 0.982 | 0.964 | 1.007 | 1.043 |
| string-short | 993.026 ± 13.766 | 0.744 | 0.616 | 0.368 | 1.037 |
| bitset-count | 5626.317 ± 40.003 | 0.221 | 0.166 | 0.115 | 1.003 |
| bitset-count-sparse | 992.273 ± 4.535 | 1.222 | 0.928 | 1.019 | 1.007 |
| bitset-count-dense | 7900.874 ± 160.334 | 0.086 | 0.079 | 0.066 | 0.996 |

## 候选进程内存

每格为 Job Object 的进程峰值提交内存：8 次样本的中位数 / 最大值（MiB）。它不是 RSS、实际物理驻留内存，也不是容器分配器的存活字节。分配/复制等计数探针单独报告。

| 负载 | MiniC BASELINE | MiniC OPTIMIZED | G++ + 自研库 | G++ + 系统 STL |
|---|---:|---:|---:|---:|
| vector-sort | 1.184 / 1.207 | 1.197 / 1.207 | 1.197 / 1.227 | 1.197 / 1.219 |
| binary-search | 1.195 / 1.227 | 1.193 / 1.223 | 1.195 / 1.227 | 1.191 / 1.242 |
| priority-queue | 1.191 / 1.211 | 1.191 / 1.203 | 1.195 / 1.223 | 1.189 / 1.207 |
| ordered-map | 1.189 / 1.227 | 1.189 / 1.211 | 1.211 / 1.242 | 1.203 / 1.207 |
| deque | 1.195 / 1.207 | 1.188 / 1.223 | 1.197 / 1.227 | 1.197 / 1.203 |
| string | 1.189 / 1.230 | 1.191 / 1.242 | 1.227 / 1.234 | 1.225 / 1.234 |
| bitset | 1.188 / 1.230 | 1.193 / 1.227 | 1.197 / 1.223 | 1.197 / 1.207 |
| string-short | 1.191 / 1.211 | 1.191 / 1.242 | 1.203 / 1.242 | 1.199 / 1.234 |
| bitset-count | 1.195 / 1.238 | 1.197 / 1.242 | 1.205 / 1.227 | 1.193 / 1.234 |
| bitset-count-sparse | 1.188 / 1.207 | 1.189 / 1.227 | 1.197 / 1.234 | 1.199 / 1.219 |
| bitset-count-dense | 1.193 / 1.230 | 1.188 / 1.203 | 1.193 / 1.199 | 1.191 / 1.242 |

## 短样本提示

- bitset / 历史 / G++ + 自研库：6/8 次低于 100 ms，最短 90.103 ms。
- bitset / 历史 / G++ + 系统 STL：7/8 次低于 100 ms，最短 89.939 ms。
- bitset / 候选 / G++ + 自研库：5/8 次低于 100 ms，最短 87.743 ms。
- bitset / 候选 / G++ + 系统 STL：5/8 次低于 100 ms，最短 91.097 ms。
- string-short / 历史 / G++ + 系统 STL：7/8 次低于 100 ms，最短 91.571 ms。
- string-short / 候选 / G++ + 系统 STL：5/8 次低于 100 ms，最短 93.145 ms。

## 批量复制/移动配对观察

使用独立的固定提交工作负载，历史与候选按构建相邻交错。时间仍包含启动、数据准备、完整顺序哈希和擦除后尾部重建；不减去控制时间，也不称为纯 memcpy 延迟。四种模式的每组均为 8 次。

| 模式 | size × rounds × iterations | MiniC BASELINE ms ± MAD | MiniC OPTIMIZED ms ± MAD | 自研库 G++ ms ± MAD | 系统 STL ms ± MAD |
|---|---:|---:|---:|---:|---:|
| VECTOR_ASSIGN | 4096 × 2 × 8192 | 426.548 ± 4.189 | 216.954 ± 4.510 | 118.136 ± 0.629 | 116.598 ± 2.256 |
| VECTOR_ERASE | 4096 × 2 × 8192 | 501.056 ± 2.929 | 248.759 ± 5.145 | 110.266 ± 4.596 | 106.043 ± 2.891 |
| POINTER_COPY | 4096 × 2 × 8192 | 420.467 ± 2.301 | 207.306 ± 1.093 | 113.892 ± 1.436 | 113.362 ± 0.446 |
| POINTER_COPY_BACKWARD | 4096 × 2 × 8192 | 426.394 ± 7.703 | 208.779 ± 2.719 | 114.874 ± 2.554 | 113.892 ± 1.119 |

下表均为中位耗时之比；逐轮配对比值的 median/MAD 另存 JSON。

| 模式 | OPT / 系统 STL | 自研库 G++ / 系统 STL | OPT / 自研库 G++ | 候选/历史 BASELINE | 候选/历史 OPT | 候选/历史自研 G++ | 候选/历史系统 STL |
|---|---:|---:|---:|---:|---:|---:|---:|
| VECTOR_ASSIGN | 1.861 | 1.013 | 1.836 | 0.566 | 0.531 | 0.863 | 0.984 |
| VECTOR_ERASE | 2.346 | 1.040 | 2.256 | 0.706 | 0.707 | 0.937 | 0.998 |
| POINTER_COPY | 1.829 | 1.005 | 1.820 | 0.603 | 0.579 | 0.814 | 0.977 |
| POINTER_COPY_BACKWARD | 1.833 | 1.009 | 1.817 | 0.606 | 0.578 | 0.850 | 0.979 |

批量模式没有低于配置时长的测量样本。

## 中断批次与重跑

以下原始目录原地保留，整批不计入任何统计；本报告只纳入明确选择且完整通过验证的批次。中断报告与日志的 SHA256 已核对并保存在 summary.json。

| 排除目录 | 原因 |
|---|---|
| baseline-rounds-512 | Windows AccessDeniedException replacing report.json; preserved and excluded from statistics; the entire 512-round group is rerun in baseline-retry-512. Active report reads were stopped before recovery. |

纳入的历史批次：`baseline-rounds-256`、`baseline-retry-512`、`baseline-rounds-1024`、`baseline-rounds-4096`、`baseline-rounds-8192`。

源码、可执行产物、编译命令、实际优化 pass、编译耗时、CPU 时间及原始样本引用保存在同目录 summary.json。编译每产物仅测一次，不作编译速度分布结论。此报告不将过程墙钟称为算法内核时间，也不据少量负载宣称普遍接近系统 STL。
