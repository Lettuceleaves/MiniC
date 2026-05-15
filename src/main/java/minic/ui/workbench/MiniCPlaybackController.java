package minic.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import minic.settings.MiniCSettings;
import minic.uiapi.UiControlResultDto;

import java.util.Objects;

/**
 * JavaFX 播放控制器。
 */
public final class MiniCPlaybackController {
    private final MiniCWorkbenchViewModel viewModel;
    private final boolean timelineEnabled;
    private Timeline timeline;

    /**
     * 创建播放控制器。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCPlaybackController(MiniCWorkbenchViewModel viewModel) {
        this(viewModel, true);
    }

    MiniCPlaybackController(MiniCWorkbenchViewModel viewModel, boolean timelineEnabled) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.timelineEnabled = timelineEnabled;
        MiniCSettings.setFrameIntervalChangeListener(() -> {
            if (running()) {
                restartTimeline();
            }
        });
        viewModel.currentStateProperty().addListener((obs, oldState, newState) -> {
            if (running() && newState != null && "PAUSED".equals(newState.playbackMode())) {
                stopTimeline();
            }
        });
    }

    /**
     * 开始普通播放。
     */
    public void play() {
        viewModel.play();
        restartTimeline();
    }

    /**
     * 开始两倍速播放。
     */
    public void playFast() {
        viewModel.playFast();
        restartTimeline();
    }

    /**
     * 暂停播放。
     */
    public void pause() {
        stopTimeline();
        viewModel.pause();
    }

    /**
     * 跳转到下一编译环节。
     *
     * @return 控制结果
     */
    public UiControlResultDto nextStage() {
        stopTimeline();
        return viewModel.nextStage();
    }

    /**
     * 手动执行一次 tick。
     *
     * @return tick 结果
     */
    public UiControlResultDto tickOnce() {
        var state = viewModel.currentStateProperty().get();
        if (state != null && "PAUSED".equals(state.playbackMode())) {
            stopTimeline();
            return null;
        }
        UiControlResultDto result = viewModel.tick();
        if (viewModel.currentStateProperty().get() != null && !viewModel.currentStateProperty().get().canNext()) {
            stopTimeline();
        }
        return result;
    }

    /**
     * 当前是否有定时器在运行。
     *
     * @return 是否运行中
     */
    public boolean running() {
        return timeline != null && timeline.getStatus() == Timeline.Status.RUNNING;
    }

    private void restartTimeline() {
        stopTimeline();
        if (!timelineEnabled) {
            return;
        }
        long interval = viewModel.currentStateProperty().get() == null
                ? 1000
                : viewModel.currentStateProperty().get().frameIntervalMillis();
        timeline = new Timeline(new KeyFrame(Duration.millis(interval), event -> tickOnce()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void stopTimeline() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }
}
