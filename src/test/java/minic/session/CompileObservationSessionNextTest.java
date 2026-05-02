package minic.session;

import minic.runtime.step.CompileStage;
import minic.runtime.step.StepOutcome;
import minic.runtime.step.StepResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompileObservationSessionNextTest {
    @Test
    void advancesCurrentStageAndMovesToNextStageOnFollowingCallAfterCompletion() {
        CompileObservationSession session = CompileObservationSession.fromSource("next.mc", "int main() { return 0; }");

        StepResult first = session.next();

        assertThat(first.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.LEXER);
        assertThat(session.globalStepCount()).isEqualTo(1);
        assertThat(session.currentState().globalStepIndex()).isEqualTo(1);
        assertThat(session.globalData().tokenSummary()).contains("INT int");

        while (session.currentStage() == CompileStage.LEXER && session.currentStepper().canNext()) {
            session.next();
        }
        assertThat(session.lexResult()).isPresent();

        StepResult enterParser = session.next();

        assertThat(enterParser.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(enterParser.title()).isEqualTo("进入阶段");
        assertThat(session.currentStage()).isEqualTo(CompileStage.PARSER);
        assertThat(session.currentStageData().stage()).isEqualTo(CompileStage.PARSER);
        assertThat(session.globalData().stageSummaries()).contains("parser prepared");
    }

    @Test
    void synchronizesStateStageDataAndGlobalDataAcrossAllStages() {
        CompileObservationSession session = CompileObservationSession.fromSource(
                "full.mc",
                """
                        extern int puts(int *text);
                        int main() { return puts("ok"); }
                        """
        );

        StepResult last = null;
        int guard = 0;
        while (session.currentState().canNext() && guard++ < 1000) {
            last = session.next();
        }

        assertThat(last).isNotNull();
        assertThat(session.currentStage()).isEqualTo(CompileStage.CODEGEN);
        assertThat(session.currentState().canNext()).isFalse();
        assertThat(session.currentStageData().stage()).isEqualTo(CompileStage.CODEGEN);
        assertThat(session.globalData().tokenSummary()).isNotEmpty();
        assertThat(session.globalData().astSummary()).isNotEmpty();
        assertThat(session.globalData().semanticSummary()).isNotEmpty();
        assertThat(session.globalData().irSummary()).isNotEmpty();
        assertThat(session.globalData().assemblySummary()).contains("END");
        assertThat(session.lexResult()).isPresent();
        assertThat(session.parseResult()).isPresent();
        assertThat(session.semanticResult()).isPresent();
        assertThat(session.irModule()).isPresent();
        assertThat(session.assemblySource()).isPresent();

        StepResult afterCompletion = session.next();

        assertThat(afterCompletion.outcome()).isEqualTo(StepOutcome.CANNOT_ADVANCE);
    }
}
