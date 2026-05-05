package minic.ui;

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
import minic.uiapi.UiSourceSpanDto;

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
    private final Region resizeHandle = new Region();
    private final HBox body = new HBox(10);
    private final Button toggle = new Button("+");
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
        Label explanation = new Label(content.explanation().isBlank() ? "暂无说明。" : content.explanation());
        explanation.getStyleClass().add("hover-inspector-explanation");
        explanation.setWrapText(true);
        ScrollPane explanationScroll = new ScrollPane(explanation);
        explanationScroll.getStyleClass().add("hover-explanation-scroll");
        explanationScroll.setFitToWidth(true);
        box.getChildren().addAll(title, explanationScroll);
        VBox.setVgrow(explanationScroll, Priority.ALWAYS);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
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
        int lineStartOffset = 0;
        for (int index = 0; index < rows.length; index++) {
            HBox row = new HBox();
            row.getStyleClass().add("hover-source-row");
            Label number = new Label(Integer.toString(index + 1));
            number.getStyleClass().add("hover-source-line-number");
            HBox text = sourceLineText(rows[index], lineStartOffset, range);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(number, text, spacer);
            box.getChildren().add(row);
            lineStartOffset += rows[index].length() + lineSeparatorLength(source, lineStartOffset + rows[index].length());
        }
        return box;
    }

    private HBox sourceLineText(String line, int lineStartOffset, UiSourceSpanDto range) {
        HBox flow = new HBox(0);
        flow.getStyleClass().add("hover-source-text-flow");
        if (line.isEmpty()) {
            Label blank = sourceChar(" ");
            flow.getChildren().add(blank);
            return flow;
        }
        for (int index = 0; index < line.length(); index++) {
            Label character = sourceChar(line.substring(index, index + 1));
            int absoluteOffset = lineStartOffset + index;
            if (range != null && absoluteOffset >= range.startOffset() && absoluteOffset < range.endOffset()) {
                character.getStyleClass().add("masked");
            }
            flow.getChildren().add(character);
        }
        return flow;
    }

    private Label sourceChar(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hover-source-text");
        return label;
    }

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
            double delta = y.doubleValue() - event.getScreenY();
            expandedHeight = clampHeight(height.doubleValue() + delta);
            applyHeight();
            event.consume();
        });
    }

    private void setExpanded(boolean expanded) {
        this.expanded = expanded;
        body.setVisible(expanded);
        body.setManaged(expanded);
        toggle.setText(expanded ? "-" : "+");
        getStyleClass().remove("collapsed");
        getStyleClass().remove("expanded");
        getStyleClass().add(expanded ? "expanded" : "collapsed");
        applyHeight();
    }

    private void applyHeight() {
        double height = expanded ? expandedHeight : COLLAPSED_HEIGHT;
        setMinHeight(height);
        setPrefHeight(height);
        setMaxHeight(expanded ? Region.USE_COMPUTED_SIZE : COLLAPSED_HEIGHT);
    }

    private double clampHeight(double height) {
        return Math.max(MIN_EXPANDED_HEIGHT, Math.min(MAX_EXPANDED_HEIGHT, height));
    }
}
