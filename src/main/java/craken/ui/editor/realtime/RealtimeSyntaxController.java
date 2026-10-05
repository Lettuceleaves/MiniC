package craken.ui.editor.realtime;

import craken.SourceRange;
import craken.compiler.Diagnostic;
import craken.compiler.SourceFile;
import craken.ui.component.editor.UiCodeEditor;
import javafx.application.Platform;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Glue between one editor view and its background analyzer: waves, listener delivery and disposal. */
public final class RealtimeSyntaxController implements AutoCloseable {
    private final UiCodeEditor editor;
    private final String sourceName;
    private final RealtimeSyntaxAnalyzer analyzer;
    private final DocumentListener documentListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { submit(); }
        @Override public void removeUpdate(DocumentEvent event) { submit(); }
        @Override public void changedUpdate(DocumentEvent event) { submit(); }
    };
    private volatile List<RealtimeDiagnostic> latest = List.of();
    private volatile Consumer<List<RealtimeDiagnostic>> diagnosticsListener = diagnostics -> { };
    private boolean closed;

    public RealtimeSyntaxController(UiCodeEditor editor, String sourceName) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        analyzer = new RealtimeSyntaxAnalyzer(analysis -> Platform.runLater(() -> apply(analysis)));
        editor.onTextArea(area -> area.getDocument().addDocumentListener(documentListener));
        submit();
    }

    public List<RealtimeDiagnostic> diagnostics() { return List.copyOf(latest); }

    /**
     * 注册报错信息消费者；立即交付当前快照，之后每次新分析结果都会再通知。
     * 必须在 JavaFX 线程调用，回调同样发生在 JavaFX 线程。
     */
    public void setOnDiagnosticsChanged(Consumer<List<RealtimeDiagnostic>> listener) {
        diagnosticsListener = Objects.requireNonNull(listener, "listener");
        listener.accept(List.copyOf(latest));
    }

    private void submit() {
        if (!closed) analyzer.submit(sourceName, editor.text());
    }

    private void apply(RealtimeSyntaxAnalysis analysis) {
        if (closed || !analysis.sourceName().equals(sourceName)
                || !analysis.sourceText().equals(editor.text())) return;
        latest = analysis.diagnostics();
        String source = analysis.sourceText();
        List<SourceRange> errorRanges = latest.stream()
                .filter(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR)
                .map(RealtimeDiagnostic::range)
                .filter(range -> resolves(source, range)).toList();
        editor.setErrorUnderlines(errorRanges);
        diagnosticsListener.accept(List.copyOf(latest));
    }

    /** Safety net: a range that cannot be resolved into the edited text must not abort the panel. */
    private static boolean resolves(String source, SourceRange range) {
        try {
            var offsets = new SourceFile("live", source);
            offsets.offsetAt(range.startLine(), range.startByte());
            offsets.offsetAt(range.endLine(), range.endByte());
            return true;
        } catch (RuntimeException outOfBounds) {
            return false;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        analyzer.close();
        editor.onTextArea(area -> area.getDocument().removeDocumentListener(documentListener));
        editor.clearErrorUnderlines();
    }
}
