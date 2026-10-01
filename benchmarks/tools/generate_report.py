"""Read-only validation/reporting for the final native STL runs; never starts a process.

Usage: python generate_report.py --root build/stl-final-performance
Only after completed.txt exists and every dataset passes validation are summary files written.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import json
import math
from pathlib import Path
import re
import statistics
import sys
import zipfile

WORKLOADS = ("vector-sort", "binary-search", "priority-queue", "ordered-map", "deque", "string", "bitset",
             "string-short", "bitset-count", "bitset-count-sparse", "bitset-count-dense")
BUILDS = ("minic-base", "minic-opt", "gxx-own", "gxx-stl")
NAMES = {"minic-base": "MiniC BASELINE", "minic-opt": "MiniC OPTIMIZED", "gxx-own": "G++ + 自研库", "gxx-stl": "G++ + 系统 STL"}


class InvalidData(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise InvalidData(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_hash(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def inside(path, root):
    result = Path(path).resolve()
    require(result.is_relative_to(root.resolve()), f"Path outside run directory: {result}")
    return result


def check_hash(path, expected):
    require(isinstance(expected, str) and re.fullmatch(r"[0-9a-f]{64}", expected), f"Invalid hash: {path}")
    require(file_hash(path) == expected, f"File hash mismatch: {path}")


def verify_manifest(directory, manifest):
    require(isinstance(manifest, dict) and manifest, f"Missing manifest: {directory}")
    for name, expected in manifest.items():
        check_hash(inside(directory / name, directory), expected)


def csv_text(value):
    return str(value).lower() if isinstance(value, bool) else str(value)


def stats(values):
    center = statistics.median(values)
    return {"median": center, "mad": statistics.median(abs(x - center) for x in values), "min": min(values), "max": max(values)}


def load_run(directory, revision, sample_count=8):
    directory = Path(directory).resolve()
    report = read_json(directory / "report.json")
    require(report.get("schemaVersion") == 1, f"Unknown schema: {directory}")
    require(report.get("status") == "passed-correctness" and not report.get("errors"), f"Incomplete/failed report: {directory}")
    require(report.get("complete", True) is True, f"Explicit incomplete report: {directory}")
    meta = report["metadata"]
    require(meta.get("revision") == revision, f"Revision mismatch: {directory}")
    require(meta.get("repetitions") == sample_count, f"Repetition configuration mismatch: {directory}")
    require(meta.get("instrumentation", "").startswith("none in timing builds"), "Instrumented timing dataset")
    require("not RSS" in meta.get("memory", ""), "Unknown memory measurement semantics")
    archive = directory / "source.zip"
    check_hash(archive, meta["archiveSha256"])
    snapshot = inside(meta["sourceSnapshot"], directory)
    verify_manifest(snapshot, meta["sourceManifest"])
    with zipfile.ZipFile(archive) as zipped:
        require(zipped.comment.decode("ascii") == revision, "Archive commit comment differs from report revision")
        contents = {name: digest(zipped.read(name)) for name in zipped.namelist() if not name.endswith("/")}
        require(contents == meta["sourceManifest"], "Source manifest differs from immutable git archive")
    verify_manifest(directory / "compiler/classes", meta["compilerClassManifest"])
    check_hash(snapshot / "benchmarks/tools/windows-supervisor.cpp", meta["observerSourceSha256"])
    check_hash(directory / "observer/windows-supervisor.exe", meta["observerArtifactSha256"])
    require(bool(meta.get("loadedToolClassSha256")), "Missing loaded tool hashes")

    builds = {}
    for build in report["builds"]:
        key = (build["workload"], build["build"])
        require(key not in builds and key[1] in BUILDS, f"Duplicate/unknown build: {key}")
        require(build.get("status") == "compiled" and build.get("compileExitCode") == 0
                and build.get("compileTimedOut") is False and build.get("compileOutputTruncated") is False, f"Bad compilation: {key}")
        source = inside(build["sourcePath"], snapshot)
        require(source.relative_to(snapshot).as_posix() == f"benchmarks/stl/{key[0]}.cpp", f"Unexpected workload source: {key}")
        check_hash(source, build["sourceSha256"])
        artifact = inside(build["artifactPath"], directory)
        check_hash(artifact, build["artifactSha256"])
        require(artifact.stat().st_size == build["artifactBytes"], f"Artifact size mismatch: {key}")
        command = build["command"]
        if key[1].startswith("gxx"):
            require("-O2" in command and "-std=c++17" in command, f"Unexpected reference flags: {key}")
            require(("-nostdinc++" in command) == (key[1] == "gxx-own"), f"Reference library selection mismatch: {key}")
        else:
            expected = "OPTIMIZED" if key[1] == "minic-opt" else "BASELINE"
            require(build.get("optimizationLevel") == expected and isinstance(build.get("passNames"), list), f"Missing optimization metadata: {key}")
        if "shimManifest" in build:
            verify_manifest(inside(build["directory"], directory) / "headers", build["shimManifest"])
        if "referenceCompatSha256" in build:
            check_hash(inside(build["directory"], directory) / "reference-header-compat.h", build["referenceCompatSha256"])
        builds[key] = build

    samples = report["samples"]
    require(isinstance(samples, list) and samples, "Missing samples")
    with (directory / "samples.csv").open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        csv_rows, columns = list(reader), reader.fieldnames
    require(len(csv_rows) == len(samples), "JSON/CSV sample count differs")
    require(columns and set(columns) == set(samples[0]), "JSON/CSV sample columns differ")
    measured, all_groups, phases = defaultdict(list), defaultdict(list), defaultdict(list)
    for index, (sample, row) in enumerate(zip(samples, csv_rows)):
        require(all(row[k] == csv_text(sample[k]) for k in columns), f"JSON/CSV sample differs at {index}")
        key = (sample["workload"], sample["build"])
        require(key in builds, f"Sample without artifact: {key}")
        require(sample["status"] == "COMPLETED" and sample["exitCode"] == 0 and sample["oracleMatched"] is True, f"Failed sample: {key}/{index}")
        require(sample["actualOutputSha256"] == sample["expectedOutputSha256"], f"Oracle hash differs: {key}")
        for metric in ("processWallNanos", "userCpuNanos", "kernelCpuNanos", "peakCommitBytes", "observerWallNanos"):
            require(isinstance(sample[metric], int) and sample[metric] >= 0, f"Invalid {metric}: {key}")
        require(sample["processWallNanos"] > 0 and sample["inputElements"] > 0, f"Empty timing/input: {key}")
        raw = inside(sample["directory"], directory)
        for name, field in (("stdin.txt", "inputSha256"), ("stdout.txt", "rawStdoutSha256"), ("stderr.txt", "rawStderrSha256")):
            check_hash(raw / name, sample[field])
        require((raw / "stderr.txt").stat().st_size == 0, f"Nonempty target stderr: {raw}")
        stdout = (raw / "stdout.txt").read_bytes().decode("utf-8").replace("\r\n", "\n")
        expected = (raw / "expected-stdout.txt").read_bytes().decode("utf-8").replace("\r\n", "\n")
        require(digest(stdout.encode()) == sample["actualOutputSha256"] and digest(expected.encode()) == sample["expectedOutputSha256"], f"Output text hash mismatch: {raw}")
        properties = dict(line.split("=", 1) for line in (raw / "observer.properties").read_text().splitlines())
        require(properties.get("schemaVersion") == "1" and properties.get("status", "").upper() == "COMPLETED"
                and properties.get("win32Error") == "0", f"Observer failed: {raw}")
        for prop, field in (("wallNanos", "processWallNanos"), ("userCpuNanos", "userCpuNanos"), ("kernelCpuNanos", "kernelCpuNanos"), ("peakCommitBytes", "peakCommitBytes"), ("exitCode", "exitCode")):
            require(int(properties[prop]) == sample[field], f"Observer/report differs: {raw}/{prop}")
        require(sample["phase"] in ("preflight", "calibration", "warmup", "measurement"), "Unknown sample phase")
        require(sample["seed"] == meta["seed"], "Sample seed differs from configuration")
        all_groups[key].append(sample)
        phases[(sample["workload"], sample["phase"], sample["repetition"])].append(sample)
        if sample["phase"] == "measurement":
            measured[key].append(sample)
    for key, group in all_groups.items():
        for phase, count in (("preflight", 1), ("warmup", meta["warmups"])):
            require(sorted(s["repetition"] for s in group if s["phase"] == phase) == list(range(count)), f"Missing/duplicate {phase}: {key}")
        calibration = sorted(s["repetition"] for s in group if s["phase"] == "calibration")
        if meta["minimumSampleMillis"] > 0:
            require(calibration and calibration == list(range(len(calibration))), f"Missing/duplicate calibration: {key}")
    for key, phase in phases.items():
        require(len(phase) == 4 and {s["build"] for s in phase} == set(BUILDS), f"Incomplete four-build round: {key}")
        require(len({(s["inputSha256"], s["expectedOutputSha256"], s["size"], s["rounds"], s["inputElements"]) for s in phase}) == 1, f"Inputs differ within round: {key}")
    require(set(measured) == set(builds), "Some build has no measurement samples")
    published = {(s["workload"], s["build"]): s for s in report["summaries"]}
    require(len(published) == len(report["summaries"]) and set(published) == set(measured), "Duplicate/missing published summary")
    groups = {}
    for key, group in measured.items():
        require(len(group) == sample_count and sorted(s["repetition"] for s in group) == list(range(sample_count)), f"Need {sample_count} unique samples: {key}")
        signature = ("size", "rounds", "seed", "inputElements", "inputSha256", "expectedOutputSha256")
        require(len({tuple(s[k] for k in signature) for s in group}) == 1, f"Input changed within group: {key}")
        wall = stats([s["processWallNanos"] for s in group])
        summary = published[key]
        require(summary["sampleCount"] == sample_count, f"Published sample count differs: {key}")
        require(all(summary[k] == group[0][k] for k in ("size", "rounds", "seed")), f"Published input parameters differ: {key}")
        for label, value in (("medianProcessWallNanos", wall["median"]), ("madProcessWallNanos", wall["mad"]), ("minProcessWallNanos", wall["min"]), ("maxProcessWallNanos", wall["max"])):
            require(math.isclose(summary[label], value, rel_tol=1e-12, abs_tol=1e-5), f"Published statistic differs: {key}/{label}")
        short = sum(s["processWallNanos"] < meta["minimumSampleMillis"] * 1_000_000 for s in group)
        require(summary["shortSampleWarning"] == bool(short), f"Published short warning differs: {key}")
        groups[key] = {k: group[0][k] for k in signature} | {
            "sampleCount": len(group), "wallNanos": wall, "userCpuNanos": stats([s["userCpuNanos"] for s in group]),
            "kernelCpuNanos": stats([s["kernelCpuNanos"] for s in group]), "peakCommitBytes": stats([s["peakCommitBytes"] for s in group]),
            "shortSamples": short, "build": builds[key], "runDirectory": str(directory)}
    for workload in {k[0] for k in groups}:
        require({k[1] for k in groups if k[0] == workload} == set(BUILDS), f"Missing fourth build: {workload}")
        pairs = Counter()
        for repetition in range(sample_count):
            row = [next(s for s in measured[(workload, b)] if s["repetition"] == repetition) for b in BUILDS]
            require(sorted(s["orderIndex"] for s in row) == [0, 1, 2, 3], f"Duplicate order position: {workload}/{repetition}")
            ordered = sorted(row, key=lambda s: s["orderIndex"])
            pairs.update((a["build"], b["build"]) for a, b in zip(ordered, ordered[1:]))
        require(pairs == Counter({(a, b): sample_count // 4 for a in BUILDS for b in BUILDS if a != b}), f"Unbalanced adjacent build pairs: {workload}")
        for build in BUILDS:
            require(Counter(s["orderIndex"] for s in measured[(workload, build)]) == Counter({i: sample_count // 4 for i in range(4)}), f"Unbalanced order positions: {workload}/{build}")
    return {"metadata": meta, "groups": groups, "directory": str(directory), "reportSha256": file_hash(directory / "report.json"), "samplesCsvSha256": file_hash(directory / "samples.csv")}


def summarize(candidate, baselines, workloads=WORKLOADS):
    require({k[0] for k in candidate["groups"]} == set(workloads), "Candidate workload set differs from required workloads")
    previous = {}
    fixed = ("gxxVersion", "gxxTarget", "java", "os", "cpu", "cpuRegistry", "processors", "seed", "repetitions", "warmups", "minimumSampleMillis", "loadedToolClassSha256", "observerSourceSha256")
    for baseline in baselines:
        require(all(baseline["metadata"][k] == candidate["metadata"][k] for k in fixed), "Host/tool/reference configuration changed between batches")
        for key, group in baseline["groups"].items():
            require(key not in previous, f"Duplicate baseline workload/build: {key}")
            previous[key] = group
    require(set(previous) == set(candidate["groups"]), "Baseline groups do not cover the candidate exactly")
    rows = []
    for workload in workloads:
        after = {b: candidate["groups"][(workload, b)] for b in BUILDS}
        before = {b: previous[(workload, b)] for b in BUILDS}
        identity = ("size", "rounds", "seed", "inputElements", "inputSha256", "expectedOutputSha256")
        reference = after["gxx-stl"]
        for group in list(after.values()) + list(before.values()):
            require(all(group[k] == reference[k] for k in identity), f"Before/after or four-build inputs differ: {workload}")
            require(group["build"]["sourceSha256"] == reference["build"]["sourceSha256"], f"Workload source changed: {workload}")
        med = lambda groups, build: groups[build]["wallNanos"]["median"]
        ratios = {"OPT/GXX-system": med(after, "minic-opt") / med(after, "gxx-stl"),
                  "GXX-own/GXX-system": med(after, "gxx-own") / med(after, "gxx-stl"),
                  "OPT/GXX-own": med(after, "minic-opt") / med(after, "gxx-own")}
        changes = {b: med(after, b) / med(before, b) for b in BUILDS}
        rows.append({"workload": workload, **{k: reference[k] for k in identity}, "before": before, "after": after,
                     "candidateRatios": ratios, "afterOverBefore": changes})
    return {"schemaVersion": 1, "status": "validated", "sampleCountPerGroup": 8, "measurement": "target process wall time; includes startup/input/output",
            "memory": "Job Object PeakProcessMemoryUsed; committed bytes, not RSS, not allocator live bytes",
            "candidateRevision": candidate["metadata"]["revision"], "baselineRevisions": sorted({b["metadata"]["revision"] for b in baselines}),
            "candidateMetadata": candidate["metadata"], "reports": [{k: run[k] for k in ("directory", "reportSha256", "samplesCsvSha256")} for run in [candidate] + baselines],
            "rows": rows}


def load_bulk(directory, baseline_revision, candidate_revision):
    """Validate the separate paired-run schema without treating a smoke run as timing."""
    directory = Path(directory).resolve()
    report = read_json(directory / "report.json")
    require(report.get("schemaVersion") == 1 and report.get("complete") is True and not report.get("errors"), "Incomplete/failed bulk report")
    meta = report["metadata"]
    require(meta.get("measurementEnabled") is True and meta.get("repetitions") == 8, "Bulk data is not an eight-sample measurement")
    require(meta.get("baselineRevision") == baseline_revision and meta.get("candidateRevision") == candidate_revision, "Bulk product revisions differ")
    require(meta.get("workloadRevision") == candidate_revision and meta.get("workloadOrigin") == "committed archive", "Bulk workload is not the pinned final source")
    require("not RSS" in meta.get("metrics", ""), "Unknown bulk memory semantics")
    for label, revision in (("baseline", baseline_revision), ("candidate", candidate_revision), ("workload", candidate_revision)):
        root = directory / label
        check_hash(root / "source.zip", meta[label + "ArchiveSha256"])
        verify_manifest(root / "source", meta[label + "SourceManifest"])
        with zipfile.ZipFile(root / "source.zip") as archive:
            require(archive.comment.decode() == revision, f"Bulk {label} archive revision differs")
            require({n: digest(archive.read(n)) for n in archive.namelist() if not n.endswith("/")} == meta[label + "SourceManifest"], f"Bulk {label} archive manifest differs")
        if label != "workload":
            verify_manifest(root / "compiler/classes", meta[label + "CompilerManifest"])
    check_hash(directory / "bulk-copy.cpp", meta["sourceSha256"])
    require(meta["workloadSourceManifest"]["benchmarks/stl/bulk-copy.cpp"] == meta["sourceSha256"], "Bulk workload copy differs from archive")
    check_hash(directory / "observer/windows-supervisor.exe", meta["observerSha256"])
    check_hash(directory / "candidate/source/benchmarks/tools/windows-supervisor.cpp", meta["observerSourceSha256"])
    require(bool(meta.get("toolClassHashes")), "Missing bulk tool hashes")
    builds = {}
    for build in report["builds"]:
        key = (build["product"], build["build"])
        require(key not in builds and key[0] in ("baseline", "candidate") and key[1] in BUILDS, "Duplicate/unknown bulk artifact")
        require(build.get("status") == "compiled" and build.get("exitCode") == 0 and build["sourceSha256"] == meta["sourceSha256"], f"Bad bulk compilation: {key}")
        artifact = inside(build["artifactPath"], directory)
        check_hash(artifact, build["artifactSha256"])
        command = build["command"]
        if key[1].startswith("gxx"):
            require("-std=c++17" in command and "-O2" in command and (("-nostdinc++" in command) == (key[1] == "gxx-own")), "Wrong bulk reference flags")
        else:
            require(build.get("optimizationLevel") == ("OPTIMIZED" if key[1] == "minic-opt" else "BASELINE") and isinstance(build.get("passes"), list), "Wrong bulk optimization mode")
        if "shimManifest" in build:
            verify_manifest(artifact.parent / "headers", build["shimManifest"])
        if "compatSha256" in build:
            check_hash(artifact.parent / "reference-header-compat.h", build["compatSha256"])
        builds[key] = build
    require(len(builds) == 8, "Need eight paired bulk artifacts")
    samples = report["samples"]
    require(samples, "Missing bulk samples")
    with (directory / "samples.csv").open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        csv_rows, columns = list(reader), reader.fieldnames
    require(len(csv_rows) == len(samples) and set(columns) == set(samples[0]), "Bulk CSV shape differs")
    modes = ("VECTOR_ASSIGN", "VECTOR_ERASE", "POINTER_COPY", "POINTER_COPY_BACKWARD")
    groups, phases, all_groups = defaultdict(list), defaultdict(list), defaultdict(list)
    identity = ("size", "rounds", "iterations", "seed", "inputSha256", "expectedSha256", "transferredElements", "observedElements")
    for sample, csv_row in zip(samples, csv_rows):
        require(all(csv_row[k] == csv_text(sample[k]) for k in columns), "Bulk CSV/JSON differs")
        key = (sample["mode"], sample["product"], sample["build"])
        require(key[0] in modes and key[1:] in builds, "Unknown bulk sample group")
        require(sample["status"] == "COMPLETED" and sample["exitCode"] == 0 and sample["correct"] is True, f"Bad bulk sample: {key}")
        require(sample["phase"] in ("preflight", "calibration", "warmup", "measurement"), "Unknown bulk phase")
        require(all(sample[k] == meta[k] for k in ("size", "rounds", "seed")), "Bulk sample configuration differs")
        raw = inside(sample["directory"], directory)
        for name, field in (("stdin.txt", "inputSha256"), ("stdout.txt", "stdoutSha256"), ("stderr.txt", "stderrSha256")):
            check_hash(raw / name, sample[field])
        require((raw / "stderr.txt").stat().st_size == 0, "Bulk stderr not empty")
        actual = (raw / "stdout.txt").read_bytes().decode().replace("\r\n", "\n")
        expected = (raw / "expected-stdout.txt").read_bytes().decode().replace("\r\n", "\n")
        require(actual == expected and digest(expected.encode()) == sample["expectedSha256"], "Bulk oracle output differs")
        properties = dict(line.split("=", 1) for line in (raw / "observer.properties").read_text().splitlines())
        require(properties.get("schemaVersion") == "1" and properties.get("status", "").upper() == "COMPLETED" and properties.get("win32Error") == "0", "Bulk observer failed")
        for metric in ("wallNanos", "userCpuNanos", "kernelCpuNanos", "peakCommitBytes", "exitCode"):
            require(isinstance(sample[metric], int) and sample[metric] >= 0 and int(properties[metric]) == sample[metric], f"Bulk observer/report differs: {metric}")
        require(sample["wallNanos"] > 0 and sample["observedElements"] > 0 and sample["transferredElements"] >= 0, "Invalid bulk work/time")
        all_groups[key].append(sample)
        phases[(sample["mode"], sample["phase"], sample["repetition"])].append(sample)
        if sample["phase"] == "measurement":
            groups[key].append(sample)
    for key, phase in phases.items():
        require(len(phase) == 8 and sorted(s["order"] for s in phase) == list(range(8)), f"Incomplete bulk paired round: {key}")
        require(len({tuple(s[k] for k in identity) for s in phase}) == 1, f"Bulk paired input differs: {key}")
        ordered = sorted(phase, key=lambda s: s["order"])
        for i in range(4):
            left, right = ordered[i*2:i*2+2]
            require(left["build"] == right["build"] and {left["product"], right["product"]} == {"baseline", "candidate"}, "Bulk products are not paired by build")
            require(left["product"] == ("baseline" if (key[2] + i) % 2 == 0 else "candidate"), "Bulk pair direction did not alternate")
    summaries = {(s["mode"], s["product"], s["build"]): s for s in report["summaries"]}
    require(len(groups) == 32 and len(summaries) == len(report["summaries"]) == 32 and set(groups) == set(summaries), "Need all 32 paired bulk groups")
    evaluated = {}
    for key, group in groups.items():
        require(len(group) == 8 and sorted(s["repetition"] for s in group) == list(range(8)), f"Need eight unique bulk samples: {key}")
        for phase, count in (("preflight", 1), ("warmup", meta["warmups"])):
            require(sorted(s["repetition"] for s in all_groups[key] if s["phase"] == phase) == list(range(count)), "Missing bulk preflight/warmup")
        require(len({tuple(s[k] for k in identity) for s in group}) == 1, "Bulk group input changed")
        published = summaries[key]
        require(published["samples"] == 8 and all(published[k] == group[0][k] for k in ("iterations", "transferredElements", "observedElements")), "Bulk published inputs/sample count differ")
        result = {k: group[0][k] for k in identity}
        for metric in ("wallNanos", "userCpuNanos", "kernelCpuNanos", "peakCommitBytes"):
            result[metric] = stats([s[metric] for s in group])
            require(math.isclose(published[metric+"Median"], result[metric]["median"], rel_tol=1e-12, abs_tol=1e-5)
                    and math.isclose(published[metric+"Mad"], result[metric]["mad"], rel_tol=1e-12, abs_tol=1e-5), "Bulk published statistic differs")
        result["shortSamples"] = sum(s["wallNanos"] < meta["minimumMillis"] * 1_000_000 for s in group)
        require(published["shortSample"] == bool(result["shortSamples"]), "Bulk short warning differs")
        result["build"] = builds[key[1:]]
        result["wallNanosByRepetition"] = [s["wallNanos"] for s in sorted(group, key=lambda s: s["repetition"])]
        evaluated[key] = result
    rows = []
    for mode in modes:
        before = {b: evaluated[(mode, "baseline", b)] for b in BUILDS}
        after = {b: evaluated[(mode, "candidate", b)] for b in BUILDS}
        reference = after["gxx-stl"]
        require(all(all(g[k] == reference[k] for k in identity) for g in list(before.values()) + list(after.values())), "Bulk final inputs differ across products/builds")
        med = lambda data, b: data[b]["wallNanos"]["median"]
        rows.append({"mode": mode, **{k: reference[k] for k in identity}, "before": before, "after": after,
            "afterOverBefore": {b: med(after, b)/med(before, b) for b in BUILDS},
            "pairedAfterOverBefore": {b: stats([x/y for x, y in zip(after[b]["wallNanosByRepetition"], before[b]["wallNanosByRepetition"])]) for b in BUILDS},
            "candidateRatios": {"OPT/GXX-system": med(after, "minic-opt")/med(after, "gxx-stl"), "GXX-own/GXX-system": med(after, "gxx-own")/med(after, "gxx-stl"), "OPT/GXX-own": med(after, "minic-opt")/med(after, "gxx-own")}})
    return {"status": "validated", "metadata": {k: v for k, v in meta.items() if not k.endswith(("SourceManifest", "CompilerManifest"))},
            "reportSha256": file_hash(directory / "report.json"), "samplesCsvSha256": file_hash(directory / "samples.csv"), "directory": str(directory), "rows": rows}


def markdown(summary):
    meta = summary["candidateMetadata"]
    clean = lambda value: str(value).replace("|", "\\|").replace("\r", "").replace("\n", " ")
    fmt = lambda group: f'{group["wallNanos"]["median"]/1e6:.3f} ± {group["wallNanos"]["mad"]/1e6:.3f}'
    lines = ["# STL 原生性能实测", "", f'候选提交：`{summary["candidateRevision"]}`；历史提交：`{", ".join(summary["baselineRevisions"])}`。', "",
             "所有组通过完整性、源码与产物哈希、JSON/CSV 一致性和独立输出 oracle 检查。每组 8 次测量；四构建与历史/候选使用相同 size、rounds、seed、输入及输出哈希。",
             "", "时间为目标进程墙钟的中位数 ± MAD（毫秒），包含启动、输入、工作负载、校验输出；MAD 是中位绝对偏差，不是置信区间。构建时间与运行时间分开。",
             "", f'环境：{clean(meta["os"])}；{clean(meta["cpuRegistry"])}；{clean(meta["gxxVersion"])}。', "",
             f'采样条件：{clean(meta["hostNote"])}', "", "## 候选四构建时间", "",
             "| 负载 | size × rounds | MiniC BASELINE | MiniC OPTIMIZED | G++ + 自研库 | G++ + 系统 STL |",
             "|---|---:|---:|---:|---:|---:|"]
    for row in summary["rows"]:
        lines.append(f'| {row["workload"]} | {row["size"]} × {row["rounds"]} | ' + " | ".join(fmt(row["after"][b]) for b in BUILDS) + " |")
    lines += ["", "## 候选比值", "", "比值均为耗时相除：小于 1 表示分子耗时更少。", "",
              "| 负载 | OPT / 系统 STL | 自研库 G++ / 系统 STL | OPT / 自研库 G++ |", "|---|---:|---:|---:|"]
    for row in summary["rows"]:
        ratios = row["candidateRatios"]
        lines.append(f'| {row["workload"]} | {ratios["OPT/GXX-system"]:.3f} | {ratios["GXX-own/GXX-system"]:.3f} | {ratios["OPT/GXX-own"]:.3f} |')
    lines += ["", "## 前后变化", "", "四个比值均为候选 / 历史耗时。前后为不同串行批次；系统 STL 的比值反映同一工作负载的批次波动，不能把全部差异归因于代码。各历史组的完整 median/MAD 保存在 summary.json。", "",
              "| 负载 | 历史 OPT ms ± MAD | MiniC BASELINE 比值 | MiniC OPTIMIZED 比值 | 自研库 G++ 比值 | 系统 STL 比值 |",
              "|---|---:|---:|---:|---:|---:|"]
    for row in summary["rows"]:
        lines.append(f'| {row["workload"]} | {fmt(row["before"]["minic-opt"])} | ' + " | ".join(f'{row["afterOverBefore"][b]:.3f}' for b in BUILDS) + " |")
    lines += ["", "## 候选进程内存", "", "每格为 Job Object 的进程峰值提交内存：8 次样本的中位数 / 最大值（MiB）。它不是 RSS、实际物理驻留内存，也不是容器分配器的存活字节。分配/复制等计数探针单独报告。", "",
              "| 负载 | MiniC BASELINE | MiniC OPTIMIZED | G++ + 自研库 | G++ + 系统 STL |", "|---|---:|---:|---:|---:|"]
    for row in summary["rows"]:
        cells = [f'{row["after"][b]["peakCommitBytes"]["median"]/1048576:.3f} / {row["after"][b]["peakCommitBytes"]["max"]/1048576:.3f}' for b in BUILDS]
        lines.append(f'| {row["workload"]} | ' + " | ".join(cells) + " |")
    lines += ["", "## 短样本提示", ""]
    phases = {"before": "历史", "after": "候选"}
    warnings = [f'- {row["workload"]} / {phases[phase]} / {NAMES[b]}：{row[phase][b]["shortSamples"]}/8 次低于 {meta["minimumSampleMillis"]} ms，最短 {row[phase][b]["wallNanos"]["min"]/1e6:.3f} ms。'
                for row in summary["rows"] for phase in ("before", "after") for b in BUILDS if row[phase][b]["shortSamples"]]
    lines += warnings or ["所有测量样本均达到配置的最短时长。"]
    if "bulk" in summary:
        bulk = summary["bulk"]
        lines += ["", "## 批量复制/移动配对观察", "", "使用独立的固定提交工作负载，历史与候选按构建相邻交错。时间仍包含启动、数据准备、完整顺序哈希和擦除后尾部重建；不减去控制时间，也不称为纯 memcpy 延迟。四种模式的每组均为 8 次。", "",
                  "| 模式 | size × rounds × iterations | MiniC BASELINE ms ± MAD | MiniC OPTIMIZED ms ± MAD | 自研库 G++ ms ± MAD | 系统 STL ms ± MAD |", "|---|---:|---:|---:|---:|---:|"]
        for row in bulk["rows"]:
            cells = [f'{row["after"][b]["wallNanos"]["median"]/1e6:.3f} ± {row["after"][b]["wallNanos"]["mad"]/1e6:.3f}' for b in BUILDS]
            lines.append(f'| {row["mode"]} | {row["size"]} × {row["rounds"]} × {row["iterations"]} | ' + " | ".join(cells) + " |")
        lines += ["", "下表均为中位耗时之比；逐轮配对比值的 median/MAD 另存 JSON。", "",
                  "| 模式 | OPT / 系统 STL | 自研库 G++ / 系统 STL | OPT / 自研库 G++ | 候选/历史 BASELINE | 候选/历史 OPT | 候选/历史自研 G++ | 候选/历史系统 STL |", "|---|---:|---:|---:|---:|---:|---:|---:|"]
        for row in bulk["rows"]:
            cells = [f'{row["candidateRatios"][k]:.3f}' for k in ("OPT/GXX-system", "GXX-own/GXX-system", "OPT/GXX-own")]
            cells += [f'{row["afterOverBefore"][b]:.3f}' for b in BUILDS]
            lines.append(f'| {row["mode"]} | ' + " | ".join(cells) + " |")
        short = [f'- {row["mode"]} / {phases[phase]} / {NAMES[b]}：{row[phase][b]["shortSamples"]}/8 次低于 {bulk["metadata"]["minimumMillis"]} ms。'
                 for row in bulk["rows"] for phase in ("before", "after") for b in BUILDS if row[phase][b]["shortSamples"]]
        lines += [""] + (short or ["批量模式没有低于配置时长的测量样本。"])
    if summary.get("interruptedRuns"):
        lines += ["", "## 中断批次与重跑", "", "以下原始目录原地保留，整批不计入任何统计；本报告只纳入明确选择且完整通过验证的批次。中断报告与日志的 SHA256 已核对并保存在 summary.json。", "",
                  "| 排除目录 | 原因 |", "|---|---|"]
        for item in summary["interruptedRuns"]:
            lines.append(f'| {clean(item["directory"])} | {clean(item["reason"])} |')
        selected = summary.get("baselineSelection", {}).get("directories", [])
        lines += ["", "纳入的历史批次：" + "、".join(f'`{clean(name)}`' for name in selected) + "。"]
    lines += ["", "源码、可执行产物、编译命令、实际优化 pass、编译耗时、CPU 时间及原始样本引用保存在同目录 summary.json。编译每产物仅测一次，不作编译速度分布结论。此报告不将过程墙钟称为算法内核时间，也不据少量负载宣称普遍接近系统 STL。", ""]
    return "\n".join(lines)


def select_baseline_runs(root, environment):
    """Resolve explicit retries, retaining and disclosing every excluded baseline batch."""
    root = Path(root).resolve()

    def relative_path(value, directory=True):
        require(isinstance(value, str) and value.strip() and not Path(value).is_absolute(), "Run paths must be nonempty relative paths")
        path = inside(root / value, root)
        require(path != root, "Run path cannot be the result root")
        require(path.is_dir() if directory else path.is_file(), f"Missing run evidence: {path}")
        return path

    explicit = "baselineRuns" in environment
    if explicit:
        names = environment["baselineRuns"]
        require(isinstance(names, list) and names, "baselineRuns must be a nonempty directory list")
        selected = [relative_path(name) for name in names]
    else:
        selected = sorted(path for path in root.glob("baseline-rounds-*") if path.is_dir())
        for path in selected:
            require(path.is_dir() and re.fullmatch(r"baseline-rounds-[1-9][0-9]*", path.name), f"Unexpected baseline path: {path}")
    require(len(set(selected)) == len(selected), "Duplicate baselineRuns directory")
    exclusions = environment.get("interruptedRuns", [])
    require(isinstance(exclusions, list), "interruptedRuns must be a list")
    interrupted, excluded = [], set()
    for item in exclusions:
        require(isinstance(item, dict) and isinstance(item.get("reason"), str) and item["reason"].strip(), "Interrupted run requires a nonempty reason")
        path = relative_path(item.get("directory"))
        require(path not in excluded, "Duplicate interruptedRuns directory")
        require(path not in selected, "A selected run cannot also be interrupted/excluded")
        check_hash(path / "report.json", item.get("reportSha256"))
        log = relative_path(item.get("logDirectoryRelativePath", item["directory"] + ".txt"), directory=False)
        check_hash(log, item.get("logSha256"))
        excluded.add(path)
        interrupted.append({"directory": path.relative_to(root).as_posix(), "reason": item["reason"],
            "reportSha256": item["reportSha256"], "logSha256": item["logSha256"],
            "logDirectoryRelativePath": log.relative_to(root).as_posix(), "includedInStatistics": False})
    existing = {p.resolve() for p in root.glob("baseline-*") if p.is_dir()}
    require(existing <= set(selected) | excluded, "Unselected baseline directories require explicit interruptedRuns reasons and hashes: " + ", ".join(str(p) for p in sorted(existing - set(selected) - excluded)))
    return selected, interrupted, explicit


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("build/stl-final-performance"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    root = args.root.resolve()
    require((root / "completed.txt").is_file(), "Final runner has not completed; no performance conclusion generated")
    environment = read_json(root / "environment.json")
    for key in ("candidate", "baseline"):
        require(re.fullmatch(r"[0-9a-f]{40,64}", environment[key]), f"Invalid pinned {key} revision")
    directories, interrupted, explicit = select_baseline_runs(root, environment)
    candidate = load_run(root / "candidate", environment["candidate"])
    baselines = []
    for directory in directories:
        run = load_run(directory, environment["baseline"])
        named_rounds = re.fullmatch(r"baseline-(?:rounds|retry)-([1-9][0-9]*)", directory.name)
        if named_rounds:
            rounds = int(named_rounds.group(1))
            require(all(g["rounds"] == rounds for g in run["groups"].values()), f"Wrong rounds group: {directory}")
        baselines.append(run)
    result = summarize(candidate, baselines)
    result["bulk"] = load_bulk(root / "bulk", environment["baseline"], environment["candidate"])
    result["environment"] = environment
    result["baselineSelection"] = {"explicit": explicit, "directories": [p.relative_to(root).as_posix() for p in directories]}
    result["interruptedRuns"] = interrupted
    # The huge full manifests remain in immutable raw reports, referenced by their hashes.
    result["candidateMetadata"] = {k: v for k, v in result["candidateMetadata"].items() if k not in ("sourceManifest", "compilerClassManifest")}
    output = (args.output or root / "summary").resolve()
    require(not output.exists(), f"Refusing to overwrite report output: {output}")
    output.mkdir(parents=True)
    (output / "summary.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output / "report.md").write_text(markdown(result), encoding="utf-8")
    print(f"Validated {len(result['rows'])} workloads; reports: {output}")


if __name__ == "__main__":
    try:
        main()
    except (InvalidData, KeyError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"Report not generated: {error}", file=sys.stderr)
        sys.exit(2)
