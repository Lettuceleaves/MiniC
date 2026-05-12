package minic.session;

import minic.runtime.step.PlaybackMode;
import minic.runtime.step.CompileStage;
import minic.runtime.step.StepOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CompileObservationSessionPlaybackTickTest {
    @Test
    void playingTickAdvancesOneFrameWithoutWaiting() {
        CompileObservationSession session = CompileObservationSession.fromSource("tick.mc", "int main() { return 0; }");

        session.play();
        long before = session.globalStepCount();

        assertThat(session.currentState().frameInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(session.tick().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.PREPROCESS);
        assertThat(session.globalStepCount()).isEqualTo(before);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PLAYING);
    }

    @Test
    void fastPlayingTickUsesHalfSecondFrameAndAdvancesOneFrame() {
        CompileObservationSession session = CompileObservationSession.fromSource("tick.mc", "int main() { return 0; }");

        session.playFast();
        long before = session.globalStepCount();

        assertThat(session.currentState().frameInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(session.tick().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.PREPROCESS);
        assertThat(session.globalStepCount()).isEqualTo(before);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.FAST_PLAYING);
    }

    @Test
    void tickDoesNotAdvanceWhenPausedAndAutoPausesAtEnd() {
        CompileObservationSession session = CompileObservationSession.fromSource("tick.mc", "int main() { return 0; }");

        assertThat(session.tick().outcome()).isEqualTo(StepOutcome.CANNOT_ADVANCE);
        assertThat(session.globalStepCount()).isZero();

        session.play();
        int guard = 0;
        while (session.playbackMode() != PlaybackMode.PAUSED && guard++ < 1000) {
            session.tick();
        }

        assertThat(session.currentState().canNext()).isFalse();
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PAUSED);
    }
}
