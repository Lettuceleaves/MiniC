package minic.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.geometry.Orientation;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import minic.uiapi.UiAssemblyLineVisualDto;
import minic.uiapi.UiDebugAsmViewDto;
import minic.uiapi.UiDebugAstViewDto;
import minic.uiapi.UiDebugDataStructureViewDto;
import minic.uiapi.UiDebugIrViewDto;
import minic.uiapi.UiDebugMetadataViewDto;
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
            sourceView.startSession();
            viewModel.startDebug();
        });
        Button breakpoint = button("断点", () -> {
            if (!viewModel.debugStartedProperty().get()) {
                sourceView.startSession();
                viewModel.startDebug();
            }
            int line = viewModel.debugStateProperty().get() == null
                    ? 1
                    : Math.max(1, viewModel.debugStateProperty().get().currentSnapshot().sourceRange() == null
                    ? 1
                    : viewModel.debugStateProperty().get().currentSnapshot().sourceRange().startLine());
            viewModel.setDebugBreakpoint(line);
        });
        Button run = button("运行到断点", viewModel::debugRunToBreakpoint);
        Button back = button("单退", viewModel::debugStepBack);
        Button split = button("拆分", this::toggleSplit);
        HBox controls = new HBox(6, start, breakpoint, run, back, split);
        controls.getStyleClass().add("controls");
        return controls;
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
            setTabText(0, "");
            setTabText(1, "");
            setTabText(2, "");
            setTabText(3, "");
            setTabText(4, "");
            return;
        }
        status.setText("Debug " + viewModel.debugStateProperty().get().executionState()
                + " · " + viewModel.debugStateProperty().get().currentSnapshot().stopReason());
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
                + "\n变量:\n" + view.variables().stream()
                .map(variable -> "  " + variable.name() + " = " + variable.valueSummary())
                .collect(Collectors.joining("\n"))
                + "\nstdout:\n" + view.stdout();
    }

    private String dataText(UiDebugDataStructureViewDto view) {
        if (view == null) {
            return "";
        }
        return "stack frames: " + view.processSpace().stackFrames().size()
                + "\nvisuals:\n" + view.visuals().stream()
                .map(visual -> "  " + visual.type() + " " + visual.name() + " · " + visual.summary())
                .collect(Collectors.joining("\n"));
    }

    private String astText(UiDebugAstViewDto view) {
        if (view == null || view.activeNode() == null) {
            return "";
        }
        return view.activeNode().kind() + " " + view.activeNode().label()
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
                + "\n" + view.lines().stream()
                .filter(UiIrLineVisualDto::active)
                .map(UiIrLineVisualDto::text)
                .collect(Collectors.joining("\n"));
    }

    private String asmText(UiDebugAsmViewDto view) {
        if (view == null) {
            return "";
        }
        return view.explanation()
                + "\n" + view.lines().stream()
                .filter(UiAssemblyLineVisualDto::active)
                .map(UiAssemblyLineVisualDto::text)
                .collect(Collectors.joining("\n"));
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
