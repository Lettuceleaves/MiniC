package minic.uilocal;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import minic.uilocal.control.MiniCControlTargetType;
import minic.uilocal.control.MiniCViewportAdapter;
import minic.uilocal.control.MiniCWorkbenchControlHub;
import minic.uilocal.text.MiniCAssemblyTextHighlighter;
import minic.uilocal.text.MiniCIrTextHighlighter;
import minic.uilocal.text.MiniCTextFlowFactory;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiAssemblyLineVisualDto;
import minic.uiapi.UiIrLineVisualDto;
import minic.uiapi.UiSemanticScopeVisualDto;
import minic.uiapi.UiSourceSpanDto;
import minic.uiapi.UiStageVisualDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 当前阶段结构化可视化区域。
 */
public final class MiniCVisualPane extends VBox {
    private static final String ACTIVE_CENTER_Y_KEY = "activeCenterY";
    private static final double DEFAULT_AST_ZOOM = 1.0;
    private static final double MIN_AST_ZOOM = 0.05;
    private static final double MAX_AST_ZOOM = 1.0;
    private static final double AST_ZOOM_STEP = 0.025;
    private static final String STAGE_SCROLL_FILTER_INSTALLED_KEY =
            "minic.uilocal.visual.stageScrollFilterInstalled";

    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCHoverInspector hoverInspector;
    private final MiniCVisualModelFactory modelFactory = new MiniCVisualModelFactory();
    private final MiniCSemanticScopeTreeModelFactory semanticScopeTreeModelFactory = new MiniCSemanticScopeTreeModelFactory();
    private final MiniCAssemblyTextModelFactory assemblyTextModelFactory = new MiniCAssemblyTextModelFactory();
    private final MiniCIrTextHighlighter irTextHighlighter = new MiniCIrTextHighlighter();
    private final MiniCAssemblyTextHighlighter assemblyTextHighlighter = new MiniCAssemblyTextHighlighter();
    private final MiniCVisualExplanationFormatter explanationFormatter;
    private final MiniCVisualAstGraphRenderer astGraphRenderer;
    private final Label header = new Label("图形视图");
    private final SplitPane splitPane = new SplitPane();
    private final StageColumn leftColumn = new StageColumn("left", false);
    private final StageColumn rightColumn = new StageColumn("right", true);
    private final Slider astZoom = new Slider(MIN_AST_ZOOM, MAX_AST_ZOOM, DEFAULT_AST_ZOOM);
    private final TextArea executionStdin = new TextArea();
    private MiniCWorkbenchControlHub controlHub;
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
        this.explanationFormatter = new MiniCVisualExplanationFormatter(range -> sourceSnippetForRange(range, null));
        this.astGraphRenderer = new MiniCVisualAstGraphRenderer(
                astZoom,
                () -> controlHub,
                () -> selectedSemanticScopeId,
                id -> selectedSemanticScopeId = id,
                this::refresh,
                this::astNodeContent,
                this::semanticScopeContent,
                this::attachInspectorClick
        );
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
                rightColumn.setContent("AST", List.of(astGraphRenderer.zoomableAstGraph(visual)));
            }
            case "semantic" -> {
                leftColumn.setContent("AST", List.of(astGraphRenderer.zoomableSemanticAstGraph(visual)));
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
                leftColumn.setContent("AST", List.of(astGraphRenderer.zoomableSemanticAstGraph(visual)));
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

    public void zoomAstIn() {
        setAstZoom(astZoom.getValue() + astGraphRenderer.graphZoomStep());
    }

    public void zoomAstOut() {
        setAstZoom(astZoom.getValue() - astGraphRenderer.graphZoomStep());
    }

    public void installViewportTargets(MiniCWorkbenchControlHub controlHub) {
        this.controlHub = Objects.requireNonNull(controlHub, "controlHub");
        leftColumn.installViewportTarget(controlHub);
        rightColumn.installViewportTarget(controlHub);
    }

    /**
     * 返回当前可参与 active tracking 的视口适配器。
     *
     * @return 视口适配器列表
     */
    public List<MiniCViewportAdapter> activeViewportAdapters() {
        ArrayList<MiniCViewportAdapter> adapters = new ArrayList<>();
        adapters.add(leftColumn.viewportAdapter);
        adapters.add(rightColumn.viewportAdapter);
        astGraphRenderer.collectGraphViewportAdapters(this, adapters);
        return adapters;
    }

    private void setAstZoom(double value) {
        astZoom.setValue(Math.max(MIN_AST_ZOOM, Math.min(MAX_AST_ZOOM, value)));
    }

    private VBox astScopeInput() {
        VBox box = new VBox(10);
        box.getStyleClass().add("asm-input-stack");
        box.getChildren().add(section("AST", List.of(astGraphRenderer.zoomableAstGraph(viewModel.astVisualDataProperty().get()))));
        box.getChildren().add(section("作用域", semanticRows(viewModel.semanticVisualDataProperty().get())));
        return box;
    }

    private void configureExecutionInputControls() {
        executionStdin.getStyleClass().add("execution-stdin");
        executionStdin.setWrapText(false);
        executionStdin.setText(viewModel.executionInputDraft());
        executionStdin.textProperty().addListener((observable, oldValue, newValue) ->
                viewModel.updateExecutionInputDraft(newValue));
    }

    private VBox executionInputPane() {
        VBox box = new VBox(8);
        boolean completed = viewModel.currentStageDataProperty().get() != null
                && viewModel.currentStageDataProperty().get().completed();
        boolean confirmed = viewModel.globalDataProperty().get() != null
                && viewModel.globalDataProperty().get().executionInputConfirmed();
        executionStdin.setDisable(completed || confirmed);
        box.getChildren().add(executionStdin);
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
        TextFlow text = MiniCTextFlowFactory.textFlow(
                irTextHighlighter.highlight(line.text()),
                "assembly-text",
                line.active()
        );
        if (line.active()) {
            row.getStyleClass().add("active");
            number.getStyleClass().add("active");
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        row.getChildren().addAll(number, text);
        attachInspectorClick(row, inspectorContent(
                "IR 行 " + line.lineNumber(),
                List.of(
                        "类型: IR",
                        "行号: " + line.lineNumber(),
                        "文本: " + line.text(),
                        explanationFormatter.rangeLine(line.range())
                ),
                line.range(),
                explanationFormatter.explainIrLine(line),
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
        return MiniCVisualSourceRows.rows(viewModel.sourceTextProperty().get(), visual);
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
        TextFlow text = MiniCTextFlowFactory.textFlow(
                assemblyTextHighlighter.highlight(line.text()),
                "assembly-text",
                line.active()
        );
        if (line.active()) {
            row.getStyleClass().add("active");
            number.getStyleClass().add("active");
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        row.getChildren().addAll(number, text);
        attachInspectorClick(row, inspectorContent(
                "汇编行 " + line.lineNumber(),
                List.of(
                        "类型: " + line.kind(),
                        "行号: " + line.lineNumber(),
                        "段: " + explanationFormatter.blankValue(line.section()),
                        "标签: " + explanationFormatter.blankValue(line.label()),
                        "文本: " + line.text(),
                        explanationFormatter.rangeLine(line.range())
                ),
                line.range(),
                explanationFormatter.explainAssemblyLine(line),
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
                        "当前节点: " + explanationFormatter.yesNo(node.active()),
                        explanationFormatter.rangeLine(node.range())
                ),
                node.range(),
                explanationFormatter.explainAstNode(node),
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
                        "当前作用域: " + explanationFormatter.yesNo(scope.active()),
                        "符号数: " + scope.symbols().size(),
                        explanationFormatter.rangeLine(scope.range())
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

    private String sourceSnippetForRange(UiSourceSpanDto range, UiStageVisualDto preferredVisual) {
        if (range == null) {
            return "<暂无源码片段>";
        }
        String source = sourceTextForRange(range, preferredVisual);
        if (source.isBlank()) {
            return "<暂无源码片段>";
        }
        int start = Math.max(0, Math.min(range.startOffset(), source.length()));
        int end = Math.max(start, Math.min(range.endOffset(), source.length()));
        String snippet = source.substring(start, end).strip();
        if (snippet.isBlank()) {
            return "<暂无源码片段>";
        }
        return snippet.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(" "));
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
                                    "文本: " + explanationFormatter.displayTokenText(token),
                                    "offset: " + token.startOffset() + ".." + token.endOffset(),
                                    "位置: " + token.startLine() + ":" + token.startColumn()
                                            + " - " + token.endLine() + ":" + token.endColumn()
                            ),
                            token.range(),
                            explanationFormatter.explainToken(token),
                            visual
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
        private final String columnId;
        private final VBox root = new VBox(6);
        private final Label title = new Label();
        private final VBox body = new VBox(4);
        private final ScrollPane scrollPane = new ScrollPane(body);
        private final MiniCViewportAdapter viewportAdapter = new StageColumnViewportAdapter();
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
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
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

        private void installViewportTarget(MiniCWorkbenchControlHub controlHub) {
            MiniCWorkbenchControlHub hub = Objects.requireNonNull(controlHub, "controlHub");
            hub.installViewportTarget(scrollPane, viewportAdapter);
            if (Boolean.TRUE.equals(scrollPane.getProperties().get(STAGE_SCROLL_FILTER_INSTALLED_KEY))) {
                return;
            }
            scrollPane.getProperties().put(STAGE_SCROLL_FILTER_INSTALLED_KEY, true);
            scrollPane.addEventHandler(ScrollEvent.SCROLL, event -> {
                hub.viewportRegistry().businessActive(viewportAdapter);
                if (event.isShiftDown() && event.getDeltaY() != 0) {
                    hub.handleScrollHorizontal(-event.getDeltaY());
                    event.consume();
                    return;
                }
                if (event.getDeltaY() != 0) {
                    hub.handleScrollVertical(-event.getDeltaY());
                    event.consume();
                    return;
                }
                if (event.getDeltaX() != 0) {
                    hub.handleScrollHorizontal(-event.getDeltaX());
                    event.consume();
                }
            });
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
            Platform.runLater(viewportAdapter::centerActiveIfNeeded);
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

        private boolean isActiveFullyVisible() {
            Bounds activeBounds = activeBounds();
            if (activeBounds == null) {
                return true;
            }
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double contentHeight = body.getBoundsInLocal().getHeight();
            if (viewportHeight <= 0 || contentHeight <= viewportHeight) {
                return true;
            }
            double maxTop = Math.max(1, contentHeight - viewportHeight);
            double visibleTop = scrollPane.getVvalue() * maxTop;
            double visibleBottom = visibleTop + viewportHeight;
            return activeBounds.getMinY() >= visibleTop && activeBounds.getMaxY() <= visibleBottom;
        }

        private Bounds activeBounds() {
            Node active = activeNode(body);
            if (active != null) {
                return body.sceneToLocal(active.localToScene(active.getBoundsInLocal()));
            }
            Double centerY = activeCenterY();
            if (centerY == null) {
                return null;
            }
            return new BoundingBox(0, centerY - 10.0, body.getBoundsInLocal().getWidth(), 20.0);
        }

        private Node activeNode(Node node) {
            if (node.getStyleClass().contains("active")) {
                return node;
            }
            if (node instanceof Parent parent) {
                for (Node child : parent.getChildrenUnmodifiable()) {
                    Node found = activeNode(child);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
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

        private void scrollVertical(double delta) {
            scrollAxis(delta, false);
        }

        private void scrollHorizontal(double delta) {
            scrollAxis(delta, true);
        }

        private void scrollAxis(double delta, boolean horizontal) {
            double viewportSize = horizontal
                    ? scrollPane.getViewportBounds().getWidth()
                    : scrollPane.getViewportBounds().getHeight();
            double contentSize = horizontal
                    ? body.getBoundsInLocal().getWidth()
                    : body.getBoundsInLocal().getHeight();
            double maxOffset = Math.max(0, contentSize - viewportSize);
            if (maxOffset <= 0) {
                if (horizontal) {
                    scrollPane.setHvalue(0);
                } else {
                    scrollPane.setVvalue(0);
                }
                return;
            }
            double current = horizontal ? scrollPane.getHvalue() : scrollPane.getVvalue();
            double target = Math.max(0, Math.min(1, current + delta / maxOffset));
            if (horizontal) {
                scrollPane.setHvalue(target);
            } else {
                scrollPane.setVvalue(target);
            }
        }

        private final class StageColumnViewportAdapter implements MiniCViewportAdapter {
            @Override
            public MiniCControlTargetType type() {
                return MiniCControlTargetType.STAGE;
            }

            @Override
            public boolean canScrollVertical() {
                return true;
            }

            @Override
            public void scrollVertical(double delta) {
                StageColumn.this.scrollVertical(delta);
            }

            @Override
            public boolean canScrollHorizontal() {
                return true;
            }

            @Override
            public void scrollHorizontal(double delta) {
                StageColumn.this.scrollHorizontal(delta);
            }

            @Override
            public boolean isActiveFullyVisible() {
                return StageColumn.this.isActiveFullyVisible();
            }

            @Override
            public void centerActive() {
                StageColumn.this.centerActive();
            }
        }
    }
}
