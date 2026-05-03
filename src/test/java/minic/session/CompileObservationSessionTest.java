package minic.session;

import minic.runtime.step.CompileStage;
import minic.runtime.step.LexerStageStepper;
import minic.runtime.step.PlaybackMode;
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
                CompileStage.LEXER,
                CompileStage.PARSER,
                CompileStage.SEMANTIC,
                CompileStage.IR,
                CompileStage.CODEGEN,
                CompileStage.TOOLCHAIN,
                CompileStage.EXECUTION
        );
        assertThat(session.currentStage()).isEqualTo(CompileStage.LEXER);
        assertThat(session.currentStepper()).isInstanceOf(LexerStageStepper.class);
        assertThat(session.globalStepCount()).isZero();
        assertThat(session.playbackMode()).isEqualTo(PlaybackMode.PAUSED);
        assertThat(session.currentState().sourceName()).isEqualTo("session.mc");
        assertThat(session.currentState().currentStage()).isEqualTo(CompileStage.LEXER);
        assertThat(session.currentStageData().stage()).isEqualTo(CompileStage.LEXER);
        assertThat(session.globalData().source()).isEqualTo("int main() { return 0; }");
        assertThat(session.globalData().stageSummaries()).contains("lexer prepared", "parser pending");
    }

    @Test
    void createsSessionFromSourceText() {
        CompileObservationSession session = CompileObservationSession.fromSource("inline.mc", "int main() { return 0; }");

        assertThat(session.currentState().sourceName()).isEqualTo("inline.mc");
        assertThat(session.lexResult()).isEmpty();
        assertThat(session.parseResult()).isEmpty();
        assertThat(session.semanticResult()).isEmpty();
        assertThat(session.irModule()).isEmpty();
        assertThat(session.assemblySource()).isEmpty();
    }
}
