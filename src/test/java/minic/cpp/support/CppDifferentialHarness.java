package minic.cpp.support;

import minic.compiler.LanguageMode;
import minic.compiler.ir.optimize.OptimizationLevel;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Test-only C++17 reference runner; no external compiler is used by product code. */
public final class CppDifferentialHarness {
    public enum Backend { MINIC_NATIVE, MINIC_DEBUG, GXX }
    public enum Status { OK, COMPILE_ERROR, COMPILE_TIMEOUT, RUNTIME_ERROR, NONZERO_EXIT, RUN_TIMEOUT, OUTPUT_LIMIT, TOOL_ERROR }
    public record Outcome(Backend backend, Status status, int exitCode, String stdout, String stderr, String diagnostics) {}
    public record Limits(Duration compileTimeout, Duration runTimeout, int debugSteps, int maxOutputBytes) {
        public Limits {
            if (compileTimeout.isNegative() || compileTimeout.isZero() || runTimeout.isNegative()
                    || runTimeout.isZero() || debugSteps < 1 || maxOutputBytes < 1) {
                throw new IllegalArgumentException("All execution limits must be positive");
            }
        }
        public static Limits defaults() { return new Limits(Duration.ofSeconds(20), Duration.ofSeconds(10), 50_000, 1_048_576); }
    }
    public record Report(Map<Backend, Outcome> outcomes, List<String> issues) {
        public boolean passed() { return issues.isEmpty(); }
        public String describe() { return String.join("\n", issues) + "\n" + outcomes; }
    }
    private final Path temporary;
    private final String referenceCompiler;
    private final Limits limits;
    private final LanguageMode languageMode;
    private final OptimizationLevel optimizationLevel;

    public CppDifferentialHarness(Path temporary, String referenceCompiler, Limits limits) {
        this(temporary, referenceCompiler, limits, LanguageMode.C);
    }

    public CppDifferentialHarness(Path temporary, String referenceCompiler, Limits limits, LanguageMode languageMode) {
        this(temporary, referenceCompiler, limits, languageMode, OptimizationLevel.BASELINE);
    }

    public CppDifferentialHarness(Path temporary, String referenceCompiler, Limits limits, OptimizationLevel optimizationLevel) {
        this(temporary, referenceCompiler, limits, LanguageMode.C, optimizationLevel);
    }

    public CppDifferentialHarness(Path temporary, String referenceCompiler, Limits limits, LanguageMode languageMode,
                                  OptimizationLevel optimizationLevel) {
        this.temporary = Objects.requireNonNull(temporary).toAbsolutePath();
        this.referenceCompiler = Objects.requireNonNull(referenceCompiler);
        this.limits = Objects.requireNonNull(limits);
        this.languageMode = Objects.requireNonNull(languageMode);
        this.optimizationLevel = Objects.requireNonNull(optimizationLevel);
    }

    /** Uses precisely the same saved source and stdin for all three backends. */
    public Report run(String name, String source, String standardInput) throws IOException, InterruptedException {
        return compileOrRun(name, source, standardInput, false);
    }

    /** Compile each backend without executing even an unexpectedly accepted negative case. */
    public Report compile(String name, String source) throws IOException, InterruptedException {
        return compileOrRun(name, source, "", true);
    }

    private Report compileOrRun(String name, String source, String standardInput, boolean compileOnly)
            throws IOException, InterruptedException {
        Files.createDirectories(temporary);
        String prefix = name.replaceAll("[^A-Za-z0-9_-]", "_");
        Path directory = Files.createTempDirectory(temporary, prefix.substring(0, Math.min(prefix.length(), 40)) + "-");
        Path sourcePath = directory.resolve("program.cpp");
        Path inputPath = directory.resolve("stdin.txt");
        Files.writeString(sourcePath, source);
        Files.writeString(inputPath, standardInput);
        return compare(List.of(runMiniC(Backend.MINIC_NATIVE, directory, sourcePath, inputPath, standardInput, compileOnly),
                runMiniC(Backend.MINIC_DEBUG, directory, sourcePath, inputPath, standardInput, compileOnly),
                runReference(directory, sourcePath, standardInput, compileOnly)));
    }

    private Outcome runMiniC(Backend backend, Path directory, Path source, Path input, String stdin, boolean compileOnly)
            throws InterruptedException {
        Path resultFile = directory.resolve(backend.name() + ".properties");
        Path phaseFile = directory.resolve(backend.name() + ".phase");
        try {
            var command = ProcessProbe.javaCommand(MiniCWorker.class, backend.name(), source.toString(),
                    input.toString(), resultFile.toString(), Integer.toString(limits.debugSteps()),
                    Integer.toString(limits.maxOutputBytes()), Long.toString(limits.runTimeout().toNanos()), languageMode.name(),
                    Long.toString(limits.compileTimeout().toNanos()), phaseFile.toString(), optimizationLevel.name(),
                    Boolean.toString(compileOnly));
            // Actual phase limits are enforced inside the worker. This outer deadline also bounds
            // JVM startup/reporting and remains a fallback if a watchdog cannot publish its result.
            Duration workerTimeout = limits.compileTimeout().plusSeconds(5);
            if (backend == Backend.MINIC_DEBUG) workerTimeout = workerTimeout.plus(limits.runTimeout());
            var process = BoundedProcess.run(command, Path.of("").toAbsolutePath(), "",
                    workerTimeout, limits.maxOutputBytes());
            if (process.timedOut()) {
                Status timeout = Files.isRegularFile(phaseFile) && Files.readString(phaseFile).equals("run")
                        ? Status.RUN_TIMEOUT : Status.COMPILE_TIMEOUT;
                return failed(backend, timeout, "MiniC worker exceeded the outer process deadline in its recorded phase");
            }
            if (process.outputExceeded()) return failed(backend, Status.OUTPUT_LIMIT, "MiniC worker console output limit exceeded");
            if (process.exitCode() != 0 || !Files.isRegularFile(resultFile)) {
                return failed(backend, Status.TOOL_ERROR, "MiniC worker failed: exit=" + process.exitCode()
                        + " stdout=" + process.stdout() + " stderr=" + process.stderr());
            }
            if (Files.size(resultFile) > 12L * limits.maxOutputBytes() + 65536) {
                return failed(backend, Status.OUTPUT_LIMIT, "MiniC worker report exceeds allowed size");
            }
            Outcome compiled = MiniCWorker.readResult(resultFile, backend);
            if (compileOnly || backend == Backend.MINIC_DEBUG || compiled.status() != Status.OK) return compiled;
            return execute(backend, directory.resolve("native/program.exe"), directory, stdin);
        } catch (IOException | IllegalArgumentException error) {
            return failed(backend, Status.TOOL_ERROR, error.toString());
        }
    }

    private Outcome runReference(Path directory, Path source, String stdin, boolean compileOnly) throws InterruptedException {
        try {
            Path executable = directory.resolve("reference.exe");
            var command = new ArrayList<>(List.of(referenceCompiler));
            command.addAll(languageMode == LanguageMode.CPP17_ALGORITHM
                    ? referenceFlags(directory) : List.of("-std=c++17", "-O2"));
            command.addAll(List.of(source.toString(), "-o", executable.toString()));
            var result = BoundedProcess.run(command, directory, "",
                    limits.compileTimeout(), limits.maxOutputBytes());
            if (result.timedOut()) return failed(Backend.GXX, Status.COMPILE_TIMEOUT, "Reference compilation timed out");
            if (result.outputExceeded()) return failed(Backend.GXX, Status.OUTPUT_LIMIT, "Reference compiler output limit exceeded");
            if (result.exitCode() != 0) return new Outcome(Backend.GXX, Status.COMPILE_ERROR, result.exitCode(),
                    "", "", result.stdout() + result.stderr());
            if (compileOnly) return new Outcome(Backend.GXX, Status.OK, 0, "", "", "");
            return execute(Backend.GXX, executable, directory, stdin);
        } catch (IOException error) {
            return failed(Backend.GXX, Status.TOOL_ERROR,
                    "Set MINIC_CXX or GXX to an executable C++ compiler path. " + error);
        }
    }

    private Outcome execute(Backend backend, Path executable, Path directory, String stdin)
            throws IOException, InterruptedException {
        var result = BoundedProcess.run(List.of(executable.toString()), directory, stdin,
                limits.runTimeout(), limits.maxOutputBytes());
        Status status = result.timedOut() ? Status.RUN_TIMEOUT : result.outputExceeded() ? Status.OUTPUT_LIMIT
                : result.exitCode() != 0 ? Status.NONZERO_EXIT : Status.OK;
        return new Outcome(backend, status, result.exitCode(), result.stdout(), result.stderr(), "");
    }

    static Outcome failed(Backend backend, Status status, String detail) {
        return new Outcome(backend, status, -1, "", "", detail);
    }

    public static Report compare(List<Outcome> outcomes) {
        var byBackend = new EnumMap<Backend, Outcome>(Backend.class);
        for (var outcome : outcomes) {
            if (byBackend.put(outcome.backend(), outcome) != null) {
                throw new IllegalArgumentException("Duplicate backend " + outcome.backend());
            }
        }
        if (byBackend.size() != Backend.values().length) throw new IllegalArgumentException("All three backends are required");
        List<String> issues = new ArrayList<>();
        for (Backend backend : Backend.values()) {
            Outcome outcome = byBackend.get(backend);
            if (outcome.status() != Status.OK || outcome.exitCode() != 0) {
                issues.add(backend + " " + outcome.status() + " exit=" + outcome.exitCode() + ": " + outcome.diagnostics());
            }
        }
        Outcome reference = byBackend.get(Backend.GXX);
        if (reference.status() == Status.OK) {
            for (Backend backend : List.of(Backend.MINIC_NATIVE, Backend.MINIC_DEBUG)) {
                Outcome actual = byBackend.get(backend);
                if (actual.status() != Status.OK) continue;
                if (!normalize(actual.stdout()).equals(normalize(reference.stdout()))) issues.add(backend + " stdout mismatch");
                if (!normalize(actual.stderr()).equals(normalize(reference.stderr()))) issues.add(backend + " stderr mismatch");
            }
        }
        return new Report(Map.copyOf(byBackend), List.copyOf(issues));
    }

    private static String normalize(String output) { return output.replace("\r\n", "\n"); }

    /** A single executable path, deliberately never parsed as a shell command. */
    public static String referenceCompiler(Map<String, String> environment) {
        for (String variable : List.of("MINIC_CXX", "GXX")) {
            String value = environment.get(variable);
            if (value != null && !value.isBlank()) return value;
        }
        return "g++";
    }

    /** MinGW 8 needs const narrow-string prototypes, but its cwchar wrappers require
     * the original macro. Scope the compatibility setting to string.h alone. */
    public static List<String> referenceFlags(Path directory) throws IOException {
        Path header=directory.resolve("reference-header-compat.h").toAbsolutePath();
        Files.writeString(header,"""
                #if defined(__MINGW32__) && defined(__GNUC__) && __GNUC__ <= 8
                #include <_mingw.h>
                #include <wchar.h>
                #pragma push_macro("_CONST_RETURN")
                #undef _CONST_RETURN
                #define _CONST_RETURN const
                #include <string.h>
                #pragma pop_macro("_CONST_RETURN")
                #endif
                """);
        return List.of("-std=c++17", "-O2", "-include", header.toString());
    }
}
