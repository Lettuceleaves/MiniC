package craken.ui.editor.realtime;

import craken.compiler.Diagnostic;
import craken.compiler.SourceFile;
import craken.compiler.lexer.Lexer;
import craken.compiler.parser.Parser;
import craken.compiler.preprocess.PreprocessResult;
import craken.compiler.preprocess.Preprocessor;
import craken.compiler.semantic.SemanticAnalyzer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;

/**
 * Background analyzer that coalesces rapid edits: only the newest queued source is analyzed.
 * Pure Java; the result sink marshals to the UI thread if necessary.
 */
public final class RealtimeSyntaxAnalyzer implements AutoCloseable {
    private final BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
    private final ResultSink sink;
    private final Runnable onAnalysisStarted;
    private volatile boolean running = true;
    private Thread worker;
    private long nextVersion;

    public RealtimeSyntaxAnalyzer(ResultSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.onAnalysisStarted = null;
    }

    /** 测试观测点：每次后台真正开始分析时触发，用于构造“分析中又有新输入”的确定性场景。 */
    RealtimeSyntaxAnalyzer(ResultSink sink, Runnable onAnalysisStarted) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.onAnalysisStarted = Objects.requireNonNull(onAnalysisStarted, "onAnalysisStarted");
    }

    public void submit(String sourceName, String sourceText) {
        ensureStarted();
        queue.offer(new Request(sourceName, sourceText, ++nextVersion));
    }

    @Override public void close() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    private synchronized void ensureStarted() {
        if (worker != null) return;
        worker = new Thread(this::runLoop, "craken-realtime-syntax");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        while (running) {
            try {
                Request request = queue.take();
                Request latest = drainLatest(request);
                if (onAnalysisStarted != null) onAnalysisStarted.run();
                RealtimeSyntaxAnalysis analysis = analyzeNow(
                        latest.sourceName(), latest.sourceText(), latest.version(), this::hasNewerRequest);
                if (analysis != null && !hasNewerRequest()) sink.accept(analysis);
            } catch (InterruptedException interrupted) {
                if (!running) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private Request drainLatest(Request first) {
        Request latest = first;
        Request next;
        while ((next = queue.poll()) != null) latest = next;
        return latest;
    }

    private boolean hasNewerRequest() { return !queue.isEmpty(); }

    /** Deterministic one-shot analysis used by tests and the worker. */
    public static RealtimeSyntaxAnalysis analyzeNow(String sourceName, String sourceText, long version) {
        return analyzeNow(sourceName, sourceText, version, () -> false);
    }

    /** Step-wise variant; {@code aborted} is consulted after every step, between stages. */
    public static RealtimeSyntaxAnalysis analyzeNow(String sourceName, String sourceText, long version,
                                                    BooleanSupplier aborted) {
        SourceFile source = new SourceFile(sourceName, sourceText);
        Preprocessor preprocessor = new Preprocessor(source, Preprocessor.Options.defaults());
        if (runToCompletion(preprocessor::canNext, preprocessor::step, aborted)) return null;
        if (!preprocessor.errors().isEmpty()) return result(sourceName, sourceText, preprocessor.errors(), version);
        PreprocessResult preprocessed = preprocessor.preprocessResult();
        // Construct the lexer from the preprocessor so token and diagnostic ranges map back
        // into the edited source instead of the expanded include output.
        Lexer lexer = new Lexer(preprocessor);
        if (runToCompletion(lexer::canNext, lexer::step, aborted)) return null;
        if (!lexer.errors().isEmpty()) return result(sourceName, sourceText, lexer.errors(), version);
        Parser parser = new Parser(lexer.tokens());
        if (runToCompletion(parser::canNext, parser::step, aborted)) return null;
        if (!parser.errors().isEmpty()) return result(sourceName, sourceText, parser.errors(), version);
        SemanticAnalyzer semantic = new SemanticAnalyzer(parser.result().program());
        if (runToCompletion(semantic::canNext, semantic::step, aborted)) return null;
        return result(sourceName, sourceText, semantic.errors(), version);
    }

    /** Runs one stage step by step; returns true when the abort condition fires after a step. */
    private static boolean runToCompletion(BooleanSupplier canNext, Runnable step, BooleanSupplier aborted) {
        while (canNext.getAsBoolean()) {
            step.run();
            if (aborted.getAsBoolean()) return true;
        }
        return false;
    }

    private static RealtimeSyntaxAnalysis result(String sourceName, String sourceText,
                                                 List<Diagnostic> diagnostics, long version) {
        return new RealtimeSyntaxAnalysis(sourceName, sourceText,
                diagnostics.stream().map(RealtimeDiagnostic::from).toList(), version);
    }

    @FunctionalInterface
    public interface ResultSink {
        void accept(RealtimeSyntaxAnalysis analysis);
    }

    private record Request(String sourceName, String sourceText, long version) {
        private Request {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(sourceText, "sourceText");
        }
    }
}
