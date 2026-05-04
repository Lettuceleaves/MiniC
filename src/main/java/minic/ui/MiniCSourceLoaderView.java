package minic.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * 源码加载和会话启动控件。
 */
public final class MiniCSourceLoaderView extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final ComboBox<String> sampleSelector = new ComboBox<>();
    private final MiniCCodeEditor sourceEditor = new MiniCCodeEditor();
    private final Button startButton = new Button("Start");

    /**
     * 创建源码加载视图。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCSourceLoaderView(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        getStyleClass().add("source-loader");
        HBox controls = new HBox(6);
        controls.getStyleClass().add("loader-controls");
        sampleSelector.getItems().setAll(MiniCSamplePrograms.all().stream().map(MiniCSampleProgram::name).toList());
        sampleSelector.getSelectionModel().select(MiniCSamplePrograms.defaultSample().name());
        sourceEditor.setText(MiniCSamplePrograms.defaultSample().source());
        startButton.getStyleClass().add("control-primary");
        sampleSelector.setOnAction(event -> applySelectedSample());
        startButton.setOnAction(event -> startSession());
        sourceEditor.input().textProperty().addListener((observable, oldValue, newValue) -> {
            sourceEditor.render(viewModel.realtimeAnalysisProperty().get());
            submitRealtimeSource();
        });
        viewModel.realtimeAnalysisProperty().addListener((observable, oldValue, newValue) -> {
            sourceEditor.render(newValue);
        });
        controls.getChildren().addAll(sampleSelector, startButton);
        getChildren().addAll(controls, sourceEditor);
        VBox.setVgrow(sourceEditor, Priority.ALWAYS);
    }

    /**
     * 使用当前编辑器内容启动观测会话。
     */
    public void startSession() {
        String name = sampleSelector.getValue() == null || sampleSelector.getValue().isBlank()
                ? "main.mc"
                : sampleSelector.getValue();
        viewModel.loadSource(name, sourceEditor.getText());
        submitRealtimeSource();
        viewModel.startSession();
    }

    private void applySelectedSample() {
        String selected = sampleSelector.getValue();
        MiniCSamplePrograms.all().stream()
                .filter(sample -> sample.name().equals(selected))
                .findFirst()
                .ifPresent(sample -> sourceEditor.setText(sample.source()));
    }

    private void submitRealtimeSource() {
        String name = sampleSelector.getValue() == null || sampleSelector.getValue().isBlank()
                ? "main.mc"
                : sampleSelector.getValue();
        viewModel.submitRealtimeSource(name, sourceEditor.getText());
    }

}
