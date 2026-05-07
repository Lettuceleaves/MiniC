package minic.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.geometry.Orientation;
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
import minic.uiapi.UiIrLineVisualDto;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Workbench 独立 Debug 模式首版面板。
 */
public final class MiniCDebugPane extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCSourceLoaderView sourceView;
    private final SplitPane splitPane = new SplitPane();
    private final TabPane tabs = new TabPane();
    private final TabPane splitTabs = new TabPane();
    private final Label status = label("", "body-text");
    private final TextField breakpointLine = new TextField("1");
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
        tabs.getStyleClass().add("debug-tabs");
        splitTabs.getStyleClass().add("debug-tabs");
        tabs.getTabs().addAll(
                tab("元数据", ""),
                tab("数据结构", ""),
                tab("AST", ""),
                tab("IR", ""),
                tab("ASM", "")
        );
        splitTabs.getTabs().addAll(
                tab("元数据", ""),
                tab("数据结构", ""),
                tab("AST", ""),
                tab("IR", ""),
                tab("ASM", "")
        );
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().setAll(tabs);
        splitPane.setDividerPositions(0.5);
        getChildren().addAll(controls, status, sourceView, splitPane);
        VBox.setVgrow(sourceView, Priority.ALWAYS);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        viewModel.debugStateProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.debugMetadataViewProperty().addListener((observable, oldValue, newValue) -> refresh());
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

    private Tab tab(String title, String text) {
        Tab tab = new Tab(title);
        tab.setClosable(false);
        tab.setContent(scroll(text));
        return tab;
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
            setTabText(0, "");
            setTabText(1, "");
            setTabText(2, "");
            setTabText(3, "");
            setTabText(4, "");
            return;
        }
        status.setText("Debug " + viewModel.debugStateProperty().get().executionState()
                + " · " + viewModel.debugStateProperty().get().currentSnapshot().stopReason()
                + " · step " + viewModel.debugStateProperty().get().currentSnapshot().visibleStepIndex()
                + " · " + viewModel.debugStateProperty().get().currentSnapshot().functionName());
        sourceView.setCurrentExecutionLine(viewModel.debugStateProperty().get().currentSnapshot().sourceRange().startLine());
        setTabText(0, metadataText(viewModel.debugMetadataViewProperty().get()));
        setTabText(1, dataText(viewModel.debugDataStructureViewProperty().get()));
        setTabText(2, astText(viewModel.debugAstViewProperty().get()));
        setTabText(3, irText(viewModel.debugIrViewProperty().get()));
        setTabText(4, asmText(viewModel.debugAsmViewProperty().get()));
        if (splitVisible) {
            refreshSplitTabs();
        }
    }

    private void setTabText(int index, String text) {
        tabs.getTabs().get(index).setContent(scroll(text == null ? "" : text));
    }

    private void setSplitTabText(int index, String text) {
        splitTabs.getTabs().get(index).setContent(scroll(text == null ? "" : text));
    }

    private void toggleSplit() {
        splitVisible = !splitVisible;
        if (splitVisible) {
            if (!splitPane.getItems().contains(splitTabs)) {
                splitPane.getItems().add(splitTabs);
            }
            splitTabs.getSelectionModel().select(tabs.getSelectionModel().getSelectedIndex());
            refreshSplitTabs();
        } else {
            splitPane.getItems().remove(splitTabs);
        }
    }

    private void refreshSplitTabs() {
        setSplitTabText(0, metadataText(viewModel.debugMetadataViewProperty().get()));
        setSplitTabText(1, dataText(viewModel.debugDataStructureViewProperty().get()));
        setSplitTabText(2, astText(viewModel.debugAstViewProperty().get()));
        setSplitTabText(3, irText(viewModel.debugIrViewProperty().get()));
        setSplitTabText(4, asmText(viewModel.debugAsmViewProperty().get()));
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

    private String visualElementText(UiDebugVisualElementDto element) {
        return "    " + element.kind() + " " + element.id()
                + " " + element.label()
                + " " + element.metadata();
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
}
