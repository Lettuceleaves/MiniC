# OJ scenario performance matrix

These eight standalone programs add 96 configurations to the existing 11 STL and four bulk workloads. They do not replace those 15 kernel-oriented comparisons. Each program represents a complete OJ solution: reading actual input, building containers, solving, hashing the complete ordered answer and writing the digest all belong to process cost. I/O can reduce the visible ratio between algorithm implementations; passing these scenarios cannot establish that the original container kernels have reached their target.

`minic.benchmark.OjScenarioWorkloads` owns the registry, full input generation and independent Java oracles. `scenarios()` returns eight source descriptions; `matrix()` returns all combinations of three named shapes, two sizes and seeds 1729 / 104729. `input(configuration, rounds)` returns the exact stdin, expected stdout, logical input-item count and rounds. A scenario's size unit and logical input-item definition are separate metadata; these are not counts of STL calls or machine instructions.

| Scenario | Sizes | Three input shapes | Independent Java oracle |
| --- | --- | --- | --- |
| Dijkstra | 256 / 1024 vertices | chain plus shortcuts; hub with initially expensive entries and later improvements; near-tie layers with an unreachable component and distances beyond 32 bits | O(V²+E) minimum scan, no priority queue |
| Sliding-window maximum | 4096 / 32768 values | strictly increasing; strictly decreasing; duplicate plateaus | TreeMap multiset counts, no monotonic deque |
| Coordinate compression | 4096 / 32768 values | shuffled wide distinct coordinates; 17-value alphabet; mostly sorted duplicate coordinates with sparse swaps | TreeSet dictionary and HashMap ranks |
| Interval scheduling | 2048 / 16384 intervals | disjoint; heavily congested; repeated touching endpoints | sorted jobs plus linear scan over at most 16 machine release times, no multiset |
| Sparse accumulation | 2048 / 16384 updates | wide signed keys; 32 hot keys; cancelling update pairs | HashMap updates followed by explicit key sorting |
| Word frequency | 2048 / 16384 words | 32 short hot words; distinct words evenly split across lengths 15/16; shared 36-character prefixes and repeated 44-character words | HashMap counting and sorted String keys |
| Bitset 0/1 knapsack | 128 / 512 weights | mainly small dense weights; mainly large weights; common divisor 64 | backward boolean-array DP, no bitset |
| Grid BFS | 32 / 128 side length | open grid; one-cell corridor connections; fragmented grid with a separating wall | integer-array FIFO and distance array |

The window width is max(2, n/16). Scheduling uses max(1, min(16, n/128)) identical machines available at time zero. Intervals are sorted by finish, start, then original index. Each accepted interval takes the latest available release time not exceeding its start. The hash records every interval ID and acceptance decision, the total and sorted final releases. This defines deterministic output even when several optimal schedules exist.

Knapsack capacity is 4096, represented by 4097 reachable bits. Across its three distributions, explicit weights exercise shifts 0, 1, 63, 64, 65, 127, 128, 4096, 4097 and 8192. A zero-weight item is a valid no-op; weights above capacity cannot add a reachable sum. Dense and sparse shape checks verify that these edge cases do not dominate the respective distribution. Each BFS case supplies its own valid start cell, including open grids, so repeated rounds do not simply reuse one fixed distance field.

## Input and output contract

The first stdin integer is the number of rounds, 1..1024. Each round contains a complete independent case. Java's specified `Random` generator uses `(seed + round * 0x9e3779b9L) & 0xffffffffL`; the C++ sources contain no input PRNG. All generated text is ASCII with LF line endings. Preparation rejects more than four million logical input items or 128 MiB of input text.

Per-round formats:

- Dijkstra: `n m`, followed by m triples `from to weight`; source is vertex 0, edges are directed and nonnegative.
- Sliding window: `n width`, followed by n signed values.
- Compression: `n`, followed by n signed coordinates.
- Scheduling: `n machines`, followed by n pairs `start finish` with finish strictly greater than start.
- Sparse accumulation: `n`, followed by n signed `key delta` pairs. Zero totals are erased.
- Word frequency: `n`, followed by n ASCII whitespace-separated words of at most 127 characters.
- Knapsack: `n`, followed by n weights in 0..8192.
- BFS: `side startRow startColumn`, followed by side rows of `.` and `#`.

Each round emits exactly:

```
round=<zero-based round> observations=<count> hash=<unsigned 64-bit decimal>
```

The hash starts at 14695981039346656037. Each observed integer is converted modulo 2^64 and mixed as `(hash XOR value) * 1099511628211`, modulo 2^64. The final value is `(hash XOR observationCount) * 1099511628211`. Order, signed values and sequence length therefore contribute. The observation sequence is every distance/window answer; the compression dictionary and every rank; the scheduling trace and releases; every updated sparse value and all final entries; every sorted word's length/ASCII characters/count; every reachable sum; or every row-major grid distance. A hash is not a collision-free substitute for all possible outputs; independent hand-solved oracle vectors and the separate small functional suite check the underlying answer semantics.

## Runner and validation boundary

The new runner compiles each source once per frozen product/build combination and reuses the resulting 32 artifacts for 96 inputs. BASE, OPT, G++ own headers and G++ system headers must all match the same Java oracle before performance acceptance. Calibration changes rounds uniformly for all builds of a configuration, generates new full input, and must meet the minimum sample duration; insufficient calibration is not a pass. Formal reports retain every configuration and its distribution rather than averaging away failures.

This workload slice was prepared against product revision 6d6441d633cf3beb3824177fd60aa1a36c8bf51f. The first oracle test compile failed because the registry did not yet exist. Nine Java tests subsequently passed, including all 96 generated configurations, hand-solved answers for all eight algorithms, shape distributions, signed hashes, malformed input and 64-bit distances. The three added shape tests initially contained a Java regex escape error; that test-only error was corrected. No C++ compilation, four-backend run or performance sampling is claimed by this slice; those are pending the unified committed runner/product validation.
