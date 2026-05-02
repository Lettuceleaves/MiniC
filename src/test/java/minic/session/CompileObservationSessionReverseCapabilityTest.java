package minic.session;

import minic.runtime.step.PlaybackMode;
import minic.runtime.step.StepOutcome;
import minic.runtime.step.StepResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompileObservationSessionReverseCapabilityTest {
    @Test
    void previousAndReversePlayRemainUnsupported() {
        CompileObservationSession session = CompileObservationSession.fromSource("reverse.mc", "int main() { return 0; }");

        StepResult previous = session.previous();
        StepResult reversePlay = session.reversePlay();

        assertThat(previous.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(previous.title()).contains("上一步");
        assertThat(reversePlay.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(reversePlay.title()).contains("自动倒放");
    }

    @Test
    void reverseCapabilitiesRemainDisabledAndReversePlayDoesNotChangePlaybackMode() {
        CompileObservationSession session = CompileObservationSession.fromSource("reverse.mc", "int main() { return 0; }");

        session.play();
        session.reversePlay();

        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PLAYING);
        assertThat(session.currentState().canPrevious()).isFalse();
        assertThat(session.currentState().canReversePlay()).isFalse();
    }
}
