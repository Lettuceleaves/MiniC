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
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCInspectorModelFactory modelFactory = new MiniCInspectorModelFactory();
    private final MiniCPlaybackController playbackController;
    private final Label currentState = body("");
    private final Label currentItem = body("");
    private final Label accumulatedOutput = body("");
    private final Button nextButton = control("Next", true);
    private final Button nextStageButton = control("Stage", false);
    private final Button playButton = control("Play", false);
    private final Button playFastButton = control("2x", false);
    private final Button pauseButton = control("Pause", false);

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
        playButton.setOnAction(event -> playbackController.play());
        playFastButton.setOnAction(event -> playbackController.playFast());
        pauseButton.setOnAction(event -> playbackController.pause());
        getChildren().addAll(
                label("MiniC Observation", "panel-title"),
                controls(),
                label("CURRENT STATE", "section-label"),
                currentState,
                label("CURRENT ITEM", "section-label"),
                currentItem,
                label("ACCUMULATED OUTPUT", "section-label"),
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
        nextButton.setDisable(!started || !hasState || !viewModel.currentStateProperty().get().canNext());
        nextStageButton.setDisable(!started || !hasState || !viewModel.currentStateProperty().get().canNext());
        playButton.setDisable(!started || !hasState || !viewModel.currentStateProperty().get().canPlay());
        playFastButton.setDisable(!started || !hasState || !viewModel.currentStateProperty().get().canPlayFast());
        pauseButton.setDisable(!started || !hasState || !viewModel.currentStateProperty().get().canPause());
    }

    private HBox controls() {
        HBox controls = new HBox(6);
        controls.getStyleClass().add("controls");
        controls.getChildren().addAll(nextButton, nextStageButton, playButton, playFastButton, pauseButton);
        return controls;
    }

    private Button control(String text, boolean primary) {
        Button button = new Button(text);
        button.getStyleClass().add(primary ? "control-primary" : "control-secondary");
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
