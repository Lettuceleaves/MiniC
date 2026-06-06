package minic.uilocal;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import minic.uilocal.control.MiniCViewportAdapter;
import minic.uilocal.control.MiniCWorkbenchControlHub;
import minic.uiapi.UiSourceSpanDto;

import java.util.List;
import java.util.Objects;

/**
 * 源码加载和会话启动控件。
 */
public final class MiniCSourceLoaderView extends VBox {
    private static final String CONTROL_SCROLL_FILTER_INSTALLED_KEY =
            "minic.uilocal.source.controlScrollFilterInstalled";

    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCCodeEditor sourceEditor = new MiniCCodeEditor();
    private final Button startButton = new Button("开始");
    private final Button openButton = new Button("打开");
    private final Button saveButton = new Button("保存");
    private final Button saveAsButton = new Button("另存为");
    private final Runnable openAction;
    private final Runnable saveAction;
    private final Runnable saveAsAction;

    /**
     * 创建源码加载视图。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCSourceLoaderView(MiniCWorkbenchViewModel viewModel) {
        this(viewModel, () -> {
        }, () -> {
        });
    }

    /**
     * 创建源码加载视图。
     *
     * @param viewModel UI 状态模型
     * @param showControls 是否显示加载和启动工具条
     */
    public MiniCSourceLoaderView(MiniCWorkbenchViewModel viewModel, boolean showControls) {
        this(viewModel, () -> {
        }, () -> {
        }, () -> {
        }, showControls);
    }

    /**
     * 创建源码加载视图。
     *
     * @param viewModel UI 状态模型
     * @param openAction 打开文件动作
     * @param saveAction 保存文件动作
     */
    public MiniCSourceLoaderView(MiniCWorkbenchViewModel viewModel, Runnable openAction, Runnable saveAction) {
        this(viewModel, openAction, saveAction, () -> {
        }, true);
    }

    public MiniCSourceLoaderView(
            MiniCWorkbenchViewModel viewModel,
            Runnable openAction,
            Runnable saveAction,
            Runnable saveAsAction
    ) {
        this(viewModel, openAction, saveAction, saveAsAction, true);
    }

    private MiniCSourceLoaderView(
            MiniCWorkbenchViewModel viewModel,
            Runnable openAction,
            Runnable saveAction,
            Runnable saveAsAction,
            boolean showControls
    ) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.openAction = Objects.requireNonNull(openAction, "openAction");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
        this.saveAsAction = Objects.requireNonNull(saveAsAction, "saveAsAction");
        getStyleClass().add("source-loader");
        String initialSourceName = viewModel.sourceNameProperty().get();
        String initialSource = viewModel.sourceTextProperty().get();
        if (initialSourceName == null || initialSourceName.isBlank()) {
            MiniCSampleProgram sample = MiniCSamplePrograms.defaultSample();
            sourceEditor.setText(sample.source());
        } else {
            sourceEditor.setText(initialSource);
        }
        startButton.getStyleClass().add("control-primary");
        openButton.getStyleClass().add("control-secondary");
        saveButton.getStyleClass().add("control-secondary");
        saveAsButton.getStyleClass().add("control-secondary");
        startButton.setOnAction(event -> startSession());
        openButton.setOnAction(event -> this.openAction.run());
        saveButton.setOnAction(event -> this.saveAction.run());
        saveAsButton.setOnAction(event -> this.saveAsAction.run());
        sourceEditor.replaceBreakpoints(viewModel.debugBreakpointLinesProperty().get());
        sourceEditor.setBreakpointChangeAction(() -> viewModel.setDebugBreakpoints(sourceEditor.breakpointLines()));
        viewModel.debugBreakpointLinesProperty().addListener((observable, oldValue, newValue) ->
                sourceEditor.replaceBreakpoints(newValue));
        sourceEditor.textProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> sourceEditor.render(viewModel.realtimeAnalysisProperty().get()));
            submitRealtimeSource();
        });
        viewModel.sourceTextProperty().addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(sourceEditor.getText(), newValue)) {
                sourceEditor.setText(newValue);
                submitRealtimeSource();
            }
        });
        viewModel.realtimeAnalysisProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                submitRealtimeSource();
                return;
            }
            sourceEditor.render(newValue);
        });
        Platform.runLater(this::submitRealtimeSource);
        if (showControls) {
            HBox controls = new HBox(6);
            controls.getStyleClass().add("loader-controls");
            controls.getChildren().addAll(startButton, openButton, saveButton, saveAsButton);
            getChildren().add(controls);
        }
        getChildren().add(sourceEditor);
        VBox.setVgrow(sourceEditor, Priority.ALWAYS);
    }

    /**
     * 使用当前编辑器内容启动观测会话。
     */
    public void startSession() {
        loadCurrentSource();
        submitRealtimeSource();
        viewModel.startSession();
    }

    /**
     * 将当前编辑器内容加载到状态模型，但不启动普通编译观察会话。
     */
    public void loadCurrentSource() {
        String currentName = viewModel.sourceNameProperty().get();
        String name = currentName == null || currentName.isBlank() ? fallbackSourceName() : currentName;
        viewModel.loadSource(name, sourceEditor.getText());
        submitRealtimeSource();
    }

    /**
     * 返回当前源码编辑器断点。
     *
     * @return 一基行号列表
     */
    public List<Integer> breakpointLines() {
        return sourceEditor.breakpointLines();
    }

    /**
     * 设置源码编辑器断点。
     *
     * @param line 一基行号
     * @param enabled 是否启用
     */
    public void setBreakpoint(int line, boolean enabled) {
        sourceEditor.setBreakpoint(line, enabled);
    }

    /**
     * 设置源码编辑器当前执行行。
     *
     * @param line 一基行号；小于 1 表示清除
     */
    public void setCurrentExecutionLine(int line) {
        sourceEditor.setCurrentExecutionLine(line);
    }

    /**
     * 设置源码编辑器当前执行源码范围。
     *
     * @param range 源码范围；{@code null} 表示清除
     */
    public void setCurrentExecutionRange(UiSourceSpanDto range) {
        sourceEditor.setCurrentExecutionRange(range);
    }

    /**
     * 返回源码编辑器文本视口适配器。
     *
     * @return 文本视口适配器
     */
    public MiniCViewportAdapter viewportAdapter() {
        return sourceEditor.viewportAdapter();
    }

    /**
     * 将源码编辑器注册到共享控制中心，参与 hover/pin 目标解析。
     *
     * @param controlHub 共享控制中心
     */
    public void installViewportTarget(MiniCWorkbenchControlHub controlHub) {
        MiniCWorkbenchControlHub hub = Objects.requireNonNull(controlHub, "controlHub");
        hub.installViewportTarget(sourceEditor, viewportAdapter());
        if (Boolean.TRUE.equals(sourceEditor.getProperties().get(CONTROL_SCROLL_FILTER_INSTALLED_KEY))) {
            return;
        }
        sourceEditor.getProperties().put(CONTROL_SCROLL_FILTER_INSTALLED_KEY, true);
        sourceEditor.addEventFilter(ScrollEvent.SCROLL, event -> {
            MiniCViewportAdapter adapter = viewportAdapter();
            hub.viewportRegistry().businessActive(adapter);
            if (event.isControlDown() && adapter.canZoom()) {
                hub.handleZoom(new Point2D(event.getX(), event.getY()), event.getDeltaY() > 0 ? 1.0 : -1.0);
                event.consume();
                return;
            }
            if (!event.isShiftDown() && adapter.canScrollVertical() && event.getDeltaY() != 0) {
                hub.handleScrollVertical(-event.getDeltaY());
                event.consume();
            }
        });
    }

    /**
     * 让源码编辑器显示常驻垂直滚动条，方便在 Debugger 源码页直接拖动。
     *
     * @param scrollStyleClass 内部滚动容器样式类
     */
    public void usePersistentEditorScrollBars(String scrollStyleClass) {
        sourceEditor.addScrollContainerStyleClass(scrollStyleClass);
        sourceEditor.setScrollBarPolicies(
                ScrollPane.ScrollBarPolicy.AS_NEEDED,
                ScrollPane.ScrollBarPolicy.ALWAYS
        );
    }

    private void submitRealtimeSource() {
        String currentName = viewModel.sourceNameProperty().get();
        String name = currentName == null || currentName.isBlank() ? fallbackSourceName() : currentName;
        viewModel.submitRealtimeSource(name, sourceEditor.getText());
    }

    private String fallbackSourceName() {
        return "untitled.mc";
    }

}
