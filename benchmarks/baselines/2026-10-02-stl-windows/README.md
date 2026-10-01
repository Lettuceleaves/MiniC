# STL 性能验收记录（Windows x64）

候选产品提交为 `e56d6d149c02432bc51f01bc06ef4f2de786f6c4`，优化前产品提交为 `b6464790fd3748edfe2d02981f0f6a421a133d6b`。两者分别从 Git archive 重新构建；测试工具取最终统一验收的固定字节码。此处“优化前”只指最后一轮库优化前版本，已包含此前的 MiniC 编译器优化流水线。

全部 120 组、960 个正式样本完成并通过独立结果和完整性核验；11 类主负载占 704 个样本，4 个 bulk 模式占 256 个。MiniC OPTIMIZED 的短字符串耗时下降 38.4%，混合／稠密位计数下降 83.4%／92.1%，四种批量操作下降 29.3%～46.9%。这些是指定负载的进程耗时，不是所有程序的加速保证。

**整体性能仍未全面接近 G++ 系统 STL。** 候选 MiniC OPTIMIZED 在主负载中的耗时是其 1.435～16.126 倍，最大差距为 deque；同一自研库经 G++ 编译的主负载耗时为系统 STL 的 0.265～1.451 倍。未优化 BASELINE 的稀疏位计数出现 22.2% 退化（1705.3 → 2083.9 ms）。常规字符串虽减少分配，但整体耗时没有明显改善。保留这些结果，不把基准通过输出校验等同于通过性能接近目标。

- [完整时间、比值、内存和波动报告](report.md)
- [机器可读汇总及产物来源](summary.json)
- [分配、比较、移动等独立计数](counts/README.md)
- [文件哈希清单](manifest.json)；[原始压缩包成员哈希](raw-members.json)

`raw-evidence.zip` 保留每个批次的原始 JSON、CSV、全部正确性检查／校准／预热／测量轮次的输入、期望输出、实际输出、错误流和 observer 记录，包括已中断批次。未复制可执行文件、编译类、Git source.zip 或完整源码树；这些产物的哈希与命令保存在原始报告中，完整原始目录仍保留于 `D:/MiniC-artifacts/cpp-stl-e56d6d1-20261002`。`environment.json` 显式列出纳入统计的历史批次及被排除的中断批次；后者没有被静默删除、补样或并入成功统计。

压缩包按内容去重，18,122 个原始文件映射为 4,933 个 blob。`raw-members.json` 的每条记录保存原始相对路径、字节数及 SHA-256；在压缩包中读取 `blobs/<sha256>` 即得到该文件的原始字节。所有 blob 均重新核对哈希，所有原始路径均核对映射和长度，内容没有因去重而删减。

目标平台为 Windows x64 LLP64，参考为本机 G++ 8.1.0 `-std=c++17 -O2`。十一种主负载的 size=4096、seed=1729；候选四构建共同校准 rounds，历史版本使用完全相同 rounds。每组预热 2 次、正式测量 8 次，四构建使用 Williams 顺序轮换。不同产品版本的主负载为分开的串行批次；独立 bulk 实验则在每个构建内相邻交错历史／候选，方向逐轮交替。操作系统和用户其他进程未受控制，未调整优先级或电源策略。

bulk 原始元数据中的通用 `hostNote` 沿用了主负载的“版本分批运行”说明；bulk 专门的 `metadata.order` 和每个实际样本记录的是相邻配对，汇总也逐项核对了该顺序。本说明以实际采样顺序为准，原始字段保留未改。

时间为目标进程 QPC 墙钟，包含启动、准备数据、工作负载、输入输出和完整结果哈希；没有减去控制组，也不是纯算法内核时间。MAD 是中位绝对偏差，不是置信区间。Job Object `PeakProcessMemoryUsed` 是峰值提交内存，不是 RSS。校准受工作量上限约束，所有低于 100 ms 的正式样本均保留并在报告中提示。编译每产物只测一次，不作编译速度分布结论。

主负载 35/704 个样本低于 100 ms，最短 87.743 ms，涉及 bitset 的两个 G++ 构建和 string-short 的系统 STL 构建；bulk 没有短样本。跨版本主负载的微小变化不能直接归因于代码，报告同时保留系统 STL 对照的批次变化。

计数探针独立使用 size=128、rounds=2、seed=1729，未混入计时构建。G++ 系统 STL 计数域为拦截的 global new/delete，自研库为 backing malloc/free；只按各自构建比较前后变化。

## 复现入口

先在目标源码工作树通过功能验收，并使用 JDK 21 构建非 UI 测试工具：

```powershell
$jdk21 = '<JDK 21 目录>'
$consoleJar = '<JUnit console standalone 1.11.4.jar>'
./scripts/test-compiler.ps1 -JavaHome $jdk21 -ConsoleJar $consoleJar -CompileOnly
$java = Join-Path $jdk21 'bin/java.exe'
$taskRoot = (Get-Location).Path
$cp = "$taskRoot/build/compiler-verification/main;$taskRoot/build/compiler-verification/test;$taskRoot/src/main/resources;$taskRoot/src/test/resources;$consoleJar"
```

使用新的输出目录，工具拒绝覆盖已有结果。以下为原测量参数；实际机器路径、每个历史批次的工作负载选择和命令均在原始 JSON 中保存。历史版本按候选报告中的 rounds 分组分别执行：

| 原测量 rounds | `--workloads` |
|---:|---|
| 256 | binary-search,priority-queue,ordered-map,string-short |
| 512 | vector-sort,bitset-count-dense |
| 1024 | bitset-count,bitset-count-sparse |
| 4096 | deque,string |
| 8192 | bitset |

```powershell
& $java -Xmx512m '-Dfile.encoding=UTF-8' '-Duser.language=en' -cp $cp minic.benchmark.StlBenchmarkMain --repository=. --revision=e56d6d149c02432bc51f01bc06ef4f2de786f6c4 --output=build/stl-repeat/candidate --gxx=C:/mingw64/bin/g++.exe --size=4096 --rounds=1 --seed=1729 --warmups=2 --repetitions=8 --minimum-ms=100 --calibration-steps=14 --run-timeout-seconds=120 --compile-timeout-seconds=180
& $java -Xmx512m '-Dfile.encoding=UTF-8' '-Duser.language=en' -cp $cp minic.benchmark.StlBenchmarkMain --repository=. --revision=b6464790fd3748edfe2d02981f0f6a421a133d6b --output=build/stl-repeat/baseline-rounds-256 --gxx=C:/mingw64/bin/g++.exe --size=4096 --rounds=256 --workloads=binary-search,priority-queue,ordered-map,string-short --seed=1729 --warmups=2 --repetitions=8 --minimum-ms=100 --calibration-steps=0 --run-timeout-seconds=120 --compile-timeout-seconds=180
& $java -Xmx512m '-Dfile.encoding=UTF-8' '-Duser.language=en' -cp $cp minic.benchmark.StlBulkBenchmarkMain --repository=. --baseline=b6464790fd3748edfe2d02981f0f6a421a133d6b --candidate=e56d6d149c02432bc51f01bc06ef4f2de786f6c4 --workload-revision=e56d6d149c02432bc51f01bc06ef4f2de786f6c4 --output=build/stl-repeat/bulk --gxx=C:/mingw64/bin/g++.exe --size=4096 --rounds=2 --iterations=8 --seed=1729 --measure=true --warmups=2 --repetitions=8 --minimum-ms=100 --calibration-steps=14 --run-timeout-seconds=120 --compile-timeout-seconds=240
```

原报告的完整核验与重新生成使用以下入口。该工具不运行基准，但会读取并核对原始目录中源码、类、可执行文件、输入输出和 observer 的哈希；只有保留完整原始树及报告中绝对路径时才能使用。它要求全部批次完成和显式 `completed.txt`，中断批次必须单独披露。不要在采样过程中读取正在原子替换的报告文件，以免 Windows 文件共享锁使写入失败。

报告工具需要 Python 3.11 或更高版本，另通过 20/20 合成完整性测试，原日志见 `report-tool-tests.txt`。这些工具测试不并入产品的 4,545 项固定提交验收数。

```powershell
python benchmarks/tools/generate_report.py --root D:/MiniC-artifacts/cpp-stl-e56d6d1-20261002 --output build/stl-report-recheck
python -m unittest discover -s benchmarks/tools -p test_generate_report.py -v
```

本次数据不外推到 Linux OJ、其他 CPU、较新版本 libstdc++ 或所有容器和重载。功能兼容范围另见 [开发与验收记录](../../../docs/cpp-stl-development.md)。
