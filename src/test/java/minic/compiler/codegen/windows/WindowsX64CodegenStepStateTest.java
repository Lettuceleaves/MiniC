package minic.compiler.codegen.windows;

import minic.compiler.ast.decl.Program;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.ir.lowering.IrLowerer;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.compiler.stage.CompilerStageStatus;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsX64CodegenStepStateTest {
    @Test
    void advancesAssemblyLinesAndBuildsEquivalentAssemblySource() {
        IrModule module = lowerWithSemantic("""
                extern int printf(int *format, int value);

                int add(int left, int right) {
                    return left + right;
                }

                int main() {
                    int value = add(40, 2);
                    printf("value=%d\\n", value);
                    return value;
                }
                """);
        WindowsX64CodegenStepState state = new WindowsX64CodegenStepState(module);
        ArrayList<WindowsX64AssemblyLine> lines = new ArrayList<>();

        while (state.canNext()) {
            lines.add(state.next());
        }

        AssemblySource stepped = state.toAssemblySource();
        AssemblySource oneShot = new WindowsX64AssemblyEmitter().emit(module);
        assertThat(stepped).isEqualTo(oneShot);
        assertThat(lines).extracting(WindowsX64AssemblyLine::text).contains(
                "; target: windows-x86_64",
                "PUBLIC minic$entry",
                "EXTERN ExitProcess:PROC",
                "EXTERN printf:PROC",
                ".const",
                "__minic$str$0 BYTE 118, 97, 108, 117, 101, 61, 37, 100, 10, 0",
                ".code",
                "minic$entry PROC",
                "main PROC",
                "END"
        );
        assertThat(lines).extracting(WindowsX64AssemblyLine::kind).contains(
                WindowsX64AssemblyLineKind.HEADER,
                WindowsX64AssemblyLineKind.STRING_DATA,
                WindowsX64AssemblyLineKind.ENTRY_POINT,
                WindowsX64AssemblyLineKind.FUNCTION_STRUCTURE,
                WindowsX64AssemblyLineKind.INSTRUCTION,
                WindowsX64AssemblyLineKind.END
        );
        assertThat(state.snapshot().status()).isEqualTo(CompilerStageStatus.COMPLETED);
        assertThat(state.snapshot().progress().completed()).isTrue();
        assertThat(state.work().completedLineCount()).isEqualTo(lines.size());
        assertThat(state.work().currentFrameLayout()).isEmpty();
        assertThat(state.result().output().assemblySource()).isEqualTo(oneShot);
    }

    @Test
    void rejectsAdvancingAfterCompletion() {
        WindowsX64CodegenStepState state = new WindowsX64CodegenStepState(lowerWithSemantic("int main() { return 0; }"));

        state.toAssemblySource();

        assertThat(state.canNext()).isFalse();
        assertThatThrownBy(state::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");
    }

    private IrModule lowerWithSemantic(String source) {
        SourceFile sourceFile = new SourceFile("codegen-step.mc", source);
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
