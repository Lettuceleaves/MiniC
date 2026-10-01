"""Synthetic integrity tests only; these are never benchmark observations."""
import copy
import csv
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
import generate_report as tool


def put(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data if isinstance(data, bytes) else data.encode())


def save_report(root, report):
    (root / "report.json").write_text(json.dumps(report), encoding="utf-8")
    with (root / "samples.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(report["samples"][0]))
        writer.writeheader()
        writer.writerows({k: tool.csv_text(v) for k, v in s.items()} for s in report["samples"])


def synthetic_run(root, revision="a" * 40, rounds=16):
    root.mkdir(parents=True)
    snapshot = root / "source"
    sources = {"benchmarks/stl/vector-sort.cpp": b"synthetic unit-test source only",
               "benchmarks/tools/windows-supervisor.cpp": b"synthetic observer source"}
    with zipfile.ZipFile(root / "source.zip", "w") as archive:
        archive.comment = revision.encode()
        for name, data in sources.items():
            archive.writestr(name, data)
            put(snapshot / name, data)
    put(root / "compiler/classes/Example.class", b"synthetic class only")
    put(root / "observer/windows-supervisor.exe", b"synthetic observer only; never executed")
    meta = {"revision": revision, "repetitions": 8, "instrumentation": "none in timing builds; synthetic unit test",
            "memory": "committed bytes, not RSS", "archiveSha256": tool.file_hash(root / "source.zip"),
            "sourceSnapshot": str(snapshot), "sourceManifest": {n: tool.digest(data) for n, data in sources.items()},
            "compilerClassManifest": {"Example.class": tool.digest(b"synthetic class only")},
            "observerSourceSha256": tool.digest(sources["benchmarks/tools/windows-supervisor.cpp"]),
            "observerArtifactSha256": tool.file_hash(root / "observer/windows-supervisor.exe"),
            "loadedToolClassSha256": {"SyntheticTool": "b" * 64}, "minimumSampleMillis": 100, "seed": 1729, "warmups": 2,
            "gxxVersion": "synthetic", "gxxTarget": "synthetic", "java": "synthetic", "os": "synthetic", "cpu": "synthetic",
            "cpuRegistry": "synthetic", "processors": 1, "hostNote": "Synthetic test data, not benchmark evidence"}
    report = {"schemaVersion": 1, "status": "passed-correctness", "errors": [], "metadata": meta, "builds": [], "samples": [], "summaries": []}
    design = ((0, 1, 3, 2), (1, 2, 0, 3), (2, 3, 1, 0), (3, 0, 2, 1))
    for bindex, build in enumerate(tool.BUILDS):
        directory = root / "builds/vector-sort" / build
        exe = directory / "program.exe"
        put(exe, ("synthetic executable " + build).encode())
        command = ["g++", "-O2", "-std=c++17"] if build.startswith("gxx") else ["java", "--compile-minic"]
        if build == "gxx-own":
            command.append("-nostdinc++")
        record = {"workload": "vector-sort", "build": build, "status": "compiled", "compileExitCode": 0,
                  "compileTimedOut": False, "compileOutputTruncated": False, "sourcePath": str(snapshot / "benchmarks/stl/vector-sort.cpp"),
                  "sourceSha256": meta["sourceManifest"]["benchmarks/stl/vector-sort.cpp"], "artifactPath": str(exe),
                  "artifactSha256": tool.file_hash(exe), "artifactBytes": exe.stat().st_size, "command": command, "directory": str(directory),
                  "compileProcessWallNanos": 1}
        if build.startswith("minic"):
            record.update(optimizationLevel="OPTIMIZED" if build == "minic-opt" else "BASELINE", passNames=[])
        report["builds"].append(record)
        for phase, count in (("preflight", 1), ("calibration", 1), ("warmup", 2), ("measurement", 8)):
            for repetition in range(count):
                directory = root / "runs" / build / f"{phase}-{repetition}"
                stdin, stdout = f"4096 {rounds} 1729\n".encode(), b"synthetic oracle\n"
                for name, data in (("stdin.txt", stdin), ("stdout.txt", stdout), ("stderr.txt", b""), ("expected-stdout.txt", stdout)):
                    put(directory / name, data)
                wall = (200 + bindex * 10 + repetition) * 1_000_000
                sample = {"workload": "vector-sort", "build": build, "phase": phase, "repetition": repetition,
                          "orderIndex": design[repetition % 4].index(bindex), "size": 4096, "rounds": rounds, "seed": 1729,
                          "inputElements": 4096 * rounds, "inputSha256": tool.digest(stdin), "expectedOutputSha256": tool.digest(stdout),
                          "actualOutputSha256": tool.digest(stdout), "rawStdoutSha256": tool.digest(stdout), "rawStderrSha256": tool.digest(b""),
                          "status": "COMPLETED", "oracleMatched": True, "processWallNanos": wall, "userCpuNanos": wall // 2,
                          "kernelCpuNanos": 0, "peakCommitBytes": 1048576, "observerWallNanos": wall + 1_000_000, "exitCode": 0,
                          "directory": str(directory)}
                props = {"schemaVersion": "1", "status": "completed", "win32Error": 0, "exitCode": 0,
                         "wallNanos": wall, "userCpuNanos": wall // 2, "kernelCpuNanos": 0, "peakCommitBytes": 1048576}
                put(directory / "observer.properties", "\n".join(f"{k}={v}" for k, v in props.items()))
                report["samples"].append(sample)
        group = [s["processWallNanos"] for s in report["samples"] if s["build"] == build and s["phase"] == "measurement"]
        values = tool.stats(group)
        report["summaries"].append({"workload": "vector-sort", "build": build, "sampleCount": 8, "size": 4096, "rounds": rounds, "seed": 1729,
            "medianProcessWallNanos": values["median"], "madProcessWallNanos": values["mad"], "minProcessWallNanos": values["min"],
            "maxProcessWallNanos": values["max"], "shortSampleWarning": False})
    save_report(root, report)
    return report


class IntegrityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="report-integrity-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "candidate"
        self.report = synthetic_run(self.root)

    def load(self):
        return tool.load_run(self.root, "a" * 40)

    def test_valid_raw_archive_csv_observer_and_artifacts(self):
        with self.assertRaisesRegex(tool.InvalidData, "Revision mismatch"):
            tool.load_run(self.root, "c" * 40)
        run = self.load()
        group = run["groups"][("vector-sort", "minic-base")]
        self.assertEqual(203_500_000, group["wallNanos"]["median"])
        self.assertEqual(2_000_000, group["wallNanos"]["mad"])

    def test_incomplete_status_is_not_a_result(self):
        self.report["status"] = "in-progress"
        save_report(self.root, self.report)
        with self.assertRaisesRegex(tool.InvalidData, "Incomplete"):
            self.load()

    def test_eighth_sample_cannot_be_missing(self):
        self.report["samples"] = [s for s in self.report["samples"] if not (s["build"] == "minic-base" and s["phase"] == "measurement" and s["repetition"] == 7)]
        save_report(self.root, self.report)
        with self.assertRaises(tool.InvalidData):
            self.load()

    def test_csv_mismatch_cannot_hide_behind_report_summary(self):
        csvfile = self.root / "samples.csv"
        csvfile.write_text(csvfile.read_text().replace("200000000", "299000000", 1))
        with self.assertRaisesRegex(tool.InvalidData, "JSON/CSV"):
            self.load()

    def test_mutated_artifact_is_rejected(self):
        Path(self.report["builds"][0]["artifactPath"]).write_bytes(b"different executable")
        with self.assertRaisesRegex(tool.InvalidData, "hash mismatch"):
            self.load()

    def test_observer_timing_is_the_source_of_samples(self):
        first = self.report["samples"][0]
        first["processWallNanos"] += 1
        save_report(self.root, self.report)
        with self.assertRaisesRegex(tool.InvalidData, "Observer/report"):
            self.load()

    def test_before_after_inputs_cannot_differ(self):
        other = self.root.parent / "baseline"
        synthetic_run(other, "b" * 40, rounds=32)
        candidate = self.load()
        with self.assertRaisesRegex(tool.InvalidData, "do not cover"):
            tool.summarize(candidate, [], workloads=("vector-sort",))
        with self.assertRaisesRegex(tool.InvalidData, "inputs differ"):
            tool.summarize(candidate, [tool.load_run(other, "b" * 40)], workloads=("vector-sort",))

    def test_report_ratios_and_memory_wording_have_explicit_meaning(self):
        other = self.root.parent / "baseline"
        synthetic_run(other, "b" * 40)
        summary = tool.summarize(self.load(), [tool.load_run(other, "b" * 40)], workloads=("vector-sort",))
        self.assertEqual(1, summary["rows"][0]["afterOverBefore"]["minic-opt"])
        self.assertIn("不是 RSS", tool.markdown(summary))
        self.assertIn("不是置信区间", tool.markdown(summary))
        self.assertLess(summary["rows"][0]["candidateRatios"]["OPT/GXX-system"], 1)
        summary["interruptedRuns"] = [{"directory": "baseline-rounds-512", "reason": "Synthetic replacement failure", "includedInStatistics": False}]
        summary["baselineSelection"] = {"explicit": True, "directories": ["baseline-retry-512"]}
        rendered = tool.markdown(summary)
        for expected in ("整批不计入任何统计", "baseline-rounds-512", "baseline-retry-512", "Synthetic replacement failure"):
            self.assertIn(expected, rendered)

    def test_missing_completion_marker_never_creates_output(self):
        output = self.root.parent / "unwritten"
        with self.assertRaisesRegex(tool.InvalidData, "has not completed"):
            tool.main(["--root", str(self.root.parent), "--output", str(output)])
        self.assertFalse(output.exists())


def synthetic_bulk(root):
    root.mkdir(parents=True)
    meta = {"baselineRevision": "b"*40, "candidateRevision": "a"*40, "workloadRevision": "a"*40,
            "workloadOrigin": "committed archive", "measurementEnabled": True, "repetitions": 8,
            "metrics": "committed bytes, not RSS", "toolClassHashes": {"Synthetic": "b"*64},
            "seed": 1729, "size": 4096, "rounds": 2, "warmups": 2, "minimumMillis": 100}
    contents = {"benchmarks/stl/bulk-copy.cpp": b"synthetic bulk source; never executed",
                "benchmarks/tools/windows-supervisor.cpp": b"synthetic observer source"}
    for label in ("baseline", "candidate", "workload"):
        (root / label).mkdir()
        with zipfile.ZipFile(root / label / "source.zip", "w") as archive:
            archive.comment = meta[label+"Revision"].encode()
            for name, content in contents.items():
                archive.writestr(name, content)
                put(root / label / "source" / name, content)
        meta[label+"ArchiveSha256"] = tool.file_hash(root / label / "source.zip")
        meta[label+"SourceManifest"] = {name: tool.digest(content) for name, content in contents.items()}
        if label != "workload":
            put(root / label / "compiler/classes/Synthetic.class", b"class only")
            meta[label+"CompilerManifest"] = {"Synthetic.class": tool.digest(b"class only")}
    put(root / "bulk-copy.cpp", contents["benchmarks/stl/bulk-copy.cpp"])
    put(root / "observer/windows-supervisor.exe", b"observer only")
    meta.update(sourceSha256=tool.file_hash(root / "bulk-copy.cpp"), observerSha256=tool.file_hash(root / "observer/windows-supervisor.exe"),
                observerSourceSha256=tool.digest(contents["benchmarks/tools/windows-supervisor.cpp"]))
    report = {"schemaVersion": 1, "complete": True, "errors": [], "metadata": meta, "builds": [], "samples": [], "summaries": []}
    for product in ("baseline", "candidate"):
        for build in tool.BUILDS:
            exe = root / "builds" / product / build / "program.exe"
            put(exe, f"synthetic artifact {product}/{build}")
            command = ["g++", "-std=c++17", "-O2"] if build.startswith("gxx") else ["java"]
            if build == "gxx-own":
                command.append("-nostdinc++")
            row = {"product": product, "build": build, "sourceSha256": meta["sourceSha256"], "status": "compiled", "exitCode": 0,
                   "artifactPath": str(exe), "artifactSha256": tool.file_hash(exe), "command": command}
            if build.startswith("minic"):
                row.update(optimizationLevel="OPTIMIZED" if build == "minic-opt" else "BASELINE", passes=[])
            report["builds"].append(row)
    modes = ("VECTOR_ASSIGN", "VECTOR_ERASE", "POINTER_COPY", "POINTER_COPY_BACKWARD")
    design = ((0, 1, 3, 2), (1, 2, 0, 3), (2, 3, 1, 0), (3, 0, 2, 1))
    for mode_index, mode in enumerate(modes):
        for phase, count in (("preflight", 1), ("calibration", 1), ("warmup", 2), ("measurement", 8)):
            for repetition in range(count):
                keys = []
                for position, bindex in enumerate(design[repetition % 4]):
                    products = ("baseline", "candidate") if (repetition+position) % 2 == 0 else ("candidate", "baseline")
                    keys.extend((p, bindex) for p in products)
                for order, (product, bindex) in enumerate(keys):
                    build = tool.BUILDS[bindex]
                    directory = root / "runs" / mode / f"{phase}-{repetition}" / str(order)
                    stdin, stdout = f"{mode} 4096 2 8 1729\n".encode(), f"synthetic {mode} oracle\n".encode()
                    for name, data in (("stdin.txt", stdin), ("stdout.txt", stdout), ("stderr.txt", b""), ("expected-stdout.txt", stdout)):
                        put(directory / name, data)
                    wall = ((200 if product == "baseline" else 180) + 20*mode_index + 10*bindex + repetition) * 1_000_000
                    row = {"mode": mode, "product": product, "build": build, "phase": phase, "repetition": repetition, "order": order,
                           "size": 4096, "rounds": 2, "iterations": 8, "seed": 1729, "transferredElements": 100+mode_index, "observedElements": 150,
                           "inputSha256": tool.digest(stdin), "expectedSha256": tool.digest(stdout), "stdoutSha256": tool.digest(stdout),
                           "stderrSha256": tool.digest(b""), "status": "COMPLETED", "exitCode": 0, "correct": True, "wallNanos": wall,
                           "userCpuNanos": wall//2, "kernelCpuNanos": 0, "peakCommitBytes": 1048576, "observerWallNanos": wall+1,
                           "directory": str(directory)}
                    props = {"schemaVersion": 1, "status": "completed", "exitCode": 0, "win32Error": 0,
                             **{k: row[k] for k in ("wallNanos", "userCpuNanos", "kernelCpuNanos", "peakCommitBytes")}}
                    put(directory / "observer.properties", "\n".join(f"{k}={v}" for k, v in props.items()))
                    report["samples"].append(row)
        for product in ("baseline", "candidate"):
            for build in tool.BUILDS:
                selected = [s for s in report["samples"] if s["mode"] == mode and s["product"] == product and s["build"] == build and s["phase"] == "measurement"]
                row = {"mode": mode, "product": product, "build": build, "samples": 8, "iterations": 8,
                       "transferredElements": selected[0]["transferredElements"], "observedElements": 150, "shortSample": False}
                for metric in ("wallNanos", "userCpuNanos", "kernelCpuNanos", "peakCommitBytes"):
                    values = tool.stats([s[metric] for s in selected])
                    row.update({metric+"Median": values["median"], metric+"Mad": values["mad"]})
                report["summaries"].append(row)
    save_report(root, report)
    return report


class BaselineSelectionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="baseline-selection-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in ("baseline-rounds-256", "baseline-rounds-512", "baseline-retry-512"):
            (self.root / name).mkdir()
        put(self.root / "baseline-rounds-512/report.json", "preserved incomplete report")
        put(self.root / "baseline-rounds-512.txt", "synthetic AccessDeniedException")
        self.environment = {"baselineRuns": ["baseline-rounds-256", "baseline-retry-512"], "interruptedRuns": [{
            "directory": "baseline-rounds-512", "reason": "Synthetic report replacement failure; whole batch rerun",
            "reportSha256": tool.file_hash(self.root / "baseline-rounds-512/report.json"),
            "logSha256": tool.file_hash(self.root / "baseline-rounds-512.txt")} ]}

    def test_explicit_retry_excludes_preserved_interrupted_batch(self):
        selected, interrupted, explicit = tool.select_baseline_runs(self.root, self.environment)
        self.assertEqual(["baseline-rounds-256", "baseline-retry-512"], [p.name for p in selected])
        self.assertTrue(explicit)
        self.assertFalse(interrupted[0]["includedInStatistics"])
        self.assertTrue((self.root / "baseline-rounds-512/report.json").exists())

    def test_unselected_batch_cannot_disappear_silently(self):
        self.environment["interruptedRuns"] = []
        with self.assertRaisesRegex(tool.InvalidData, "Unselected baseline"):
            tool.select_baseline_runs(self.root, self.environment)

    def test_selected_and_interrupted_cannot_overlap(self):
        self.environment["baselineRuns"].append("baseline-rounds-512")
        with self.assertRaisesRegex(tool.InvalidData, "also be interrupted"):
            tool.select_baseline_runs(self.root, self.environment)

    def test_duplicate_selected_directory_alias_is_rejected(self):
        self.environment["baselineRuns"].append("./baseline-retry-512")
        with self.assertRaisesRegex(tool.InvalidData, "Duplicate baselineRuns"):
            tool.select_baseline_runs(self.root, self.environment)

    def test_absolute_or_escaping_paths_are_rejected(self):
        for name in (str(self.root / "baseline-retry-512"), "../outside"):
            with self.subTest(path=name):
                self.environment["baselineRuns"] = [name]
                with self.assertRaises(tool.InvalidData):
                    tool.select_baseline_runs(self.root, self.environment)

    def test_interrupted_reason_and_hash_are_mandatory(self):
        record = self.environment["interruptedRuns"][0]
        reason = record["reason"]
        record["reason"] = " "
        with self.assertRaisesRegex(tool.InvalidData, "reason"):
            tool.select_baseline_runs(self.root, self.environment)
        record["reason"] = reason
        record["reportSha256"] = "0" * 64
        with self.assertRaisesRegex(tool.InvalidData, "hash mismatch"):
            tool.select_baseline_runs(self.root, self.environment)

    def test_default_selection_remains_original_glob(self):
        (self.root / "baseline-retry-512").rmdir()
        selected, interrupted, explicit = tool.select_baseline_runs(self.root, {})
        self.assertEqual(["baseline-rounds-256", "baseline-rounds-512"], [p.name for p in selected])
        self.assertEqual([], interrupted)
        self.assertFalse(explicit)


class BulkIntegrityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="bulk-report-integrity-")
        cls.root = Path(cls.temp.name) / "bulk"
        cls.original = synthetic_bulk(cls.root)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def setUp(self):
        self.report = copy.deepcopy(self.original)
        save_report(self.root, self.report)

    def load(self):
        return tool.load_bulk(self.root, "b"*40, "a"*40)

    def test_paired_modes_preserve_inputs_and_raw_observer_metrics(self):
        result = self.load()
        self.assertEqual(4, len(result["rows"]))
        self.assertLess(result["rows"][0]["afterOverBefore"]["minic-opt"], 1)
        self.assertIn("pairedAfterOverBefore", result["rows"][0])

    def test_smoke_does_not_become_performance(self):
        self.report["metadata"]["measurementEnabled"] = False
        save_report(self.root, self.report)
        with self.assertRaisesRegex(tool.InvalidData, "not an eight-sample measurement"):
            self.load()

    def test_reversed_pair_order_is_rejected(self):
        first, second = self.report["samples"][:2]
        first["order"], second["order"] = second["order"], first["order"]
        save_report(self.root, self.report)
        with self.assertRaisesRegex(tool.InvalidData, "direction"):
            self.load()

    def test_published_bulk_statistics_must_match_samples(self):
        self.report["summaries"][0]["wallNanosMedian"] += 1_000_000
        save_report(self.root, self.report)
        with self.assertRaisesRegex(tool.InvalidData, "statistic differs"):
            self.load()


if __name__ == "__main__":
    unittest.main(verbosity=2)
