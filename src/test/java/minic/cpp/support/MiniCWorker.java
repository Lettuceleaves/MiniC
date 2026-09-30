package minic.cpp.support;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrLowerer;
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
        long runTimeoutMillis = Long.parseLong(args[6]);
        LanguageMode languageMode = LanguageMode.valueOf(args[7]);
        var completed = new AtomicBoolean();
        SourceFile source = new SourceFile(sourcePath.toString(), Files.readString(sourcePath));
        try {
            CompilerApi compiler = compiler(source, sourcePath.getParent(), languageMode);
            var ir = compiler.stages().stream().filter(IrLowerer.class::isInstance).findFirst().orElseThrow();
            var link = compiler.stages().stream().filter(Linker.class::isInstance).findFirst().orElseThrow();
            compiler.runThrough(backend == Backend.MINIC_NATIVE ? link : ir);
            if (!(backend == Backend.MINIC_NATIVE ? link : ir).succeeded()) {
                String errors = compiler.stages().stream()
                        .flatMap(stage -> stage.errors().stream()).map(error -> error.describe()).collect(Collectors.joining("\n"));
                publish(resultPath, CppDifferentialHarness.failed(backend, Status.COMPILE_ERROR, cap(errors, outputLimit)), completed);
                return;
            }
            if (backend == Backend.MINIC_NATIVE) {
                publish(resultPath, new Outcome(backend, Status.OK, 0, "", "", ""), completed);
                return;
            }
            DebugApi debug = new DebugApi(source, Files.readString(Path.of(args[2])), languageMode);
            // A source-level debug step may itself loop forever; its watchdog is independent.
            Thread.ofPlatform().daemon(true).name("debug-run-deadline").start(() -> {
                try {
                    Thread.sleep(runTimeoutMillis);
                    if (publish(resultPath, CppDifferentialHarness.failed(backend, Status.RUN_TIMEOUT,
                            "Debug execution exceeded wall-clock limit"), completed)) System.exit(0);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (IOException failure) {
                    failure.printStackTrace();
                    System.exit(2);
                }
            });
            int steps = 0;
            while (debug.canNext() && steps++ < stepLimit) {
                debug.next();
                if (exceeds(debug.current().runtime().stdout(), outputLimit)
                        || exceeds(debug.current().runtime().stderr(), outputLimit)) {
                    publish(resultPath, new Outcome(backend, Status.OUTPUT_LIMIT, -1,
                            cap(debug.current().runtime().stdout(), outputLimit),
                            cap(debug.current().runtime().stderr(), outputLimit), "Debug output limit exceeded"), completed);
                    return;
                }
            }
            var state = debug.current();
            Status status = debug.canNext() ? Status.RUN_TIMEOUT
                    : state.stop().status() == Debugger.Status.FAILED ? Status.RUNTIME_ERROR : Status.OK;
            Integer terminationStatus = state.runtime().termination().status();
            int exitCode = status == Status.OK && terminationStatus != null ? terminationStatus : -1;
            if (status == Status.OK && terminationStatus == null) status = Status.RUNTIME_ERROR;
            if (status == Status.OK && exitCode != 0) status = Status.NONZERO_EXIT;
            publish(resultPath, new Outcome(backend, status, exitCode, state.runtime().stdout(), state.runtime().stderr(),
                    debug.canNext() ? "Debug step budget exceeded: " + stepLimit : state.stop().error()), completed);
        } catch (Exception failure) {
            publish(resultPath, CppDifferentialHarness.failed(backend, Status.TOOL_ERROR, cap(failure.toString(), outputLimit)), completed);
        }
    }

    private static CompilerApi compiler(SourceFile source, Path directory, LanguageMode languageMode) {
        var preprocessor = new Preprocessor(source, Preprocessor.Options.defaults(languageMode));
        var lexer = new Lexer(preprocessor, languageMode);
        var parser = new Parser(lexer, true);
        var semantic = new SemanticAnalyzer(parser);
        var ir = new IrLowerer(semantic);
        var assembler = new Assembler(ir);
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
