package minic.uilocal;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import minic.uilocal.control.MiniCWorkbenchControlHub;

import java.util.Objects;

/**
 * 可停靠或悬浮的编译控制台。
 */
public final class MiniCCompilerControlsView extends VBox {
    private static final double CONTROL_BUTTON_WIDTH = 78;
    private static final double CONTROL_BUTTON_HEIGHT = 28;
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCPlaybackController playbackController;
    private final MiniCWorkbenchControlHub controlHub;
    private final Button nextButton = control("下一步", true);
    private final Button nextStageButton = control("下一阶段", false);
    private final Button runToExecutionButton = control("到执行", false);
    private final Button playButton = control("播放", false);
    private final Button playFastButton = control("2x", false);
    private final Button pauseButton = control("暂停", false);

    public MiniCCompilerControlsView(MiniCWorkbenchViewModel viewModel, MiniCWorkbenchControlHub controlHub) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.controlHub = Objects.requireNonNull(controlHub, "controlHub");
        this.playbackController = new MiniCPlaybackController(viewModel);
        registerCompilerCommands();
        getStyleClass().add("compiler-controls");
        getStyleClass().add("controls");
        getStyleClass().add("inspector-controls");
        nextButton.setOnAction(event -> execute(MiniCWorkbenchControlHub.COMPILER_NEXT));
        nextStageButton.setOnAction(event -> execute(MiniCWorkbenchControlHub.COMPILER_NEXT_STAGE));
        runToExecutionButton.setOnAction(event -> execute(MiniCWorkbenchControlHub.COMPILER_RUN_TO_EXECUTION));
        playButton.setOnAction(event -> execute(MiniCWorkbenchControlHub.COMPILER_PLAY));
        playFastButton.setOnAction(event -> execute(MiniCWorkbenchControlHub.COMPILER_PLAY_FAST));
        pauseButton.setOnAction(event -> execute(MiniCWorkbenchControlHub.COMPILER_PAUSE));
        HBox firstRow = new HBox(6, nextButton, nextStageButton, runToExecutionButton);
        HBox secondRow = new HBox(6, playButton, playFastButton, pauseButton);
        firstRow.getStyleClass().add("inspector-control-row");
        secondRow.getStyleClass().add("inspector-control-row");
        getChildren().addAll(firstRow, secondRow);
        refresh();
        viewModel.sessionStartedProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStateProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    public void refresh() {
        boolean started = viewModel.sessionStartedProperty().get();
        boolean hasState = viewModel.currentStateProperty().get() != null;
        nextButton.setDisable(!started || !hasState || !controlHub.commandEnabled(MiniCWorkbenchControlHub.COMPILER_NEXT));
        nextStageButton.setDisable(!started || !hasState || !controlHub.commandEnabled(MiniCWorkbenchControlHub.COMPILER_NEXT_STAGE));
        runToExecutionButton.setDisable(!started || !hasState || !controlHub.commandEnabled(MiniCWorkbenchControlHub.COMPILER_RUN_TO_EXECUTION));
        playButton.setDisable(!started || !hasState || !controlHub.commandEnabled(MiniCWorkbenchControlHub.COMPILER_PLAY));
        playFastButton.setDisable(!started || !hasState || !controlHub.commandEnabled(MiniCWorkbenchControlHub.COMPILER_PLAY_FAST));
        pauseButton.setDisable(!started || !hasState || !controlHub.commandEnabled(MiniCWorkbenchControlHub.COMPILER_PAUSE));
    }

    private void registerCompilerCommands() {
        controlHub.registerCompilerCommands(new MiniCWorkbenchControlHub.CompilerCommands(
                viewModel::canNextControl,
                viewModel::next,
                viewModel::canNextStageControl,
                playbackController::nextStage,
                viewModel::canRunToExecutionControl,
                viewModel::runToExecution,
                viewModel::canPlayControl,
                playbackController::play,
                viewModel::canPlayFastControl,
                playbackController::playFast,
                () -> viewModel.currentStateProperty().get() != null
                        && viewModel.currentStateProperty().get().canPause(),
                playbackController::pause
        ));
    }

    private void execute(String commandId) {
        controlHub.execute(commandId);
        refresh();
    }

    private Button control(String text, boolean primary) {
        Button button = new Button(text);
        button.getStyleClass().add(primary ? "control-primary" : "control-secondary");
        button.getStyleClass().add("inspector-control-button");
        button.setMinSize(CONTROL_BUTTON_WIDTH, CONTROL_BUTTON_HEIGHT);
        button.setPrefSize(CONTROL_BUTTON_WIDTH, CONTROL_BUTTON_HEIGHT);
        button.setMaxSize(CONTROL_BUTTON_WIDTH, CONTROL_BUTTON_HEIGHT);
        return button;
    }
}
