package minic.runtime.debug;

import minic.compiler.ast.decl.Program;
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

class IrDebugInterpreterTest {
    @Test
    void executesMainThroughLocalStoresLoadsMovesAndReturn() {
        SourceFile sourceFile = new SourceFile("debug.mc", """
                int main() {
                    int x = 1;
                    return x;
                }
                """);
        IrModule module = lower(sourceFile);

        DebugSession session = new IrDebugInterpreter().runMain(module, sourceFile);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.currentSnapshot().stopReason()).isEqualTo(DebugStopReason.COMPLETED);
        assertThat(session.currentSnapshot().processSpace().io().stdout()).isEqualTo("return 1");
        assertThat(session.snapshots()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(session.events()).extracting(DebugEvent::type)
                .contains("DECLARE_LOCAL", "STORE_LOCAL", "LOAD_LOCAL", "RETURN");
        assertThat(session.currentSnapshot().processSpace().stack().frames()).singleElement().satisfies(frame ->
                assertThat(frame.locals()).anySatisfy(local -> {
                    assertThat(local.name()).isEqualTo("x");
                    assertThat(local.valueSummary()).isEqualTo("1");
                }));
    }

    private IrModule lower(SourceFile sourceFile) {
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
