package minic.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Group;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import minic.uiapi.UiAssemblyLineVisualDto;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiDebugAsmViewDto;
import minic.uiapi.UiDebugAstViewDto;
import minic.uiapi.UiDebugBreakpointDto;
import minic.uiapi.UiDebugDataStructureViewDto;
import minic.uiapi.UiDebugEventDto;
import minic.uiapi.UiDebugFrameDto;
import minic.uiapi.UiDebugIrViewDto;
import minic.uiapi.UiDebugMetadataViewDto;
import minic.uiapi.UiDebugTimelineItemDto;
import minic.uiapi.UiDebugVariableDto;
import minic.uiapi.UiDebugVisualElementDto;
import minic.uiapi.UiDebugVisualStructureDto;
import minic.uiapi.UiIrLineVisualDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Workbench 独立 Debug 模式首版面板。
 */
public final class MiniCDebugPane extends VBox {
    private static final double DEBUG_AST_ZOOM = 0.8;
    private static final int METADATA_LIST_LIMIT = 200;
    private static final double VISUAL_CELL_SIZE = 44;
    private static final double VISUAL_NODE_RADIUS = VISUAL_CELL_SIZE / 2;
    private static final double VISUAL_NULL_SIZE = 16;
    private static final double VISUAL_GRID_GAP = 34;
    private static final double VISUAL_MARGIN = 24;
    private static final List<DebugView> DEBUG_VIEWS = List.of(
            new DebugView("metadata", "元数据"),
            new DebugView("data", "数据结构"),
            new DebugView("ast", "AST"),
            new DebugView("ir", "IR"),
            new DebugView("asm", "ASM")
    );
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCSourceLoaderView sourceView;
    private final HBox debugBody = new HBox();
    private final VBox viewSelector = new VBox(4);
    private final SplitPane workspaceSplitPane = new SplitPane();
    private final SplitPane viewSplitPane = new SplitPane();
    private final VBox primaryContent = new VBox();
    private final VBox splitContent = new VBox();
    private final MiniCAstGraphModelFactory astGraphModelFactory = new MiniCAstGraphModelFactory();
    private final Label status = label("", "body-text");
    private final TextField breakpointLine = new TextField("1");
    private String selectedViewId = "metadata";
    private String selectedSplitViewId = "metadata";
    private boolean splitVisible;

    /**
     * 创建 Debug 面板。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCDebugPane(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        getStyleClass().add("debug-pane");
        sourceView = new MiniCSourceLoaderView(viewModel, false);
        HBox controls = controls();
        configureDebugBody();
        getChildren().addAll(controls, status, debugBody);
        VBox.setVgrow(debugBody, Priority.ALWAYS);
        viewModel.debugAsmViewProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
    }

    private HBox controls() {
        Button start = button("启动", () -> {
            viewModel.setDebugBreakpoints(sourceView.breakpointLines());
            sourceView.loadCurrentSource();
            viewModel.startDebug();
        });
        Button breakpoint = button("设断点", () -> {
            sourceView.setBreakpoint(breakpointLine(), true);
            viewModel.setDebugBreakpoint(breakpointLine());
        });
        Button clearBreakpoint = button("清断点", () -> {
            sourceView.setBreakpoint(breakpointLine(), false);
            viewModel.clearDebugBreakpoint(breakpointLine());
        });
        Button fast = button("快进", viewModel::debugFastForward);
        Button run = button("运行到断点", viewModel::debugRunToBreakpoint);
        Button step = button("单步", viewModel::debugStepOver);
        Button into = button("步入", viewModel::debugStepInto);
        Button out = button("步返", viewModel::debugStepOut);
        Button pause = button("暂停", viewModel::debugPause);
        Button restart = button("重启", viewModel::debugRestart);
        Button close = button("关闭", viewModel::debugClose);
        Button back = button("单退", viewModel::debugStepBack);
        Button backBreakpoint = button("步退", viewModel::debugBackToBreakpoint);
        Button backCall = button("返回调用处", viewModel::debugBackToCallSite);
        Button split = button("拆分", this::toggleSplit);
        breakpointLine.getStyleClass().add("debug-breakpoint-line");
        breakpointLine.setPrefWidth(58);
        HBox controls = new HBox(
                6,
                start,
                label("行", "body-text"),
                breakpointLine,
                breakpoint,
                clearBreakpoint,
                fast,
                run,
                step,
                into,
                out,
                pause,
                restart,
                close,
                back,
                backBreakpoint,
                backCall,
                split
        );
        controls.getStyleClass().add("controls");
        return controls;
    }

    private int breakpointLine() {
        try {
            return Math.max(1, Integer.parseInt(breakpointLine.getText().trim()));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("control-secondary");
        button.setOnAction(event -> action.run());
        return button;
    }

    private ScrollPane scroll(String text) {
        return scroll(text, "debug:plain");
    }

    private ScrollPane scroll(String text, String viewportKey) {
        Label body = label(text, "body-text");
        body.setWrapText(true);
        ScrollPane scroll = new ScrollPane(body);
        scroll.getStyleClass().add("visual-scroll");
        scroll.setFitToWidth(true);
        return rememberViewport(scroll, viewportKey);
    }

    private void refresh() {
        if (!viewModel.debugStartedProperty().get() || viewModel.debugStateProperty().get() == null) {
            status.setText("Debug 未启动");
            sourceView.setCurrentExecutionLine(0);
            setPrimaryContent(scroll(""));
            setSplitContent(scroll(""));
            return;
        }
        status.setText("Debug " + viewModel.debugStateProperty().get().executionState()
                + " · " + viewModel.debugStateProperty().get().currentSnapshot().stopReason()
                + " · step " + viewModel.debugStateProperty().get().currentSnapshot().visibleStepIndex()
                + " · " + viewModel.debugStateProperty().get().currentSnapshot().functionName());
        sourceView.setCurrentExecutionLine(currentSourceLine());
        setPrimaryContent(contentFor(selectedViewId, false));
        if (splitVisible) {
            setSplitContent(contentFor(selectedSplitViewId, true));
        }
    }

    private void configureDebugBody() {
        debugBody.getStyleClass().add("debug-workspace");
        viewSelector.getStyleClass().add("debug-view-selector");
        DEBUG_VIEWS.forEach(view -> viewSelector.getChildren().add(viewButton(view)));
        primaryContent.getStyleClass().add("debug-view-content");
        splitContent.getStyleClass().add("debug-view-content");
        viewSplitPane.setOrientation(Orientation.HORIZONTAL);
        viewSplitPane.getItems().setAll(primaryContent);
        workspaceSplitPane.setOrientation(Orientation.HORIZONTAL);
        workspaceSplitPane.getItems().setAll(sourceView, viewSplitPane);
        workspaceSplitPane.setDividerPositions(0.5);
        debugBody.getChildren().setAll(viewSelector, workspaceSplitPane);
        HBox.setHgrow(workspaceSplitPane, Priority.ALWAYS);
        refreshViewButtons();
    }

    private Button viewButton(DebugView view) {
        Button button = new Button(view.title());
        button.getStyleClass().add("debug-view-button");
        button.setAccessibleText("Debug视图:" + view.title());
        button.setOnAction(event -> {
            selectedViewId = view.id();
            setPrimaryContent(contentFor(selectedViewId, false));
            refreshViewButtons();
        });
        return button;
    }

    private void refreshViewButtons() {
        for (int index = 0; index < DEBUG_VIEWS.size(); index++) {
            Button button = (Button) viewSelector.getChildren().get(index);
            button.getStyleClass().remove("active");
            if (DEBUG_VIEWS.get(index).id().equals(selectedViewId)) {
                button.getStyleClass().add("active");
            }
        }
    }

    private void setPrimaryContent(Node content) {
        primaryContent.getChildren().setAll(content);
    }

    private void setSplitContent(Node content) {
        splitContent.getChildren().setAll(content);
    }

    private void toggleSplit() {
        splitVisible = !splitVisible;
        if (splitVisible) {
            if (!viewSplitPane.getItems().contains(splitContent)) {
                viewSplitPane.getItems().add(splitContent);
            }
            selectedSplitViewId = selectedViewId;
            setSplitContent(contentFor(selectedSplitViewId, true));
        } else {
            viewSplitPane.getItems().remove(splitContent);
        }
    }

    private Node contentFor(String viewId, boolean split) {
        return switch (viewId) {
            case "metadata" -> split
                    ? scroll(metadataText(viewModel.debugMetadataViewProperty().get()), debugViewportKey("metadata", true))
                    : metadataContent(viewModel.debugMetadataViewProperty().get(), debugViewportKey("metadata", false));
            case "data" -> split
                    ? scroll(dataText(viewModel.debugDataStructureViewProperty().get()), debugViewportKey("data", true))
                    : dataContent(viewModel.debugDataStructureViewProperty().get(), debugViewportKey("data", false));
            case "ast" -> split
                    ? scroll(astText(viewModel.debugAstViewProperty().get()), debugViewportKey("ast", true))
                    : astContent(viewModel.debugAstViewProperty().get(), debugViewportKey("ast", false));
            case "ir" -> split
                    ? scroll(irText(viewModel.debugIrViewProperty().get()), debugViewportKey("ir", true))
                    : irContent(viewModel.debugIrViewProperty().get(), debugViewportKey("ir", false));
            case "asm" -> split
                    ? scroll(asmText(viewModel.debugAsmViewProperty().get()), debugViewportKey("asm", true))
                    : asmContent(viewModel.debugAsmViewProperty().get(), debugViewportKey("asm", false));
            default -> scroll("");
        };
    }

    private String debugViewportKey(String viewId, boolean split) {
        return "debug:" + (split ? "split:" : "primary:") + viewId;
    }

    private ScrollPane metadataContent(UiDebugMetadataViewDto view, String viewportKey) {
        VBox content = new VBox(10);
        content.getStyleClass().add("debug-metadata");
        if (view == null) {
            return wrap(content, viewportKey);
        }
        content.getChildren().addAll(
                metadataSummary(view),
                metadataSection("调用栈", view.callStack().stream().map(this::frameText).toList()),
                metadataSection("变量", view.variables().stream().map(this::variableText).toList()),
                metadataSection("断点", view.breakpoints().stream().map(this::breakpointText).toList()),
                metadataSection("事件日志", boundedLines(view.events().stream().map(this::eventText).toList())),
                metadataSection("Snapshot 时间线", boundedLines(view.timeline().stream().map(this::timelineText).toList())),
                metadataSection("stdout", List.of(view.stdout().isBlank() ? "(empty)" : view.stdout())),
                metadataSection("stderr", List.of(view.stderr().isBlank() ? "(empty)" : view.stderr()))
        );
        return wrap(content, viewportKey);
    }

    private Node metadataSummary(UiDebugMetadataViewDto view) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("debug-summary-grid");
        addSummaryRow(grid, 0, "状态", view.executionState());
        addSummaryRow(grid, 1, "停止原因", view.stopReason());
        addSummaryRow(grid, 2, "函数", view.currentFunction());
        addSummaryRow(grid, 3, "源码", rangeText(view.currentSourceRange()));
        return grid;
    }

    private void addSummaryRow(GridPane grid, int row, String key, String value) {
        Label name = label(key, "debug-summary-key");
        Label body = label(value == null || value.isBlank() ? "(empty)" : value, "debug-summary-value");
        grid.add(name, 0, row);
        grid.add(body, 1, row);
    }

    private Node metadataSection(String title, List<String> lines) {
        VBox section = new VBox(4);
        section.getStyleClass().add("debug-section");
        Label heading = label(title, "debug-section-title");
        VBox body = new VBox(2);
        body.getStyleClass().add("debug-section-body");
        List<String> visibleLines = lines.isEmpty() ? List.of("(empty)") : lines;
        visibleLines.forEach(line -> body.getChildren().add(label(line, "debug-section-line")));
        section.getChildren().addAll(heading, body);
        return section;
    }

    private List<String> boundedLines(List<String> lines) {
        if (lines.size() <= METADATA_LIST_LIMIT) {
            return lines;
        }
        int omitted = lines.size() - METADATA_LIST_LIMIT;
        java.util.ArrayList<String> visible = new java.util.ArrayList<>();
        visible.add("(已省略较早的 " + omitted + " 条，显示最近 " + METADATA_LIST_LIMIT + " 条)");
        visible.addAll(lines.subList(omitted, lines.size()));
        return visible;
    }

    private ScrollPane wrap(Node content) {
        return wrap(content, "debug:wrap");
    }

    private ScrollPane wrap(Node content, String viewportKey) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("visual-scroll");
        scroll.setFitToWidth(true);
        return rememberViewport(scroll, viewportKey);
    }

    private ScrollPane rememberViewport(ScrollPane scroll, String viewportKey) {
        MiniCWorkbenchViewModel.UiViewportState state = viewModel.viewportState(viewportKey);
        scroll.setHvalue(state.hvalue());
        scroll.setVvalue(state.vvalue());
        scroll.hvalueProperty().addListener((observable, oldValue, newValue) ->
                viewModel.saveViewportState(viewportKey, newValue.doubleValue(), scroll.getVvalue()));
        scroll.vvalueProperty().addListener((observable, oldValue, newValue) ->
                viewModel.saveViewportState(viewportKey, scroll.getHvalue(), newValue.doubleValue()));
        return scroll;
    }

    private String metadataText(UiDebugMetadataViewDto view) {
        if (view == null) {
            return "";
        }
        return "状态: " + view.executionState()
                + "\n停止原因: " + view.stopReason()
                + "\n函数: " + view.currentFunction()
                + "\n源码: " + rangeText(view.currentSourceRange())
                + "\n\n调用栈:\n" + view.callStack().stream()
                .map(this::frameText)
                .collect(Collectors.joining("\n"))
                + "\n变量:\n" + view.variables().stream()
                .map(this::variableText)
                .collect(Collectors.joining("\n"))
                + "\n\n断点:\n" + view.breakpoints().stream()
                .map(this::breakpointText)
                .collect(Collectors.joining("\n"))
                + "\n\n事件:\n" + view.events().stream()
                .map(this::eventText)
                .collect(Collectors.joining("\n"))
                + "\n\n时间线:\n" + view.timeline().stream()
                .map(this::timelineText)
                .collect(Collectors.joining("\n"))
                + "\n\nstdout:\n" + view.stdout()
                + "\n\nstderr:\n" + view.stderr();
    }

    private String dataText(UiDebugDataStructureViewDto view) {
        if (view == null) {
            return "";
        }
        return "stack frames: " + view.processSpace().stackFrames().size()
                + "\nfunctions: " + view.processSpace().functions()
                + "\ncurrent: " + view.processSpace().currentFunctionName()
                + " / " + view.processSpace().currentInstructionId()
                + "\nvisuals:\n" + view.visuals().stream()
                .map(visual -> "  " + visual.type() + " " + visual.name() + " · " + visual.summary()
                        + "\n" + visual.elements().stream()
                        .map(this::visualElementText)
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n"))
                + "\nwarnings:\n" + String.join("\n", view.warnings());
    }

    private ScrollPane dataContent(UiDebugDataStructureViewDto view, String viewportKey) {
        VBox content = new VBox(10);
        content.getStyleClass().add("debug-data-space");
        if (view == null) {
            return wrap(content, viewportKey);
        }
        content.getChildren().addAll(
                processSpaceSection("code", List.of(
                        "current function: " + view.processSpace().currentFunctionName(),
                        "current instruction: " + view.processSpace().currentInstructionId(),
                        "functions: " + String.join(", ", view.processSpace().functions())
                )),
                processSpaceSection("static/data", view.processSpace().staticValues().stream()
                        .map(this::variableText)
                        .toList()),
                processSpaceSection("stack", view.processSpace().stackFrames().stream()
                        .map(this::frameWithValuesText)
                        .toList()),
                processSpaceSection("heap", view.processSpace().heapValues().stream()
                        .map(this::variableText)
                        .toList()),
                processSpaceSection("io", List.of(
                        "stdin: " + emptyText(view.processSpace().stdin()),
                        "stdout: " + emptyText(view.processSpace().stdout()),
                        "stderr: " + emptyText(view.processSpace().stderr())
                )),
                visualCards(view.visuals()),
                metadataSection("warnings", view.warnings())
        );
        return wrap(content, viewportKey);
    }

    private Node processSpaceSection(String title, List<String> lines) {
        VBox section = new VBox(4);
        section.getStyleClass().add("debug-process-section");
        Label heading = label(title, "debug-process-title");
        VBox body = new VBox(2);
        body.getStyleClass().add("debug-section-body");
        List<String> visibleLines = lines.isEmpty() ? List.of("(empty)") : lines;
        visibleLines.forEach(line -> body.getChildren().add(label(line, "debug-section-line")));
        section.getChildren().addAll(heading, body);
        return section;
    }

    private Node visualCards(List<UiDebugVisualStructureDto> visuals) {
        VBox section = new VBox(6);
        section.getStyleClass().add("debug-visuals");
        section.getChildren().add(label("visual structures", "debug-section-title"));
        if (visuals.isEmpty()) {
            section.getChildren().add(label("(empty)", "debug-section-line"));
            return section;
        }
        visuals.forEach(visual -> section.getChildren().add(visualCard(visual)));
        return section;
    }

    private Node visualCard(UiDebugVisualStructureDto visual) {
        VBox card = new VBox(4);
        card.getStyleClass().add("debug-visual-card");
        card.getChildren().add(label(
                visual.type() + " " + visual.name() + " · " + visual.kind(),
                "debug-visual-title"
        ));
        card.getChildren().add(label(visual.summary(), "debug-section-line"));
        Node diagram = visualDiagram(visual);
        if (diagram != null) {
            card.getChildren().add(diagram);
        }
        visual.elements().forEach(element -> card.getChildren().add(label(visualElementText(element), "debug-section-line")));
        return card;
    }

    private Node visualDiagram(UiDebugVisualStructureDto visual) {
        List<UiDebugVisualElementDto> arrayCells = visual.elements().stream()
                .filter(element -> element.kind().equals("ARRAY_CELL"))
                .toList();
        if (!arrayCells.isEmpty()) {
            return arrayDiagram(arrayCells);
        }
        List<UiDebugVisualElementDto> graphNodes = visual.elements().stream()
                .filter(element -> element.kind().equals("GRAPH_NODE"))
                .toList();
        if (!graphNodes.isEmpty()) {
            List<UiDebugVisualElementDto> graphEdges = visual.elements().stream()
                    .filter(element -> element.kind().equals("GRAPH_EDGE"))
                    .toList();
            return graphDiagram(visual.kind(), graphNodes, graphEdges);
        }
        return null;
    }

    private Node arrayDiagram(List<UiDebugVisualElementDto> cells) {
        Pane pane = new Pane();
        pane.getStyleClass().add("debug-visual-diagram");
        double width = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE * cells.size();
        double height = VISUAL_MARGIN * 2 + VISUAL_CELL_SIZE;
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        for (int index = 0; index < cells.size(); index++) {
            UiDebugVisualElementDto cell = cells.get(index);
            double x = VISUAL_MARGIN + index * VISUAL_CELL_SIZE;
            double y = VISUAL_MARGIN;
            Rectangle rect = new Rectangle(x, y, VISUAL_CELL_SIZE, VISUAL_CELL_SIZE);
            rect.getStyleClass().add("debug-array-cell");
            Text text = visualText(shortLabel(cell.label()), x, y + 4, VISUAL_CELL_SIZE);
            pane.getChildren().addAll(rect, text);
        }
        return pane;
    }

    private Node graphDiagram(String kind, List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges) {
        Pane pane = new Pane();
        pane.getStyleClass().add("debug-visual-diagram");
        List<UiDebugVisualElementDto> visibleNodes = visibleGraphNodes(kind, nodes, edges);
        Map<String, UiDebugVisualElementDto> nodesById = visibleNodes.stream()
                .collect(Collectors.toMap(this::simpleVisualId, node -> node, (left, right) -> left, LinkedHashMap::new));
        Map<String, VisualPoint> positions = graphPositions(kind, visibleNodes, edges);
        double width = Math.max(220, positions.values().stream().mapToDouble(VisualPoint::x).max().orElse(160) + VISUAL_MARGIN);
        double height = Math.max(150, positions.values().stream().mapToDouble(VisualPoint::y).max().orElse(100) + VISUAL_MARGIN);
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        for (UiDebugVisualElementDto edge : edges) {
            VisualPoint from = positions.get(edge.metadata().getOrDefault("from", ""));
            VisualPoint to = positions.get(edge.metadata().getOrDefault("to", ""));
            if (from != null && to != null) {
                pane.getChildren().addAll(arrow(from, to, isNullNode(nodesById, edge.metadata().getOrDefault("to", ""))));
            }
        }
        for (UiDebugVisualElementDto node : visibleNodes) {
            VisualPoint point = positions.get(simpleVisualId(node));
            if (point == null) {
                continue;
            }
            if (isNullNode(node)) {
                Rectangle nullRect = new Rectangle(
                        point.x() - VISUAL_NULL_SIZE / 2,
                        point.y() - VISUAL_NULL_SIZE / 2,
                        VISUAL_NULL_SIZE,
                        VISUAL_NULL_SIZE
                );
                nullRect.getStyleClass().add("debug-null-node");
                nullRect.setAccessibleText(simpleVisualId(node));
                pane.getChildren().add(nullRect);
                continue;
            }
            Circle circle = new Circle(point.x(), point.y(), VISUAL_NODE_RADIUS);
            circle.getStyleClass().add("debug-graph-node");
            circle.setAccessibleText(simpleVisualId(node));
            Text text = visualText(shortLabel(node.label()), point.x() - VISUAL_NODE_RADIUS, point.y() + 4, VISUAL_CELL_SIZE);
            pane.getChildren().addAll(circle, text);
        }
        return pane;
    }

    private boolean isNullNode(Map<String, UiDebugVisualElementDto> nodesById, String id) {
        UiDebugVisualElementDto node = nodesById.get(id);
        return node != null && isNullNode(node);
    }

    private boolean isNullNode(UiDebugVisualElementDto node) {
        return Boolean.parseBoolean(node.metadata().getOrDefault("visual-null", "false"));
    }

    private List<UiDebugVisualElementDto> visibleGraphNodes(
            String kind,
            List<UiDebugVisualElementDto> nodes,
            List<UiDebugVisualElementDto> edges
    ) {
        if (!kind.equals("tree") && !kind.equals("binary_tree")) {
            return nodes;
        }
        java.util.HashSet<String> nodeIds = new java.util.HashSet<>();
        nodes.forEach(node -> nodeIds.add(simpleVisualId(node)));
        java.util.HashSet<String> edgeNodeIds = new java.util.HashSet<>();
        edges.forEach(edge -> {
            String from = edge.metadata().get("from");
            String to = edge.metadata().get("to");
            if (from != null) {
                edgeNodeIds.add(from);
            }
            if (to != null) {
                edgeNodeIds.add(to);
            }
        });
        return nodes.stream()
                .filter(node -> {
                    String id = simpleVisualId(node);
                    String summary = node.metadata().get("summary");
                    return edgeNodeIds.contains(id) || summary == null || !nodeIds.contains(summary);
                })
                .toList();
    }

    private Map<String, VisualPoint> graphPositions(
            String kind,
            List<UiDebugVisualElementDto> nodes,
            List<UiDebugVisualElementDto> edges
    ) {
        if (kind.equals("tree") || kind.equals("binary_tree")) {
            return treePositions(nodes, edges);
        }
        LinkedHashMap<String, VisualPoint> positions = new LinkedHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            String id = simpleVisualId(nodes.get(index));
            positions.put(id, new VisualPoint(
                    VISUAL_MARGIN + VISUAL_NODE_RADIUS + index * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP),
                    VISUAL_MARGIN + VISUAL_NODE_RADIUS
            ));
        }
        return positions;
    }

    private Map<String, VisualPoint> treePositions(List<UiDebugVisualElementDto> nodes, List<UiDebugVisualElementDto> edges) {
        LinkedHashMap<String, UiDebugVisualElementDto> nodeById = new LinkedHashMap<>();
        nodes.forEach(node -> nodeById.put(simpleVisualId(node), node));
        LinkedHashMap<String, ArrayList<String>> childrenById = new LinkedHashMap<>();
        java.util.HashSet<String> childIds = new java.util.HashSet<>();
        nodeById.keySet().forEach(id -> childrenById.put(id, new ArrayList<>()));
        for (UiDebugVisualElementDto edge : orderedTreeEdges(edges)) {
            String from = edge.metadata().get("from");
            String to = edge.metadata().get("to");
            if (from != null && to != null && nodeById.containsKey(from) && nodeById.containsKey(to)) {
                childrenById.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
                childIds.add(to);
            }
        }
        ArrayList<String> roots = new ArrayList<>();
        nodeById.keySet().stream()
                .filter(id -> !childIds.contains(id))
                .forEach(roots::add);
        if (roots.isEmpty()) {
            roots.addAll(nodeById.keySet());
        }
        LinkedHashMap<String, VisualPoint> positions = new LinkedHashMap<>();
        TreeLayoutCursor cursor = new TreeLayoutCursor();
        for (String root : roots) {
            layoutTree(root, 0, childrenById, positions, new java.util.HashSet<>(), cursor);
            cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
        }
        return positions;
    }

    private List<UiDebugVisualElementDto> orderedTreeEdges(List<UiDebugVisualElementDto> edges) {
        return edges.stream()
                .sorted(java.util.Comparator
                        .comparing((UiDebugVisualElementDto edge) -> edge.metadata().getOrDefault("from", ""))
                        .thenComparingInt(edge -> treeEdgeOrder(edge.metadata().getOrDefault("key", edge.label()))))
                .toList();
    }

    private int treeEdgeOrder(String key) {
        return switch (key) {
            case "left" -> 0;
            case "right" -> 1;
            default -> 2;
        };
    }

    private double layoutTree(
            String nodeId,
            int depth,
            Map<String, ArrayList<String>> childrenById,
            Map<String, VisualPoint> positions,
            java.util.Set<String> visiting,
            TreeLayoutCursor cursor
    ) {
        if (!visiting.add(nodeId)) {
            double x = cursor.nextLeafX;
            cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
            positions.put(nodeId, new VisualPoint(x, treeY(depth)));
            return x;
        }
        List<String> children = childrenById.getOrDefault(nodeId, new ArrayList<>());
        if (children.isEmpty()) {
            double x = cursor.nextLeafX;
            cursor.nextLeafX += VISUAL_CELL_SIZE + VISUAL_GRID_GAP;
            positions.put(nodeId, new VisualPoint(x, treeY(depth)));
            visiting.remove(nodeId);
            return x;
        }
        ArrayList<Double> childXs = new ArrayList<>();
        for (String child : children) {
            childXs.add(layoutTree(child, depth + 1, childrenById, positions, visiting, cursor));
        }
        double x = (childXs.getFirst() + childXs.getLast()) / 2;
        positions.put(nodeId, new VisualPoint(x, treeY(depth)));
        visiting.remove(nodeId);
        return x;
    }

    private double treeY(int depth) {
        return VISUAL_MARGIN + VISUAL_NODE_RADIUS + depth * (VISUAL_CELL_SIZE + VISUAL_GRID_GAP);
    }

    private List<Node> arrow(VisualPoint from, VisualPoint to, boolean nullTarget) {
        double angle = Math.atan2(to.y() - from.y(), to.x() - from.x());
        double startX = from.x() + Math.cos(angle) * VISUAL_NODE_RADIUS;
        double startY = from.y() + Math.sin(angle) * VISUAL_NODE_RADIUS;
        double targetRadius = nullTarget ? VISUAL_NULL_SIZE / 2 : VISUAL_NODE_RADIUS;
        double endX = to.x() - Math.cos(angle) * targetRadius;
        double endY = to.y() - Math.sin(angle) * targetRadius;
        Line line = new Line(startX, startY, endX, endY);
        line.getStyleClass().addAll("debug-graph-edge", "debug-pointer-arrow");
        double arrowSize = 8;
        Polygon head = new Polygon(
                endX, endY,
                endX - Math.cos(angle - Math.PI / 6) * arrowSize,
                endY - Math.sin(angle - Math.PI / 6) * arrowSize,
                endX - Math.cos(angle + Math.PI / 6) * arrowSize,
                endY - Math.sin(angle + Math.PI / 6) * arrowSize
        );
        head.getStyleClass().addAll("debug-graph-edge-head", "debug-pointer-arrow");
        return List.of(line, head);
    }

    private Text visualText(String label, double x, double y, double width) {
        Text text = new Text(label);
        text.getStyleClass().add("debug-visual-label");
        text.setX(x);
        text.setY(y + VISUAL_CELL_SIZE / 2);
        text.setWrappingWidth(width);
        text.setFill(Color.web("#d4d4d4"));
        return text;
    }

    private String simpleVisualId(UiDebugVisualElementDto element) {
        String metadataId = element.metadata().get("id");
        if (metadataId != null && !metadataId.isBlank()) {
            return metadataId;
        }
        int index = element.id().lastIndexOf('-');
        return index < 0 ? element.id() : element.id().substring(index + 1);
    }

    private String astText(UiDebugAstViewDto view) {
        if (view == null || view.activeNode() == null) {
            return "";
        }
        return view.activeNode().kind() + " " + view.activeNode().label()
                + "\nrange: " + rangeText(view.activeNode().sourceRange())
                + "\n" + view.activeNode().explanation()
                + "\nIR: " + view.relatedIrIds()
                + "\nASM: " + view.relatedAsmIds();
    }

    private ScrollPane astContent(UiDebugAstViewDto view, String viewportKey) {
        VBox content = new VBox(8);
        content.getStyleClass().add("debug-ast-view");
        content.getChildren().add(astSummary(view));
        content.getChildren().add(debugAstGraph(view));
        return wrap(content, viewportKey);
    }

    private Node astSummary(UiDebugAstViewDto view) {
        if (view == null || view.activeNode() == null) {
            return metadataSection("当前 AST 节点", List.of("(empty)"));
        }
        return metadataSection("当前 AST 节点", List.of(
                view.activeNode().kind() + " " + view.activeNode().label(),
                "range: " + rangeText(view.activeNode().sourceRange()),
                view.activeNode().explanation(),
                "IR: " + view.relatedIrIds(),
                "ASM: " + view.relatedAsmIds()
        ));
    }

    private Node debugAstGraph(UiDebugAstViewDto view) {
        if (view == null || view.root() == null) {
            return emptyAstPane("AST 尚未就绪");
        }
        MiniCAstGraphModel graph = astGraphModelFactory.create(view.root());
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
            UiAstNodeVisualDto astNode = astNodeById(view.root(), node.id());
            String tooltip = astNode == null
                    ? node.label()
                    : astNode.kind() + " " + astNode.label() + "\n" + rangeText(astNode.range());
            circle.setAccessibleText(tooltip);
            text.setAccessibleText(tooltip);
            pane.getChildren().addAll(circle, text);
        });
        Group group = new Group(pane);
        pane.setScaleX(DEBUG_AST_ZOOM);
        pane.setScaleY(DEBUG_AST_ZOOM);
        pane.setManaged(false);
        Pane viewport = new Pane(group);
        viewport.getStyleClass().add("ast-graph-viewport");
        viewport.setMinSize(graph.width() * DEBUG_AST_ZOOM, graph.height() * DEBUG_AST_ZOOM);
        viewport.setPrefSize(graph.width() * DEBUG_AST_ZOOM, graph.height() * DEBUG_AST_ZOOM);
        return viewport;
    }

    private Pane emptyAstPane(String message) {
        Pane pane = new Pane(label(message, "body-text"));
        pane.getStyleClass().add("ast-graph");
        pane.setMinSize(360, 180);
        pane.setPrefSize(360, 180);
        return pane;
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

    private String shortLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String compact = label.replace('\n', ' ').strip();
        return compact.length() <= 10 ? compact : compact.substring(0, 9) + "...";
    }

    private String irText(UiDebugIrViewDto view) {
        if (view == null) {
            return "";
        }
        return view.explanation()
                + "\ncurrent: " + view.currentInstructionId()
                + "\nrange: " + rangeText(view.currentSourceRange())
                + "\n\nactive:\n" + view.lines().stream()
                .filter(UiIrLineVisualDto::active)
                .map(UiIrLineVisualDto::text)
                .collect(Collectors.joining("\n"))
                + "\n\noperands:\n" + view.operands().stream()
                .map(operand -> "  " + operand.name() + " " + operand.typeName()
                        + " = " + operand.valueSummary() + " @ " + operand.valueRef())
                .collect(Collectors.joining("\n"));
    }

    private ScrollPane irContent(UiDebugIrViewDto view, String viewportKey) {
        VBox content = new VBox(8);
        content.getStyleClass().add("debug-code-view");
        if (view == null) {
            return wrap(content, viewportKey);
        }
        content.getChildren().add(metadataSection("IR", List.of(
                view.explanation(),
                "current: " + view.currentInstructionId(),
                "range: " + rangeText(view.currentSourceRange())
        )));
        VBox rows = new VBox(2);
        rows.getStyleClass().add("debug-code-rows");
        view.lines().forEach(line -> rows.getChildren().add(irLineRow(line)));
        content.getChildren().add(rows);
        content.getChildren().add(metadataSection("operands", view.operands().stream()
                .map(operand -> operand.name() + " " + operand.typeName()
                        + " = " + operand.valueSummary() + " @ " + operand.valueRef())
                .toList()));
        return wrap(content, viewportKey);
    }

    private Node irLineRow(UiIrLineVisualDto line) {
        HBox row = new HBox();
        row.getStyleClass().add("debug-code-row");
        if (line.active()) {
            row.getStyleClass().add("active");
        }
        Label number = label(Integer.toString(line.lineNumber()), "debug-code-line-number");
        Label text = label(line.text().isEmpty() ? " " : line.text(), "debug-code-text");
        row.getChildren().addAll(number, text);
        return row;
    }

    private String asmText(UiDebugAsmViewDto view) {
        if (view == null) {
            return "";
        }
        return view.explanation()
                + "\nIR: " + view.relatedIrIds()
                + "\n\nactive asm:\n" + view.lines().stream()
                .filter(UiAssemblyLineVisualDto::active)
                .map(line -> "  " + line.lineNumber() + ": " + line.text())
                .collect(Collectors.joining("\n"));
    }

    private ScrollPane asmContent(UiDebugAsmViewDto view, String viewportKey) {
        VBox content = new VBox(8);
        content.getStyleClass().add("debug-code-view");
        if (view == null) {
            return wrap(content, viewportKey);
        }
        content.getChildren().add(metadataSection("ASM", List.of(
                view.explanation(),
                "IR: " + view.relatedIrIds()
        )));
        VBox rows = new VBox(2);
        rows.getStyleClass().add("debug-code-rows");
        view.lines().forEach(line -> rows.getChildren().add(asmLineRow(line)));
        content.getChildren().add(rows);
        return wrap(content, viewportKey);
    }

    private Node asmLineRow(UiAssemblyLineVisualDto line) {
        HBox row = new HBox();
        row.getStyleClass().add("debug-code-row");
        if (line.active()) {
            row.getStyleClass().add("active");
        }
        Label number = label(Integer.toString(line.lineNumber()), "debug-code-line-number");
        Label text = label(line.text().isEmpty() ? " " : line.text(), "debug-code-text");
        row.getChildren().addAll(number, text);
        return row;
    }

    private String frameText(UiDebugFrameDto frame) {
        return "  " + frame.functionName()
                + " return=" + (frame.returnTarget() == null ? "" : frame.returnTarget())
                + " active=" + rangeText(frame.activeRange());
    }

    private String frameWithValuesText(UiDebugFrameDto frame) {
        String parameters = frame.parameters().stream()
                .map(this::variableText)
                .collect(Collectors.joining("; "));
        String locals = frame.locals().stream()
                .map(this::variableText)
                .collect(Collectors.joining("; "));
        return frameText(frame)
                + " params=[" + parameters + "]"
                + " locals=[" + locals + "]";
    }

    private String variableText(UiDebugVariableDto variable) {
        return "  " + variable.name()
                + " " + variable.typeName()
                + " " + variable.valueKind()
                + " = " + variable.valueSummary()
                + " @ " + variable.address();
    }

    private String breakpointText(UiDebugBreakpointDto breakpoint) {
        return "  line " + breakpoint.line() + " enabled=" + breakpoint.enabled();
    }

    private String eventText(UiDebugEventDto event) {
        return "  #" + event.eventId()
                + " [" + event.type() + "] " + event.title()
                + " · " + event.description();
    }

    private String timelineText(UiDebugTimelineItemDto item) {
        return "  snapshot " + item.snapshotId()
                + " step=" + item.visibleStepIndex()
                + " reason=" + item.stopReason()
                + " breakpoint=" + item.breakpointHit()
                + " range=" + rangeText(item.sourceRange());
    }

    private int currentSourceLine() {
        if (viewModel.debugStateProperty().get() == null
                || viewModel.debugStateProperty().get().currentSnapshot().sourceRange() == null) {
            return 0;
        }
        return viewModel.debugStateProperty().get().currentSnapshot().sourceRange().startLine();
    }

    private String visualElementText(UiDebugVisualElementDto element) {
        return "    " + element.kind() + " " + element.id()
                + " " + element.label()
                + " " + element.metadata();
    }

    private String emptyText(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }

    private String rangeText(minic.uiapi.UiSourceSpanDto range) {
        if (range == null) {
            return "";
        }
        return range.sourceName()
                + ":" + range.startLine()
                + ":" + range.startColumn()
                + "-" + range.endLine()
                + ":" + range.endColumn();
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private record DebugView(String id, String title) {
    }

    private record VisualPoint(double x, double y) {
    }

    private static final class TreeLayoutCursor {
        private double nextLeafX = VISUAL_MARGIN + VISUAL_NODE_RADIUS;
    }
}
