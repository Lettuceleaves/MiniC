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
    void advancesThroughAstObservationNodesAfterParsingTopLevelDeclarations() {
        ParserStageStepper stepper = new ParserStageStepper(lex(new SourceFile(
                "parser.mc",
                """
                        struct Point { int x; };
                        int add(int a, int b) { return a + b; }
                        """
        )));

        StepResult first = stepper.next();

        assertThat(first.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(first.description()).contains("build StructDecl Point");
        assertThat(stepper.canNext()).isTrue();
        assertThat(stepper.data().currentItem()).contains("build StructDecl Point");
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();

        int guard = 0;
        while (stepper.canNext() && guard++ < 100) {
            stepper.next();
        }

        assertThat(stepper.canNext()).isFalse();
        assertThat(stepper.snapshot().sourceName()).isEqualTo("parser.mc");
        assertThat(stepper.data().inputSummary()).anyMatch(summary -> summary.startsWith("tokens="));
        assertThat(stepper.data().inputSummary()).contains("first=STRUCT struct", "last=EOF <empty>");
        assertThat(stepper.data().currentItem()).isNotBlank();
        assertThat(stepper.data().accumulatedOutput())
                .anySatisfy(item -> assertThat(item).contains("build StructDecl Point"))
                .anySatisfy(item -> assertThat(item).contains("build FunctionDecl add"))
                .anySatisfy(item -> assertThat(item).contains("build BlockStmt"))
                .anySatisfy(item -> assertThat(item).contains("build ReturnStmt"))
                .anySatisfy(item -> assertThat(item).contains("build BinaryExpr PLUS"));
        assertThat(stepper.parserState().toParseResult().program().functions()).hasSize(1);
    }

    @Test
    void reportsParserDiagnosticsAndKeepsRecoverableProgress() {
        ParserStageStepper stepper = new ParserStageStepper(lex(new SourceFile(
                "invalid-parser.mc",
                "main() {} int main() { return 0; }"
        )));

        StepResult first = stepper.next();
        int guard = 0;
        while (stepper.canNext() && stepper.data().diagnostics().isEmpty() && guard++ < 100) {
            stepper.next();
        }

        assertThat(first.outcome()).isIn(StepOutcome.ADVANCED, StepOutcome.FAILED);
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(stepper.data().diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("PAR001");
        while (stepper.canNext() && guard++ < 100) {
            stepper.next();
        }
        assertThat(stepper.canNext()).isFalse();
        assertThat(stepper.data().accumulatedOutput())
                .anySatisfy(item -> assertThat(item).contains("FunctionDecl main"));
    }

    @Test
    void advancesLargeAstRevealWithoutRepeatedWholeTreeScans() {
        ParserStageStepper stepper = new ParserStageStepper(lex(new SourceFile(
                "large-parser.mc",
                generatedProgram(80)
        )));

        long started = System.nanoTime();
        int steps = 0;
        while (stepper.canNext()) {
            stepper.next();
            stepper.data();
            steps++;
        }
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(steps).isGreaterThan(500);
        assertThat(elapsedMillis).isLessThan(2_000);
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

    private static String generatedProgram(int functions) {
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < functions; index++) {
            source.append("int f").append(index).append("(int x) {\n")
                    .append("    int a = x + ").append(index).append(";\n")
                    .append("    int b = a * 2;\n")
                    .append("    if (b > 10) { return b; }\n")
                    .append("    return b + 1;\n")
                    .append("}\n");
        }
        return source.toString();
    }
}
