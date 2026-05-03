package minic.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import minic.uiapi.UiStageVisualDto;

import java.util.List;
import java.util.Objects;

/**
 * 当前阶段结构化可视化区域。
 */
public final class MiniCVisualPane extends VBox {
    private static final String ACTIVE_CENTER_Y_KEY = "activeCenterY";
    private static final double DEFAULT_AST_ZOOM = 1.0;

    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCVisualModelFactory modelFactory = new MiniCVisualModelFactory();
    private final MiniCAstGraphModelFactory astGraphModelFactory = new MiniCAstGraphModelFactory();
    private final MiniCSemanticScopeTreeModelFactory semanticScopeTreeModelFactory = new MiniCSemanticScopeTreeModelFactory();
    private final MiniCAssemblyTextModelFactory assemblyTextModelFactory = new MiniCAssemblyTextModelFactory();
    private final Label header = new Label("Graph View");
    private final SplitPane splitPane = new SplitPane();
    private final StageColumn leftColumn = new StageColumn(false);
    private final StageColumn rightColumn = new StageColumn(true);
    private final Slider astZoom = new Slider(0.55, 1.85, DEFAULT_AST_ZOOM);
    private final TextArea executionStdin = new TextArea();
    private final CheckBox executionNoInput = new CheckBox("无输入");
    private final Button executionConfirm = new Button("确认输入");
    private double dividerPosition = 0.5;

    /**
     * 创建 Visual Pane。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCVisualPane(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        getStyleClass().add("pane");
        header.getStyleClass().add("pane-head");
        splitPane.getStyleClass().add("stage-flow");
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setMinWidth(0);
        splitPane.setMaxWidth(Double.MAX_VALUE);
        splitPane.getItems().setAll(leftColumn.root, rightColumn.root);
        splitPane.setDividerPositions(dividerPosition);
        splitPane.getDividers().getFirst().positionProperty().addListener((observable, oldValue, newValue) ->
                dividerPosition = newValue.doubleValue());
        astZoom.getStyleClass().add("ast-zoom-slider");
        astZoom.setBlockIncrement(0.1);
        astZoom.setMajorTickUnit(0.25);
        astZoom.setShowTickMarks(true);
        configureExecutionInputControls();
        getChildren().addAll(header, splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        refresh();
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStageVisualDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.lexerVisualDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.astVisualDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.semanticVisualDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    /**
     * 刷新可视化内容。
     */
    public void refresh() {
        String stage = viewModel.currentStageDataProperty().get() == null
                ? "pending"
                : viewModel.currentStageDataProperty().get().stage();
        header.setText("Graph View · " + stage);
        UiStageVisualDto visual = viewModel.currentStageVisualDataProperty().get();
        if (visual == null) {
            leftColumn.setContent(stage, fallbackRows());
            rightColumn.setContent("Output", List.of());
            restoreDivider();
            return;
        }
        switch (stage) {
            case "lexer" -> {
                leftColumn.setContent("Source", sourceRows());
                rightColumn.setContent("Tokens", tokenRows(visual));
            }
            case "parser" -> {
                leftColumn.setContent("Tokens", tokenRows(viewModel.lexerVisualDataProperty().get()));
                rightColumn.setContent("AST", List.of(zoomableAstGraph(visual)));
            }
            case "semantic" -> {
                leftColumn.setContent("AST", List.of(zoomableAstGraph(viewModel.astVisualDataProperty().get())));
                rightColumn.setContent("Scope", semanticRows(visual));
            }
            case "codegen" -> {
                leftColumn.setContent("AST + Scope", List.of(astScopeInput()));
                rightColumn.setContent("Assembly", assemblyRows(visual));
            }
            case "execution" -> {
                leftColumn.setContent("STDIN", List.of(executionInputPane()));
                rightColumn.setContent("OUTPUT", executionOutputRows());
            }
            default -> {
                leftColumn.setContent(stage, fallbackRows());
                rightColumn.setContent("Output", List.of());
            }
        }
        restoreDivider();
    }

    private void restoreDivider() {
        Platform.runLater(() -> splitPane.setDividerPositions(dividerPosition));
    }

    private VBox astScopeInput() {
        VBox box = new VBox(10);
        box.getStyleClass().add("asm-input-stack");
        box.getChildren().add(section("AST", List.of(zoomableAstGraph(viewModel.astVisualDataProperty().get()))));
        box.getChildren().add(section("Scope", semanticRows(viewModel.semanticVisualDataProperty().get())));
        return box;
    }

    private void configureExecutionInputControls() {
        executionStdin.getStyleClass().add("execution-stdin");
        executionStdin.setWrapText(false);
        executionNoInput.selectedProperty().addListener((observable, oldValue, selected) -> executionStdin.setDisable(selected));
        executionConfirm.setOnAction(event -> viewModel.confirmExecutionInput(
                executionNoInput.isSelected() ? "" : executionStdin.getText()
        ));
    }

    private VBox executionInputPane() {
        VBox box = new VBox(8);
        HBox actions = new HBox(8, executionNoInput, executionConfirm);
        actions.getStyleClass().add("execution-actions");
        boolean completed = viewModel.currentStageDataProperty().get() != null
                && viewModel.currentStageDataProperty().get().completed();
        boolean confirmed = viewModel.globalDataProperty().get() != null
                && viewModel.globalDataProperty().get().executionInputSummary().stream()
                .anyMatch(line -> line.equals("stdin confirmed"));
        executionStdin.setDisable(executionNoInput.isSelected() || completed || confirmed);
        executionNoInput.setDisable(completed || confirmed);
        executionConfirm.setDisable(completed || confirmed);
        box.getChildren().addAll(actions, executionStdin);
        VBox.setVgrow(executionStdin, Priority.ALWAYS);
        return box;
    }

    private List<Label> executionOutputRows() {
        if (viewModel.globalDataProperty().get() == null
                || viewModel.globalDataProperty().get().executionOutputSummary().isEmpty()) {
            return List.of(monoLabel("Execution output will appear here."));
        }
        return viewModel.globalDataProperty().get().executionOutputSummary().stream()
                .map(this::monoLabel)
                .toList();
    }

    private Label monoLabel(String text) {
        Label label = new Label(text.isEmpty() ? " " : text);
        label.getStyleClass().add("assembly-text");
        return label;
    }

    private VBox section(String title, List<? extends Node> rows) {
        VBox section = new VBox(6);
        section.getStyleClass().add("stage-flow-column");
        section.setMinWidth(0);
        section.setMaxWidth(Double.MAX_VALUE);
        Label label = new Label(title);
        label.getStyleClass().add("stage-flow-title");
        VBox body = new VBox(4);
        body.getStyleClass().add("stage-flow-body");
        body.setMinWidth(0);
        body.getChildren().setAll(rows);
        section.getChildren().addAll(label, body);
        return section;
    }

    private List<HBox> sourceRows() {
        String source = viewModel.sourceTextProperty().get();
        String[] lines = source.split("\\R", -1);
        java.util.ArrayList<HBox> rows = new java.util.ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            HBox row = new HBox();
            row.getStyleClass().add("lexer-row");
            Label number = new Label(Integer.toString(index + 1));
            number.getStyleClass().add("lexer-line-number");
            Label text = new Label(lines[index].isEmpty() ? " " : lines[index]);
            text.getStyleClass().add("source-flow-line");
            row.getChildren().addAll(number, text);
            rows.add(row);
        }
        return rows;
    }

    private List<Label> fallbackRows() {
        List<MiniCVisualItem> items = modelFactory.create(
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        return items.stream().map(this::node).toList();
    }

    private Label node(MiniCVisualItem item) {
        Label label = new Label(item.label());
        label.getStyleClass().add("visual-node");
        if (item.hot()) {
            label.getStyleClass().add("hot");
        }
        return label;
    }

    private Pane astGraph(UiStageVisualDto visual) {
        if (visual == null || visual.astRoot() == null) {
            return emptyPane("AST not ready");
        }
        MiniCAstGraphModel graph = astGraphModelFactory.create(visual);
        Pane pane = new Pane();
        pane.getStyleClass().add("ast-graph");
        pane.setMinSize(graph.width(), graph.height());
        pane.setPrefSize(graph.width(), graph.height());
        graph.edges().forEach(edge -> {
            Line line = new Line(edge.fromX(), edge.fromY(), edge.toX(), edge.toY());
            line.getStyleClass().add("ast-edge");
            if (edge.hot()) {
                line.getStyleClass().add("hot");
            }
            pane.getChildren().add(line);
        });
        graph.nodes().forEach(node -> {
            Circle circle = new Circle(node.x(), node.y(), node.root() ? 30 : node.leaf() ? 22 : 26);
            circle.getStyleClass().add("ast-graph-node");
            if (node.root()) {
                circle.getStyleClass().add("root");
            }
            if (node.active()) {
                circle.getStyleClass().add("hot");
                pane.getProperties().put(ACTIVE_CENTER_Y_KEY, node.y());
            }
            if (node.leaf()) {
                circle.getStyleClass().add("leaf");
            }
            Text text = new Text(shortLabel(node.label()));
            text.getStyleClass().add("ast-graph-label");
            text.setX(node.x() - 32);
            text.setY(node.y() + 4);
            text.setWrappingWidth(64);
            text.setFill(Color.web("#d4d4d4"));
            pane.getChildren().addAll(circle, text);
        });
        return pane;
    }

    private VBox zoomableAstGraph(UiStageVisualDto visual) {
        VBox box = new VBox(6);
        box.getStyleClass().add("ast-zoom-box");
        HBox controls = new HBox(8);
        controls.getStyleClass().add("ast-zoom-controls");
        Label title = new Label("Zoom");
        title.getStyleClass().add("ast-zoom-label");
        Label value = new Label();
        value.getStyleClass().add("ast-zoom-value");
        value.textProperty().bind(astZoom.valueProperty().multiply(100).asString("%.0f%%"));
        controls.getChildren().addAll(title, astZoom, value);
        Pane graph = astGraph(visual);
        Group graphGroup = new Group(graph);
        double baseWidth = graph.getPrefWidth();
        double baseHeight = graph.getPrefHeight();
        graph.scaleXProperty().bind(astZoom.valueProperty());
        graph.scaleYProperty().bind(astZoom.valueProperty());
        graph.setManaged(false);
        Pane graphViewport = new Pane(graphGroup);
        graphViewport.getStyleClass().add("ast-graph-viewport");
        graphViewport.prefWidthProperty().bind(astZoom.valueProperty().multiply(baseWidth));
        graphViewport.prefHeightProperty().bind(astZoom.valueProperty().multiply(baseHeight));
        graphViewport.minWidthProperty().bind(graphViewport.prefWidthProperty());
        graphViewport.minHeightProperty().bind(graphViewport.prefHeightProperty());
        updateZoomedActiveMarker(box, graph, astZoom.getValue());
        astZoom.valueProperty().addListener((observable, oldValue, newValue) ->
                updateZoomedActiveMarker(box, graph, newValue.doubleValue()));
        box.getChildren().addAll(controls, graphViewport);
        box.setMinWidth(0);
        return box;
    }

    private void updateZoomedActiveMarker(VBox box, Pane graph, double zoom) {
        Object marker = graph.getProperties().get(ACTIVE_CENTER_Y_KEY);
        if (marker instanceof Number number) {
            box.getProperties().put(ACTIVE_CENTER_Y_KEY, 38 + number.doubleValue() * zoom);
        }
    }

    private Pane emptyPane(String message) {
        Pane pane = new Pane();
        pane.getStyleClass().add("empty-visual");
        pane.setMinSize(260, 120);
        pane.setPrefSize(260, 120);
        Text text = new Text(message);
        text.getStyleClass().add("ast-graph-label");
        text.setX(18);
        text.setY(38);
        text.setFill(Color.web("#858585"));
        pane.getChildren().add(text);
        return pane;
    }

    private String shortLabel(String label) {
        String compact = label
                .replace("FunctionDecl", "Fn")
                .replace("BlockStmt", "Block")
                .replace("ReturnStmt", "Return")
                .replace("IfStmt", "If")
                .replace("BinaryExpr", "Bin")
                .replace("IntegerLiteralExpr", "Int")
                .replace("NameExpr", "Name");
        return compact.length() <= 12 ? compact : compact.substring(0, 12);
    }

    private List<HBox> assemblyRows(UiStageVisualDto visual) {
        return assemblyTextModelFactory.create(visual).stream()
                .map(this::assemblyRow)
                .toList();
    }

    private HBox assemblyRow(MiniCAssemblyTextLine line) {
        HBox row = new HBox();
        row.getStyleClass().add("assembly-row");
        Label number = new Label(Integer.toString(line.lineNumber()));
        number.getStyleClass().add("assembly-line-number");
        Label text = new Label(line.text());
        text.getStyleClass().add("assembly-text");
        if (line.active()) {
            row.getStyleClass().add("active");
            number.getStyleClass().add("active");
            text.getStyleClass().add("active");
        }
        row.getChildren().addAll(number, text);
        return row;
    }

    private List<HBox> semanticRows(UiStageVisualDto visual) {
        if (visual == null || visual.semanticRoot() == null) {
            return List.of(textRow("Scope not ready", "semantic-row", "semantic-scope-line"));
        }
        return semanticScopeTreeModelFactory.create(visual).stream()
                .map(this::semanticRow)
                .toList();
    }

    private HBox semanticRow(MiniCSemanticScopeTreeLine line) {
        HBox row = new HBox();
        row.getStyleClass().add("semantic-row");
        Label label = new Label("  ".repeat(line.depth()) + "^ " + line.label() + "  " + String.join(", ", line.symbols()));
        label.getStyleClass().add("semantic-scope-line");
        if (line.active()) {
            row.getStyleClass().add("active");
            label.getStyleClass().add("active");
        }
        if (line.onActivePath()) {
            label.getStyleClass().add("path");
        }
        row.getChildren().add(label);
        return row;
    }

    private List<HBox> tokenRows(UiStageVisualDto visual) {
        if (visual == null || visual.lexerTokens().isEmpty()) {
            return List.of(textRow("Tokens not ready", "token-row", "token-text"));
        }
        return visual.lexerTokens().stream()
                .map(token -> {
                    HBox row = new HBox(8);
                    row.getStyleClass().add("token-row");
                    Label kind = new Label(token.kind());
                    kind.getStyleClass().add("token-kind");
                    Label text = new Label(token.text().isEmpty() ? "<EOF>" : token.text());
                    text.getStyleClass().add("token-text");
                    Label range = new Label(token.startLine() + ":" + token.startColumn());
                    range.getStyleClass().add("token-range");
                    if (token.active()) {
                        row.getStyleClass().add("active");
                        kind.getStyleClass().add("active");
                        text.getStyleClass().add("active");
                        range.getStyleClass().add("active");
                    }
                    row.getChildren().addAll(kind, text, range);
                    return row;
                })
                .toList();
    }

    private HBox textRow(String text, String rowStyle, String textStyle) {
        HBox row = new HBox();
        row.getStyleClass().add(rowStyle);
        Label label = new Label(text);
        label.getStyleClass().add(textStyle);
        row.getChildren().add(label);
        return row;
    }

    private final class StageColumn {
        private final VBox root = new VBox(6);
        private final Label title = new Label();
        private final VBox body = new VBox(4);
        private final ScrollPane scrollPane = new ScrollPane(body);
        private final boolean autoCenter;

        private StageColumn(boolean autoCenter) {
            this.autoCenter = autoCenter;
            root.getStyleClass().add("stage-flow-column");
            root.setMinWidth(0);
            root.setMaxWidth(Double.MAX_VALUE);
            title.getStyleClass().add("stage-flow-title");
            body.getStyleClass().add("stage-flow-body");
            body.setMinWidth(0);
            scrollPane.getStyleClass().add("stage-flow-scroll");
            scrollPane.setFitToWidth(false);
            scrollPane.setFitToHeight(false);
            scrollPane.setMinWidth(0);
            root.getChildren().addAll(title, scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
            if (autoCenter) {
                scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> centerActiveLater());
            }
        }

        private void setContent(String titleText, List<? extends Node> rows) {
            title.setText(titleText);
            body.getChildren().setAll(rows);
            if (autoCenter) {
                centerActiveLater();
            }
        }

        private void centerActiveLater() {
            Platform.runLater(this::centerActive);
        }

        private void centerActive() {
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double contentHeight = body.getBoundsInLocal().getHeight();
            if (viewportHeight <= 0 || contentHeight <= viewportHeight) {
                scrollPane.setVvalue(0);
                return;
            }
            Double activeCenterY = activeCenterY();
            if (activeCenterY == null) {
                return;
            }
            double targetTop = activeCenterY - viewportHeight / 2.0;
            double maxTop = contentHeight - viewportHeight;
            double clampedTop = Math.max(0, Math.min(targetTop, maxTop));
            scrollPane.setVvalue(clampedTop / maxTop);
        }

        private Double activeCenterY() {
            for (Node child : body.getChildren()) {
                Object marker = child.getProperties().get(ACTIVE_CENTER_Y_KEY);
                if (marker instanceof Number number) {
                    return child.getLayoutY() + number.doubleValue();
                }
                if (hasActiveStyle(child)) {
                    return child.getLayoutY() + child.getBoundsInLocal().getHeight() / 2.0;
                }
            }
            return null;
        }

        private boolean hasActiveStyle(Node node) {
            if (node.getStyleClass().contains("active")) {
                return true;
            }
            if (node instanceof Parent parent) {
                return parent.getChildrenUnmodifiable().stream().anyMatch(this::hasActiveStyle);
            }
            return false;
        }
    }
}
