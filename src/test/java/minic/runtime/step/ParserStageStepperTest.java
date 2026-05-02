package minic.runtime.step;

import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.lexer.Token;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParserStageStepperTest {
    @Test
    void advancesOneAstNodeThroughUnifiedStageApi() {
        ParserStageStepper stepper = new ParserStageStepper(lex(new SourceFile(
                "parser.mc",
                """
                        struct Point { int x; };
                        int add(int a, int b) { return a + b; }
                        """
        )));

        StepResult first = stepper.next();
        StepResult second = stepper.next();

        assertThat(first.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(first.description()).contains("StructDecl Point");
        assertThat(second.outcome()).isEqualTo(StepOutcome.STAGE_COMPLETED);
        assertThat(second.description()).contains("FunctionDecl add");
        assertThat(stepper.canNext()).isFalse();
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(stepper.snapshot().sourceName()).isEqualTo("parser.mc");
        assertThat(stepper.data().inputSummary()).anyMatch(summary -> summary.startsWith("tokens="));
        assertThat(stepper.data().inputSummary()).contains("first=STRUCT struct", "last=EOF <empty>");
        assertThat(stepper.data().currentItem()).contains("FunctionDecl add");
        assertThat(stepper.data().accumulatedOutput()).contains(
                "StructDecl Point fields=1",
                "FunctionDecl add function params=2"
        );
        assertThat(stepper.parserState().toParseResult().program().functions()).hasSize(1);
    }

    @Test
    void reportsParserDiagnosticsAndKeepsRecoverableProgress() {
        ParserStageStepper stepper = new ParserStageStepper(lex(new SourceFile(
                "invalid-parser.mc",
                "main() {} int main() { return 0; }"
        )));

        StepResult invalid = stepper.next();
        StepResult valid = stepper.next();

        assertThat(invalid.outcome()).isEqualTo(StepOutcome.FAILED);
        assertThat(invalid.diagnostics()).hasSize(1);
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(valid.outcome()).isEqualTo(StepOutcome.STAGE_COMPLETED);
        assertThat(stepper.data().diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("PAR001");
        assertThat(stepper.data().accumulatedOutput()).contains("FunctionDecl main function params=0");
    }

    @Test
    void previousRemainsUnsupported() {
        ParserStageStepper stepper = new ParserStageStepper(lex(new SourceFile("parser.mc", "")));

        StepResult previous = stepper.previous();

        assertThat(stepper.canPrevious()).isFalse();
        assertThat(previous.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
    }

    private List<Token> lex(SourceFile sourceFile) {
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        return lexResult.tokens();
    }
}
