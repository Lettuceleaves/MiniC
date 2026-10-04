package craken.ui.run;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import craken.compiler.Diagnostic;
import craken.compiler.link.ExecutableArtifact;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.display.UiProgressBar;
import craken.ui.component.input.UiTextArea;
import craken.ui.interaction.inputoutput.InputOutputPanel;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 一次运行独有的编译反馈及交互终端；切换面板不会重复运行程序。 */
final class RunPanel extends BorderPane implements AutoCloseable {
    private final Path sourcePath;
    private final Runnable cancelCompilation;
    private final Runnable requestClose;
    private final Label status = new Label("正在编译…");
    private final UiTextArea output = new UiTextArea();
    private final Button cancel = new Button("取消");
    private final HBox toolbar;
    private final UiProgressBar progressBar = new UiProgressBar(0);
    private final StackPane progressView = new StackPane();
    private InputOutputPanel inputOutput;
    private ExecutableArtifact artifact;
    private boolean mounted;
    private boolean closed;
    private boolean compiling = true;

    RunPanel(Path sourcePath, Runnable cancelCompilation, Runnable requestClose) {
        this.sourcePath = Objects.requireNonNull(sourcePath);
        this.cancelCompilation = Objects.requireNonNull(cancelCompilation);
        this.requestClose = Objects.requireNonNull(requestClose);
        sceneProperty().addListener((observable, previous, scene) -> {
            if (scene != null) {
                mounted = true;
                start();
            }
        });
        setMinSize(0, 0);
        setFocusTraversable(true);
        getStyleClass().add("run-panel");
        Label title = new Label("输入输出 · " + sourcePath.getFileName());
        title.setId("run-title");
        title.getStyleClass().add("terminal-title");
        title.setTooltip(new UiTooltip(sourcePath.toString()));
        status.setId("run-status");
        status.getStyleClass().add("terminal-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        cancel.setId("run-cancel");
        cancel.getStyleClass().add("interaction-action");
        cancel.setTooltip(new UiTooltip("取消本次编译"));
        cancel.setOnAction(event -> cancelCompilation.run());
        toolbar = new HBox(8, title, status, spacer, cancel);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 8, 0, 12));
        toolbar.setMinHeight(32);
        toolbar.setPrefHeight(32);
        toolbar.setMaxHeight(32);
        toolbar.getStyleClass().add("interaction-toolbar");
        setTop(toolbar);
        progressBar.setId("run-progress");
        progressBar.getStyleClass().add("run-progress");
        progressBar.setMinSize(0, 0);
        progressBar.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        progressBar.setAccessibleText("编译进度");
        Label percentage = new Label();
        percentage.setId("run-progress-percentage");
        percentage.getStyleClass().add("run-progress-percentage");
        percentage.textProperty().bind(progressBar.progressProperty().multiply(100).asString("%.0f%%"));
        percentage.setMouseTransparent(true);
        progressView.setId("run-progress-view");
        progressView.setMinSize(0, 0);
        progressView.getChildren().addAll(progressBar, percentage);
        // 内容区被压缩时，进度填充和百分比也不能越界画到上方标题栏。
        Rectangle progressClip = new Rectangle();
        progressClip.widthProperty().bind(progressView.widthProperty());
        progressClip.heightProperty().bind(progressView.heightProperty());
        progressView.setClip(progressClip);
        output.setId("run-diagnostics");
        output.setEditable(false);
        output.setWrapText(true);
        setCenter(progressView);
    }

    void progress(RunCompilation.Progress progress) {
        if (closed || !compiling || progress.fraction() < progressBar.getProgress()) return;
        // 相同/迟到的较小值不会让已经完成的阶段倒退。
        progressBar.setProgress(progress.fraction());
        progressBar.setAccessibleHelp("已完成 " + progress.completedStages() + " / " + progress.totalStages() + " 个编译阶段");
    }

    void compiled(ExecutableArtifact executable) {
        if (closed) return;
        compiling = false;
        progressBar.setProgress(1);
        boolean ownsFocus = ownsInputFocus();
        artifact = Objects.requireNonNull(executable);
        cancel.setDisable(true);
        inputOutput = new InputOutputPanel(sourcePath.toAbsolutePath().getParent(), executable.path());
        inputOutput.setOnCloseRequest(requestClose);
        setTop(null);
        setCenter(inputOutput);
        // 编译期间切换到其他交互面板只隐藏视图，不应推迟已请求的程序运行。
        if (mounted) {
            if (ownsFocus && getScene() != null) inputOutput.activate();
            else inputOutput.start();
        }
    }

    void failed(String stage, List<Diagnostic> diagnostics) {
        String details = diagnostics.stream().map(diagnostic ->
                "第 " + diagnostic.range().startLine() + " 行，UTF-8 字节位置 " + diagnostic.range().startByte()
                        + "\n" + diagnostic.describe()).collect(Collectors.joining("\n\n"));
        failed("编译失败" + (stage == null || stage.isBlank() ? "" : "（" + stage + "）"), details);
    }

    void failed(String title, String details) {
        if (closed) return;
        compiling = false;
        if (inputOutput != null) {
            inputOutput.close();
            inputOutput = null;
        }
        artifact = null;
        setTop(toolbar);
        setCenter(output);
        status.setText(title);
        output.setText("源文件：" + sourcePath + "\n\n" + details);
        cancel.setDisable(true);
    }

    void cancelled() {
        failed("已取消", "本次编译已取消，未启动程序。");
    }

    void start() {
        if (!closed && inputOutput != null && getScene() != null) inputOutput.start();
    }

    void activate() {
        if (closed || getScene() == null) return;
        if (inputOutput != null) inputOutput.activate();
        else requestFocus();
    }

    private boolean ownsInputFocus() {
        if (getScene() == null) return false;
        for (var node = getScene().getFocusOwner(); node != null; node = node.getParent()) {
            if (node == this) return true;
        }
        return false;
    }

    boolean isClosed() { return closed; }
    ExecutableArtifact artifact() { return artifact; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancelCompilation.run();
        if (inputOutput != null) inputOutput.close();
    }
}
