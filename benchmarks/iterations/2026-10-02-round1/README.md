# STL performance iteration 1 — 2026-10-02

Diagnostic result: **none of the 15 workloads meets the required ≤1.2× system STL gate**. This is an intermediate optimization checkpoint, not completion.

MiniC OPTIMIZED and G++ own-library executables come from frozen `7a1f7317b95cd8eea9b1c79909b8731bb4b47fb7`. System STL executables are reused from the prior verified `e56d6d1` baseline. All workload source hashes and exact original input/oracle bytes match. Target process QPC wall time includes startup, input, setup and full output hashing; there is no control subtraction or kernel-only timing. G++ uses the recorded Windows x64 version 8.1.0 and reference flags.

Each workload ran one correctness preflight, two warmups and six measured repetitions for each of three builds. All six build-order permutations were used. All 405 executions passed the independent original oracle, including 270 measurements. Cooperating compilation/test jobs had completed before sampling; unrelated OS/user activity was not controlled. Thirteen measured samples were below 100 ms, so this run cannot serve as the final acceptance gate even if a ratio were to pass. Ratios below use medians; dispersion and raw samples remain available.

| Workload | MiniC OPT ms | System STL ms | OPT / system | Own library G++ / system |
| --- | ---: | ---: | ---: | ---: |
| vector-sort | 320.647 | 136.627 | 2.347 | 0.942 |
| binary-search | 359.204 | 155.251 | 2.314 | 0.971 |
| priority-queue | 264.665 | 113.233 | 2.337 | 0.960 |
| ordered-map | 390.814 | 187.713 | 2.082 | 0.845 |
| deque | 743.522 | 125.257 | 5.936 | 1.072 |
| string | 554.240 | 108.383 | 5.114 | 1.142 |
| bitset | 456.605 | 92.114 | 4.957 | 1.105 |
| string-short | 605.875 | 93.642 | 6.470 | 1.077 |
| bitset-count | 878.990 | 631.452 | 1.392 | 0.262 |
| bitset-count-sparse | 853.767 | 642.720 | 1.328 | 0.268 |
| bitset-count-dense | 618.113 | 338.038 | 1.829 | 0.388 |
| bulk-vector-assign | 215.261 | 114.639 | 1.878 | 1.014 |
| bulk-vector-erase | 259.775 | 106.244 | 2.445 | 1.063 |
| bulk-pointer-copy | 211.321 | 119.787 | 1.764 | 0.951 |
| bulk-pointer-copy-backward | 206.343 | 113.979 | 1.810 | 1.052 |

The preceding archived deque result was 16.126× system STL; this iteration is 5.936×. These are separate sampling runs, not a simultaneous old/new paired estimate. String and short-string ratios remain high; the next work targets scalar/parameter stack accesses, compare/branch generation, inline budgets, and library byte operations.

`raw-evidence.zip` preserves exact run files and copied native artifacts as content-addressed `blobs/<sha256>`; `raw-members.json` maps their original relative paths. It includes the full report, configuration, original build commands, raw supervisor properties, stdout/stderr, oracle/input, focused runner and build helper. The immutable Git revision identifies product sources. The original working-directory source archive and compiled Java classes remain in `build/optimization-round1`; the published ZIP does not include that entire source/class tree. Paths inside raw reports describe the original run locations.
