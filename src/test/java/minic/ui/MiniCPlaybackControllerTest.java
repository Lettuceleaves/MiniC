package minic.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCPlaybackControllerTest {
    @Test
    void playFastPauseAndManualTickRefreshStateWithoutWaiting() {
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCWorkbenchController controller = new MiniCWorkbenchController(viewModel);
        controller.startDefaultSession();
        MiniCPlaybackController playback = new MiniCPlaybackController(viewModel, false);

        playback.play();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PLAYING");
        assertThat(viewModel.currentStateProperty().get().frameIntervalMillis()).isEqualTo(1000);

        playback.tickOnce();
        assertThat(viewModel.currentStateProperty().get().currentStage()).isEqualTo("preprocess");
        assertThat(viewModel.currentStateProperty().get().globalStepIndex()).isZero();

        playback.playFast();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("FAST_PLAYING");
        assertThat(viewModel.currentStateProperty().get().frameIntervalMillis()).isEqualTo(500);

        playback.pause();
        assertThat(viewModel.currentStateProperty().get().playbackMode()).isEqualTo("PAUSED");
        assertThat(playback.running()).isFalse();
    }
}
