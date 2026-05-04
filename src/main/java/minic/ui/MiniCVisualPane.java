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
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiAssemblyLineVisualDto;
import minic.uiapi.UiIrLineVisualDto;
import minic.uiapi.UiLexerTokenVisualDto;
import minic.uiapi.UiSemanticScopeVisualDto;
import minic.uiapi.UiSourceSpanDto;
import minic.uiapi.UiStageVisualDto;

import java.util.List;
import java.util.Objects;

/**
 * 当前阶段结构化可视化区域。
 */
public final class MiniCVisualPane extends VBox {
    private static final String ACTIVE_CENTER_Y_KEY = "activeCenterY";
    private static final double DEFAULT_AST_ZOOM = 1.0;
    private static final double MIN_AST_ZOOM = 0.001;
    private static final double MAX_AST_ZOOM = 1.0;
    private static final double AST_ZOOM_STEP = 0.025;
    private static final String AST_DRAG_START_X_KEY = "astDragStartX";
    private static final String AST_DRAG_START_Y_KEY = "astDragStartY";
    private static final String AST_DRAG_START_H_KEY = "astDragStartH";
    private static final String AST_DRAG_START_V_KEY = "astDragStartV";

    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCHoverInspector hoverInspector;
    private final MiniCVisualModelFactory modelFactory = new MiniCVisualModelFactory();
    private final MiniCAstGraphModelFactory astGraphModelFactory = new MiniCAstGraphModelFactory();
    private final MiniCSemanticScopeTreeModelFactory semanticScopeTreeModelFactory = new MiniCSemanticScopeTreeModelFactory();
    private final MiniCAssemblyTextModelFactory assemblyTextModelFactory = new MiniCAssemblyTextModelFactory();
    private final Label header = new Label("Graph View");
    private final SplitPane splitPane = new SplitPane();
    private final StageColumn leftColumn = new StageColumn(false);
    private final StageColumn rightColumn = new StageColumn(true);
    private final Slider astZoom = new Slider(MIN_AST_ZOOM, MAX_AST_ZOOM, DEFAULT_AST_ZOOM);
    private final TextArea executionStdin = new TextArea();
    private final CheckBox executionNoInput = new CheckBox("无输入");
    private final Button executionConfirm = new Button("确认输入");
    private String selectedSemanticScopeId = "";
    private boolean refreshScheduled;

    /**
     * 创建 Visual Pane。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCVisualPane(MiniCWorkbenchViewModel viewModel) {
        this(viewModel, new MiniCHoverInspector());
    }

    /**
     * 创建 Visual Pane。
     *
     * @param viewModel UI 状态模型
     * @param hoverInspector hover inspector 共享状态
     */
    public MiniCVisualPane(MiniCWorkbenchViewModel viewModel, MiniCHoverInspector hoverInspector) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.hoverInspector = Objects.requireNonNull(hoverInspector, "hoverInspector");
        getStyleClass().add("pane");
        header.getStyleClass().add("pane-head");
        splitPane.getStyleClass().add("stage-flow");
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setMinWidth(0);
        splitPane.setMaxWidth(Double.MAX_VALUE);
        splitPane.getItems().setAll(leftColumn.root, rightColumn.root);
        splitPane.setDividerPositions(0.5);
        astZoom.getStyleClass().add("ast-zoom-slider");
        astZoom.setBlockIncrement(AST_ZOOM_STEP);
        astZoom.setMajorTickUnit(0.25);
        astZoom.setShowTickMarks(true);
        configureExecutionInputControls();
        getChildren().addAll(header, splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        requestRefresh();
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
        viewModel.currentStageVisualDataProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
        viewModel.lexerVisualDataProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
        viewModel.astVisualDataProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
        viewModel.semanticVisualDataProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
        viewModel.codegenVisualDataProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
        viewModel.selectedVisualStageProperty().addListener((observable, oldValue, newValue) -> requestRefresh());
    }

    private void requestRefresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::requestRefresh);
            return;
        }
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        Platform.runLater(() -> {
            refreshScheduled = false;
            refresh();
        });
    }

    /**
     * 刷新可视化内容。
     */
    public void refresh() {
        hoverInspector.clear();
        String currentStage = viewModel.currentStageDataProperty().get() == null
                ? "pending"
                : viewModel.currentStageDataProperty().get().stage();
        String selectedStage = viewModel.selectedVisualStageProperty().get();
        String stage = selectedStage == null || selectedStage.isBlank() ? currentStage : selectedStage;
        if (!"semantic".equals(stage)) {
            selectedSemanticScopeId = "";
        }
        header.setText("Graph View · " + stage + (stage.equals(currentStage) ? "" : " · snapshot"));
        UiStageVisualDto visual = visualForStage(stage);
        if (visual == null) {
            leftColumn.setContent(stage, fallbackRows());
            rightColumn.setContent("Output", List.of());
            return;
        }
        switch (stage) {
            case "lexer" -> {
                leftColumn.setContent("Source", sourceRows(visual));
                rightColumn.setContent("Tokens", tokenRows(visual));
            }
            case "parser" -> {
                leftColumn.setContent("Tokens", tokenRows(viewModel.lexerVisualDataProperty().get()));
                rightColumn.setContent("AST", List.of(zoomableAstGraph(visual)));
            }
            case "semantic" -> {
                leftColumn.setContent("AST", List.of(zoomableSemanticAstGraph(visual)));
                rightColumn.setContent("Scope", activeScopeRows(visual));
            }
            case "codegen" -> {
                leftColumn.setContent("IR", codegenIrRows(visual));
                rightColumn.setContent("Assembly", assemblyRows(visual));
            }
            case "source" -> {
                leftColumn.setContent("Source", sourceRows(null));
                rightColumn.setContent("Output", List.of(monoLabel("Source loaded.")));
            }
            case "ir" -> {
                leftColumn.setContent("AST", List.of(zoomableSemanticAstGraph(visual)));
                if (selectedSemanticScopeId == null || selectedSemanticScopeId.isBlank()) {
                    rightColumn.setContent("IR", globalRows(stage));
                } else {
                    rightColumn.setContent("Scope", activeScopeRows(visual));
                }
            }
            case "toolchain" -> {
                leftColumn.setContent("Assembly", assemblyRows(visualForStage("codegen")));
                rightColumn.setContent("Toolchain", globalRows(stage));
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
    }

    private UiStageVisualDto visualForStage(String stage) {
        return switch (stage) {
            case "lexer" -> viewModel.lexerVisualDataProperty().get();
            case "parser" -> viewModel.astVisualDataProperty().get();
            case "semantic" -> viewModel.semanticVisualDataProperty().get();
            case "codegen" -> viewModel.codegenVisualDataProperty().get();
            default -> viewModel.currentStageVisualDataProperty().get();
        };
    }

    /**
     * 放大 AST 图。
     */
    public void zoomAstIn() {
        setAstZoom(astZoom.getValue() + AST_ZOOM_STEP);
    }

    /**
     * 缩小 AST 图。
     */
    public void zoomAstOut() {
        setAstZoom(astZoom.getValue() - AST_ZOOM_STEP);
    }

    private void setAstZoom(double value) {
        astZoom.setValue(Math.max(MIN_AST_ZOOM, Math.min(MAX_AST_ZOOM, value)));
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

    private List<Label> globalRows(String stage) {
        if (viewModel.globalDataProperty().get() == null) {
            return List.of(monoLabel("No data."));
        }
        List<String> rows = switch (stage) {
            case "ir" -> viewModel.globalDataProperty().get().irSummary();
            case "toolchain" -> viewModel.globalDataProperty().get().artifactSummary();
            default -> List.of();
        };
        if (rows.isEmpty()) {
            return List.of(monoLabel("No " + stage + " output yet."));
        }
        return rows.stream().map(this::monoLabel).toList();
    }

    private List<HBox> codegenIrRows(UiStageVisualDto codegenVisual) {
        if (codegenVisual == null || codegenVisual.irLines().isEmpty()) {
            return List.of(textRow("No ir output yet.", "assembly-row", "assembly-text"));
        }
        return codegenVisual.irLines().stream()
                .map(this::irRow)
                .toList();
    }

    private HBox irRow(UiIrLineVisualDto line) {
        HBox row = new HBox();
        row.getStyleClass().add("assembly-row");
        Label number = new Label(Integer.toString(line.lineNumber()));
        number.getStyleClass().add("assembly-line-number");
        Label text = new Label(line.text().isEmpty() ? " " : line.text());
        text.getStyleClass().add("assembly-text");
        if (line.active()) {
            row.getStyleClass().add("active");
            number.getStyleClass().add("active");
            text.getStyleClass().add("active");
        }
        row.getChildren().addAll(number, text);
        attachInspectorClick(row, inspectorContent(
                "IR line " + line.lineNumber(),
                List.of(
                        "kind: ir",
                        "line: " + line.lineNumber(),
                        "text: " + line.text(),
                        rangeLine(line.range())
                ),
                line.range(),
                "This IR row is the intermediate representation produced before backend code generation."
        ));
        return row;
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

    private List<HBox> sourceRows(UiStageVisualDto visual) {
        String source = viewModel.sourceTextProperty().get();
        String[] lines = source.split("\\R", -1);
        UiLexerTokenVisualDto activeToken = activeSourceToken(visual);
        java.util.ArrayList<HBox> rows = new java.util.ArrayList<>();
        int offset = 0;
        for (int index = 0; index < lines.length; index++) {
            HBox row = new HBox();
            row.getStyleClass().add("lexer-row");
            Label number = new Label(Integer.toString(index + 1));
            number.getStyleClass().add("lexer-line-number");
            HBox text = sourceLineFlow(lines[index], offset, activeToken);
            offset += lines[index].length() + lineSeparatorLength(source, offset + lines[index].length());
            row.getChildren().addAll(number, text);
            rows.add(row);
        }
        return rows;
    }

    private UiLexerTokenVisualDto activeSourceToken(UiStageVisualDto visual) {
        if (visual == null) {
            return null;
        }
        return visual.lexerTokens().stream()
                .filter(UiLexerTokenVisualDto::active)
                .filter(token -> token.startOffset() >= 0 && token.endOffset() > token.startOffset())
                .findFirst()
                .orElse(null);
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

    private HBox sourceLineFlow(String line, int lineStartOffset, UiLexerTokenVisualDto activeToken) {
        HBox flow = new HBox(0);
        flow.getStyleClass().add("source-flow-line");
        if (line.isEmpty()) {
            Label blank = new Label(" ");
            blank.getStyleClass().add("source-flow-text");
            flow.getChildren().add(blank);
            return flow;
        }
        for (int index = 0; index < line.length(); index++) {
            Label text = new Label(line.substring(index, index + 1));
            text.getStyleClass().add("source-flow-text");
            if (isMaskedSourceOffset(lineStartOffset + index, activeToken)) {
                text.getStyleClass().add("source-token-mask");
            }
            flow.getChildren().add(text);
        }
        return flow;
    }

    private boolean isMaskedSourceOffset(int offset, UiLexerTokenVisualDto activeToken) {
        return activeToken != null
                && offset >= activeToken.startOffset()
                && offset < activeToken.endOffset();
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
                circle.getStyleClass().add("active");
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
            UiAstNodeVisualDto astNode = astNodeById(visual.astRoot(), node.id());
            MiniCHoverInspectorContent content = astNodeContent(astNode);
            attachInspectorClick(circle, content);
            attachInspectorClick(text, content);
            pane.getChildren().addAll(circle, text);
        });
        return pane;
    }

    private Pane semanticAstGraph(UiStageVisualDto visual) {
        if (visual == null || visual.astRoot() == null) {
            return emptyPane("AST not ready");
        }
        MiniCAstGraphModel graph = astGraphModelFactory.create(visual);
        Pane pane = new Pane();
        pane.getStyleClass().add("ast-graph");
        pane.setMinSize(graph.width(), graph.height());
        pane.setPrefSize(graph.width(), graph.height());
        addSemanticScopeMasks(pane, graph, visual);
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
                circle.getStyleClass().add("active");
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
            UiAstNodeVisualDto astNode = astNodeById(visual.astRoot(), node.id());
            MiniCHoverInspectorContent content = astNodeContent(astNode);
            attachInspectorClick(circle, content);
            attachInspectorClick(text, content);
            pane.getChildren().addAll(circle, text);
        });
        return pane;
    }

    private void addSemanticScopeMasks(Pane pane, MiniCAstGraphModel graph, UiStageVisualDto visual) {
        List<ScopeEntry> scopes = flattenScopes(visual.semanticRoot());
        for (ScopeEntry entry : scopes) {
            if (entry.scope().range() == null) {
                continue;
            }
            BoundsBox bounds = scopeBounds(entry.scope().range(), graph, visual.astRoot());
            if (bounds == null) {
                continue;
            }
            Rectangle mask = new Rectangle(bounds.x() - 34, bounds.y() - 34, bounds.width() + 68, bounds.height() + 68);
            mask.getStyleClass().add("semantic-graph-scope-mask-" + (entry.depth() % 4));
            mask.setOnMouseClicked(event -> {
                selectedSemanticScopeId = entry.scope().id();
                refresh();
                event.consume();
            });
            attachInspectorClick(mask, semanticScopeContent(entry.scope(), entry.depth()));
            if (entry.scope().active()) {
                mask.getStyleClass().add("active-scope-mask");
            }
            if (entry.scope().id().equals(selectedSemanticScopeId)) {
                mask.getStyleClass().add("selected-scope-mask");
            }
            pane.getChildren().add(mask);
        }
    }

    private BoundsBox scopeBounds(UiSourceSpanDto scopeRange, MiniCAstGraphModel graph, UiAstNodeVisualDto root) {
        java.util.ArrayList<MiniCAstGraphNode> covered = new java.util.ArrayList<>();
        collectCoveredGraphNodes(scopeRange, root, graph, covered);
        if (covered.isEmpty()) {
            return null;
        }
        double minX = covered.stream().mapToDouble(MiniCAstGraphNode::x).min().orElse(0);
        double maxX = covered.stream().mapToDouble(MiniCAstGraphNode::x).max().orElse(0);
        double minY = covered.stream().mapToDouble(MiniCAstGraphNode::y).min().orElse(0);
        double maxY = covered.stream().mapToDouble(MiniCAstGraphNode::y).max().orElse(0);
        return new BoundsBox(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private void collectCoveredGraphNodes(
            UiSourceSpanDto scopeRange,
            UiAstNodeVisualDto astNode,
            MiniCAstGraphModel graph,
            java.util.ArrayList<MiniCAstGraphNode> covered
    ) {
        if (astNode.range() != null && contains(scopeRange, astNode.range())) {
            graph.nodes().stream()
                    .filter(node -> node.id().equals(astNode.id()))
                    .findFirst()
                    .ifPresent(covered::add);
        }
        astNode.children().forEach(child -> collectCoveredGraphNodes(scopeRange, child, graph, covered));
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
        value.textProperty().bind(astZoom.valueProperty().multiply(100).asString("%.1f%%"));
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
        configureAstGraphWheelZoom(graphViewport);
        configureAstGraphDrag(graphViewport);
        updateZoomedActiveMarker(box, graph, astZoom.getValue());
        astZoom.valueProperty().addListener((observable, oldValue, newValue) ->
                updateZoomedActiveMarker(box, graph, newValue.doubleValue()));
        box.getChildren().addAll(controls, graphViewport);
        box.setMinWidth(0);
        return box;
    }

    private VBox zoomableSemanticAstGraph(UiStageVisualDto visual) {
        return zoomableAstGraph(visual, true);
    }

    private VBox zoomableAstGraph(UiStageVisualDto visual, boolean semanticMasks) {
        VBox box = new VBox(6);
        box.getStyleClass().add("ast-zoom-box");
        HBox controls = new HBox(8);
        controls.getStyleClass().add("ast-zoom-controls");
        Label title = new Label("Zoom");
        title.getStyleClass().add("ast-zoom-label");
        Label value = new Label();
        value.getStyleClass().add("ast-zoom-value");
        value.textProperty().bind(astZoom.valueProperty().multiply(100).asString("%.1f%%"));
        controls.getChildren().addAll(title, astZoom, value);
        Pane graph = semanticMasks ? semanticAstGraph(visual) : astGraph(visual);
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
        configureAstGraphWheelZoom(graphViewport);
        configureAstGraphDrag(graphViewport);
        updateZoomedActiveMarker(box, graph, astZoom.getValue());
        astZoom.valueProperty().addListener((observable, oldValue, newValue) ->
                updateZoomedActiveMarker(box, graph, newValue.doubleValue()));
        box.getChildren().addAll(controls, graphViewport);
        box.setMinWidth(0);
        return box;
    }

    private void configureAstGraphWheelZoom(Pane graphViewport) {
        graphViewport.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) {
                return;
            }
            setAstZoom(astZoom.getValue() + (event.getDeltaY() > 0 ? AST_ZOOM_STEP : -AST_ZOOM_STEP));
            event.consume();
        });
    }

    private void configureAstGraphDrag(Pane graphViewport) {
        graphViewport.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.SECONDARY) {
                return;
            }
            ScrollPane scrollPane = nearestScrollPane(graphViewport);
            if (scrollPane == null) {
                return;
            }
            graphViewport.getProperties().put(AST_DRAG_START_X_KEY, event.getScreenX());
            graphViewport.getProperties().put(AST_DRAG_START_Y_KEY, event.getScreenY());
            graphViewport.getProperties().put(AST_DRAG_START_H_KEY, scrollPane.getHvalue());
            graphViewport.getProperties().put(AST_DRAG_START_V_KEY, scrollPane.getVvalue());
            event.consume();
        });
        graphViewport.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isSecondaryButtonDown()) {
                return;
            }
            ScrollPane scrollPane = nearestScrollPane(graphViewport);
            if (scrollPane == null) {
                return;
            }
            Object startX = graphViewport.getProperties().get(AST_DRAG_START_X_KEY);
            Object startY = graphViewport.getProperties().get(AST_DRAG_START_Y_KEY);
            Object startH = graphViewport.getProperties().get(AST_DRAG_START_H_KEY);
            Object startV = graphViewport.getProperties().get(AST_DRAG_START_V_KEY);
            if (!(startX instanceof Number x)
                    || !(startY instanceof Number y)
                    || !(startH instanceof Number h)
                    || !(startV instanceof Number v)) {
                return;
            }
            double contentWidth = scrollPane.getContent().getBoundsInLocal().getWidth();
            double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();
            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double maxX = Math.max(1, contentWidth - viewportWidth);
            double maxY = Math.max(1, contentHeight - viewportHeight);
            double deltaX = x.doubleValue() - event.getScreenX();
            double deltaY = y.doubleValue() - event.getScreenY();
            scrollPane.setHvalue(clamp(h.doubleValue() + deltaX / maxX));
            scrollPane.setVvalue(clamp(v.doubleValue() + deltaY / maxY));
            event.consume();
        });
    }

    private ScrollPane nearestScrollPane(Node node) {
        Parent parent = node.getParent();
        while (parent != null) {
            if (parent instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
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
        if (visual == null || visual.assemblyLines().isEmpty()) {
            return List.of(textRow("Assembly not ready", "assembly-row", "assembly-text"));
        }
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
        attachInspectorClick(row, inspectorContent(
                "Assembly line " + line.lineNumber(),
                List.of(
                        "kind: " + line.kind(),
                        "line: " + line.lineNumber(),
                        "section: " + blankValue(line.section()),
                        "label: " + blankValue(line.label()),
                        "text: " + line.text(),
                        rangeLine(line.range())
                ),
                line.range(),
                "This assembly row is emitted by the Windows x64 backend from the current IR/codegen state."
        ));
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

    private List<Label> activeScopeRows(UiStageVisualDto visual) {
        UiSemanticScopeVisualDto activeScope = selectedScope(visual == null ? null : visual.semanticRoot());
        if (activeScope == null) {
            return List.of(monoLabel("No active scope."));
        }
        if (activeScope.symbols().isEmpty()) {
            return List.of(monoLabel(activeScope.label() + " has no symbols yet."));
        }
        return activeScope.symbols().stream()
                .map(this::monoLabel)
                .toList();
    }

    private UiSemanticScopeVisualDto selectedScope(UiSemanticScopeVisualDto root) {
        if (selectedSemanticScopeId != null && !selectedSemanticScopeId.isBlank()) {
            UiSemanticScopeVisualDto selected = scopeById(root, selectedSemanticScopeId);
            if (selected != null) {
                return selected;
            }
            selectedSemanticScopeId = "";
        }
        return activeScope(root);
    }

    private UiSemanticScopeVisualDto scopeById(UiSemanticScopeVisualDto scope, String id) {
        if (scope == null) {
            return null;
        }
        if (scope.id().equals(id)) {
            return scope;
        }
        for (UiSemanticScopeVisualDto child : scope.children()) {
            UiSemanticScopeVisualDto found = scopeById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private UiSemanticScopeVisualDto activeScope(UiSemanticScopeVisualDto scope) {
        if (scope == null) {
            return null;
        }
        if (scope.active()) {
            return scope;
        }
        for (UiSemanticScopeVisualDto child : scope.children()) {
            UiSemanticScopeVisualDto active = activeScope(child);
            if (active != null) {
                return active;
            }
        }
        return null;
    }

    private List<ScopeEntry> flattenScopes(UiSemanticScopeVisualDto root) {
        if (root == null) {
            return List.of();
        }
        java.util.ArrayList<ScopeEntry> scopes = new java.util.ArrayList<>();
        flattenScopes(root, 0, scopes);
        return scopes;
    }

    private void flattenScopes(UiSemanticScopeVisualDto scope, int depth, java.util.ArrayList<ScopeEntry> scopes) {
        scopes.add(new ScopeEntry(scope, depth));
        scope.children().forEach(child -> flattenScopes(child, depth + 1, scopes));
    }

    private boolean contains(UiSourceSpanDto outer, UiSourceSpanDto inner) {
        return outer.sourceName().equals(inner.sourceName())
                && outer.startOffset() <= inner.startOffset()
                && outer.endOffset() >= inner.endOffset();
    }

    private UiAstNodeVisualDto astNodeById(UiAstNodeVisualDto node, String id) {
        if (node == null) {
            return null;
        }
        if (node.id().equals(id)) {
            return node;
        }
        for (UiAstNodeVisualDto child : node.children()) {
            UiAstNodeVisualDto found = astNodeById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private MiniCHoverInspectorContent astNodeContent(UiAstNodeVisualDto node) {
        if (node == null) {
            return MiniCHoverInspectorContent.empty();
        }
        return inspectorContent(
                "AST node " + node.kind(),
                List.of(
                        "id: " + node.id(),
                        "kind: " + node.kind(),
                        "label: " + node.label(),
                        "children: " + node.children().size(),
                        "active: " + node.active(),
                        rangeLine(node.range())
                ),
                node.range(),
                "This AST node is the parsed syntax structure for the highlighted source span."
        );
    }

    private MiniCHoverInspectorContent semanticScopeContent(UiSemanticScopeVisualDto scope, int depth) {
        if (scope == null) {
            return MiniCHoverInspectorContent.empty();
        }
        return inspectorContent(
                "Semantic scope " + scope.label(),
                List.of(
                        "id: " + scope.id(),
                        "depth: " + depth,
                        "active: " + scope.active(),
                        "symbols: " + scope.symbols().size(),
                        rangeLine(scope.range())
                ),
                scope.range(),
                "Semantic scope details are shown in the right side of the stage view; this panel only shows scope metadata and source position."
        );
    }

    private MiniCHoverInspectorContent inspectorContent(
            String title,
            List<String> metadata,
            UiSourceSpanDto range,
            String explanation
    ) {
        return new MiniCHoverInspectorContent(title, metadata, viewModel.sourceTextProperty().get(), range, explanation);
    }

    private void attachInspectorClick(Node node, MiniCHoverInspectorContent content) {
        node.setOnMouseClicked(event -> {
            hoverInspector.show(content);
            event.consume();
        });
    }

    private String rangeLine(UiSourceSpanDto range) {
        if (range == null) {
            return "source range: unavailable";
        }
        return "source range: " + range.sourceName()
                + " " + range.startLine() + ":" + range.startColumn()
                + " - " + range.endLine() + ":" + range.endColumn()
                + " offsets " + range.startOffset() + ".." + range.endOffset();
    }

    private String blankValue(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private String displayTokenText(UiLexerTokenVisualDto token) {
        return token.text().isEmpty() ? "<EOF>" : token.text();
    }

    private record ScopeEntry(UiSemanticScopeVisualDto scope, int depth) {
    }

    private record BoundsBox(double x, double y, double width, double height) {
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
                    attachInspectorClick(row, inspectorContent(
                            "Token " + token.kind(),
                            List.of(
                                    "kind: " + token.kind(),
                                    "text: " + displayTokenText(token),
                                    "offset: " + token.startOffset() + ".." + token.endOffset(),
                                    "position: " + token.startLine() + ":" + token.startColumn()
                                            + " - " + token.endLine() + ":" + token.endColumn()
                            ),
                            token.range(),
                            "Lexer token details. The lexer view already shows the token's source mapping, so this panel only repeats compact metadata."
                    ));
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
            scrollPane.setHvalue(0);
            root.getChildren().addAll(title, scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
            if (autoCenter) {
                scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> centerActiveLater());
            }
        }

        private void setContent(String titleText, List<? extends Node> rows) {
            title.setText(titleText);
            body.getChildren().setAll(rows);
            stabilizeHorizontalOrigin();
            if (autoCenter) {
                centerActiveLater();
            }
        }

        private void stabilizeHorizontalOrigin() {
            scrollPane.setHvalue(0);
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
