# Separate bitset count workloads

The original nine workloads and their numeric counter-probe IDs remain unchanged.
`bitset-count-sparse` is ID 9; `bitset-count-dense` is ID 10. The older mixed
`bitset-count` workload remains available but must not stand in for either result.

Both new workloads use `bitset<1024>` and one runtime-selected anchor per 64-bit
word. Sparse starts with one set bit per word; dense starts with one unset bit.
Every logical input chooses a word and bit, flips it, counts, flips the **same**
bit again and counts again. Thus every count immediately follows a real mutation,
and no word drifts out of 0..2 set bits (sparse) or 62..64 set bits (dense), even
for long runs. Every fourth input deliberately selects the anchor; the others
select a runtime-generated bit. Both count results and the selected index enter
the ordered hash; the final 1024 bits also enter the hash.

The operation unit remains one logical input, which performs **two count calls
and two bit flips**. Process timing includes initialization, PRNG, mutation,
checksums, startup and I/O. It is not isolated count latency. The benchmark report
records this definition per build, and counter reports record it per workload.
Report sparse and dense results separately; never average them into a universal
speedup claim. Timing builds are not counter-instrumented.

`StlBenchmarkWorkloads` uses a boolean model with count deltas. The independent
`StlBitsetCountWorkloadsTest` oracle recounts all 1024 bits after every mutation
and checks every word's density bounds, then compares ordered hashes. Separate
instrumented probes report `count_calls`, `bit_mutations` and `count_total` and
require respectively `2*n`, `2*n` and the independent count oracle's total in each
round. Existing object/allocation balance checks remain in force.

Correctness preflight compiles exact identical source/stdin as MiniC BASELINE,
MiniC OPTIMIZED, G++ with these own headers, and G++ with its system STL. It checks
size 17 and 4096 with two rounds, preserves all compiler/run logs and hashes, and
does not collect timing samples. The probe preflight also exercises all original
nine workloads at size 17 and the two new ones at size 4096. This preflight does
not change bitset's implementation or assert a performance improvement.

After functional acceptance, select these workloads with the existing benchmark
runner's `--workloads=bitset-count-sparse,bitset-count-dense`. Calibration, idle-host
controls, raw samples, median/MAD and all four builds are still required. Do not
use JUnit durations, count-probe wall time or preflight durations as benchmark
samples.
