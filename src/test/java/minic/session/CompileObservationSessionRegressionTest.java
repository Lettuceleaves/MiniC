package minic.session;

import minic.runtime.step.CompileStage;
import minic.runtime.step.PlaybackMode;
import minic.runtime.step.StepOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompileObservationSessionRegressionTest {
    @Test
    void createsSessionsAdvancesStagesAndStopsAtDiagnosticBoundaries() {
        CompileObservationSession session = CompileObservationSession.fromSource("main.mc", "int main() { return 0; }");

        assertThat(session.stageOrder()).containsExactly(CompileStage.SOURCE, CompileStage.PREPROCESS, CompileStage.LEXER,
                CompileStage.PARSER, CompileStage.SEMANTIC, CompileStage.IR, CompileStage.CODEGEN, CompileStage.TOOLCHAIN, CompileStage.EXECUTION);
        assertThat(session.currentStage()).isEqualTo(CompileStage.SOURCE);
        assertThat(session.next().outcome()).isIn(StepOutcome.ADVANCED, StepOutcome.STAGE_COMPLETED);
        assertThat(session.nextStage().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.globalStepCount()).isGreaterThanOrEqualTo(1);

        CompileObservationSession invalid = CompileObservationSession.fromSource("bad.mc", "int main() { return missing; }");
        for (int guard = 0; invalid.currentState().canNext() && guard < 1000; guard++) {
            invalid.nextStage();
        }
        assertThat(invalid.currentStage()).isEqualTo(CompileStage.SEMANTIC);
        assertThat(invalid.currentStageData().diagnostics()).isNotEmpty();
    }

    @Test
    void supportsPlaybackTickModesPauseStateAndReservedReverseControls() {
        CompileObservationSession session = CompileObservationSession.fromSource("main.mc", "int main() { return 0; }");

        assertThat(session.play().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PLAYING);
        assertThat(session.tick().outcome()).isIn(StepOutcome.ADVANCED, StepOutcome.STAGE_COMPLETED);
        assertThat(session.playFast().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.FAST_PLAYING);
        assertThat(session.pause().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PAUSED);
        assertThat(session.previous().outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(session.reversePlay().outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
    }
}
