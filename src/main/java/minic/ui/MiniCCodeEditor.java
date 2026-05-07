package minic.ui;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import minic.uiapi.UiDiagnosticDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiRealtimeAnalysisDto;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 RichTextFX 的代码编辑器，负责语法高亮、真实光标/选择和补全提示。
 */
public final class MiniCCodeEditor extends StackPane {
    private static final String TAB_TEXT = "    ";
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
    private final StyleClassedTextArea input = new StyleClassedTextArea();
    private final IntFunction<Node> lineNumberFactory = LineNumberFactory.get(input);
    private final Set<Integer> breakpointLines = new LinkedHashSet<>();
    private final Pane diagnosticLayer = new Pane();
    private final VBox diagnosticDetails = new VBox(4);
    private final ListView<String> completionList = new ListView<>();
    private UiRealtimeAnalysisDto latestAnalysis;
    private List<UiDiagnosticDto> latestDiagnostics = List.of();
    private Runnable breakpointChangeAction = () -> {
    };
    private int currentExecutionLine;

    /**
     * 创建代码编辑器。
     */
    public MiniCCodeEditor() {
        getStyleClass().add("code-editor");
        input.getStyleClass().add("source-editor");
        input.setWrapText(false);
        input.setParagraphGraphicFactory(this::paragraphGraphic);
        input.setTextInsertionStyle(List.of("token-plain"));
        input.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCompletionKeys);
        input.addEventFilter(KeyEvent.KEY_TYPED, this::handleTypedText);
        input.caretPositionProperty().addListener((observable, oldValue, newValue) -> updateCompletion(false));
        input.viewportDirtyEvents().subscribe(event -> Platform.runLater(this::drawDiagnostics));
        input.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused && !completionList.isFocused()) {
                hideCompletion();
            }
        });
        diagnosticLayer.getStyleClass().add("diagnostic-layer");
        diagnosticLayer.setMouseTransparent(true);
        diagnosticLayer.prefWidthProperty().bind(widthProperty());
        diagnosticLayer.prefHeightProperty().bind(heightProperty());
        diagnosticDetails.getStyleClass().add("editor-diagnostic-details");
        diagnosticDetails.setManaged(false);
        diagnosticDetails.setVisible(false);
        diagnosticDetails.maxWidthProperty().bind(widthProperty());
        configureCompletionList();
        getChildren().addAll(new VirtualizedScrollPane<>(input), diagnosticLayer, diagnosticDetails, completionList);
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
     * 返回当前编辑器本地断点行。
     *
     * @return 一基行号列表
     */
    public List<Integer> breakpointLines() {
        return breakpointLines.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * 设置或清除指定行断点。
     *
     * @param line 一基行号
     * @param enabled 是否启用
     */
    public void setBreakpoint(int line, boolean enabled) {
        if (line < 1) {
            return;
        }
        if (enabled) {
            breakpointLines.add(line);
        } else {
            breakpointLines.remove(line);
        }
        refreshParagraphGraphics();
        breakpointChangeAction.run();
    }

    /**
     * 使用外部共享断点集合刷新编辑器本地 gutter，不触发变更回调。
     *
     * @param lines 一基行号列表
     */
    public void replaceBreakpoints(List<Integer> lines) {
        breakpointLines.clear();
        Objects.requireNonNull(lines, "lines").stream()
                .filter(line -> line != null && line >= 1)
                .sorted()
                .forEach(breakpointLines::add);
        refreshParagraphGraphics();
    }

    /**
     * 切换指定行断点。
     *
     * @param line 一基行号
     */
    public void toggleBreakpoint(int line) {
        if (breakpointLines.contains(line)) {
            breakpointLines.remove(line);
        } else if (line >= 1) {
            breakpointLines.add(line);
        }
        refreshParagraphGraphics();
        breakpointChangeAction.run();
    }

    /**
     * 设置断点集合变更回调。
     *
     * @param breakpointChangeAction 回调
     */
    public void setBreakpointChangeAction(Runnable breakpointChangeAction) {
        this.breakpointChangeAction = Objects.requireNonNull(breakpointChangeAction, "breakpointChangeAction");
    }

    /**
     * 设置当前 Debug 执行行。
     *
     * @param line 一基行号；小于 1 表示清除
     */
    public void setCurrentExecutionLine(int line) {
        currentExecutionLine = Math.max(0, line);
        refreshParagraphGraphics();
    }

    /**
     * 根据实时分析结果重绘高亮。
     *
     * @param analysis 实时分析结果
     */
    public void render(UiRealtimeAnalysisDto analysis) {
        String source = input.getText();
        if (analysis != null && !source.equals(analysis.sourceText())) {
            analysis = latestAnalysis != null && source.equals(latestAnalysis.sourceText()) ? latestAnalysis : null;
        }
        if (analysis != null) {
            latestAnalysis = analysis;
        }
        latestDiagnostics = analysis == null ? List.of() : analysis.diagnostics();
        input.setStyleSpans(0, styleSpans(source, analysis));
        Platform.runLater(this::drawDiagnostics);
        updateDiagnosticDetails();
        if (source.isEmpty()) {
            hideCompletion();
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
        if (tokens.isEmpty()) {
            builder.add(List.of("token-plain"), source.length());
            return builder.create();
        }
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

    private Node paragraphGraphic(int paragraphIndex) {
        int line = paragraphIndex + 1;
        Label breakpoint = new Label(breakpointLines.contains(line) ? "●" : "");
        breakpoint.getStyleClass().add("breakpoint-gutter");
        if (breakpointLines.contains(line)) {
            breakpoint.getStyleClass().add("active");
        }
        breakpoint.setAlignment(Pos.CENTER);
        breakpoint.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                toggleBreakpoint(line);
                event.consume();
            }
        });
        Label execution = new Label(currentExecutionLine == line ? "▶" : "");
        execution.getStyleClass().add("execution-gutter");
        HBox graphic = new HBox(execution, breakpoint, lineNumberFactory.apply(paragraphIndex));
        graphic.getStyleClass().add("editor-gutter");
        if (currentExecutionLine == line) {
            graphic.getStyleClass().add("current-execution");
        }
        graphic.setAlignment(Pos.CENTER_LEFT);
        return graphic;
    }

    private void refreshParagraphGraphics() {
        input.setParagraphGraphicFactory(this::paragraphGraphic);
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
        if (isCompletionShowing()) {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                applySelectedCompletion();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.DOWN) {
                selectCompletionOffset(1);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.UP) {
                selectCompletionOffset(-1);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                hideCompletion();
                event.consume();
                return;
            }
        }
        if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
            updateCompletion(true);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.TAB) {
            input.replaceSelection(TAB_TEXT);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.BACK_SPACE) {
            applyEdit(MiniCEditorTyping.backspace(
                    input.getText(),
                    input.getSelection().getStart(),
                    input.getSelection().getEnd()
            ));
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.ENTER) {
            insertNewlineWithIndent();
            event.consume();
            return;
        }
    }

    private void handleTypedText(KeyEvent event) {
        String text = event.getCharacter();
        if (text == null || text.isEmpty() || text.charAt(0) < 32 || event.isControlDown() || event.isAltDown()) {
            return;
        }
        MiniCEditorTyping.EditResult result = MiniCEditorTyping.type(
                input.getText(),
                input.getSelection().getStart(),
                input.getSelection().getEnd(),
                text
        );
        applyEdit(result);
        event.consume();
    }

    private void applyEdit(MiniCEditorTyping.EditResult result) {
        if (!result.replacement().isEmpty() || result.replaceStart() != result.replaceEnd()) {
            input.replaceText(result.replaceStart(), result.replaceEnd(), result.replacement());
        }
        input.selectRange(result.selectionStart(), result.selectionEnd());
    }

    private void insertNewlineWithIndent() {
        int caret = input.getCaretPosition();
        formatCurrentLineBefore(caret);
        caret = input.getCaretPosition();
        String source = input.getText();
        String currentIndent = currentLineIndent(caret);
        boolean afterOpeningBrace = caret > 0 && source.charAt(caret - 1) == '{';
        boolean beforeClosingBrace = caret < source.length() && source.charAt(caret) == '}';
        if (afterOpeningBrace && !beforeClosingBrace) {
            input.insertText(caret, "}");
            source = input.getText();
            beforeClosingBrace = caret < source.length() && source.charAt(caret) == '}';
        }
        if (afterOpeningBrace && beforeClosingBrace) {
            String innerIndent = currentIndent + TAB_TEXT;
            input.insertText(caret, "\n" + innerIndent + "\n" + currentIndent);
            input.moveTo(caret + 1 + innerIndent.length());
            return;
        }
        String nextIndent = currentIndent + (afterOpeningBrace ? TAB_TEXT : "");
        input.insertText(caret, "\n" + nextIndent);
    }

    private void formatCurrentLineBefore(int caret) {
        String source = input.getText();
        int lineStart = source.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        int lineEnd = caret;
        String line = source.substring(lineStart, lineEnd);
        String formatted = formatLine(line);
        if (!line.equals(formatted)) {
            input.replaceText(lineStart, lineEnd, formatted);
            input.moveTo(lineStart + formatted.length());
        }
    }

    private String formatLine(String line) {
        String indent = leadingWhitespace(line);
        String content = line.substring(indent.length()).stripTrailing();
        if (content.isEmpty()) {
            return indent;
        }
        return indent + formatOutsideLiterals(content);
    }

    private String formatOutsideLiterals(String text) {
        StringBuilder result = new StringBuilder();
        StringBuilder segment = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quote != 0) {
                result.append(value);
                if (escaping) {
                    escaping = false;
                } else if (value == '\\') {
                    escaping = true;
                } else if (value == quote) {
                    quote = 0;
                }
                continue;
            }
            if (value == '"' || value == '\'') {
                result.append(formatCodeSegment(segment.toString()));
                segment.setLength(0);
                result.append(value);
                quote = value;
            } else {
                segment.append(value);
            }
        }
        result.append(formatCodeSegment(segment.toString()));
        return result.toString().replaceAll("\\s+", " ").trim();
    }

    private String formatCodeSegment(String content) {
        return content
                .replaceAll("\\s+([,;\\)\\]\\}])", "$1")
                .replaceAll("([\\(\\[\\{])\\s+", "$1")
                .replaceAll("\\s*([+\\-*/%<>=!&|]=?|==|!=|<=|>=|&&|\\|\\|)\\s*", " $1 ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\)\\s*\\{", ") {")
                .replaceAll("\\b(if|for|while)\\s*\\(", "$1 (")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String currentLineIndent(int caret) {
        String source = input.getText();
        int lineStart = source.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        return leadingWhitespace(source.substring(lineStart, caret));
    }

    private String leadingWhitespace(String text) {
        int index = 0;
        while (index < text.length()) {
            char value = text.charAt(index);
            if (value != ' ' && value != '\t') {
                break;
            }
            index++;
        }
        return text.substring(0, index);
    }

    private void updateCompletion(boolean force) {
        if (!input.isFocused()) {
            hideCompletion();
            return;
        }
        Prefix prefix = prefixAtCaret();
        if (!force && prefix.text().isEmpty()) {
            hideCompletion();
            return;
        }
        List<String> suggestions = completionSuggestions(prefix.text());
        if (suggestions.isEmpty()) {
            hideCompletion();
            return;
        }
        completionList.getItems().setAll(suggestions);
        completionList.getSelectionModel().selectFirst();
        showCompletion();
    }

    private void configureCompletionList() {
        completionList.getStyleClass().add("completion-list");
        completionList.setManaged(false);
        completionList.setVisible(false);
        completionList.setFocusTraversable(false);
        completionList.maxHeightProperty().bind(heightProperty().multiply(0.25));
        completionList.prefHeightProperty().bind(completionList.maxHeightProperty());
        completionList.prefWidthProperty().bind(widthProperty());
        completionList.setCellFactory(view -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            };
            cell.getStyleClass().add("completion-item");
            cell.setOnMouseClicked(event -> {
                if (!cell.isEmpty()) {
                    applyCompletion(cell.getItem());
                }
            });
            return cell;
        });
    }

    private void showCompletion() {
        completionList.setVisible(true);
        completionList.toFront();
        Platform.runLater(this::layoutCompletionList);
    }

    private void hideCompletion() {
        completionList.setVisible(false);
    }

    private void drawDiagnostics() {
        diagnosticLayer.getChildren().clear();
        String source = input.getText();
        if (source.isEmpty() || latestDiagnostics.isEmpty()) {
            updateDiagnosticDetails();
            return;
        }
        for (UiDiagnosticDto diagnostic : latestDiagnostics) {
            int start = safeOffset(source, diagnostic.startOffset());
            int end = safeOffset(source, diagnostic.endOffset());
            if (end <= start) {
                end = Math.min(source.length(), start + 1);
                if (end <= start && start > 0) {
                    start--;
                }
            }
            boundsForRange(source, start, end)
                    .map(diagnosticLayer::screenToLocal)
                    .ifPresent(this::addDiagnosticWave);
        }
        updateDiagnosticDetails();
    }

    private java.util.Optional<Bounds> boundsForRange(String source, int start, int end) {
        java.util.Optional<Bounds> bounds = input.getCharacterBoundsOnScreen(start, end);
        if (bounds.isPresent() || source.isEmpty()) {
            return bounds;
        }
        int fallbackStart = Math.max(0, Math.min(start, source.length() - 1));
        int fallbackEnd = Math.min(source.length(), fallbackStart + 1);
        return input.getCharacterBoundsOnScreen(fallbackStart, fallbackEnd);
    }

    private void addDiagnosticWave(Bounds bounds) {
        double startX = Math.max(0, bounds.getMinX());
        double endX = Math.min(getWidth(), Math.max(startX + 8, bounds.getMaxX()));
        double baseY = Math.max(0, Math.min(getHeight(), bounds.getMaxY() - 2));
        double amplitude = 2;
        double step = 4;
        ArrayList<Double> points = new ArrayList<>();
        boolean up = true;
        for (double x = startX; x <= endX; x += step) {
            points.add(x);
            points.add(baseY + (up ? -amplitude : amplitude));
            up = !up;
        }
        points.add(endX);
        points.add(baseY);
        Polyline wave = new Polyline();
        wave.getStyleClass().add("diagnostic-wave");
        wave.getPoints().setAll(points);
        wave.setStroke(Color.web("#f48771"));
        wave.setStrokeWidth(1.4);
        diagnosticLayer.getChildren().add(wave);
    }

    private boolean isCompletionShowing() {
        return completionList.isVisible() && !completionList.getItems().isEmpty();
    }

    private void layoutCompletionList() {
        double maxHeight = Math.max(0, getHeight() * 0.25);
        double rowHeight = 26;
        double preferredHeight = Math.min(maxHeight, Math.max(rowHeight, completionList.getItems().size() * rowHeight + 2));
        double reservedBottom = diagnosticDetails.isVisible() ? diagnosticDetailsHeight() : 0;
        double y = Math.max(0, getHeight() - reservedBottom - preferredHeight);
        completionList.resizeRelocate(0, y, getWidth(), preferredHeight);
    }

    private void updateDiagnosticDetails() {
        diagnosticDetails.getChildren().clear();
        String source = input.getText();
        if (source.isEmpty() || latestDiagnostics.isEmpty()) {
            diagnosticDetails.setVisible(false);
            Platform.runLater(this::layoutCompletionList);
            return;
        }
        latestDiagnostics.stream()
                .sorted(Comparator.comparingInt(UiDiagnosticDto::startOffset))
                .limit(4)
                .map(diagnostic -> diagnosticDetail(source, diagnostic))
                .forEach(diagnosticDetails.getChildren()::add);
        diagnosticDetails.setVisible(true);
        Platform.runLater(this::layoutDiagnosticDetails);
    }

    private Label diagnosticDetail(String source, UiDiagnosticDto diagnostic) {
        SourcePosition position = sourcePosition(source, diagnostic.startOffset());
        int start = safeOffset(source, diagnostic.startOffset());
        int end = safeOffset(source, diagnostic.endOffset());
        String message = diagnostic.message() == null || diagnostic.message().isBlank()
                ? "编译器没有返回更具体的错误原因。"
                : diagnostic.message();
        String text = "错误位置: 第 " + position.line()
                + " 行，第 " + position.byteOffsetInLine()
                + " 个字节，offset " + start + ".." + end
                + "。原因: " + message
                + "。请检查该位置附近的关键字、标识符、括号、分号、表达式或宏展开结果是否符合 MiniC 当前语法。";
        Label label = new Label(text);
        label.getStyleClass().add("editor-diagnostic-detail");
        label.setWrapText(true);
        return label;
    }

    private void layoutDiagnosticDetails() {
        if (!diagnosticDetails.isVisible()) {
            return;
        }
        double width = Math.max(0, getWidth());
        double preferredHeight = diagnosticDetailsHeight();
        diagnosticDetails.resizeRelocate(0, Math.max(0, getHeight() - preferredHeight), width, preferredHeight);
        layoutCompletionList();
    }

    private double diagnosticDetailsHeight() {
        double width = Math.max(0, getWidth());
        diagnosticDetails.applyCss();
        return Math.min(140, diagnosticDetails.prefHeight(width));
    }

    private void selectCompletionOffset(int offset) {
        int size = completionList.getItems().size();
        if (size == 0) {
            return;
        }
        int selected = completionList.getSelectionModel().getSelectedIndex();
        int next = Math.max(0, Math.min(size - 1, selected + offset));
        completionList.getSelectionModel().select(next);
        completionList.scrollTo(next);
    }

    private void applySelectedCompletion() {
        String selected = completionList.getSelectionModel().getSelectedItem();
        if (selected == null && !completionList.getItems().isEmpty()) {
            selected = completionList.getItems().getFirst();
        }
        if (selected != null) {
            applyCompletion(selected);
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
        hideCompletion();
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

    private SourcePosition sourcePosition(String source, int offset) {
        int safeOffset = safeOffset(source, offset);
        int line = 1;
        int lineStart = 0;
        for (int index = 0; index < safeOffset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
                lineStart = index + 1;
            }
        }
        int byteOffsetInLine = source.substring(lineStart, safeOffset)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                .length + 1;
        return new SourcePosition(line, byteOffsetInLine);
    }

    private record Prefix(String text, int startOffset, int endOffset) {
    }

    private record SourcePosition(int line, int byteOffsetInLine) {
    }
}
