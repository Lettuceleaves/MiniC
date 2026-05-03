package minic.runtime.step;

import minic.compiler.ast.decl.Program;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticStageStepperTest {
    @Test
    void advancesSemanticActionsThroughUnifiedStageApi() {
        SemanticStageStepper stepper = new SemanticStageStepper(parse("""
                struct Point { int x; };
                int add(int left, int right) { return left + right; }
                int main() { return add(1, 2); }
                """));

        StepResult first = stepper.next();
        while (stepper.canNext()) {
            stepper.next();
        }

        assertThat(first.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(first.description()).contains("REGISTER_STRUCTS");
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(stepper.snapshot().canPrevious()).isFalse();
        assertThat(stepper.data().inputSummary()).contains("structs=1", "functions=2", "bodyFunctions=2");
        assertThat(stepper.data().currentItem()).contains("VALIDATE_FUNCTION_RETURN main");
        assertThat(stepper.data().accumulatedOutput()).contains(
                "symbol Point STRUCT",
                "symbol add FUNCTION",
                "symbol main FUNCTION"
        );
        assertThat(stepper.data().accumulatedOutput()).anyMatch(summary -> summary.startsWith("expressionTypes="));
        assertThat(stepper.data().diagnostics()).isEmpty();
        assertThat(stepper.semanticState().toSemanticResult().diagnostics()).isEmpty();
    }

    @Test
    void reportsSemanticDiagnosticsWithStableActionKind() {
        SemanticStageStepper stepper = new SemanticStageStepper(parse("""
                int main() {
                    return missing;
                }
                """));

        StepResult diagnosticResult = null;
        while (stepper.canNext()) {
            StepResult result = stepper.next();
            if (result.outcome() == StepOutcome.FAILED) {
                diagnosticResult = result;
            }
        }

        assertThat(diagnosticResult).isNotNull();
        assertThat(diagnosticResult.description()).contains("REPORT_DIAGNOSTIC");
        assertThat(diagnosticResult.diagnostics()).hasSize(1);
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(stepper.data().diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("SEM001");
    }

    @Test
    void previousRemainsUnsupported() {
        SemanticStageStepper stepper = new SemanticStageStepper(parse("int main() { return 0; }"));

        StepResult previous = stepper.previous();

        assertThat(stepper.canPrevious()).isFalse();
        assertThat(previous.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
    }

    private Program parse(String source) {
        SourceFile sourceFile = new SourceFile("semantic-runtime.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return parseResult.program();
    }
}
