package minic.ui;

import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import minic.uiapi.UiGlobalDataDto;
import minic.uiapi.UiStageDataDto;

import java.util.List;
import java.util.Objects;

/**
 * 可执行文件运行输入/输出面板。
 */
public final class MiniCExecutionPane extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final TextArea stdinEditor = new TextArea();
    private final CheckBox noInput = new CheckBox("无输入");
    private final Button confirmButton = new Button("确认输入");
    private final TextArea outputView = new TextArea();

    /**
     * 创建运行面板。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCExecutionPane(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        getStyleClass().addAll("pane", "execution-pane");
        configureControls();
        getChildren().addAll(header(), split());
        VBox.setVgrow(getChildren().get(1), Priority.ALWAYS);
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        refresh();
    }

    private Label header() {
        Label label = new Label("runtime.execution");
        label.getStyleClass().add("pane-head");
        return label;
    }

    private SplitPane split() {
        SplitPane splitPane = new SplitPane(inputPane(), outputPane());
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getStyleClass().add("stage-flow");
        splitPane.setDividerPositions(0.42);
        return splitPane;
    }

    private VBox inputPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("stage-flow-column");
        Label title = new Label("STDIN");
        title.getStyleClass().add("stage-flow-title");
        HBox actions = new HBox(8, noInput, confirmButton);
        actions.getStyleClass().add("execution-actions");
        stdinEditor.getStyleClass().add("execution-stdin");
        VBox.setVgrow(stdinEditor, Priority.ALWAYS);
        pane.getChildren().addAll(title, actions, stdinEditor);
        return pane;
    }

    private VBox outputPane() {
        VBox pane = new VBox();
        pane.getStyleClass().add("stage-flow-column");
        Label title = new Label("OUTPUT");
        title.getStyleClass().add("stage-flow-title");
        outputView.getStyleClass().add("execution-output");
        outputView.setEditable(false);
        VBox.setVgrow(outputView, Priority.ALWAYS);
        pane.getChildren().addAll(title, outputView);
        return pane;
    }

    private void configureControls() {
        stdinEditor.setWrapText(false);
        outputView.setWrapText(false);
        noInput.selectedProperty().addListener((observable, oldValue, selected) -> stdinEditor.setDisable(selected));
        confirmButton.setOnAction(event -> viewModel.confirmExecutionInput(noInput.isSelected() ? "" : stdinEditor.getText()));
    }

    private void refresh() {
        UiStageDataDto stageData = viewModel.currentStageDataProperty().get();
        UiGlobalDataDto globalData = viewModel.globalDataProperty().get();
        boolean executionActive = stageData != null && "execution".equals(stageData.stage());
        boolean completed = stageData != null && "execution".equals(stageData.stage()) && stageData.completed();
        boolean confirmed = globalData != null && globalData.executionInputSummary().stream()
                .anyMatch(line -> line.equals("stdin confirmed"));
        stdinEditor.setDisable(noInput.isSelected() || !executionActive || completed || confirmed);
        noInput.setDisable(!executionActive || completed || confirmed);
        confirmButton.setDisable(!executionActive || completed || confirmed);
        outputView.setText(outputText(globalData));
    }

    private String outputText(UiGlobalDataDto globalData) {
        if (globalData == null || globalData.executionOutputSummary().isEmpty()) {
            return "Execution output will appear here.";
        }
        List<String> lines = globalData.executionOutputSummary();
        return String.join(System.lineSeparator(), lines);
    }
}
