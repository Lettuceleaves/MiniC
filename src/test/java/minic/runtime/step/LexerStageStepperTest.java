package minic.runtime.step;

import minic.compiler.lexer.TokenKind;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LexerStageStepperTest {
    @Test
    void advancesOneTokenOrDiagnosticThroughUnifiedStageApi() {
        LexerStageStepper stepper = new LexerStageStepper(new SourceFile("lexer.mc", "int @ x;"));

        StepResult first = stepper.next();
        StepResult diagnostic = stepper.next();

        assertThat(first.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(first.title()).isEqualTo("读取 token");
        assertThat(diagnostic.outcome()).isEqualTo(StepOutcome.FAILED);
        assertThat(diagnostic.diagnostics()).hasSize(1);
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(stepper.snapshot().canPrevious()).isFalse();
        assertThat(stepper.data().inputSummary()).contains("source=lexer.mc", "length=8");
        assertThat(stepper.data().currentItem()).contains("LEX001");
        assertThat(stepper.data().accumulatedOutput()).contains("INT int");
        assertThat(stepper.data().diagnostics()).hasSize(1);

        StepResult recovered = stepper.next();

        assertThat(recovered.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(stepper.lexerState().currentToken()).hasValueSatisfying(token ->
                assertThat(token.kind()).isEqualTo(TokenKind.IDENTIFIER));
    }

    @Test
    void reportsCompletionAndCannotAdvanceAfterEof() {
        LexerStageStepper stepper = new LexerStageStepper(new SourceFile("empty.mc", ""));

        StepResult eof = stepper.next();
        StepResult afterCompletion = stepper.next();

        assertThat(eof.outcome()).isEqualTo(StepOutcome.STAGE_COMPLETED);
        assertThat(stepper.canNext()).isFalse();
        assertThat(stepper.data().accumulatedOutput()).contains("EOF <empty>");
        assertThat(stepper.snapshot().capabilities().canNext()).isFalse();
        assertThat(afterCompletion.outcome()).isEqualTo(StepOutcome.CANNOT_ADVANCE);
    }

    @Test
    void previousRemainsUnsupported() {
        LexerStageStepper stepper = new LexerStageStepper(new SourceFile("lexer.mc", "int main;"));

        StepResult previous = stepper.previous();

        assertThat(stepper.canPrevious()).isFalse();
        assertThat(previous.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
    }
}
