package minic.ui;

import javafx.geometry.Bounds;
import javafx.scene.control.TextArea;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiRealtimeAnalysisDto;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 叠层代码编辑器：TextFlow 负责语法高亮，TextArea 负责输入和光标。
 */
public final class MiniCCodeEditor extends StackPane {
    private static final List<String> KEYWORDS = List.of(
            "bool", "char", "int", "long", "float", "double", "extern", "struct",
            "return", "if", "else", "while", "for", "break", "continue", "true", "false", "null"
    );
    private static final List<String> COMMON_EXTERNALS = List.of(
            "printf", "scanf", "puts", "getchar", "putchar", "malloc", "free", "memset", "memcpy", "strlen"
    );
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final Pattern DECLARED_NAME_PATTERN = Pattern.compile(
            "\\b(?:extern\\s+)?(?:bool|char|int|long|float|double|struct\\s+[A-Za-z_][A-Za-z0-9_]*)(?:\\s*\\*)*\\s+([A-Za-z_][A-Za-z0-9_]*)"
    );
    private final TextFlow highlightLayer = new TextFlow();
    private final TextArea input = new TextArea();
    private final ContextMenu completionMenu = new ContextMenu();
    private UiRealtimeAnalysisDto latestAnalysis;

    /**
     * 创建代码编辑器。
     */
    public MiniCCodeEditor() {
        getStyleClass().add("code-editor");
        highlightLayer.getStyleClass().add("code-highlight-layer");
        highlightLayer.setMouseTransparent(true);
        highlightLayer.setFocusTraversable(false);
        input.getStyleClass().add("source-editor");
        input.setWrapText(false);
        input.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCompletionKeys);
        input.caretPositionProperty().addListener((observable, oldValue, newValue) -> updateCompletion(false));
        input.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                completionMenu.hide();
            }
        });
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
        if (analysis != null && !source.equals(analysis.sourceText())) {
            analysis = null;
        }
        latestAnalysis = analysis;
        highlightLayer.getChildren().clear();
        if (source.isEmpty()) {
            completionMenu.hide();
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
        updateCompletion(false);
    }

    private void handleCompletionKeys(KeyEvent event) {
        if (completionMenu.isShowing()) {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                CustomMenuItem firstItem = (CustomMenuItem) completionMenu.getItems().getFirst();
                applyCompletion(((Label) firstItem.getContent()).getText());
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                completionMenu.hide();
                event.consume();
                return;
            }
        }
        if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
            updateCompletion(true);
            event.consume();
        }
    }

    private void updateCompletion(boolean force) {
        if (!input.isFocused()) {
            completionMenu.hide();
            return;
        }
        Prefix prefix = prefixAtCaret();
        if (!force && prefix.text().isEmpty()) {
            completionMenu.hide();
            return;
        }
        List<String> suggestions = completionSuggestions(prefix.text());
        if (suggestions.isEmpty()) {
            completionMenu.hide();
            return;
        }
        completionMenu.getItems().setAll(suggestions.stream()
                .map(suggestion -> {
                    Label label = new Label(suggestion);
                    label.getStyleClass().add("completion-item");
                    CustomMenuItem item = new CustomMenuItem(label, true);
                    item.setOnAction(event -> applyCompletion(suggestion));
                    return item;
                })
                .toList());
        showCompletionMenu();
    }

    private void showCompletionMenu() {
        Optional<Node> caretNode = Optional.ofNullable(input.lookup(".caret"));
        if (caretNode.isPresent()) {
            Bounds screenBounds = caretNode.get().localToScreen(caretNode.get().getBoundsInLocal());
            if (screenBounds != null) {
                completionMenu.show(input, screenBounds.getMinX(), screenBounds.getMaxY() + 2);
                return;
            }
        }
        if (!completionMenu.isShowing()) {
            Bounds screenBounds = input.localToScreen(input.getBoundsInLocal());
            if (screenBounds != null) {
                completionMenu.show(input, screenBounds.getMinX() + 12, screenBounds.getMinY() + 24);
            }
        }
    }

    private List<String> completionSuggestions(String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.addAll(KEYWORDS);
        candidates.addAll(extractDeclaredNames());
        candidates.addAll(COMMON_EXTERNALS);
        return candidates.stream()
                .filter(candidate -> prefix.isEmpty() || candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .filter(candidate -> !candidate.equals(prefix))
                .limit(9)
                .toList();
    }

    private Set<String> extractDeclaredNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (latestAnalysis != null) {
            latestAnalysis.tokens().stream()
                    .filter(token -> "IDENTIFIER".equals(token.kind()))
                    .map(UiLexerTokenVisualDto::text)
                    .forEach(names::add);
        }
        String source = input.getText();
        Matcher declaredMatcher = DECLARED_NAME_PATTERN.matcher(source);
        while (declaredMatcher.find()) {
            names.add(declaredMatcher.group(1));
        }
        Matcher identifierMatcher = IDENTIFIER_PATTERN.matcher(source);
        while (identifierMatcher.find()) {
            String identifier = identifierMatcher.group();
            if (!KEYWORDS.contains(identifier)) {
                names.add(identifier);
            }
        }
        return names;
    }

    private void applyCompletion(String suggestion) {
        Prefix prefix = prefixAtCaret();
        input.replaceText(prefix.startOffset(), prefix.endOffset(), suggestion);
        input.positionCaret(prefix.startOffset() + suggestion.length());
        completionMenu.hide();
    }

    private Prefix prefixAtCaret() {
        String source = input.getText();
        int caret = input.getCaretPosition();
        int start = caret;
        while (start > 0 && isIdentifierPart(source.charAt(start - 1))) {
            start--;
        }
        if (start < caret && Character.isDigit(source.charAt(start))) {
            return new Prefix("", caret, caret);
        }
        return new Prefix(source.substring(start, caret), start, caret);
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
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

    private record Prefix(String text, int startOffset, int endOffset) {
    }
}
