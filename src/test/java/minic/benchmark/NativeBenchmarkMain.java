package minic.benchmark;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.link.Linker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compares baseline/optimized native MiniC and G++ on C-subset workloads.
 * An optimization mode name does not imply that passes are registered or that it is faster.
 * Runs from the repository root. Use --help for configuration. Child compilation is isolated
 * so even a stuck MiniC compiler can be timed out by the parent process.
 */
public final class NativeBenchmarkMain {
    private static final List<String> WORKLOADS = List.of("array-scan", "function-calls", "memory-copy");
    private static final List<String> GXX_FLAGS = List.of("-std=c++17", "-O2");
    private static final int OUTPUT_LIMIT = 65_536;

    private NativeBenchmarkMain() {}

    public static void main(String[] args) throws Exception {
        if ((args.length == 2 || args.length == 3) && args[0].equals("--compile-minic")) {
            compileMiniC(Path.of(args[1]), args.length == 3 ? OptimizationLevel.valueOf(args[2]) : OptimizationLevel.BASELINE);
            return;
        }
        if (List.of(args).contains("--help")) { help(); return; }
        Config config = Config.parse(args);
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            throw new IllegalStateException("The current MiniC native backend emits Windows x64 PE executables.");
        }
        Files.createDirectories(config.output());
        NativeBenchmarkReport report = new NativeBenchmarkReport();
        try {
            metadata(config, report);
            for (String workload : WORKLOADS) benchmark(config, report, workload);
        } catch (Exception exception) {
            report.errors.add(exception.toString());
            throw exception;
        } finally {
            report.write(config.output(), config.minSampleMillis() * 1_000_000L);
            System.out.println("Benchmark report: " + config.output().resolve("report.json"));
        }
    }

    private static void metadata(Config config, NativeBenchmarkReport report) throws Exception {
        var version = NativeBenchmarkSupport.run(List.of(config.gxx(), "--version"), Path.of("."), "",
                config.compileTimeout(), OUTPUT_LIMIT);
        version.requireSuccess();
        var target = NativeBenchmarkSupport.run(List.of(config.gxx(), "-dumpmachine"), Path.of("."), "",
                config.compileTimeout(), OUTPUT_LIMIT);
        target.requireSuccess();
        report.metadata.put("startedAt", Instant.now().toString());
        report.metadata.put("scope", "C subset native comparison; these workloads do not measure STL implementations");
        report.metadata.put("optimizationInterpretation", "Mode names are configurations, not performance claims; per-build passNames records passes actually applied and can be empty");
        report.metadata.put("hostControl", "uncontrolled shared workstation; concurrent development may affect timing; not a release performance gate");
        report.metadata.put("timing", "process-wall including start, stdin, execution, and output drain; not CPU/kernel time");
        report.metadata.put("compileTiming", "MiniC process-wall includes JVM startup; compilerPipelineNanos excludes JVM startup; g++ compilation is process-wall");
        report.metadata.put("gxxPath", config.gxx());
        report.metadata.put("gxxVersion", version.stdout().strip());
        report.metadata.put("gxxTarget", target.stdout().strip());
        report.metadata.put("gxxFlags", GXX_FLAGS);
        report.metadata.put("minicFlags", List.of("LanguageMode.C", "native executable without debug runtime instrumentation"));
        report.metadata.put("executionOrder", "six permutations rotate baseline, optimized and G++; same input and Java checksum oracle for every executable");
        report.metadata.put("commit", config.commit().isBlank() ? git("rev-parse", "HEAD") : config.commit());
        report.metadata.put("workingTreeStatus", git("status", "--porcelain"));
        report.metadata.put("cpu", config.cpu().equals("unknown") ? cpuDescription() : config.cpu());
        report.metadata.put("processors", Runtime.getRuntime().availableProcessors());
        report.metadata.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        report.metadata.put("java", System.getProperty("java.runtime.version"));
        report.metadata.put("size", config.size());
        report.metadata.put("initialRounds", config.rounds());
        report.metadata.put("seed", config.seed());
        report.metadata.put("repetitions", config.repetitions());
        report.metadata.put("warmups", config.warmups());
        report.metadata.put("maxCalibrationDoublings", config.calibrationSteps());
        report.metadata.put("calibrationPreflight", "one launch per executable before calibration to expose first-launch overhead separately");
        report.metadata.put("minimumSampleMillis", config.minSampleMillis());
        report.metadata.put("runTimeoutMillis", config.runTimeout().toMillis());
        report.metadata.put("compileTimeoutMillis", config.compileTimeout().toMillis());
        report.metadata.put("outputLimitBytesPerStream", OUTPUT_LIMIT);
        report.metadata.put("stlReferenceLibrary", "not used: these are C subset workloads");
    }

    private static void benchmark(Config config, NativeBenchmarkReport report, String workload) throws Exception {
        byte[] source = Files.readAllBytes(Path.of("benchmarks", "native", workload + ".cpp"));
        String sourceHash = sha256(source);
        Path sources = config.output().resolve("sources");
        Files.createDirectories(sources);
        Path binaries = config.output().resolve("binaries");
        Files.createDirectories(binaries);
        // Unique basenames prevent CompilerApi's default artifact directory from colliding with other tests.
        Path sourcePath = sources.resolve("bench_" + workload + "_" + UUID.randomUUID().toString().replace("-", "") + ".cpp");
        Files.write(sourcePath, source);
        Map<String, Path> executables = new LinkedHashMap<>();
        for (OptimizationLevel level : List.of(OptimizationLevel.BASELINE, OptimizationLevel.OPTIMIZED)) {
            String compiler = "minic-" + level.name().toLowerCase(java.util.Locale.ROOT);
            var minic = NativeBenchmarkSupport.run(javaCommand("--compile-minic", sourcePath.toString(), level.name()), Path.of("."),
                    "", config.compileTimeout(), OUTPUT_LIMIT);
            minic.requireSuccess();
            var compiled = NativeBenchmarkSupport.compilation(minic.stdout());
            if (compiled.level() != level) throw new IllegalStateException("Compiler reported the wrong optimization mode");
            if (!Files.isRegularFile(compiled.artifact())) throw new IllegalStateException("MiniC artifact is missing");
            // CompilerApi derives the working artifact name from the source. Save
            // this build before the next mode compiles that same source basename.
            Path saved = binaries.resolve(workload + "-" + compiler + ".exe");
            Files.copy(compiled.artifact(), saved);
            executables.put(compiler, saved);
            report.builds.add(new NativeBenchmarkReport.Build(workload, compiler, compiled.level().name(), compiled.passNames(),
                    sourceHash, saved.toString(), sha256(Files.readAllBytes(saved))));
            recordCompile(report, config, workload, compiler, minic.wallNanos(), compiled.compilerPipelineNanos(), sourceHash);
        }

        Path referenceExecutable = binaries.resolve(workload + "-gxx.exe");
        if (Files.exists(referenceExecutable)) throw new IllegalArgumentException("output executable already exists: " + referenceExecutable);
        List<String> command = new ArrayList<>(List.of(config.gxx()));
        command.addAll(GXX_FLAGS);
        command.addAll(List.of(sourcePath.toString(), "-o", referenceExecutable.toString()));
        var reference = NativeBenchmarkSupport.run(command, Path.of("."), "", config.compileTimeout(), OUTPUT_LIMIT);
        reference.requireSuccess();
        executables.put("g++", referenceExecutable);
        report.builds.add(new NativeBenchmarkReport.Build(workload, "g++", null, List.of(), sourceHash,
                referenceExecutable.toString(), sha256(Files.readAllBytes(referenceExecutable))));
        recordCompile(report, config, workload, "g++", reference.wallNanos(), null, sourceHash);

        int rounds = config.rounds();
        long expected = NativeBenchmarkSupport.expectedChecksum(workload, config.size(), rounds, config.seed());
        if (config.minSampleMillis() > 0) {
            for (String compiler : NativeBenchmarkSupport.order(0)) {
                runSample(config, report, executables.get(compiler), workload, compiler,
                        "preflight", 0, rounds, expected, sourceHash);
            }
            for (int step = 0; step <= config.calibrationSteps(); step++) {
                long shortest = Long.MAX_VALUE;
                for (String compiler : NativeBenchmarkSupport.order(step)) {
                    long elapsed = runSample(config, report, executables.get(compiler), workload, compiler,
                            "calibration", step, rounds, expected, sourceHash);
                    shortest = Math.min(shortest, elapsed);
                }
                if (shortest >= config.minSampleMillis() * 1_000_000L || step == config.calibrationSteps()
                        || (long)config.size() * rounds * 2 > 2_000_000_000L) break;
                rounds = Math.multiplyExact(rounds, 2);
                expected = NativeBenchmarkSupport.expectedChecksum(workload, config.size(), rounds, config.seed());
            }
        }
        for (int i = 0; i < config.warmups() + config.repetitions(); i++) {
            boolean warmup = i < config.warmups();
            for (String compiler : NativeBenchmarkSupport.order(i)) {
                runSample(config, report, executables.get(compiler), workload, compiler,
                        warmup ? "warmup" : "measurement", warmup ? i : i - config.warmups(), rounds, expected, sourceHash);
            }
        }
        System.out.println(workload + ": checksums verified, size=" + config.size() + ", rounds=" + rounds);
        report.write(config.output(), config.minSampleMillis() * 1_000_000L);
    }

    private static long runSample(Config config, NativeBenchmarkReport report, Path executable, String workload,
                                  String compiler, String phase, int repetition, int rounds, long expected,
                                  String sourceHash) throws Exception {
        String input = config.size() + " " + rounds + " " + config.seed() + "\n";
        var run = NativeBenchmarkSupport.run(List.of(executable.toAbsolutePath().toString()), Path.of("."), input,
                config.runTimeout(), OUTPUT_LIMIT);
        try {
            run.requireSuccess();
            if (!run.stderr().isBlank()) throw new IllegalStateException("unexpected stderr: " + run.stderr());
            NativeBenchmarkSupport.requireChecksum(run.stdout(), expected);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(workload + "/" + compiler + "/" + phase + "/" + repetition
                    + " input=" + input.strip() + ": " + exception.getMessage(), exception);
        }
        report.samples.add(new NativeBenchmarkReport.Sample(workload, compiler, phase, repetition, config.size(),
                rounds, config.seed(), run.wallNanos(), null, Long.toUnsignedString(expected), sourceHash));
        return run.wallNanos();
    }

    private static void recordCompile(NativeBenchmarkReport report, Config config, String workload,
                                      String compiler, long wall, Long internal, String hash) {
        report.samples.add(new NativeBenchmarkReport.Sample(workload, compiler, "compile", 0, config.size(),
                config.rounds(), config.seed(), wall, internal, "", hash));
    }

    private static void compileMiniC(Path source, OptimizationLevel level) throws Exception {
        var sourceFile = new SourceFile(source.toAbsolutePath().toString(), Files.readString(source));
        long started = System.nanoTime();
        CompilerApi api = new CompilerApi(sourceFile, LanguageMode.C, level);
        Linker linker = api.stages().stream().filter(Linker.class::isInstance).map(Linker.class::cast)
                .findFirst().orElseThrow();
        api.runThrough(linker);
        if (!linker.succeeded()) {
            throw new IllegalStateException("MiniC compile failed: " + api.stages().stream()
                    .flatMap(stage -> stage.errors().stream()).toList());
        }
        System.out.println("compile_ns=" + (System.nanoTime() - started));
        System.out.println("artifact=" + linker.result().executableArtifactOptional().orElseThrow().path().toAbsolutePath());
        Assembler assembler = api.stages().stream().filter(Assembler.class::isInstance).map(Assembler.class::cast).findFirst().orElseThrow();
        System.out.println("optimization_level=" + assembler.optimizationLevel().name());
        List<String> passes = assembler.optimizationResult().passNames();
        System.out.println("pass_count=" + passes.size());
        for (int index = 0; index < passes.size(); index++)
            System.out.println("pass_" + index + "=" + Base64.getEncoder().encodeToString(passes.get(index).getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> javaCommand(String... args) {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Xmx1g", "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                "-cp", System.getProperty("java.class.path"), NativeBenchmarkMain.class.getName()));
        command.addAll(List.of(args));
        return command;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String git(String... arguments) {
        try {
            List<String> command = new ArrayList<>(List.of("git"));
            command.addAll(List.of(arguments));
            var result = NativeBenchmarkSupport.run(command, Path.of("."), "", Duration.ofSeconds(10), OUTPUT_LIMIT);
            result.requireSuccess();
            return result.stdout().strip();
        } catch (Exception e) { return "unavailable: " + e.getClass().getSimpleName(); }
    }

    private static String cpuDescription() {
        try {
            Path reg = Path.of(System.getenv().getOrDefault("SystemRoot", "C:/Windows"), "System32", "reg.exe");
            var result = NativeBenchmarkSupport.run(List.of(reg.toString(), "query",
                    "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "/v", "ProcessorNameString"),
                    Path.of("."), "", Duration.ofSeconds(10), OUTPUT_LIMIT);
            result.requireSuccess();
            return result.stdout().lines().filter(line -> line.contains("REG_SZ"))
                    .map(line -> line.substring(line.indexOf("REG_SZ") + 6).strip()).findFirst().orElse("unknown");
        } catch (Exception ignored) { return "unknown (supply --cpu to identify the machine)"; }
    }

    private static void help() {
        System.out.println("""
                Run NativeBenchmarkMain from the repository root (Windows x64, JDK 21).
                Options use --name=value (no shell is invoked):
                  --gxx=C:/mingw64/bin/g++.exe   Reference compiler; fixed -std=c++17 -O2
                  --output=build/native-benchmark/<timestamp>   JSON, CSV, exact sources and all three binaries
                  --size=65536 --rounds=512 --seed=1729
                  --repetitions=5 --warmups=1
                  --min-sample-ms=100 --calibration-steps=6
                  --run-timeout-seconds=30 --compile-timeout-seconds=120
                  --commit=<revision> --cpu=<machine description>   Optional metadata overrides
                Every workload builds MiniC BASELINE, MiniC OPTIMIZED, and G++ separately. Actual
                optimization levels and applied pass names are retained; an empty pass list is
                valid and OPTIMIZED does not itself claim acceleration. Measurement order rotates.
                After a separately recorded preflight launch, calibration doubles rounds for ALL three builds until all process-wall samples meet
                the minimum or the doubling limit is reached. All actual inputs and raw samples are
                retained; short measurement samples are flagged. Increase the budget on a fixed,
                idle machine before drawing performance conclusions. Time includes startup and I/O;
                no kernel/CPU time or allocation counts are claimed. Checksums are verified against
                an independent Java oracle on every run. A failure produces a nonzero exit and report.
                Smoke example: --size=16 --rounds=2 --warmups=0 --repetitions=1 --min-sample-ms=0
                Existing output report files are refused; use a fresh output directory per run.
                These workloads exercise the current C subset; they do not benchmark STL containers.
                """);
    }

    record Config(String gxx, Path output, int size, int rounds, int seed, int repetitions, int warmups,
                  int calibrationSteps, long minSampleMillis, Duration runTimeout, Duration compileTimeout,
                  String commit, String cpu) {
        static Config parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String arg : args) {
                int equals = arg.indexOf('=');
                if (!arg.startsWith("--") || equals < 3) throw new IllegalArgumentException("expected --name=value: " + arg);
                if (values.put(arg.substring(2, equals), arg.substring(equals + 1)) != null)
                    throw new IllegalArgumentException("duplicate option: " + arg);
            }
            String gxx = values.remove("gxx");
            if (gxx == null) gxx = "C:/mingw64/bin/g++.exe";
            String output = values.remove("output");
            Path directory = Path.of(output == null ? "build/native-benchmark/" + Instant.now().toEpochMilli() : output)
                    .toAbsolutePath().normalize();
            if (Files.exists(directory.resolve("report.json")) || Files.exists(directory.resolve("samples.csv")))
                throw new IllegalArgumentException("output already contains a report: " + directory);
            int size = integer(values, "size", 65536, 1, 16_777_216);
            int rounds = integer(values, "rounds", 512, 1, 1_000_000);
            int seed = integer(values, "seed", 1729, 0, Integer.MAX_VALUE);
            int reps = integer(values, "repetitions", 5, 1, 100);
            int warmups = integer(values, "warmups", 1, 0, 100);
            int calibration = integer(values, "calibration-steps", 6, 0, 10);
            if ((long)size * rounds > 2_000_000_000L) throw new IllegalArgumentException("size * rounds exceeds the workload budget");
            int minimum = integer(values, "min-sample-ms", 100, 0, 60_000);
            Duration runTimeout = Duration.ofSeconds(integer(values, "run-timeout-seconds", 30, 1, 3600));
            Duration compileTimeout = Duration.ofSeconds(integer(values, "compile-timeout-seconds", 120, 1, 3600));
            String commit = values.remove("commit");
            String cpu = values.remove("cpu");
            if (!values.isEmpty()) throw new IllegalArgumentException("unknown options: " + values.keySet());
            return new Config(gxx, directory, size, rounds, seed, reps, warmups, calibration, minimum,
                    runTimeout, compileTimeout, commit == null ? "" : commit,
                    cpu == null ? System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unknown") : cpu);
        }

        private static int integer(Map<String, String> values, String key, int fallback, int min, int max) {
            String value = values.remove(key);
            int parsed = value == null ? fallback : Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new IllegalArgumentException("invalid --" + key + ": " + parsed);
            return parsed;
        }
    }
}
