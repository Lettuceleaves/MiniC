package minic.session;

import minic.runtime.step.CompileStage;
import minic.runtime.step.PlaybackMode;
import minic.runtime.step.SourceStageStepper;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompileObservationSessionTest {
    @Test
    void createsSessionFromSourceAndHoldsStageOrderCurrentStepperAndGlobalStepCount() {
        CompileObservationSession session = CompileObservationSession.fromSource(
                new SourceFile("session.mc", "int main() { return 0; }")
        );

        assertThat(session.stageOrder()).containsExactly(
                CompileStage.SOURCE,
                CompileStage.PREPROCESS,
                CompileStage.LEXER,
                CompileStage.PARSER,
                CompileStage.SEMANTIC,
                CompileStage.IR,
                CompileStage.CODEGEN,
                CompileStage.TOOLCHAIN,
                CompileStage.EXECUTION
        );
        assertThat(session.currentStage()).isEqualTo(CompileStage.SOURCE);
        assertThat(session.currentStepper()).isInstanceOf(SourceStageStepper.class);
        assertThat(session.globalStepCount()).isZero();
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PAUSED);
        assertThat(session.currentState().sourceName()).isEqualTo("session.mc");
        assertThat(session.currentState().currentStage()).isEqualTo(CompileStage.SOURCE);
        assertThat(session.currentStageData().stage()).isEqualTo(CompileStage.SOURCE);
        assertThat(session.globalData().source()).isEqualTo("int main() { return 0; }");
        assertThat(session.globalData().stageSummaries()).contains("source prepared", "preprocess prepared", "lexer pending");
    }

    @Test
    void createsSessionFromSourceText() {
        CompileObservationSession session = CompileObservationSession.fromSource("inline.mc", "int main() { return 0; }");

        assertThat(session.currentState().sourceName()).isEqualTo("inline.mc");
        assertThat(session.preprocessResult()).isEmpty();
        assertThat(session.lexResult()).isEmpty();
        assertThat(session.parseResult()).isEmpty();
        assertThat(session.semanticResult()).isEmpty();
        assertThat(session.irModule()).isEmpty();
        assertThat(session.assemblySource()).isEmpty();
    }
}
