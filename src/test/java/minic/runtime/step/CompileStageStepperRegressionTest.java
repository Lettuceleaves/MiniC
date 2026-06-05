package minic.runtime.step;

import minic.source.SourceFile;
import minic.compiler.toolchain.ExecutableArtifact;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompileStageStepperRegressionTest {
    @Test
    void advancesAllCompileStagesThroughUnifiedForwardOnlyContract() {
        SourceFile source = new SourceFile("steps.mc", "int main() { return 0; }");
        StageStepper lexer = new LexerStageStepper(source);

        assertThat(lexer.stage()).isEqualTo(CompileStage.LEXER);
        assertThat(lexer.snapshot().canNext()).isTrue();
        while (lexer.canNext()) {
            lexer.next();
        }
        assertThat(lexer.data().progress().completed()).isTrue();

        StageStepper parser = new ParserStageStepper(((LexerStageStepper) lexer).lexerState().tokens());
        while (parser.canNext()) {
            parser.next();
        }
        assertThat(parser.data().progress().completed()).isTrue();
    }

    @Test
    void exposesStableCurrentStageGlobalProgressCapabilitiesAndResults() {
        CurrentStepState state = new LexerStageStepper(new SourceFile("state.mc", "int main() { return 0; }")).snapshot();

        assertThat(state.currentStage()).isEqualTo(CompileStage.LEXER);
        assertThat(state.globalStepIndex()).isZero();
        assertThat(state.capabilities().canNext()).isTrue();
        assertThat(StepCapabilities.forwardOnly().previousUnsupported(CompileStage.LEXER).outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
        assertThat(StageProgress.completed(3).completed()).isTrue();
    }

    @Test
    void waitsForConfirmedExecutionInputBeforeRunningExecutable() {
        ExecutionStageStepper stepper = new ExecutionStageStepper(
                new SourceFile("run.mc", "int main() { return 0; }"),
                new ExecutableArtifact(Path.of("main.exe"))
        );

        StepResult result = stepper.next();

        assertThat(result.outcome()).isEqualTo(StepOutcome.CANNOT_ADVANCE);
        assertThat(stepper.data().inputSummary()).anySatisfy(line -> assertThat(line).contains("stdin pending"));
    }
}
