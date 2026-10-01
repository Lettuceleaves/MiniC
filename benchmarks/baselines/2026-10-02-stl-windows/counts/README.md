# STL allocation and operation-count evidence

These are count-only correctness results, not performance timings. This export was prepared using only lightweight file I/O while a separate timing run was active.

- Before product/compiler: `b6464790fd3748edfe2d02981f0f6a421a133d6b`.
- After product/compiler: `e56d6d149c02432bc51f01bc06ef4f2de786f6c4`.
- Same input in both: size **128**, rounds **2**, seed **1729**.
- Eleven workloads × four builds (MiniC BASE, MiniC OPT, G++ own library, G++ host STL); 44 successful workload/build pairs and 88 verified rounds per revision.
- All 88 before/after round inputs and output hashes match. Workload/oracle/probe source and native supervisor equality records are preserved in `comparison/comparison.json`. The later standalone `bulk-copy.cpp` workload is outside this 11-workload count suite.

## Files and provenance

`baseline/counts.json` and `final/counts.json` are exact original report bytes. They retain compile commands, artifact/header/probe hashes, instrumentation description, input and counters. The small `run.txt` files are also exact original bytes (original captured text). `final/verification.json` is the original final-run validation record. Comparison JSON and CSV files are unchanged copies of the previously generated comparison. No executables, class files, instrumented library copies, or large raw stdout trees are included. Original reports can contain absolute provenance paths from the test host; those paths are not prerequisites for reading this evidence.

`export-validation.json` rechecks both complete result sets, round-input/hash equality, source equality records, and exact-copy hashes. `manifest.json` lists every exported payload by relative path, byte size and SHA-256. It excludes itself to avoid a recursive hash; its own hash is supplied in the handoff. Scoped `.gitattributes` uses `* -text` to preserve all evidence bytes when committed.

## Counting rules and result

Allocation domains differ: host STL counts intercepted global `new`/`delete`; MiniC and G++ own-library builds count backing `malloc`/`free` calls through the same copied `memory.mh` instrumentation. Compare before/after **within each build**; do not equate these domains or treat instrumented counts as timings. Lifetime, allocation/free balance and expected hash validation passed separately for every build.

`per-round-counters.csv` retains each counter before/after/delta. `summary.csv` sums counters over two rounds, except `peak_*`, where it reports the maximum. Counts include all work done by each probe, not merely one container operation.

Across all three own-library builds, `string` allocations fall **20 → 8**, with allocated bytes **1048 → 908**; `string-short` allocations fall **1488 → 32**, with bytes **16640 → 992**. Other workload allocation totals are unchanged. Comparison, copy/move and live-object counters are unchanged; host-STL before/after counts are unchanged. These statements concern this fixed small input only and imply no timing speedup.
