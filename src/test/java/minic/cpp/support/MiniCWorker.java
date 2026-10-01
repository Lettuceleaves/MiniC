package minic.cpp.support;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrLowerer;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.cpp.support.CppDifferentialHarness.Backend;
import minic.cpp.support.CppDifferentialHarness.Outcome;
import minic.cpp.support.CppDifferentialHarness.Status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Child JVM boundary: a hanging compiler/debug step cannot hang the JUnit process. */
public final class MiniCWorker {
    private MiniCWorker() {}

    public static void main(String[] args) throws Exception {
        Backend backend = Backend.valueOf(args[0]);
        Path sourcePath = Path.of(args[1]);
        Path resultPath = Path.of(args[3]);
        int stepLimit = Integer.parseInt(args[4]);
        int outputLimit = Integer.parseInt(args[5]);
        long runTimeoutNanos = Long.parseLong(args[6]);
        LanguageMode languageMode = LanguageMode.valueOf(args[7]);
        long compileTimeoutNanos = Long.parseLong(args[8]);
        Path phasePath = Path.of(args[9]);
        OptimizationLevel optimizationLevel = args.length > 10 ? OptimizationLevel.valueOf(args[10]) : OptimizationLevel.BASELINE;
        var completed = new AtomicBoolean();
        try {
            Compilation compilation = timed(phasePath, "compile", compileTimeoutNanos, resultPath, backend,
                    Status.COMPILE_TIMEOUT, completed, () -> {
                        SourceFile source = new SourceFile(sourcePath.toString(), Files.readString(sourcePath));
                        CompilerApi compiler = compiler(source, sourcePath.getParent(), languageMode,
                                backend == Backend.MINIC_NATIVE ? optimizationLevel : OptimizationLevel.BASELINE);
                        var ir = compiler.stages().stream().filter(IrLowerer.class::isInstance)
                                .map(IrLowerer.class::cast).findFirst().orElseThrow();
                        var link = compiler.stages().stream().filter(Linker.class::isInstance).findFirst().orElseThrow();
                        compiler.runThrough(backend == Backend.MINIC_NATIVE ? link : ir);
                        boolean successful = (backend == Backend.MINIC_NATIVE ? link : ir).succeeded();
                        String errors = compiler.stages().stream().flatMap(stage -> stage.errors().stream())
                                .map(error -> error.describe()).collect(Collectors.joining("\n"));
                        return new Compilation(source, successful ? ir.result() : null, successful, cap(errors, outputLimit));
                    });
            if (!compilation.successful()) {
                publish(resultPath, CppDifferentialHarness.failed(backend, Status.COMPILE_ERROR, compilation.errors()), completed);
                return;
            }
            if (backend == Backend.MINIC_NATIVE) {
                publish(resultPath, new Outcome(backend, Status.OK, 0, "", "", ""), completed);
                return;
            }
            Outcome outcome = timed(phasePath, "run", runTimeoutNanos, resultPath, backend,
                    Status.RUN_TIMEOUT, completed, () -> {
                DebugApi debug = DebugApi.fromIr(compilation.source(), compilation.ir(), Files.readString(Path.of(args[2])));
                int steps = 0;
                while (debug.canNext() && steps++ < stepLimit) {
                    debug.next();
                    if (exceeds(debug.current().runtime().stdout(), outputLimit)
                            || exceeds(debug.current().runtime().stderr(), outputLimit)) {
                        return new Outcome(backend, Status.OUTPUT_LIMIT, -1,
                                cap(debug.current().runtime().stdout(), outputLimit),
                                cap(debug.current().runtime().stderr(), outputLimit), "Debug output limit exceeded");
                    }
                }
                var state = debug.current();
                Status status = debug.canNext() ? Status.RUN_TIMEOUT
                        : state.stop().status() == Debugger.Status.FAILED ? Status.RUNTIME_ERROR : Status.OK;
                Integer terminationStatus = state.runtime().termination().status();
                int exitCode = status == Status.OK && terminationStatus != null ? terminationStatus : -1;
                if (status == Status.OK && terminationStatus == null) status = Status.RUNTIME_ERROR;
                if (status == Status.OK && exitCode != 0) status = Status.NONZERO_EXIT;
                return new Outcome(backend, status, exitCode, state.runtime().stdout(), state.runtime().stderr(),
                        debug.canNext() ? "Debug step budget exceeded: " + stepLimit : state.stop().error());
            });
            publish(resultPath, outcome, completed);
        } catch (DeadlineExceeded ignored) {
            // The deadline already published a phase-specific outcome.
        } catch (Exception failure) {
            publish(resultPath, CppDifferentialHarness.failed(backend, Status.TOOL_ERROR, cap(failure.toString(), outputLimit)), completed);
        }
    }

    private record Compilation(SourceFile source, IrResult ir, boolean successful, String errors) {}

    private static <T> T timed(Path phasePath, String phase, long nanos, Path resultPath, Backend backend,
                               Status timeoutStatus, AtomicBoolean completed, Callable<T> action) throws Exception {
        Files.writeString(phasePath, phase);
        try (var deadline = new PhaseDeadline(nanos, resultPath, backend, timeoutStatus, completed)) {
            return action.call();
        }
    }

    private static final class DeadlineExceeded extends RuntimeException {}

    /** A phase cannot borrow the next phase's budget, even if the watchdog thread is scheduled late. */
    private static final class PhaseDeadline implements AutoCloseable {
        private final long started = System.nanoTime();
        private final long timeoutNanos;
        private final Path resultPath;
        private final Backend backend;
        private final Status status;
        private final AtomicBoolean completed;
        private final Thread watcher;
        private boolean closed;

        private PhaseDeadline(long timeoutNanos, Path resultPath, Backend backend, Status status,
                              AtomicBoolean completed) {
            this.timeoutNanos = timeoutNanos;
            this.resultPath = resultPath;
            this.backend = backend;
            this.status = status;
            this.completed = completed;
            watcher = Thread.ofPlatform().daemon(true).name("worker-" + status + "-deadline").start(() -> {
                try {
                    TimeUnit.NANOSECONDS.sleep(timeoutNanos);
                    synchronized (this) {
                        if (closed) return;
                        closed = true;
                        timeout();
                        System.exit(0);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (IOException failure) {
                    failure.printStackTrace();
                    System.exit(2);
                }
            });
        }

        private void timeout() throws IOException {
            publish(resultPath, CppDifferentialHarness.failed(backend, status,
                    status + " exceeded its independent phase budget: " + timeoutNanos + " ns"), completed);
        }

        @Override public synchronized void close() throws IOException {
            if (closed) throw new DeadlineExceeded();
            closed = true;
            watcher.interrupt();
            if (System.nanoTime() - started >= timeoutNanos) {
                timeout();
                throw new DeadlineExceeded();
            }
        }
    }

    private static CompilerApi compiler(SourceFile source, Path directory, LanguageMode languageMode,
                                        OptimizationLevel optimizationLevel) {
        var preprocessor = new Preprocessor(source, Preprocessor.Options.defaults(languageMode));
        var lexer = new Lexer(preprocessor, languageMode);
        var parser = new Parser(lexer, true);
        var semantic = new SemanticAnalyzer(parser);
        var ir = new IrLowerer(semantic);
        var assembler = new Assembler(ir, optimizationLevel);
        var obj = new ObjBuilder(source, assembler, directory.resolve("native"), "program");
        var linker = new Linker(source, obj, directory.resolve("native"), "program");
        return new CompilerApi(List.of(preprocessor, lexer, parser, semantic, ir, assembler, obj, linker));
    }

    private static boolean exceeds(String text, int limit) { return text.getBytes(StandardCharsets.UTF_8).length > limit; }
    private static String cap(String text, int limit) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= limit ? text : new String(bytes, 0, limit, StandardCharsets.UTF_8);
    }

    private static boolean publish(Path path, Outcome result, AtomicBoolean completed) throws IOException {
        if (!completed.compareAndSet(false, true)) return false;
        var properties = new Properties();
        properties.setProperty("status", result.status().name());
        properties.setProperty("exitCode", Integer.toString(result.exitCode()));
        properties.setProperty("stdout", result.stdout());
        properties.setProperty("stderr", result.stderr());
        properties.setProperty("diagnostics", result.diagnostics());
        try (var output = Files.newOutputStream(path)) { properties.store(output, "MiniC differential worker result"); }
        return true;
    }

    static Outcome readResult(Path path, Backend backend) throws IOException {
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) { properties.load(input); }
        return new Outcome(backend, Status.valueOf(properties.getProperty("status")),
                Integer.parseInt(properties.getProperty("exitCode")), properties.getProperty("stdout"),
                properties.getProperty("stderr"), properties.getProperty("diagnostics"));
    }
}
