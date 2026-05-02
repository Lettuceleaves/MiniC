package minic.runtime.step;

import minic.compiler.ast.decl.Program;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IrStageStepperTest {
    @Test
    void advancesIrStructureActionsThroughUnifiedStageApi() {
        Parsed parsed = parseAndAnalyze("""
                extern int puts(int *text);
                int add(int left, int right) { return left + right; }
                int main() { return puts("ok") + add(1, 2); }
                """);
        IrStageStepper stepper = new IrStageStepper(parsed.program(), parsed.semanticResult());

        StepResult first = stepper.next();
        StepResult second = stepper.next();
        while (stepper.canNext()) {
            stepper.next();
        }

        assertThat(first.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(first.description()).contains("REGISTER_EXTERNAL puts");
        assertThat(second.description()).contains("LOWER_FUNCTION add");
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(stepper.data().inputSummary()).contains("functions=3");
        assertThat(stepper.data().currentItem()).contains("COMPLETE_MODULE module");
        assertThat(stepper.data().accumulatedOutput()).contains("extern puts");
        assertThat(stepper.data().accumulatedOutput()).anyMatch(summary -> summary.startsWith("add blocks="));
        assertThat(stepper.data().accumulatedOutput()).anyMatch(summary -> summary.startsWith("main blocks="));
        assertThat(stepper.irState().toIrModule().functions()).hasSize(2);
    }

    @Test
    void reportsCannotAdvanceAfterCompletionAndPreviousUnsupported() {
        Parsed parsed = parseAndAnalyze("int main() { return 0; }");
        IrStageStepper stepper = new IrStageStepper(parsed.program(), parsed.semanticResult());

        while (stepper.canNext()) {
            stepper.next();
        }
        StepResult afterCompletion = stepper.next();
        StepResult previous = stepper.previous();

        assertThat(afterCompletion.outcome()).isEqualTo(StepOutcome.CANNOT_ADVANCE);
        assertThat(stepper.canPrevious()).isFalse();
        assertThat(previous.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
    }

    private Parsed parseAndAnalyze(String source) {
        SourceFile sourceFile = new SourceFile("ir-runtime.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(parseResult.program());
        assertThat(semanticResult.diagnostics()).isEmpty();
        return new Parsed(parseResult.program(), semanticResult);
    }

    private record Parsed(Program program, SemanticResult semanticResult) {
    }
}
