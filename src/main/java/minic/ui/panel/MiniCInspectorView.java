package minic.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * 右侧观测详情面板。
 */
public final class MiniCInspectorView extends VBox {
    private static final double INSPECTOR_BUTTON_WIDTH = 78;
    private static final double INSPECTOR_BUTTON_HEIGHT = 28;
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCInspectorModelFactory modelFactory = new MiniCInspectorModelFactory();
    private final MiniCPlaybackController playbackController;
    private final Label currentState = body("");
    private final Label currentItem = body("");
    private final Label accumulatedOutput = body("");
    private final Button nextButton = control("下一步", true);
    private final Button nextStageButton = control("下一阶段", false);
    private final Button runToExecutionButton = control("到执行", false);
    private final Button playButton = control("播放", false);
    private final Button playFastButton = control("2x", false);
    private final Button pauseButton = control("暂停", false);

    /**
     * 创建 Inspector。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCInspectorView(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        playbackController = new MiniCPlaybackController(viewModel);
        getStyleClass().add("inspector");
        nextButton.setOnAction(event -> viewModel.next());
        nextStageButton.setOnAction(event -> playbackController.nextStage());
        runToExecutionButton.setOnAction(event -> viewModel.runToExecution());
        playButton.setOnAction(event -> playbackController.play());
        playFastButton.setOnAction(event -> playbackController.playFast());
        pauseButton.setOnAction(event -> playbackController.pause());
        getChildren().addAll(
                label("MiniC 观测", "panel-title"),
                controls(),
                label("当前状态", "section-label"),
                currentState,
                label("当前项", "section-label"),
                currentItem,
                label("累计输出", "section-label"),
                accumulatedOutput
        );
        refresh();
        viewModel.sessionStartedProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStateProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    /**
     * 刷新 Inspector 文本和控制按钮状态。
     */
    public void refresh() {
        MiniCInspectorModel model = modelFactory.create(
                viewModel.currentStateProperty().get(),
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        currentState.setText(model.currentState());
        currentItem.setText(model.currentItem());
        accumulatedOutput.setText(model.accumulatedOutput());
        boolean started = viewModel.sessionStartedProperty().get();
        boolean hasState = viewModel.currentStateProperty().get() != null;
        nextButton.setDisable(!started || !hasState || !viewModel.canNextControl());
        nextStageButton.setDisable(!started || !hasState || !viewModel.canNextStageControl());
        runToExecutionButton.setDisable(!started || !hasState || !viewModel.canRunToExecutionControl());
        playButton.setDisable(!started || !hasState || !viewModel.canPlayControl());
        playFastButton.setDisable(!started || !hasState || !viewModel.canPlayFastControl());
        pauseButton.setDisable(!started || !hasState || !viewModel.currentStateProperty().get().canPause());
    }

    private VBox controls() {
        HBox firstRow = new HBox(6, nextButton, nextStageButton, runToExecutionButton);
        HBox secondRow = new HBox(6, playButton, playFastButton, pauseButton);
        firstRow.getStyleClass().add("inspector-control-row");
        secondRow.getStyleClass().add("inspector-control-row");
        VBox controls = new VBox(6);
        controls.getStyleClass().add("controls");
        controls.getStyleClass().add("inspector-controls");
        controls.getChildren().addAll(firstRow, secondRow);
        return controls;
    }

    private Button control(String text, boolean primary) {
        Button button = new Button(text);
        button.getStyleClass().add(primary ? "control-primary" : "control-secondary");
        button.getStyleClass().add("inspector-control-button");
        button.setMinSize(INSPECTOR_BUTTON_WIDTH, INSPECTOR_BUTTON_HEIGHT);
        button.setPrefSize(INSPECTOR_BUTTON_WIDTH, INSPECTOR_BUTTON_HEIGHT);
        button.setMaxSize(INSPECTOR_BUTTON_WIDTH, INSPECTOR_BUTTON_HEIGHT);
        return button;
    }

    private Label body(String text) {
        return label(text, "body-text");
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
