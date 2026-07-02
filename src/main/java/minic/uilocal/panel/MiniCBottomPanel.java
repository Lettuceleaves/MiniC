package minic.uilocal;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import minic.uilocal.text.MiniCExplanationTextHighlighter;
import minic.uilocal.text.MiniCSyntaxTextStyleMapper;
import minic.uilocal.text.MiniCTextFlowFactory;
import minic.uilocal.text.MiniCTextStyleRole;
import minic.uilocal.text.MiniCTextStyles;
import minic.uiapi.MiniCRealtimeAnalysisApi;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiSourceSpanDto;
import minic.settings.MiniCSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 可折叠的底部信息栏，用于展示 Graph View hover inspector。
 */
public final class MiniCBottomPanel extends VBox {
    private static final double COLLAPSED_HEIGHT = 24.0;
    private static final double DEFAULT_EXPANDED_HEIGHT = 212.0;
    private static final double MIN_EXPANDED_HEIGHT = 120.0;
    private static final double MAX_EXPANDED_HEIGHT = 520.0;
    private static final String DRAG_START_Y_KEY = "bottomPanelDragStartY";
    private static final String DRAG_START_HEIGHT_KEY = "bottomPanelDragStartHeight";

    private final MiniCHoverInspector inspector;
    private final MiniCExplanationTextHighlighter explanationTextHighlighter = new MiniCExplanationTextHighlighter();
    private final MiniCSyntaxTextStyleMapper syntaxTextStyleMapper = new MiniCSyntaxTextStyleMapper();
    private final MiniCRealtimeAnalysisApi realtimeAnalysisApi = new MiniCRealtimeAnalysisApi();
    private final Region resizeHandle = new Region();
    private final HBox body = new HBox(10);
    private final Button toggle = new Button("+");
    private final Runnable uiScaleChangeListener = this::applyHeightOnFxThread;
    private double expandedHeight = DEFAULT_EXPANDED_HEIGHT;
    private boolean expanded;

    /**
     * 创建底部面板。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCBottomPanel(MiniCWorkbenchViewModel viewModel) {
        this(new MiniCHoverInspector());
    }

    /**
     * 创建底部面板。
     *
     * @param viewModel UI 状态模型
     * @param diagnosticSelection diagnostic 选择状态；可为 {@code null}
     */
    public MiniCBottomPanel(MiniCWorkbenchViewModel viewModel, MiniCDiagnosticSelection diagnosticSelection) {
        this(new MiniCHoverInspector());
    }

    /**
     * 创建空底部信息栏。
     */
    public MiniCBottomPanel() {
        this(new MiniCHoverInspector());
    }

    /**
     * 创建底部信息栏。
     *
     * @param inspector hover inspector 共享状态
     */
    public MiniCBottomPanel(MiniCHoverInspector inspector) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        getStyleClass().add("bottom-panel");
        resizeHandle.getStyleClass().add("bottom-resize-handle");
        resizeHandle.setCursor(Cursor.V_RESIZE);
        configureResizeHandle();
        HBox header = new HBox();
        header.getStyleClass().add("bottom-bar");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toggle.getStyleClass().add("bottom-toggle");
        toggle.setOnAction(event -> setExpanded(!expanded));
        header.getChildren().addAll(spacer, toggle);
        body.getStyleClass().add("bottom-body");
        getChildren().addAll(resizeHandle, header, body);
        inspector.contentProperty().addListener((observable, oldValue, newValue) -> render(newValue));
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                MiniCSettings.removeUiScaleChangeListener(uiScaleChangeListener);
            }
            if (newScene != null) {
                MiniCSettings.addUiScaleChangeListener(uiScaleChangeListener);
                applyHeight();
            }
        });
        render(inspector.contentProperty().get());
        setExpanded(false);
    }

    private void render(MiniCHoverInspectorContent content) {
        body.getChildren().clear();
        if (content == null || content.emptyContent()) {
            return;
        }
        body.getChildren().addAll(leftContent(content), rightContent(content));
        if (!expanded) {
            setExpanded(true);
        }
    }

    private VBox leftContent(MiniCHoverInspectorContent content) {
        VBox box = new VBox(8);
        box.getStyleClass().add("hover-inspector-left");
        Label title = new Label(content.title());
        title.getStyleClass().add("hover-inspector-title");
        VBox metadata = lines(content.metadata(), "hover-inspector-meta");
        VBox source = sourceLines(content.source(), content.range());
        VBox contentBox = new VBox(8, title, metadata, source);
        contentBox.getStyleClass().add("hover-left-content");
        ScrollPane leftScroll = new ScrollPane(contentBox);
        leftScroll.getStyleClass().add("hover-left-scroll");
        leftScroll.setFitToWidth(true);
        centerSourceRangeLater(leftScroll, content.range(), source.getChildren().size());
        box.getChildren().add(leftScroll);
        VBox.setVgrow(leftScroll, Priority.ALWAYS);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private VBox rightContent(MiniCHoverInspectorContent content) {
        VBox box = new VBox(8);
        box.getStyleClass().add("hover-inspector-right");
        Label title = new Label("说明");
        title.getStyleClass().add("hover-inspector-title");
        TextFlow explanation = explanationText(content.explanation().isBlank() ? "暂无说明。" : content.explanation());
        ScrollPane explanationScroll = new ScrollPane(explanation);
        explanationScroll.getStyleClass().add("hover-explanation-scroll");
        explanationScroll.setFitToWidth(true);
        box.getChildren().addAll(title, explanationScroll);
        VBox.setVgrow(explanationScroll, Priority.ALWAYS);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private TextFlow explanationText(String text) {
        return MiniCTextFlowFactory.textFlow(
                explanationTextHighlighter.highlight(text),
                "hover-inspector-explanation",
                false
        );
    }

    private VBox lines(List<String> rows, String styleClass) {
        VBox box = new VBox(2);
        box.getStyleClass().add(styleClass);
        for (String row : rows) {
            Label label = new Label(row == null || row.isBlank() ? " " : row);
            label.getStyleClass().add("hover-inspector-line");
            box.getChildren().add(label);
        }
        return box;
    }

    private VBox sourceLines(String source, UiSourceSpanDto range) {
        VBox box = new VBox(0);
        box.getStyleClass().add("hover-source");
        String[] rows = source.split("\\R", -1);
        List<SourceTokenStyle> tokenStyles = sourceTokenStyles(source);
        int lineStartOffset = 0;
        for (int index = 0; index < rows.length; index++) {
            HBox row = new HBox();
            row.getStyleClass().add("hover-source-row");
            Label number = new Label(Integer.toString(index + 1));
            number.getStyleClass().add("hover-source-line-number");
            HBox text = sourceLineText(rows[index], lineStartOffset, range, tokenStyles);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(number, text, spacer);
            box.getChildren().add(row);
            lineStartOffset += rows[index].length() + lineSeparatorLength(source, lineStartOffset + rows[index].length());
        }
        return box;
    }

    private HBox sourceLineText(
            String line,
            int lineStartOffset,
            UiSourceSpanDto range,
            List<SourceTokenStyle> tokenStyles
    ) {
        HBox flow = new HBox(0);
        flow.getStyleClass().add("hover-source-text-flow");
        if (line.isEmpty()) {
            Label blank = sourceChar(" ", MiniCTextStyles.classes(MiniCTextStyleRole.CODE_PLAIN));
            flow.getChildren().add(blank);
            return flow;
        }
        for (int index = 0; index < line.length(); index++) {
            int absoluteOffset = lineStartOffset + index;
            Label character = sourceChar(line.substring(index, index + 1), sourceStyleClasses(absoluteOffset, tokenStyles));
            if (range != null && absoluteOffset >= range.startOffset() && absoluteOffset < range.endOffset()) {
                character.getStyleClass().add("masked");
            }
            flow.getChildren().add(character);
        }
        return flow;
    }

    private Label sourceChar(String text, Collection<String> textStyleClasses) {
        Label label = new Label(text);
        label.getStyleClass().add("hover-source-text");
        label.getStyleClass().addAll(textStyleClasses);
        return label;
    }

    private List<SourceTokenStyle> sourceTokenStyles(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            List<UiLexerTokenVisualDto> tokens = realtimeAnalysisApi.tokenize("hover-inspector-source.mc", source).stream()
                    .filter(token -> !"EOF".equals(token.kind()))
                    .filter(token -> token.endOffset() > token.startOffset())
                    .sorted(Comparator.comparingInt(UiLexerTokenVisualDto::startOffset))
                    .toList();
            ArrayList<SourceTokenStyle> styles = new ArrayList<>(tokens.size());
            for (int index = 0; index < tokens.size(); index++) {
                styles.add(sourceTokenStyle(tokens, index));
            }
            return List.copyOf(styles);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private SourceTokenStyle sourceTokenStyle(List<UiLexerTokenVisualDto> tokens, int index) {
        UiLexerTokenVisualDto token = tokens.get(index);
        return new SourceTokenStyle(
                token.startOffset(),
                token.endOffset(),
                new ArrayList<>(syntaxTextStyleMapper.styleClassesForToken(tokens, index, false))
        );
    }

    private Collection<String> sourceStyleClasses(int absoluteOffset, List<SourceTokenStyle> tokenStyles) {
        return tokenStyles.stream()
                .filter(style -> absoluteOffset >= style.startOffset() && absoluteOffset < style.endOffset())
                .findFirst()
                .map(SourceTokenStyle::styleClasses)
                .orElseGet(() -> MiniCTextStyles.classes(MiniCTextStyleRole.CODE_PLAIN));
    }

    private record SourceTokenStyle(int startOffset, int endOffset, Collection<String> styleClasses) {}

    private int lineSeparatorLength(String source, int separatorOffset) {
        if (separatorOffset >= source.length()) {
            return 0;
        }
        if (source.charAt(separatorOffset) == '\r'
                && separatorOffset + 1 < source.length()
                && source.charAt(separatorOffset + 1) == '\n') {
            return 2;
        }
        return 1;
    }

    private void centerSourceRangeLater(ScrollPane scrollPane, UiSourceSpanDto range, int lineCount) {
        if (range == null || lineCount <= 1) {
            return;
        }
        Platform.runLater(() -> {
            double target = (range.startLine() - 1.0) / Math.max(1.0, lineCount - 1.0);
            scrollPane.setVvalue(Math.max(0.0, Math.min(1.0, target)));
        });
    }

    private void configureResizeHandle() {
        resizeHandle.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!expanded) {
                setExpanded(true);
            }
            resizeHandle.getProperties().put(DRAG_START_Y_KEY, event.getScreenY());
            resizeHandle.getProperties().put(DRAG_START_HEIGHT_KEY, expandedHeight);
            event.consume();
        });
        resizeHandle.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            Object startY = resizeHandle.getProperties().get(DRAG_START_Y_KEY);
            Object startHeight = resizeHandle.getProperties().get(DRAG_START_HEIGHT_KEY);
            if (!(startY instanceof Number y) || !(startHeight instanceof Number height)) {
                return;
            }
            double delta = (y.doubleValue() - event.getScreenY()) / uiScale();
            expandedHeight = clampHeight(height.doubleValue() + delta);
            applyHeight();
            event.consume();
        });
    }

    private void setExpanded(boolean expanded) {
        this.expanded = expanded;
        resizeHandle.setVisible(expanded);
        resizeHandle.setManaged(expanded);
        body.setVisible(expanded);
        body.setManaged(expanded);
        toggle.setText(expanded ? "-" : "+");
        getStyleClass().remove("collapsed");
        getStyleClass().remove("expanded");
        getStyleClass().add(expanded ? "expanded" : "collapsed");
        applyHeight();
    }

    private void applyHeight() {
        double height = scaled(expanded ? expandedHeight : COLLAPSED_HEIGHT);
        setMinHeight(height);
        setPrefHeight(height);
        setMaxHeight(expanded ? Region.USE_COMPUTED_SIZE : height);
    }

    private double clampHeight(double height) {
        return Math.max(MIN_EXPANDED_HEIGHT, Math.min(MAX_EXPANDED_HEIGHT, height));
    }

    private void applyHeightOnFxThread() {
        if (Platform.isFxApplicationThread()) {
            applyHeight();
        } else {
            Platform.runLater(this::applyHeight);
        }
    }

    private double scaled(double value) {
        return value * uiScale();
    }

    private double uiScale() {
        return MiniCSettings.uiScale();
    }
}
