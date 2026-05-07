package minic.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import minic.uiapi.UiAssemblyLineVisualDto;
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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Workbench 独立 Debug 模式首版面板。
 */
public final class MiniCDebugPane extends VBox {
    private static final List<DebugView> DEBUG_VIEWS = List.of(
            new DebugView("metadata", "元数据"),
            new DebugView("data", "数据结构"),
            new DebugView("ast", "AST"),
            new DebugView("ir", "IR"),
            new DebugView("asm", "ASM")
    );
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCSourceLoaderView sourceView;
    private final SplitPane splitPane = new SplitPane();
    private final HBox primaryDebugView = new HBox();
    private final HBox splitDebugView = new HBox();
    private final VBox primaryContent = new VBox();
    private final VBox splitContent = new VBox();
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
        sourceView = new MiniCSourceLoaderView(viewModel);
        HBox controls = controls();
        configureDebugView(primaryDebugView, primaryContent, false);
        configureDebugView(splitDebugView, splitContent, true);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().setAll(primaryDebugView);
        splitPane.setDividerPositions(0.5);
        getChildren().addAll(controls, status, sourceView, splitPane);
        VBox.setVgrow(sourceView, Priority.ALWAYS);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        viewModel.debugStateProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.debugMetadataViewProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.debugDataStructureViewProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.debugAstViewProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.debugIrViewProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.debugAsmViewProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
    }

    private HBox controls() {
        Button start = button("启动", () -> {
            viewModel.setDebugBreakpoints(sourceView.breakpointLines());
            sourceView.startSession();
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
        Label body = label(text, "body-text");
        body.setWrapText(true);
        ScrollPane scroll = new ScrollPane(body);
        scroll.getStyleClass().add("visual-scroll");
        scroll.setFitToWidth(true);
        return scroll;
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

    private void configureDebugView(HBox root, VBox content, boolean split) {
        root.getStyleClass().add("debug-view-shell");
        VBox selector = new VBox(4);
        selector.getStyleClass().add("debug-view-selector");
        DEBUG_VIEWS.forEach(view -> selector.getChildren().add(viewButton(view, split)));
        content.getStyleClass().add("debug-view-content");
        root.getChildren().setAll(selector, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        refreshViewButtons(split);
    }

    private Button viewButton(DebugView view, boolean split) {
        Button button = new Button(view.title());
        button.getStyleClass().add("debug-view-button");
        button.setAccessibleText("Debug视图:" + view.title());
        button.setOnAction(event -> {
            if (split) {
                selectedSplitViewId = view.id();
                setSplitContent(contentFor(selectedSplitViewId, true));
            } else {
                selectedViewId = view.id();
                setPrimaryContent(contentFor(selectedViewId, false));
            }
            refreshViewButtons(split);
        });
        return button;
    }

    private void refreshViewButtons(boolean split) {
        HBox root = split ? splitDebugView : primaryDebugView;
        String selected = split ? selectedSplitViewId : selectedViewId;
        VBox selector = (VBox) root.getChildren().getFirst();
        for (int index = 0; index < DEBUG_VIEWS.size(); index++) {
            Button button = (Button) selector.getChildren().get(index);
            button.getStyleClass().remove("active");
            if (DEBUG_VIEWS.get(index).id().equals(selected)) {
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
            if (!splitPane.getItems().contains(splitDebugView)) {
                splitPane.getItems().add(splitDebugView);
            }
            selectedSplitViewId = selectedViewId;
            setSplitContent(contentFor(selectedSplitViewId, true));
            refreshViewButtons(true);
        } else {
            splitPane.getItems().remove(splitDebugView);
        }
    }

    private Node contentFor(String viewId, boolean split) {
        return switch (viewId) {
            case "metadata" -> split
                    ? scroll(metadataText(viewModel.debugMetadataViewProperty().get()))
                    : metadataContent(viewModel.debugMetadataViewProperty().get());
            case "data" -> split
                    ? scroll(dataText(viewModel.debugDataStructureViewProperty().get()))
                    : dataContent(viewModel.debugDataStructureViewProperty().get());
            case "ast" -> scroll(astText(viewModel.debugAstViewProperty().get()));
            case "ir" -> scroll(irText(viewModel.debugIrViewProperty().get()));
            case "asm" -> scroll(asmText(viewModel.debugAsmViewProperty().get()));
            default -> scroll("");
        };
    }

    private ScrollPane metadataContent(UiDebugMetadataViewDto view) {
        VBox content = new VBox(10);
        content.getStyleClass().add("debug-metadata");
        if (view == null) {
            return wrap(content);
        }
        content.getChildren().addAll(
                metadataSummary(view),
                metadataSection("调用栈", view.callStack().stream().map(this::frameText).toList()),
                metadataSection("变量", view.variables().stream().map(this::variableText).toList()),
                metadataSection("断点", view.breakpoints().stream().map(this::breakpointText).toList()),
                metadataSection("事件日志", view.events().stream().map(this::eventText).toList()),
                metadataSection("Snapshot 时间线", view.timeline().stream().map(this::timelineText).toList()),
                metadataSection("stdout", List.of(view.stdout().isBlank() ? "(empty)" : view.stdout())),
                metadataSection("stderr", List.of(view.stderr().isBlank() ? "(empty)" : view.stderr()))
        );
        return wrap(content);
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

    private ScrollPane wrap(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("visual-scroll");
        scroll.setFitToWidth(true);
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

    private ScrollPane dataContent(UiDebugDataStructureViewDto view) {
        VBox content = new VBox(10);
        content.getStyleClass().add("debug-data-space");
        if (view == null) {
            return wrap(content);
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
        return wrap(content);
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
        visual.elements().forEach(element -> card.getChildren().add(label(visualElementText(element), "debug-section-line")));
        return card;
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
}
