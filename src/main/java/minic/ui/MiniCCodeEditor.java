package minic.ui;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiRealtimeAnalysisDto;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 RichTextFX 的代码编辑器，负责语法高亮、真实光标/选择和补全提示。
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
    private final CodeArea input = new CodeArea();
    private final ContextMenu completionMenu = new ContextMenu();
    private UiRealtimeAnalysisDto latestAnalysis;

    /**
     * 创建代码编辑器。
     */
    public MiniCCodeEditor() {
        getStyleClass().add("code-editor");
        input.getStyleClass().add("source-editor");
        input.setWrapText(false);
        input.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCompletionKeys);
        input.caretPositionProperty().addListener((observable, oldValue, newValue) -> updateCompletion(false));
        input.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused && !completionMenu.isShowing()) {
                completionMenu.hide();
            }
        });
        getChildren().add(new VirtualizedScrollPane<>(input));
    }

    /**
     * 返回文本属性。
     *
     * @return 文本属性
     */
    public ObservableValue<String> textProperty() {
        return input.textProperty();
    }

    /**
     * 设置文本。
     *
     * @param text 文本
     */
    public void setText(String text) {
        input.replaceText(text);
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
        input.setStyleSpans(0, styleSpans(source, analysis));
        if (source.isEmpty()) {
            completionMenu.hide();
            return;
        }
        updateCompletion(false);
    }

    private StyleSpans<Collection<String>> styleSpans(String source, UiRealtimeAnalysisDto analysis) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        List<UiLexerTokenVisualDto> tokens = analysis == null
                ? List.of()
                : analysis.tokens().stream()
                .filter(token -> token.startOffset() >= 0 && token.endOffset() > token.startOffset())
                .sorted(Comparator.comparingInt(UiLexerTokenVisualDto::startOffset))
                .toList();
        List<UiDiagnosticDto> diagnostics = analysis == null ? List.of() : analysis.diagnostics();
        int cursor = 0;
        for (UiLexerTokenVisualDto token : tokens) {
            int start = safeOffset(source, token.startOffset());
            int end = safeOffset(source, token.endOffset());
            if (start > cursor) {
                builder.add(List.of("token-plain"), start - cursor);
            }
            builder.add(tokenStyles(token.kind(), overlapsDiagnostic(start, end, diagnostics)), end - start);
            cursor = end;
        }
        if (cursor < source.length()) {
            builder.add(List.of("token-plain"), source.length() - cursor);
        }
        if (source.isEmpty()) {
            builder.add(List.of("token-plain"), 0);
        }
        return builder.create();
    }

    private Collection<String> tokenStyles(String kind, boolean diagnostic) {
        String tokenStyle = switch (kind) {
            case "BOOL", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE", "EXTERN", "STRUCT",
                    "RETURN", "IF", "ELSE", "WHILE", "FOR", "BREAK", "CONTINUE" -> "token-keyword";
            case "STRING_LITERAL", "CHAR_LITERAL" -> "token-string";
            case "INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL",
                    "BOOL_LITERAL", "NULL_LITERAL" -> "token-literal";
            case "IDENTIFIER" -> "token-identifier";
            default -> "token-operator";
        };
        return diagnostic ? List.of(tokenStyle, "diagnostic") : List.of(tokenStyle);
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
        Optional<Bounds> caretBounds = input.getCaretBounds();
        if (caretBounds.isPresent()) {
            Bounds screenBounds = input.localToScreen(caretBounds.get());
            if (screenBounds != null) {
                completionMenu.show(input, screenBounds.getMinX(), screenBounds.getMaxY() + 2);
                return;
            }
        }
        Node node = input;
        Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
        if (screenBounds != null && !completionMenu.isShowing()) {
            completionMenu.show(input, screenBounds.getMinX() + 12, screenBounds.getMinY() + 24);
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
        input.moveTo(prefix.startOffset() + suggestion.length());
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
