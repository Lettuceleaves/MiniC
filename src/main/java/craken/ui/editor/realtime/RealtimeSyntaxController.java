package craken.ui.editor.realtime;

import craken.SourceRange;
import craken.compiler.Diagnostic;
import craken.compiler.SourceFile;
import craken.ui.component.editor.UiCodeEditor;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Glue between one editor view and its background analyzer: waves, detail panel and disposal. */
public final class RealtimeSyntaxController implements AutoCloseable {
    private static final int MAX_DETAIL_ROWS = 4;
    private final UiCodeEditor editor;
    private final String sourceName;
    private final RealtimeSyntaxAnalyzer analyzer;
    private final VBox panel = new VBox(2);
    private final DocumentListener documentListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { submit(); }
        @Override public void removeUpdate(DocumentEvent event) { submit(); }
        @Override public void changedUpdate(DocumentEvent event) { submit(); }
    };
    private volatile List<RealtimeDiagnostic> latest = List.of();
    private boolean closed;

    public RealtimeSyntaxController(UiCodeEditor editor, String sourceName) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        analyzer = new RealtimeSyntaxAnalyzer(analysis -> Platform.runLater(() -> apply(analysis)));
        panel.setId("realtime-diagnostics");
        panel.getStyleClass().add("realtime-diagnostics");
        panel.setMouseTransparent(true);
        panel.setVisible(false);
        panel.maxWidthProperty().bind(editor.widthProperty().subtract(16));
        editor.getChildren().add(panel);
        StackPane.setAlignment(panel, Pos.BOTTOM_LEFT);
        editor.onTextArea(area -> area.getDocument().addDocumentListener(documentListener));
        submit();
    }

    public List<RealtimeDiagnostic> diagnostics() { return List.copyOf(latest); }

    public VBox view() { return panel; }

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
        renderPanel();
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

    private void renderPanel() {
        panel.getChildren().clear();
        if (latest.isEmpty()) { panel.setVisible(false); return; }
        latest.stream()
                .sorted(Comparator.comparingInt((RealtimeDiagnostic diagnostic) -> diagnostic.range().startLine())
                        .thenComparingInt(diagnostic -> diagnostic.range().startByte()))
                .limit(MAX_DETAIL_ROWS)
                .map(this::detailRow)
                .forEach(panel.getChildren()::add);
        panel.setVisible(true);
    }

    private Label detailRow(RealtimeDiagnostic diagnostic) {
        String level = switch (diagnostic.severity()) {
            case ERROR -> "错误";
            case WARNING -> "警告";
            case INFO -> "提示";
        };
        String advice = diagnostic.solution().isBlank() ? "" : " · 建议：" + diagnostic.solution();
        Label label = new Label("第 " + diagnostic.range().startLine() + " 行 · [" + diagnostic.code() + "] "
                + level + "：" + diagnostic.message() + advice);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add(diagnostic.severity() == Diagnostic.Severity.ERROR
                ? "realtime-diagnostic-error" : "realtime-diagnostic-warning");
        return label;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        analyzer.close();
        editor.onTextArea(area -> area.getDocument().removeDocumentListener(documentListener));
        editor.clearErrorUnderlines();
        editor.getChildren().remove(panel);
        panel.getChildren().clear();
    }
}
