package minic.session;

import minic.runtime.step.PlaybackMode;
import minic.runtime.step.StepOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CompileObservationSessionPlaybackStateTest {
    @Test
    void supportsPlayingFastPlayingAndPauseState() {
        CompileObservationSession session = CompileObservationSession.fromSource("play.mc", "int main() { return 0; }");

        assertThat(session.play().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PLAYING);
        assertThat(session.currentState().playbackMode()).isEqualTo(PlaybackMode.PLAYING);
        assertThat(session.currentState().frameInterval()).isEqualTo(Duration.ofSeconds(1));

        assertThat(session.playFast().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.FAST_PLAYING);
        assertThat(session.currentState().frameInterval()).isEqualTo(Duration.ofMillis(500));

        assertThat(session.pause().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PAUSED);
        assertThat(session.currentState().playbackMode()).isEqualTo(PlaybackMode.PAUSED);
        assertThat(session.currentState().frameInterval()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void keepsReverseCapabilitiesReservedAsUnavailable() {
        CompileObservationSession session = CompileObservationSession.fromSource("play.mc", "int main() { return 0; }");

        session.play();

        assertThat(session.currentState().canPrevious()).isFalse();
        assertThat(session.currentState().canReversePlay()).isFalse();
        assertThat(session.currentState().canPause()).isTrue();
    }
}
