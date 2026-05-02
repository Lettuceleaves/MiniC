package minic.runtime.step;

import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.windows.WindowsX64AssemblyEmitter;
import minic.compiler.ir.lowering.IrLowerer;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodegenStageStepperTest {
    @Test
    void advancesAssemblyLinesThroughUnifiedStageApi() {
        IrModule module = lower("""
                extern int puts(int *text);
                int main() {
                    return puts("ok");
                }
                """);
        CodegenStageStepper stepper = new CodegenStageStepper(module);

        StepResult first = stepper.next();
        while (stepper.canNext()) {
            stepper.next();
        }

        AssemblySource assemblySource = stepper.codegenState().toAssemblySource();
        assertThat(first.outcome()).isEqualTo(StepOutcome.ADVANCED);
        assertThat(first.description()).contains("HEADER target");
        assertThat(stepper.snapshot().sourceRangeOptional()).isPresent();
        assertThat(stepper.data().inputSummary()).contains("functions=1", "strings=1", "externs=1");
        assertThat(stepper.data().currentItem()).contains("END module END", "section=end", "label=module");
        assertThat(stepper.data().accumulatedOutput()).contains(
                "; target: windows_x86_64".replace("windows_x86_64", "windows-x86_64"),
                "PUBLIC minic$entry",
                "END"
        );
        assertThat(assemblySource).isEqualTo(new WindowsX64AssemblyEmitter().emit(module));
    }

    @Test
    void reportsCannotAdvanceAfterCompletionAndPreviousUnsupported() {
        CodegenStageStepper stepper = new CodegenStageStepper(lower("int main() { return 0; }"));

        while (stepper.canNext()) {
            stepper.next();
        }
        StepResult afterCompletion = stepper.next();
        StepResult previous = stepper.previous();

        assertThat(afterCompletion.outcome()).isEqualTo(StepOutcome.CANNOT_ADVANCE);
        assertThat(stepper.canPrevious()).isFalse();
        assertThat(previous.outcome()).isEqualTo(StepOutcome.UNSUPPORTED);
    }

    private IrModule lower(String source) {
        SourceFile sourceFile = new SourceFile("codegen-runtime.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        Program program = parseResult.program();
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();
        return new IrLowerer().lower(program, semanticResult);
    }
}
