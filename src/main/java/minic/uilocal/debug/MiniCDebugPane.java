package minic.uilocal;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import minic.uilocal.control.MiniCActiveTrackingService;
import minic.uilocal.control.MiniCControlTargetType;
import minic.uilocal.control.MiniCWorkbenchControlHub;
import minic.uilocal.text.MiniCAssemblyTextHighlighter;
import minic.uilocal.text.MiniCExplanationTextHighlighter;
import minic.uilocal.text.MiniCIrTextHighlighter;
import minic.uilocal.text.MiniCTextFlowFactory;
import minic.uiapi.UiAssemblyLineVisualDto;
import minic.uiapi.UiDebugAsmViewDto;
import minic.uiapi.UiDebugAstViewDto;
import minic.uiapi.UiDebugDataStructureViewDto;
import minic.uiapi.UiDebugIrViewDto;
import minic.uiapi.UiDebugMetadataViewDto;
import minic.uiapi.UiDebugVisualElementDto;
import minic.uiapi.UiDebugVisualStructureDto;
import minic.uiapi.UiIrLineVisualDto;
import minic.uiapi.UiSourceSpanDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Workbench 独立 Debug 模式首版面板。
 */
public final class MiniCDebugPane extends VBox {
    private static final double DEFAULT_AST_ZOOM = 0.8;
    private static final double MIN_AST_ZOOM = 0.05;
    private static final double MAX_AST_ZOOM = 2.0;
    private static final double TEXT_ZOOM_STEP = 1.0;
    private static final double VIEWPORT_KEY_SCROLL_DELTA = 48.0;
    private static final List<String> DEBUG_SHORTCUT_ACTIONS = List.of(
            MiniCWorkbenchControlHub.DEBUG_START,
            MiniCWorkbenchControlHub.DEBUG_RUN_TO_END,
            MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT,
            MiniCWorkbenchControlHub.DEBUG_STEP_OVER,
            MiniCWorkbenchControlHub.DEBUG_STEP_INTO,
            MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT,
            MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER,
            MiniCWorkbenchControlHub.DEBUG_STEP_BACK
    );
    private static final double DEBUG_CONTROL_BUTTON_WIDTH = 92;
    private static final double DEBUG_BUTTON_HEIGHT = 28;
    private static final int METADATA_LIST_LIMIT = 200;
    private static final List<DebugView> DEBUG_VIEWS = List.of(
            new DebugView("metadata", "元数据"),
            new DebugView("data", "数据结构"),
            new DebugView("ast", "AST"),
            new DebugView("ir", "IR"),
            new DebugView("asm", "ASM")
    );
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCSourceLoaderView sourceView;
    private final MiniCWorkbenchControlHub controlHub;
    private final HBox debugBody = new HBox();
    private final VBox viewSelector = new VBox(4);
    private final SplitPane workspaceSplitPane = new SplitPane();
    private final SplitPane viewSplitPane = new SplitPane();
    private final VBox primaryContent = new VBox();
    private final VBox splitContent = new VBox();
    private final MiniCIrTextHighlighter irTextHighlighter = new MiniCIrTextHighlighter();
    private final MiniCAssemblyTextHighlighter assemblyTextHighlighter = new MiniCAssemblyTextHighlighter();
    private final MiniCExplanationTextHighlighter explanationTextHighlighter = new MiniCExplanationTextHighlighter();
    private final MiniCDebugVisualDiagramRenderer visualDiagramRenderer =
            new MiniCDebugVisualDiagramRenderer(this::installExplanationTooltip);
    private final MiniCDebugViewportController viewportController;
    private final MiniCDebugAstGraphRenderer astGraphRenderer;
    private final MiniCKeyBindingConfig keyBindings = MiniCKeyBindingConfig.loadDefault();
    private final LinkedHashSet<KeyCode> pressedKeys = new LinkedHashSet<>();
    private final Slider astZoom = new Slider(MIN_AST_ZOOM, MAX_AST_ZOOM, DEFAULT_AST_ZOOM);
    private final Label status = label("", "body-text");
    private String selectedViewId = "metadata";
    private String selectedSplitViewId = "metadata";
    private boolean splitVisible;

    /**
     * 创建 Debug 面板。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCDebugPane(MiniCWorkbenchViewModel viewModel) {
        this(viewModel, new MiniCWorkbenchControlHub());
    }

    /**
     * 创建 Debug 面板。
     *
     * @param viewModel UI 状态模型
     * @param controlHub 共享控制中心
     */
    public MiniCDebugPane(MiniCWorkbenchViewModel viewModel, MiniCWorkbenchControlHub controlHub) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.controlHub = Objects.requireNonNull(controlHub, "controlHub");
        this.viewportController = new MiniCDebugViewportController(this.controlHub, astZoom);
        this.astGraphRenderer = new MiniCDebugAstGraphRenderer(astZoom, viewportController);
        getStyleClass().add("debug-pane");
        sourceView = new MiniCSourceLoaderView(viewModel, false);
        sourceView.usePersistentEditorScrollBars("debug-source-editor-scroll");
        sourceView.installViewportTarget(controlHub);
        controlHub.addActiveTrackingAction(new MiniCActiveTrackingService(
                () -> viewportController.activeViewportAdapters(this, sourceView.viewportAdapter())
        )::trackActiveViewports);
        registerDebuggerCommands();
        HBox controls = controls();
        configureDebugBody();
        getChildren().addAll(controls, status, debugBody);
        VBox.setVgrow(debugBody, Priority.ALWAYS);
        viewModel.debugAsmViewProperty().addListener((observable, oldValue, newValue) -> refresh());
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        addEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyReleased);
        addEventFilter(ScrollEvent.SCROLL, this::handleShortcut);
        refresh();
    }

    private HBox controls() {
        Button start = button("从头开始", MiniCWorkbenchControlHub.DEBUG_START, "重新加载当前源码和断点，从第一条调试快照开始");
        Button end = button("运行到结束", MiniCWorkbenchControlHub.DEBUG_RUN_TO_END, "一直运行到程序结束或运行时错误");
        Button run = button("下个断点", MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT, "运行到下一个断点");
        Button step = button("本层下一句", MiniCWorkbenchControlHub.DEBUG_STEP_OVER, "不进入函数调用，运行本调用层的下一句");
        Button into = button("下一句", MiniCWorkbenchControlHub.DEBUG_STEP_INTO, "运行下一句，遇到函数调用时允许进入函数内部");
        Button backBreakpoint = button("上个断点", MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT, "回退到上一个断点命中状态");
        Button backOver = button("本层上一句", MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER, "不钻回函数内部，回退本调用层的上一句");
        Button back = button("上一句", MiniCWorkbenchControlHub.DEBUG_STEP_BACK, "回退上一句，允许回到函数调用内部");
        List.of(run, step, into, backBreakpoint, backOver, back)
                .forEach(this::formatPairedButton);
        List.of(start, end)
                .forEach(this::formatSingleButton);
        HBox forwardControls = new HBox(6, start, run, step, into);
        HBox backwardControls = new HBox(6, end, backBreakpoint, backOver, back);
        forwardControls.setAlignment(Pos.TOP_LEFT);
        backwardControls.setAlignment(Pos.TOP_LEFT);
        forwardControls.getStyleClass().add("debug-paired-row");
        backwardControls.getStyleClass().add("debug-paired-row");
        VBox pairedControls = new VBox(4);
        pairedControls.setAlignment(Pos.TOP_LEFT);
        pairedControls.getStyleClass().add("debug-paired-controls");
        pairedControls.getChildren().addAll(
                forwardControls,
                backwardControls
        );
        HBox controls = new HBox(
                6,
                pairedControls
        );
        controls.getStyleClass().add("controls");
        controls.getStyleClass().add("debug-controls");
        controls.setAlignment(Pos.TOP_LEFT);
        return controls;
    }

    private void registerDebuggerCommands() {
        controlHub.registerDebuggerCommands(new MiniCWorkbenchControlHub.DebuggerCommands(
                () -> true,
                this::startFromBeginning,
                this::debugStarted,
                viewModel::debugRunToEnd,
                this::debugStarted,
                viewModel::debugRunToBreakpoint,
                this::debugStarted,
                viewModel::debugStepOver,
                this::debugStarted,
                viewModel::debugStepInto,
                this::debugStarted,
                viewModel::debugBackToBreakpoint,
                this::debugStarted,
                viewModel::debugStepBackOver,
                this::debugStarted,
                viewModel::debugStepBack
        ));
    }

    private void startFromBeginning() {
        viewModel.setDebugBreakpoints(sourceView.breakpointLines());
        sourceView.loadCurrentSource();
        viewModel.startDebug();
    }

    private Button button(String text, String commandId, String tooltipText) {
        Button button = new Button(text);
        button.getStyleClass().add("control-secondary");
        button.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !controlHub.commandEnabled(commandId),
                viewModel.debugStartedProperty()
        ));
        button.setOnAction(event -> {
            controlHub.execute(commandId);
            refresh();
        });
        if (tooltipText != null && !tooltipText.isBlank()) {
            button.setTooltip(new Tooltip(tooltipText));
        }
        return button;
    }

    private boolean debugStarted() {
        return viewModel.debugStartedProperty().get();
    }

    private void formatSingleButton(Button button) {
        button.getStyleClass().add("debug-control-single-button");
        lockButtonSize(button, DEBUG_CONTROL_BUTTON_WIDTH);
    }

    private void formatPairedButton(Button button) {
        button.getStyleClass().add("debug-control-paired-button");
        lockButtonSize(button, DEBUG_CONTROL_BUTTON_WIDTH);
    }

    private void lockButtonSize(Button button, double width) {
        button.setMinSize(width, DEBUG_BUTTON_HEIGHT);
        button.setPrefSize(width, DEBUG_BUTTON_HEIGHT);
        button.setMaxSize(width, DEBUG_BUTTON_HEIGHT);
    }

    private ScrollPane scroll(String text) {
        return scroll(text, "debug:plain");
    }

    private ScrollPane scroll(String text, String viewportKey) {
        TextFlow body = explanationText(text, "body-text");
        ScrollPane scroll = new ScrollPane(body);
        scroll.getStyleClass().add("visual-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        viewportController.installScrollViewportTarget(scroll);
        return rememberViewport(scroll, viewportKey);
    }

    private void refresh() {
        if (!viewModel.debugStartedProperty().get() || viewModel.debugStateProperty().get() == null) {
            status.setText("Debug 未启动");
            sourceView.setCurrentExecutionLine(0);
            sourceView.setCurrentExecutionRange(null);
            setPrimaryContent(scroll(""));
            setSplitContent(scroll(""));
            return;
        }
        status.setText("Debug " + viewModel.debugStateProperty().get().executionState()
                + " · " + viewModel.debugStateProperty().get().currentSnapshot().stopReason()
                + " · step " + viewModel.debugStateProperty().get().currentSnapshot().visibleStepIndex()
                + " · " + viewModel.debugStateProperty().get().currentSnapshot().functionName());
        sourceView.setCurrentExecutionLine(currentSourceLine());
        sourceView.setCurrentExecutionRange(currentSourceRange());
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
        VBox.setVgrow(primaryContent, Priority.ALWAYS);
        VBox.setVgrow(splitContent, Priority.ALWAYS);
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
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    private void setSplitContent(Node content) {
        splitContent.getChildren().setAll(content);
        VBox.setVgrow(content, Priority.ALWAYS);
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
                metadataSection("调用栈", view.callStack().stream().map(MiniCDebugTextFormatter::frameText).toList()),
                metadataSection("变量", view.variables().stream().map(MiniCDebugTextFormatter::variableText).toList()),
                metadataSection("断点", view.breakpoints().stream().map(MiniCDebugTextFormatter::breakpointText).toList()),
                metadataSection("事件日志", boundedLines(view.events().stream().map(MiniCDebugTextFormatter::eventText).toList())),
                metadataSection("Snapshot 时间线", boundedLines(view.timeline().stream().map(MiniCDebugTextFormatter::timelineText).toList())),
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
        addSummaryRow(grid, 3, "源码", MiniCDebugTextFormatter.rangeText(view.currentSourceRange()));
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
        visibleLines.forEach(line -> body.getChildren().add(explanationText(line, "debug-section-line")));
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
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        viewportController.installScrollViewportTarget(scroll);
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
                + "\n源码: " + MiniCDebugTextFormatter.rangeText(view.currentSourceRange())
                + "\n\n调用栈:\n" + view.callStack().stream()
                .map(MiniCDebugTextFormatter::frameText)
                .collect(Collectors.joining("\n"))
                + "\n变量:\n" + view.variables().stream()
                .map(MiniCDebugTextFormatter::variableText)
                .collect(Collectors.joining("\n"))
                + "\n\n断点:\n" + view.breakpoints().stream()
                .map(MiniCDebugTextFormatter::breakpointText)
                .collect(Collectors.joining("\n"))
                + "\n\n事件:\n" + view.events().stream()
                .map(MiniCDebugTextFormatter::eventText)
                .collect(Collectors.joining("\n"))
                + "\n\n时间线:\n" + view.timeline().stream()
                .map(MiniCDebugTextFormatter::timelineText)
                .collect(Collectors.joining("\n"))
                + "\n\nstdout:\n" + view.stdout()
                + "\n\nstderr:\n" + view.stderr();
    }

    private String dataText(UiDebugDataStructureViewDto view) {
        if (view == null) {
            return "";
        }
        return "runtime:\n" + String.join("\n", runtimeSummary(view))
                + "\nvisuals:\n" + view.visuals().stream()
                .map(visual -> "  " + visual.type() + " " + visual.name()
                        + " · " + visual.kind()
                        + " · " + visual.summary()
                        + compactVisualCounts(visual.elements()))
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
                processSpaceSection("runtime", runtimeSummary(view)),
                visualCards(view.visuals()),
                metadataSection("warnings", view.warnings())
        );
        return wrap(content, viewportKey);
    }

    private List<String> runtimeSummary(UiDebugDataStructureViewDto view) {
        return List.of(
                "current: " + view.processSpace().currentFunctionName()
                        + " / " + view.processSpace().currentInstructionId(),
                "functions=" + view.processSpace().functions().size()
                        + " · stackFrames=" + view.processSpace().stackFrames().size()
                        + " · heapEntries=" + view.processSpace().heapValues().size(),
                "stdout: " + MiniCDebugTextFormatter.emptyText(view.processSpace().stdout())
        );
    }

    private Node processSpaceSection(String title, List<String> lines) {
        VBox section = new VBox(4);
        section.getStyleClass().add("debug-process-section");
        Label heading = label(title, "debug-process-title");
        VBox body = new VBox(2);
        body.getStyleClass().add("debug-section-body");
        List<String> visibleLines = lines.isEmpty() ? List.of("(empty)") : lines;
        visibleLines.forEach(line -> body.getChildren().add(explanationText(line, "debug-section-line")));
        section.getChildren().addAll(heading, body);
        return section;
    }

    private Node visualCards(List<UiDebugVisualStructureDto> visuals) {
        VBox section = new VBox(6);
        section.getStyleClass().add("debug-visuals");
        section.getChildren().add(label("visual structures", "debug-section-title"));
        if (visuals.isEmpty()) {
            section.getChildren().add(explanationText("(empty)", "debug-section-line"));
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
        card.getChildren().add(explanationText(visual.summary(), "debug-section-line"));
        if (!visual.explanation().isBlank()) {
            Tooltip.install(card, explanationTooltip(visual.explanation()));
        }
        String counts = compactVisualCounts(visual.elements());
        if (!counts.isBlank()) {
            card.getChildren().add(explanationText(counts.substring(3), "debug-section-line"));
        }
        Node diagram = visualDiagramRenderer.visualDiagram(visual);
        if (diagram != null) {
            card.getChildren().add(diagram);
        }
        compactVisualElementLines(visual)
                .forEach(line -> card.getChildren().add(explanationText(line, "debug-section-line")));
        return card;
    }

    private String compactVisualCounts(List<UiDebugVisualElementDto> elements) {
        long cells = elements.stream().filter(element -> element.kind().equals("ARRAY_CELL")).count();
        long nodes = elements.stream().filter(element -> element.kind().equals("GRAPH_NODE")).count();
        long edges = elements.stream().filter(element -> element.kind().equals("GRAPH_EDGE")).count();
        long fields = elements.stream().filter(element -> element.kind().equals("COMPOSITE_PART")).count();
        ArrayList<String> parts = new ArrayList<>();
        if (cells > 0) {
            parts.add("cells=" + cells);
        }
        if (nodes > 0) {
            parts.add("nodes=" + nodes);
        }
        if (edges > 0) {
            parts.add("edges=" + edges);
        }
        if (fields > 0) {
            parts.add("fields=" + fields);
        }
        return parts.isEmpty() ? "" : " · " + String.join(" · ", parts);
    }

    private List<String> compactVisualElementLines(UiDebugVisualStructureDto visual) {
        boolean diagramBacked = visual.elements().stream()
                .anyMatch(element -> element.kind().equals("ARRAY_CELL") || element.kind().equals("GRAPH_NODE"));
        if (diagramBacked) {
            return List.of();
        }
        return visual.elements().stream()
                .filter(element -> element.kind().equals("COMPOSITE_PART"))
                .map(MiniCDebugTextFormatter::visualElementText)
                .filter(line -> !line.isBlank())
                .limit(12)
                .toList();
    }

    private String astText(UiDebugAstViewDto view) {
        if (view == null || view.activeNode() == null) {
            return "";
        }
        return view.activeNode().kind() + " " + view.activeNode().label()
                + "\nrange: " + MiniCDebugTextFormatter.rangeText(view.activeNode().sourceRange())
                + "\n" + view.activeNode().explanation()
                + "\nIR: " + view.relatedIrIds()
                + "\nASM: " + view.relatedAsmIds();
    }

    private ScrollPane astContent(UiDebugAstViewDto view, String viewportKey) {
        VBox content = new VBox(8);
        content.getStyleClass().add("debug-ast-view");
        content.getChildren().add(astSummary(view));
        content.getChildren().add(astGraphRenderer.debugAstGraph(view));
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("visual-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        viewportController.installScrollViewportTarget(scroll);
        return rememberViewport(scroll, viewportKey);
    }

    private Node astSummary(UiDebugAstViewDto view) {
        if (view == null || view.activeNode() == null) {
            return metadataSection("当前 AST 节点", List.of("(empty)"));
        }
        return metadataSection("当前 AST 节点", List.of(
                view.activeNode().kind() + " " + view.activeNode().label(),
                "range: " + MiniCDebugTextFormatter.rangeText(view.activeNode().sourceRange()),
                view.activeNode().explanation(),
                "IR: " + view.relatedIrIds(),
                "ASM: " + view.relatedAsmIds()
        ));
    }

    private String irText(UiDebugIrViewDto view) {
        if (view == null) {
            return "";
        }
        return view.explanation()
                + "\ncurrent: " + view.currentInstructionId()
                + "\nrange: " + MiniCDebugTextFormatter.rangeText(view.currentSourceRange())
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
                "range: " + MiniCDebugTextFormatter.rangeText(view.currentSourceRange())
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
        TextFlow text = MiniCTextFlowFactory.textFlow(
                irTextHighlighter.highlight(line.text()),
                "debug-code-text",
                line.active()
        );
        HBox.setHgrow(text, Priority.ALWAYS);
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
        TextFlow text = MiniCTextFlowFactory.textFlow(
                assemblyTextHighlighter.highlight(line.text()),
                "debug-code-text",
                line.active()
        );
        HBox.setHgrow(text, Priority.ALWAYS);
        row.getChildren().addAll(number, text);
        return row;
    }

    private int currentSourceLine() {
        if (viewModel.debugStateProperty().get() == null
                || viewModel.debugStateProperty().get().currentSnapshot().sourceRange() == null) {
            return 0;
        }
        return viewModel.debugStateProperty().get().currentSnapshot().sourceRange().startLine();
    }

    private UiSourceSpanDto currentSourceRange() {
        if (viewModel.debugStateProperty().get() == null) {
            return null;
        }
        return viewModel.debugStateProperty().get().currentSnapshot().sourceRange();
    }

    private TextFlow explanationText(String text, String styleClass) {
        return MiniCTextFlowFactory.textFlow(
                explanationTextHighlighter.highlight(text),
                styleClass,
                false
        );
    }

    private Tooltip explanationTooltip(String text) {
        Tooltip tooltip = new Tooltip();
        tooltip.setGraphic(explanationText(text, "debug-section-line"));
        return tooltip;
    }

    private void installExplanationTooltip(Node node, UiDebugVisualElementDto element) {
        String explanation = element.metadata().getOrDefault("explanation", "");
        if (!explanation.isBlank()) {
            Tooltip.install(node, explanationTooltip(explanation));
        }
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private void handleKeyPressed(KeyEvent event) {
        if (!isModifier(event.getCode())) {
            pressedKeys.add(event.getCode());
        }
        handleShortcut(event);
    }

    private void handleKeyReleased(KeyEvent event) {
        if (!isModifier(event.getCode())) {
            pressedKeys.remove(event.getCode());
        }
    }

    private void handleShortcut(KeyEvent event) {
        if (event.isConsumed()) {
            return;
        }
        if (handleDebugCommandShortcut(event) || handleViewportShortcut(event)) {
            return;
        }
    }

    private boolean handleDebugCommandShortcut(KeyEvent event) {
        for (String action : DEBUG_SHORTCUT_ACTIONS) {
            if (keyBindings.matches(action, event, pressedKeys)) {
                controlHub.execute(action);
                refresh();
                event.consume();
                return true;
            }
        }
        return false;
    }

    private boolean handleViewportShortcut(KeyEvent event) {
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, event, pressedKeys)) {
            controlHub.handleZoom(Point2D.ZERO, viewportZoomDelta(1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, event, pressedKeys)) {
            controlHub.handleZoom(Point2D.ZERO, viewportZoomDelta(-1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP, event, pressedKeys)) {
            controlHub.handleScrollVertical(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, event, pressedKeys)) {
            controlHub.handleScrollVertical(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE, event, pressedKeys)) {
            controlHub.handleCenterActive();
            event.consume();
            return true;
        }
        return false;
    }

    private void handleShortcut(ScrollEvent event) {
        if (event.isConsumed()) {
            return;
        }
        if (handleDebugCommandShortcut(event) || handleViewportShortcut(event)) {
            return;
        }
    }

    private boolean handleDebugCommandShortcut(ScrollEvent event) {
        for (String action : DEBUG_SHORTCUT_ACTIONS) {
            if (keyBindings.matches(action, event, pressedKeys)) {
                controlHub.execute(action);
                refresh();
                event.consume();
                return true;
            }
        }
        return false;
    }

    private boolean handleViewportShortcut(ScrollEvent event) {
        Point2D point = new Point2D(event.getX(), event.getY());
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, event, pressedKeys)) {
            controlHub.handleZoom(point, viewportZoomDelta(1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, event, pressedKeys)) {
            controlHub.handleZoom(point, viewportZoomDelta(-1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP, event, pressedKeys)) {
            controlHub.handleScrollVertical(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, event, pressedKeys)) {
            controlHub.handleScrollVertical(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE, event, pressedKeys)) {
            controlHub.handleCenterActive();
            event.consume();
            return true;
        }
        return false;
    }

    private double viewportZoomDelta(double direction) {
        return direction * controlHub.viewportRegistry().currentTarget()
                .filter(adapter -> adapter.type() == MiniCControlTargetType.TEXT)
                .map(adapter -> TEXT_ZOOM_STEP)
                .orElse(viewportController.graphZoomStep());
    }

    private static boolean isModifier(KeyCode code) {
        return code == KeyCode.CONTROL
                || code == KeyCode.ALT
                || code == KeyCode.SHIFT
                || code == KeyCode.META;
    }

    private record DebugView(String id, String title) {
    }

}
