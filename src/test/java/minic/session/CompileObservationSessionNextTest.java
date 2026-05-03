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
    void nextStageCompletesCurrentStageAndEntersFollowingStage() {
        CompileObservationSession session = CompileObservationSession.fromSource("next-stage.mc", "int main() { return 0; }");

        StepResult result = session.nextStage();

        assertThat(result.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(result.title()).contains("跳转到下一环节");
        assertThat(session.currentStage()).isEqualTo(CompileStage.PARSER);
        assertThat(session.lexResult()).isPresent();
        assertThat(session.currentStageData().stage()).isEqualTo(CompileStage.PARSER);
    }

    @Test
    void nextStageStopsBeforeIrWhenSemanticDiagnosticsExist() {
        CompileObservationSession session = CompileObservationSession.fromSource(
                "diagnostic-stage.mc",
                """
                        int main() {
                            missing = 1;
                            return 0;
                        }
                        """
        );

        assertThat(session.nextStage().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.PARSER);
        assertThat(session.nextStage().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.SEMANTIC);

        StepResult semanticJump = session.nextStage();

        assertThat(semanticJump.outcome()).isEqualTo(StepOutcome.FAILED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.SEMANTIC);
        assertThat(session.semanticResult()).isPresent();
        assertThat(session.irModule()).isEmpty();
        assertThat(session.globalData().diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：missing");
    }

    @Test
    void nextStageStopsBeforeSemanticWhenParserDiagnosticsExist() {
        CompileObservationSession session = CompileObservationSession.fromSource(
                "unsupported-syntax.mc",
                """
                        int main() {
                            int i = 0;
                            i++;
                            return i;
                        }
                        """
        );

        assertThat(session.nextStage().outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.PARSER);

        StepResult parserJump = session.nextStage();

        assertThat(parserJump.outcome()).isEqualTo(StepOutcome.FAILED);
        assertThat(session.currentStage()).isEqualTo(CompileStage.PARSER);
        assertThat(session.parseResult()).isPresent();
        assertThat(session.semanticResult()).isEmpty();
        assertThat(session.globalData().diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .contains("期望表达式");
    }

    @Test
    void globalDataCanReadCompletedParserSummaryAfterStageJump() {
        CompileObservationSession session = CompileObservationSession.fromSource("parser-summary.mc", "int main() { return 0; }");

        session.nextStage();
        session.nextStage();

        assertThat(session.currentStage()).isEqualTo(CompileStage.SEMANTIC);
        assertThat(session.globalData().astSummary()).isNotEmpty();
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
        assertThat(session.currentStage()).isEqualTo(CompileStage.TOOLCHAIN);
        assertThat(session.currentState().canNext()).isFalse();
        assertThat(session.currentStageData().stage()).isEqualTo(CompileStage.TOOLCHAIN);
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
