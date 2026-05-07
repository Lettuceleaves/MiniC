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
    private final Label header = new Label("图形视图");
    private final SplitPane splitPane = new SplitPane();
    private final StageColumn leftColumn = new StageColumn("left", false);
    private final StageColumn rightColumn = new StageColumn("right", true);
    private final Slider astZoom = new Slider(MIN_AST_ZOOM, MAX_AST_ZOOM, DEFAULT_AST_ZOOM);
    private final TextArea executionStdin = new TextArea();
    private final CheckBox executionNoInput = new CheckBox("无输入");
    private final Button executionConfirm = new Button("确认输入");
    private String selectedSemanticScopeId = "";
    private boolean refreshScheduled;
    private String activeVisualStage = "pending";

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
        activeVisualStage = stage;
        if (!"semantic".equals(stage)) {
            selectedSemanticScopeId = "";
        }
        header.setText("图形视图 · " + stageName(stage) + (stage.equals(currentStage) ? "" : " · 快照"));
        UiStageVisualDto visual = visualForStage(stage);
        if (visual == null) {
            leftColumn.setContent(stage, fallbackRows());
            rightColumn.setContent("输出", List.of());
            return;
        }
        switch (stage) {
            case "preprocess" -> {
                leftColumn.setContent("源码", sourceRows(null));
                rightColumn.setContent("预处理后产物", preprocessRows());
            }
            case "lexer" -> {
                leftColumn.setContent("预处理后产物", sourceRows(visual));
                rightColumn.setContent("Token", tokenRows(visual));
            }
            case "parser" -> {
                leftColumn.setContent("Token", tokenRows(viewModel.lexerVisualDataProperty().get()));
                rightColumn.setContent("AST", List.of(zoomableAstGraph(visual)));
            }
            case "semantic" -> {
                leftColumn.setContent("AST", List.of(zoomableSemanticAstGraph(visual)));
                rightColumn.setContent("作用域", activeScopeRows(visual));
            }
            case "codegen" -> {
                leftColumn.setContent("IR", codegenIrRows(visual));
                rightColumn.setContent("汇编", assemblyRows(visual));
            }
            case "source" -> {
                leftColumn.setContent("源码", sourceRows(null));
                rightColumn.setContent("输出", List.of(monoLabel("源码已加载。")));
            }
            case "ir" -> {
                leftColumn.setContent("AST", List.of(zoomableSemanticAstGraph(visual)));
                if (selectedSemanticScopeId == null || selectedSemanticScopeId.isBlank()) {
                    rightColumn.setContent("IR", globalRows(stage));
                } else {
                    rightColumn.setContent("作用域", activeScopeRows(visual));
                }
            }
            case "toolchain" -> {
                leftColumn.setContent("汇编", assemblyRows(visualForStage("codegen")));
                rightColumn.setContent("工具链", globalRows(stage));
            }
            case "execution" -> {
                leftColumn.setContent("STDIN", List.of(executionInputPane()));
                rightColumn.setContent("输出", executionOutputRows());
            }
            default -> {
                leftColumn.setContent(stage, fallbackRows());
                rightColumn.setContent("输出", List.of());
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

    private String stageName(String stage) {
        return switch (stage) {
            case "source" -> "源码";
            case "preprocess" -> "预编译";
            case "lexer" -> "词法分析";
            case "parser" -> "语法分析";
            case "semantic" -> "语义分析";
            case "ir" -> "IR 降级";
            case "codegen" -> "代码生成";
            case "toolchain" -> "工具链";
            case "execution" -> "执行";
            case "pending" -> "等待中";
            default -> stage;
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
        box.getChildren().add(section("作用域", semanticRows(viewModel.semanticVisualDataProperty().get())));
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
            return List.of(monoLabel("执行输出会显示在这里。"));
        }
        return viewModel.globalDataProperty().get().executionOutputSummary().stream()
                .map(this::monoLabel)
                .toList();
    }

    private List<Label> globalRows(String stage) {
        if (viewModel.globalDataProperty().get() == null) {
            return List.of(monoLabel("暂无数据。"));
        }
        List<String> rows = switch (stage) {
            case "ir" -> viewModel.globalDataProperty().get().irSummary();
            case "toolchain" -> viewModel.globalDataProperty().get().artifactSummary();
            default -> List.of();
        };
        if (rows.isEmpty()) {
            return List.of(monoLabel(stageName(stage) + " 暂无输出。"));
        }
        return rows.stream().map(this::monoLabel).toList();
    }

    private List<Label> preprocessRows() {
        UiStageVisualDto visual = visualForStage("preprocess");
        if (visual == null || visual.genericItems().isEmpty()) {
            return List.of(monoLabel("预处理产物会显示在这里。"));
        }
        return visual.genericItems().stream()
                .map(this::monoLabel)
                .toList();
    }

    private List<HBox> codegenIrRows(UiStageVisualDto codegenVisual) {
        if (codegenVisual == null || codegenVisual.irLines().isEmpty()) {
            return List.of(textRow("IR 暂无输出。", "assembly-row", "assembly-text"));
        }
        return codegenVisual.irLines().stream()
                .map(line -> irRow(line, codegenVisual))
                .toList();
    }

    private HBox irRow(UiIrLineVisualDto line, UiStageVisualDto visual) {
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
                "IR 行 " + line.lineNumber(),
                List.of(
                        "类型: IR",
                        "行号: " + line.lineNumber(),
                        "文本: " + line.text(),
                        rangeLine(line.range())
                ),
                line.range(),
                explainIrLine(line),
                visual
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
        String source = visual == null || visual.sourceText().isBlank()
                ? viewModel.sourceTextProperty().get()
                : visual.sourceText();
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
            return emptyPane("AST 尚未就绪");
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
            MiniCHoverInspectorContent content = astNodeContent(astNode, visual);
            attachInspectorClick(circle, content);
            attachInspectorClick(text, content);
            pane.getChildren().addAll(circle, text);
        });
        return pane;
    }

    private Pane semanticAstGraph(UiStageVisualDto visual) {
        if (visual == null || visual.astRoot() == null) {
            return emptyPane("AST 尚未就绪");
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
            MiniCHoverInspectorContent content = astNodeContent(astNode, visual);
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
            attachInspectorClick(mask, semanticScopeContent(entry.scope(), entry.depth(), visual));
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
        Label title = new Label("缩放");
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
        graphViewport.setMinSize(baseWidth, baseHeight);
        graphViewport.setPrefSize(baseWidth, baseHeight);
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
        Label title = new Label("缩放");
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
        graphViewport.setMinSize(baseWidth, baseHeight);
        graphViewport.setPrefSize(baseWidth, baseHeight);
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
            return List.of(textRow("汇编尚未就绪", "assembly-row", "assembly-text"));
        }
        return assemblyTextModelFactory.create(visual).stream()
                .map(line -> assemblyRow(line, visual))
                .toList();
    }

    private HBox assemblyRow(MiniCAssemblyTextLine line, UiStageVisualDto visual) {
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
                "汇编行 " + line.lineNumber(),
                List.of(
                        "类型: " + line.kind(),
                        "行号: " + line.lineNumber(),
                        "段: " + blankValue(line.section()),
                        "标签: " + blankValue(line.label()),
                        "文本: " + line.text(),
                        rangeLine(line.range())
                ),
                line.range(),
                explainAssemblyLine(line),
                visual
        ));
        return row;
    }

    private List<HBox> semanticRows(UiStageVisualDto visual) {
        if (visual == null || visual.semanticRoot() == null) {
            return List.of(textRow("作用域尚未就绪", "semantic-row", "semantic-scope-line"));
        }
        return semanticScopeTreeModelFactory.create(visual).stream()
                .map(this::semanticRow)
                .toList();
    }

    private List<Label> activeScopeRows(UiStageVisualDto visual) {
        UiSemanticScopeVisualDto activeScope = selectedScope(visual == null ? null : visual.semanticRoot());
        if (activeScope == null) {
            return List.of(monoLabel("暂无活动作用域。"));
        }
        if (activeScope.symbols().isEmpty()) {
            return List.of(monoLabel(activeScope.label() + " 暂无符号。"));
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

    private MiniCHoverInspectorContent astNodeContent(UiAstNodeVisualDto node, UiStageVisualDto visual) {
        if (node == null) {
            return MiniCHoverInspectorContent.empty();
        }
        return inspectorContent(
                "AST 节点 " + node.kind(),
                List.of(
                        "id: " + node.id(),
                        "类型: " + node.kind(),
                        "标签: " + node.label(),
                        "子节点数: " + node.children().size(),
                        "当前节点: " + yesNo(node.active()),
                        rangeLine(node.range())
                ),
                node.range(),
                explainAstNode(node),
                visual
        );
    }

    private MiniCHoverInspectorContent semanticScopeContent(UiSemanticScopeVisualDto scope, int depth, UiStageVisualDto visual) {
        if (scope == null) {
            return MiniCHoverInspectorContent.empty();
        }
        return inspectorContent(
                "语义作用域 " + scope.label(),
                List.of(
                        "id: " + scope.id(),
                        "深度: " + depth,
                        "当前作用域: " + yesNo(scope.active()),
                        "符号数: " + scope.symbols().size(),
                        rangeLine(scope.range())
                ),
                scope.range(),
                "语义阶段右侧已经展示该作用域内的变量和符号，这里只显示作用域元数据与源码位置。",
                visual
        );
    }

    private MiniCHoverInspectorContent inspectorContent(
            String title,
            List<String> metadata,
            UiSourceSpanDto range,
            String explanation
    ) {
        return inspectorContent(title, metadata, range, explanation, null);
    }

    private MiniCHoverInspectorContent inspectorContent(
            String title,
            List<String> metadata,
            UiSourceSpanDto range,
            String explanation,
            UiStageVisualDto visual
    ) {
        return new MiniCHoverInspectorContent(title, metadata, sourceTextForRange(range, visual), range, explanation);
    }

    private String sourceTextForRange(UiSourceSpanDto range, UiStageVisualDto preferredVisual) {
        String preferredSource = sourceTextFromVisual(range, preferredVisual);
        if (!preferredSource.isBlank()) {
            return preferredSource;
        }
        for (UiStageVisualDto visual : new UiStageVisualDto[]{
                viewModel.currentStageVisualDataProperty().get(),
                viewModel.semanticVisualDataProperty().get(),
                viewModel.astVisualDataProperty().get(),
                viewModel.lexerVisualDataProperty().get(),
                viewModel.codegenVisualDataProperty().get()
        }) {
            String source = sourceTextFromVisual(range, visual);
            if (!source.isBlank()) {
                return source;
            }
        }
        return viewModel.sourceTextProperty().get();
    }

    private String sourceTextFromVisual(UiSourceSpanDto range, UiStageVisualDto visual) {
        if (visual == null || visual.sourceText().isBlank()) {
            return "";
        }
        if (range == null || visualContainsSourceName(visual, range.sourceName())) {
            return visual.sourceText();
        }
        return "";
    }

    private boolean visualContainsSourceName(UiStageVisualDto visual, String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return true;
        }
        return visual.lexerTokens().stream().anyMatch(token -> sameSource(token.range(), sourceName))
                || astContainsSourceName(visual.astRoot(), sourceName)
                || scopeContainsSourceName(visual.semanticRoot(), sourceName)
                || visual.irLines().stream().anyMatch(line -> sameSource(line.range(), sourceName))
                || visual.assemblyLines().stream().anyMatch(line -> sameSource(line.range(), sourceName));
    }

    private boolean astContainsSourceName(UiAstNodeVisualDto node, String sourceName) {
        if (node == null) {
            return false;
        }
        if (sameSource(node.range(), sourceName)) {
            return true;
        }
        return node.children().stream().anyMatch(child -> astContainsSourceName(child, sourceName));
    }

    private boolean scopeContainsSourceName(UiSemanticScopeVisualDto scope, String sourceName) {
        if (scope == null) {
            return false;
        }
        if (sameSource(scope.range(), sourceName)) {
            return true;
        }
        return scope.children().stream().anyMatch(child -> scopeContainsSourceName(child, sourceName));
    }

    private boolean sameSource(UiSourceSpanDto range, String sourceName) {
        return range != null && range.sourceName().equals(sourceName);
    }

    private void attachInspectorClick(Node node, MiniCHoverInspectorContent content) {
        node.setOnMouseClicked(event -> {
            hoverInspector.show(content);
            event.consume();
        });
    }

    private String rangeLine(UiSourceSpanDto range) {
        if (range == null) {
            return "源码范围: 不可用";
        }
        return "源码范围: " + range.sourceName()
                + " " + range.startLine() + ":" + range.startColumn()
                + " - " + range.endLine() + ":" + range.endColumn()
                + " offset " + range.startOffset() + ".." + range.endOffset();
    }

    private String blankValue(String value) {
        return value == null || value.isBlank() ? "<无>" : value;
    }

    private String displayTokenText(UiLexerTokenVisualDto token) {
        return token.text().isEmpty() ? "<EOF>" : token.text();
    }

    private String yesNo(boolean value) {
        return value ? "是" : "否";
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
            return List.of(textRow("Token 尚未就绪", "token-row", "token-text"));
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
                                    "类型: " + token.kind(),
                                    "文本: " + displayTokenText(token),
                                    "offset: " + token.startOffset() + ".." + token.endOffset(),
                                    "位置: " + token.startLine() + ":" + token.startColumn()
                                            + " - " + token.endLine() + ":" + token.endColumn()
                            ),
                            token.range(),
                            explainToken(token),
                            visual
                    ));
                    return row;
                })
                .toList();
    }

    private String explainToken(UiLexerTokenVisualDto token) {
        String text = displayTokenText(token);
        String role = tokenRole(token.kind());
        return "词法阶段把源码字符流切成 token。当前 token 是 " + token.kind()
                + "，文本为 `" + text + "`，源码位置是 " + token.startLine() + ":" + token.startColumn()
                + " 到 " + token.endLine() + ":" + token.endColumn()
                + "，offset 范围是 " + token.startOffset() + ".." + token.endOffset()
                + "。\n\n解释: " + role
                + "\n\n用途: token 是 parser 的输入。只要看清当前 token 的类别、文本和位置，就能理解 AST 为什么会在这里创建某个节点，或者为什么语法错误会落在这一段源码上。";
    }

    private String tokenRole(String kind) {
        if (isTypeKeyword(kind)) {
            return "这是类型关键字，用来声明变量、函数返回值或形参类型。parser 会把它放进声明节点，语义分析会用它检查赋值、返回值和函数调用是否类型匹配。";
        }
        if (isControlKeyword(kind)) {
            return "这是控制流关键字，决定 parser 构造哪类语句节点。例如 return 产生返回语句，if/else 产生分支，for/while 产生循环，break/continue 约束在循环作用域内。";
        }
        if ("EXTERN".equals(kind)) {
            return "extern 表示外部声明。它告诉编译器该函数或符号由外部目标文件/运行库提供，语义分析登记签名，代码生成只产生调用引用。";
        }
        return switch (kind) {
            case "IDENTIFIER" -> "标识符是用户定义的名字，后续语义分析会把它解析为变量、函数或类型，并检查是否已经声明、是否在当前作用域可见。";
            case "INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL", "CHAR_LITERAL", "BOOL_LITERAL", "NULL_LITERAL" ->
                    "字面量是源码中直接写出的值，会在 AST 中形成常量表达式，后续 IR 通常把它变成立即数、常量地址或空指针值。";
            case "STRING_LITERAL" -> "字符串字面量会作为连续字符数据保存，调用 printf 这类外部函数时通常作为格式串地址传入。";
            case "PLUS", "MINUS", "STAR", "SLASH", "PERCENT", "EQUAL", "PLUS_EQUAL", "MINUS_EQUAL", "PLUS_PLUS", "MINUS_MINUS",
                    "EQUAL_EQUAL", "BANG_EQUAL", "LESS", "LESS_EQUAL", "GREATER", "GREATER_EQUAL", "AMPERSAND", "BANG", "DOT" ->
                    "这是运算符 token，描述表达式或语句之间的动作。parser 会依据优先级和结合性组织表达式树，语义分析再检查操作数类型。";
            case "LEFT_PAREN", "RIGHT_PAREN", "LEFT_BRACE", "RIGHT_BRACE", "LEFT_BRACKET", "RIGHT_BRACKET", "COMMA", "SEMICOLON" ->
                    "这是结构/分隔符 token，用来限定参数列表、语句块、数组/下标或语句边界。它主要决定 AST 的层级和边界。";
            case "EOF" -> "EOF 是源码结束标记，不对应真实字符，用来告诉 parser 输入已经耗尽。";
            default -> "该 token 是 lexer 从字符流中切分出的最小语法单元，parser 不再直接看原始字符，而是消费这些 token。";
        };
    }

    private boolean isTypeKeyword(String kind) {
        return switch (kind) {
            case "VOID", "BOOL", "CHAR", "INT", "LONG", "FLOAT", "DOUBLE", "STRUCT" -> true;
            default -> false;
        };
    }

    private boolean isControlKeyword(String kind) {
        return switch (kind) {
            case "RETURN", "IF", "ELSE", "WHILE", "FOR", "BREAK", "CONTINUE" -> true;
            default -> false;
        };
    }

    private String explainAstNode(UiAstNodeVisualDto node) {
        String role = switch (node.kind()) {
            case "Program" -> "Program 是整棵语法树的根，收集所有顶层声明。后续语义分析和 IR lowering 都从这里开始遍历。";
            case "FunctionDecl" -> "FunctionDecl 表示函数声明或定义，包含返回类型、函数名、参数列表和可选函数体。它会进入符号表，并在 IR 阶段变成一个函数单元。";
            case "Parameter" -> "Parameter 表示函数形参。语义分析会把它加入函数体作用域，函数调用检查也会用它验证实参与形参是否匹配。";
            case "BlockStmt" -> "BlockStmt 表示一对花括号包围的语句块。它通常创建新的作用域，局部变量只在这个块及其子块内可见。";
            case "VarDeclStmt" -> "VarDeclStmt 表示局部变量声明。语义分析会检查重名和初始化表达式类型，IR 阶段会为变量分配临时值或栈位置。";
            case "ReturnStmt" -> "ReturnStmt 表示函数返回。语义分析会检查返回值类型是否匹配函数返回类型，IR/ASM 会生成返回值传递和退出序列。";
            case "IfStmt" -> "IfStmt 表示条件分支。IR 阶段会把它降成条件跳转和基本块。";
            case "ForStmt" -> "ForStmt 表示 for 循环，包含初始化、条件、步进和循环体。IR 阶段会把它拆成循环入口、条件判断、循环体、步进和退出跳转。";
            case "WhileStmt" -> "WhileStmt 表示 while 循环，IR 阶段会生成条件判断和回边跳转。";
            case "ExprStmt" -> "ExprStmt 表示把表达式当作语句执行，例如函数调用或赋值。它通常关注副作用而不是最终值。";
            case "BinaryExpr" -> "BinaryExpr 表示二元表达式，左右子节点分别是操作数。语义分析会检查两侧类型，IR 阶段会生成对应计算或比较指令。";
            case "UnaryExpr" -> "UnaryExpr 表示单目表达式，例如取负、取地址、解引用或自增自减。它会影响值类别和后续代码生成方式。";
            case "CallExpr" -> "CallExpr 表示函数调用。语义分析会解析被调用函数并检查参数数量/类型，代码生成会按调用约定传参。";
            case "NameExpr" -> "NameExpr 表示一个名字引用。语义分析会在作用域链里查找它对应的符号，IR 阶段再读取或写入该符号。";
            case "IntegerLiteralExpr" -> "IntegerLiteralExpr 表示整数常量，通常直接降低为 IR 立即数。";
            case "StringLiteralExpr" -> "StringLiteralExpr 表示字符串常量，后端会把它放入数据区，并把地址传给使用它的表达式。";
            default -> "该节点表示源码中的一个语法结构。父子关系说明 parser 如何把线性的 token 串组织成可遍历的树。";
        };
        return "AST 阶段把 token 串组织成树。当前节点是 " + node.kind()
                + "，显示标签为 `" + node.label() + "`，id 为 `" + node.id() + "`，子节点数 "
                + node.children().size() + "，当前是否正在处理: " + yesNo(node.active()) + "。"
                + "\n\n解释: " + role
                + "\n\n用途: AST 是语义分析和 IR lowering 的共同输入。看这个节点的类型、标签、子节点和源码遮罩，就能知道编译器当前把哪一段源码理解成了什么语法结构。";
    }

    private String explainIrLine(UiIrLineVisualDto line) {
        String text = line.text();
        String lower = text.toLowerCase(java.util.Locale.ROOT).trim();
        String role;
        if (lower.contains("call")) {
            role = "call 表示函数调用。它把已经求值的参数交给目标函数，并把返回值保存到临时值或忽略。";
        } else if (lower.contains("ret") || lower.startsWith("return")) {
            role = "return/ret 表示当前函数结束并返回结果，后端会把返回值放到平台约定的位置。";
        } else if (lower.contains("br") || lower.contains("jump")) {
            role = "br/jump 表示控制流跳转，用来连接 if、for、while 这类结构产生的基本块。";
        } else if (lower.contains("cmp") || lower.contains("<") || lower.contains(">") || lower.contains("==")) {
            role = "比较类 IR 会计算条件值，后续通常接条件跳转，用于分支和循环判断。";
        } else if (lower.contains("store") || lower.contains("=")) {
            role = "赋值/存储类 IR 把右侧计算结果写入变量、临时值或内存位置，是变量状态变化的来源。";
        } else if (lower.contains("load")) {
            role = "load 表示读取变量或内存中的值，后续计算使用的是读取出的临时值。";
        } else {
            role = "该 IR 行是后端前的中间表示，把 AST 中较复杂的语法结构拆成更接近机器执行顺序的小步骤。";
        }
        return "IR 阶段把 AST 降成更线性的中间表示。当前是第 " + line.lineNumber()
                + " 行，文本为 `" + text + "`。"
                + "\n\n解释: " + role
                + "\n\n用途: IR 抹平了很多语法细节，让后端可以按顺序生成汇编。结合左侧源码范围和这一行文本，可以看出某个 AST 节点最终产生了哪一步计算、跳转、调用或返回。";
    }

    private String explainAssemblyLine(MiniCAssemblyTextLine line) {
        String text = line.text().trim();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String role;
        if (line.kind().equals("LABEL") || text.endsWith(":")) {
            role = "标签是汇编中的跳转目标，不直接执行计算。条件分支、循环和函数入口会跳到这些名字。";
        } else if (lower.startsWith("mov")) {
            role = "mov 负责复制数据，例如把立即数、寄存器值或内存值移动到目标寄存器/内存。";
        } else if (lower.startsWith("add") || lower.startsWith("sub") || lower.startsWith("imul")) {
            role = "算术指令执行整数计算，通常对应源码里的加减乘或栈指针调整。";
        } else if (lower.startsWith("cmp") || lower.startsWith("test")) {
            role = "cmp/test 设置 CPU 标志位，后续条件跳转会根据这些标志决定走哪条路径。";
        } else if (lower.startsWith("j")) {
            role = "j* 是跳转指令。无条件跳转用于连接基本块，条件跳转用于 if/循环判断。";
        } else if (lower.startsWith("call")) {
            role = "call 调用函数。Windows x64 下前几个参数通常放在 rcx、rdx、r8、r9，返回值通常在 rax。";
        } else if (lower.startsWith("ret")) {
            role = "ret 从当前函数返回到调用者，结束当前函数的机器级执行。";
        } else if (lower.startsWith("push") || lower.startsWith("pop")) {
            role = "push/pop 操作栈，用于保存/恢复寄存器或维护调用帧。";
        } else {
            role = "该汇编行是最终交给汇编器的低层指令或伪指令，直接决定生成的目标文件内容。";
        }
        return "汇编阶段把 IR 翻译成 Windows x64 汇编。当前第 " + line.lineNumber()
                + " 行，类型 `" + line.kind() + "`，段 `" + blankValue(line.section())
                + "`，标签 `" + blankValue(line.label()) + "`，文本为 `" + line.text() + "`。"
                + "\n\n解释: " + role
                + "\n\n用途: 汇编是生成 exe 前最接近机器执行的表示。看这一行可以知道源码/IR 最终变成了寄存器移动、算术、比较、跳转、函数调用还是返回。";
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
        private final String columnId;
        private final VBox root = new VBox(6);
        private final Label title = new Label();
        private final VBox body = new VBox(4);
        private final ScrollPane scrollPane = new ScrollPane(body);
        private final boolean autoCenter;
        private String viewportKey = "";
        private boolean restoringViewport;
        private boolean hasSavedViewport;

        private StageColumn(String columnId, boolean autoCenter) {
            this.columnId = columnId;
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
            scrollPane.hvalueProperty().addListener((observable, oldValue, newValue) -> saveViewport());
            scrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> saveViewport());
            if (autoCenter) {
                scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> centerActiveLater());
            }
        }

        private void setContent(String titleText, List<? extends Node> rows) {
            title.setText(titleText);
            viewportKey = "visual:" + activeVisualStage + ":" + columnId + ":" + titleText;
            hasSavedViewport = hasSavedViewport(viewportKey);
            body.getChildren().setAll(rows);
            restoreViewportLater();
            if (autoCenter && !hasSavedViewport) {
                centerActiveLater();
            }
        }

        private boolean hasSavedViewport(String key) {
            MiniCWorkbenchViewModel.UiViewportState state = viewModel.viewportState(key);
            return state.hvalue() != 0.0 || state.vvalue() != 0.0;
        }

        private void restoreViewportLater() {
            Platform.runLater(() -> {
                MiniCWorkbenchViewModel.UiViewportState state = viewModel.viewportState(viewportKey);
                restoringViewport = true;
                try {
                    scrollPane.setHvalue(state.hvalue());
                    scrollPane.setVvalue(state.vvalue());
                } finally {
                    restoringViewport = false;
                }
            });
        }

        private void saveViewport() {
            if (restoringViewport || viewportKey.isBlank()) {
                return;
            }
            viewModel.saveViewportState(viewportKey, scrollPane.getHvalue(), scrollPane.getVvalue());
        }

        private void centerActiveLater() {
            Platform.runLater(this::centerActive);
        }

        private void centerActive() {
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double contentHeight = body.getBoundsInLocal().getHeight();
            if (viewportHeight <= 0 || contentHeight <= viewportHeight) {
                if (!hasSavedViewport) {
                    scrollPane.setVvalue(0);
                }
                return;
            }
            Double activeCenterY = activeCenterY();
            if (activeCenterY == null) {
                return;
            }
            double targetTop = activeCenterY - viewportHeight / 2.0;
            double maxTop = contentHeight - viewportHeight;
            double clampedTop = Math.max(0, Math.min(targetTop, maxTop));
            restoringViewport = true;
            try {
                scrollPane.setVvalue(clampedTop / maxTop);
            } finally {
                restoringViewport = false;
            }
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
