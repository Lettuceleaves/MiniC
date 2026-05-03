package minic.compiler.ir.lowering;

import minic.compiler.ast.decl.Program;
import minic.compiler.ir.instruction.IrCallInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.model.IrModule;
import minic.compiler.lexer.LexResult;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.ParseResult;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.semantic.SemanticResult;
import minic.runtime.step.CompileStage;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IrStepStateTest {
    @Test
    void advancesIrStructureActionsAndBuildsEquivalentModule() {
        Program program = parse("""
                extern int puts(char *text);
                int add(int left, int right) { return left + right; }
                int main() { return puts("hello") + add(1, 2); }
                """);
        SemanticResult semanticResult = new SemanticAnalyzer().analyze(program);
        assertThat(semanticResult.diagnostics()).isEmpty();
        IrStepState state = new IrStepState(program, semanticResult);

        assertThat(state.stage()).isEqualTo(CompileStage.IR);
        assertThat(state.input().program()).isSameAs(program);

        assertThat(state.next()).isEqualTo(new IrLoweringAction(IrLoweringActionKind.REGISTER_EXTERNAL, "puts"));
        assertThat(state.work().externalFunctionCount()).isEqualTo(1);
        assertThat(state.next()).isEqualTo(new IrLoweringAction(IrLoweringActionKind.LOWER_FUNCTION, "add"));
        assertThat(state.work().functionCount()).isEqualTo(1);
        assertThat(state.next()).isEqualTo(new IrLoweringAction(IrLoweringActionKind.LOWER_FUNCTION, "main"));
        assertThat(state.next()).isEqualTo(new IrLoweringAction(IrLoweringActionKind.COMPLETE_MODULE, "module"));

        IrModule stepped = state.toIrModule();
        IrModule direct = new IrLowerer().lower(program, semanticResult);

        assertThat(stepped.externalFunctionNames()).containsExactly("puts");
        assertThat(stepped.functions()).extracting(IrFunction::name).containsExactly("add", "main");
        assertThat(stepped.stringData()).hasSize(1);
        assertThat(stepped.findFunction("main").orElseThrow().blocks().getFirst().instructions())
                .filteredOn(IrCallInstruction.class::isInstance)
                .hasSize(2);
        assertThat(stepped.functions()).extracting(IrFunction::name)
                .containsExactlyElementsOf(direct.functions().stream().map(IrFunction::name).toList());
        assertThat(state.result().output().irModule().functions()).hasSize(2);
    }

    @Test
    void rejectsAdvancingAfterCompletion() {
        IrStepState state = new IrStepState(parse("int main() { return 0; }"), java.util.Map.of(), java.util.Map.of());

        while (state.canNext()) {
            state.next();
        }

        assertThatThrownBy(state::next)
                .isInstanceOf(IllegalStateException.class);
    }

    private Program parse(String source) {
        SourceFile sourceFile = new SourceFile("ir-step.mc", source);
        LexResult lexResult = new Lexer(sourceFile).lex();
        assertThat(lexResult.diagnostics()).isEmpty();
        ParseResult parseResult = new Parser(lexResult.tokens()).parse();
        assertThat(parseResult.diagnostics()).isEmpty();
        return parseResult.program();
    }
}
