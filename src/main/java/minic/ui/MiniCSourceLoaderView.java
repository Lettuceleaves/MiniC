package minic.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * 源码加载和会话启动控件。
 */
public final class MiniCSourceLoaderView extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCCodeEditor sourceEditor = new MiniCCodeEditor();
    private final Button startButton = new Button("开始");
    private final Button openButton = new Button("打开");
    private final Button saveButton = new Button("保存");
    private final Runnable openAction;
    private final Runnable saveAction;

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
     * @param openAction 打开文件动作
     * @param saveAction 保存文件动作
     */
    public MiniCSourceLoaderView(MiniCWorkbenchViewModel viewModel, Runnable openAction, Runnable saveAction) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.openAction = Objects.requireNonNull(openAction, "openAction");
        this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
        getStyleClass().add("source-loader");
        HBox controls = new HBox(6);
        controls.getStyleClass().add("loader-controls");
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
        startButton.setOnAction(event -> startSession());
        openButton.setOnAction(event -> this.openAction.run());
        saveButton.setOnAction(event -> this.saveAction.run());
        sourceEditor.setBreakpointChangeAction(() -> viewModel.setDebugBreakpoints(sourceEditor.breakpointLines()));
        sourceEditor.textProperty().addListener((observable, oldValue, newValue) -> {
            sourceEditor.render(viewModel.realtimeAnalysisProperty().get());
            submitRealtimeSource();
        });
        viewModel.realtimeAnalysisProperty().addListener((observable, oldValue, newValue) -> {
            sourceEditor.render(newValue);
        });
        Platform.runLater(this::submitRealtimeSource);
        controls.getChildren().addAll(startButton, openButton, saveButton);
        getChildren().addAll(controls, sourceEditor);
        VBox.setVgrow(sourceEditor, Priority.ALWAYS);
    }

    /**
     * 使用当前编辑器内容启动观测会话。
     */
    public void startSession() {
        String currentName = viewModel.sourceNameProperty().get();
        String name = currentName == null || currentName.isBlank() ? fallbackSourceName() : currentName;
        viewModel.loadSource(name, sourceEditor.getText());
        submitRealtimeSource();
        viewModel.startSession();
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

    private void submitRealtimeSource() {
        String currentName = viewModel.sourceNameProperty().get();
        String name = currentName == null || currentName.isBlank() ? fallbackSourceName() : currentName;
        viewModel.submitRealtimeSource(name, sourceEditor.getText());
    }

    private String fallbackSourceName() {
        return "untitled.mc";
    }

}
