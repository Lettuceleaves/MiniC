package minic.ui;

import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiRealtimeAnalysisDto;

import java.util.Comparator;
import java.util.List;

/**
 * 叠层代码编辑器：TextFlow 负责语法高亮，TextArea 负责输入和光标。
 */
public final class MiniCCodeEditor extends StackPane {
    private final TextFlow highlightLayer = new TextFlow();
    private final TextArea input = new TextArea();

    /**
     * 创建代码编辑器。
     */
    public MiniCCodeEditor() {
        getStyleClass().add("code-editor");
        highlightLayer.getStyleClass().add("code-highlight-layer");
        input.getStyleClass().add("source-editor");
        input.setWrapText(false);
        highlightLayer.prefWidthProperty().bind(widthProperty());
        highlightLayer.prefHeightProperty().bind(heightProperty());
        getChildren().addAll(highlightLayer, input);
    }

    /**
     * 返回底层输入控件。
     *
     * @return TextArea
     */
    public TextArea input() {
        return input;
    }

    /**
     * 设置文本。
     *
     * @param text 文本
     */
    public void setText(String text) {
        input.setText(text);
        render(null);
    }

    /**
     * 返回文本。
     *
     * @return 文本
     */
    public String getText() {
        return input.getText();
    }

    /**
     * 返回文本长度。
     *
     * @return 文本长度
     */
    public int getLength() {
        return input.getLength();
    }

    /**
     * 选择范围。
     *
     * @param start 起始 offset
     * @param end 结束 offset
     */
    public void selectRange(int start, int end) {
        input.selectRange(start, end);
    }

    /**
     * 根据实时分析结果重绘高亮。
     *
     * @param analysis 实时分析结果
     */
    public void render(UiRealtimeAnalysisDto analysis) {
        String source = input.getText();
        highlightLayer.getChildren().clear();
        if (source.isEmpty()) {
            return;
        }
        List<UiLexerTokenVisualDto> tokens = analysis == null
                ? List.of()
                : analysis.tokens().stream()
                .filter(token -> token.startOffset() >= 0 && token.endOffset() > token.startOffset())
                .sorted(Comparator.comparingInt(UiLexerTokenVisualDto::startOffset))
                .toList();
        List<UiDiagnosticDto> diagnostics = analysis == null ? List.of() : analysis.diagnostics();
        int cursor = 0;
        for (UiLexerTokenVisualDto token : tokens) {
            if (token.startOffset() > cursor) {
                addSegment(source.substring(cursor, safeOffset(source, token.startOffset())), "plain", false);
            }
            int start = safeOffset(source, token.startOffset());
            int end = safeOffset(source, token.endOffset());
            addSegment(source.substring(start, end), tokenStyle(token.kind()), overlapsDiagnostic(start, end, diagnostics));
            cursor = end;
        }
        if (cursor < source.length()) {
            addSegment(source.substring(cursor), "plain", overlapsDiagnostic(cursor, source.length(), diagnostics));
        }
    }

    private void addSegment(String text, String tokenStyle, boolean diagnostic) {
        if (text.isEmpty()) {
            return;
        }
        Text node = new Text(text);
        node.getStyleClass().add("code-token");
        node.getStyleClass().add("token-" + tokenStyle);
        if (diagnostic) {
            node.getStyleClass().add("diagnostic");
        }
        highlightLayer.getChildren().add(node);
    }

    private String tokenStyle(String kind) {
        return switch (kind) {
            case "BOOL", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE", "EXTERN", "STRUCT",
                    "RETURN", "IF", "ELSE", "WHILE", "FOR", "BREAK", "CONTINUE" -> "keyword";
            case "STRING_LITERAL", "CHAR_LITERAL" -> "string";
            case "INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL",
                    "BOOL_LITERAL", "NULL_LITERAL" -> "literal";
            case "IDENTIFIER" -> "identifier";
            case "EOF" -> "plain";
            default -> "operator";
        };
    }

    private boolean overlapsDiagnostic(int start, int end, List<UiDiagnosticDto> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic ->
                Math.max(start, diagnostic.startOffset()) < Math.min(end, diagnostic.endOffset()));
    }

    private int safeOffset(String source, int offset) {
        return Math.max(0, Math.min(offset, source.length()));
    }
}
