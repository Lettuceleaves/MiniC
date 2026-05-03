package minic.ui;

import javafx.application.Platform;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.diagnostics.Diagnostic;
import minic.source.SourceFile;
import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiRealtimeAnalysisDto;
import minic.uiapi.UiSourceSpanDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 后台实时 lexer/parser/semantic 分析器。
 */
public final class MiniCRealtimeAnalyzer implements AutoCloseable {
    private final BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
    private final ResultSink resultSink;
    private final Thread worker;
    private volatile boolean running = true;
    private long nextVersion;

    /**
     * 创建实时分析器。
     *
     * @param resultSink 结果回调
     */
    public MiniCRealtimeAnalyzer(ResultSink resultSink) {
        this.resultSink = Objects.requireNonNull(resultSink, "resultSink");
        worker = new Thread(this::runLoop, "minic-realtime-analyzer");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 提交一次编辑输入。
     *
     * @param sourceName 源码名称
     * @param sourceText 源码文本
     */
    public void submit(String sourceName, String sourceText) {
        queue.offer(new Request(sourceName, sourceText, ++nextVersion));
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
    }

    private void runLoop() {
        while (running) {
            try {
                Request request = queue.take();
                Request latest = drainLatest(request);
                UiRealtimeAnalysisDto result = analyzeNow(latest.sourceName(), latest.sourceText(), latest.version());
                Platform.runLater(() -> resultSink.accept(result));
            } catch (InterruptedException exception) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private Request drainLatest(Request request) {
        Request latest = request;
        Request next;
        while ((next = queue.poll()) != null) {
            latest = next;
        }
        return latest;
    }

    static UiRealtimeAnalysisDto analyzeNow(String sourceName, String sourceText, long version) {
        SourceFile sourceFile = new SourceFile(sourceName, sourceText);
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        LexResult lexResult = new Lexer(sourceFile).lex();
        diagnostics.addAll(lexResult.diagnostics());
        List<UiLexerTokenVisualDto> tokens = lexResult.tokens().stream()
                .map(token -> new UiLexerTokenVisualDto(
                        token.kind().name(),
                        token.lexeme(),
                        UiSourceSpanDto.from(token.range()),
                        false
                ))
                .toList();
        if (diagnostics.isEmpty()) {
            ParseResult parseResult = new Parser(lexResult.tokens()).parse();
            diagnostics.addAll(parseResult.diagnostics());
            if (diagnostics.isEmpty()) {
                SemanticResult semanticResult = new SemanticAnalyzer().analyze(parseResult.program());
                diagnostics.addAll(semanticResult.diagnostics());
            }
        }
        return new UiRealtimeAnalysisDto(
                sourceName,
                sourceText,
                diagnostics.stream().map(MiniCRealtimeAnalyzer::diagnosticDto).toList(),
                tokens,
                version
        );
    }

    private static UiDiagnosticDto diagnosticDto(Diagnostic diagnostic) {
        return new UiDiagnosticDto(
                diagnostic.code(),
                diagnostic.severity().name(),
                diagnostic.message(),
                diagnostic.range().sourceFile().path(),
                diagnostic.range().startOffset(),
                diagnostic.range().endOffset()
        );
    }

    /**
     * 实时分析结果回调。
     */
    @FunctionalInterface
    public interface ResultSink {
        /**
         * 接收分析结果。
         *
         * @param result 分析结果
         */
        void accept(UiRealtimeAnalysisDto result);
    }

    private record Request(String sourceName, String sourceText, long version) {
        private Request {
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(sourceText, "sourceText");
        }
    }
}
