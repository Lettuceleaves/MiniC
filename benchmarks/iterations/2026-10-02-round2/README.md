# STL performance iteration 2 — 2026-10-02

Diagnostic result: **2 of 15 workloads meet the selected ≤1.2× system STL threshold; 13 remain above it. The overall target is not complete.** Mixed bitset count is 1.094× and sparse bitset count is 0.882×. Twenty measured samples from other groups are shorter than 100 ms; this iteration does not establish the final acceptance gate.

MiniC OPTIMIZED and G++ own-library executables use frozen `eda431e3de785c397c3c1c70a419765702d95fd8`. System STL executables are reused from the verified `e56d6d1` baseline. All workload source hashes and exact original input/oracle bytes match. The target process QPC wall includes startup, input, setup and full output hashing. No subtraction or workload-only timer is used. G++ uses the recorded Windows x64 version 8.1.0 and reference flags.

Before sampling, all non-UI sources compiled and the combined targeted suite passed 556/556 tests. This is a targeted integration result, not a replacement for the earlier full-suite run on e56d6d1. All cooperating compile/test jobs finished before timing; unrelated OS/user activity was not controlled.

Each workload ran one preflight, two warmups and six measured repetitions per build, using all six build-order permutations. All 405 native executions matched the original independent oracle, including all 270 measurements. Ratios below are medians; raw values and MAD are preserved.

| Workload | MiniC OPT ms | System STL ms | OPT / system | Own library G++ / system |
| --- | ---: | ---: | ---: | ---: |
| vector-sort | 299.823 | 138.365 | 2.167 | 0.963 |
| binary-search | 298.385 | 157.902 | 1.890 | 0.990 |
| priority-queue | 218.898 | 112.173 | 1.951 | 0.984 |
| ordered-map | 336.590 | 194.795 | 1.728 | 0.857 |
| deque | 537.759 | 134.663 | 3.993 | 0.969 |
| string | 329.351 | 115.350 | 2.855 | 0.936 |
| bitset | 347.685 | 89.594 | 3.881 | 0.995 |
| string-short | 290.256 | 95.417 | 3.042 | 1.049 |
| bitset-count | 675.641 | 617.540 | 1.094 | 0.261 |
| bitset-count-sparse | 564.742 | 639.980 | 0.882 | 0.263 |
| bitset-count-dense | 447.514 | 332.355 | 1.346 | 0.379 |
| bulk-vector-assign | 158.604 | 117.765 | 1.347 | 1.036 |
| bulk-vector-erase | 174.477 | 106.454 | 1.639 | 0.980 |
| bulk-pointer-copy | 154.499 | 116.216 | 1.329 | 0.974 |
| bulk-pointer-copy-backward | 156.340 | 113.815 | 1.374 | 1.030 |

Relative to iteration 1, binary-search ratios changed from 2.314 to 1.890, ordered-map from 2.082 to 1.728, deque from 5.936 to 3.993, string from 5.114 to 2.855, and short-string from 6.470 to 3.042. These are separate sampling runs, not simultaneously paired old/new estimates. This revision adds CFG merging, private-address and read-only-parameter promotion, post-inline scalar preparation, loop-aware register allocation/inlining, predicate-chain branch fusion, a scalar aggregate storage-width fix, and library byte operations. The next work targets remaining temporary copies, constant-argument inline candidates, field-address loads and string data access.

`raw-evidence.zip` stores exact files and copied native artifacts as `blobs/<sha256>`; `raw-members.json` maps original relative names. The archive includes the full report, configuration, compile commands, supervisor properties, raw output, input/oracle, runner/build helpers, integration XML and original reference reports. Immutable Git revisions identify product sources. The source/class tree remains in `build/optimization-round2`; original toolchain provenance also remains in the previous formal baseline archive. Paths inside raw reports describe their original run locations.
