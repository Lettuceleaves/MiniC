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
        assertThat(session.snapshots()).anySatisfy(snapshot ->
                assertThat(snapshot.processSpace().stack().frames()).anySatisfy(frame ->
                        assertThat(frame.locals()).anySatisfy(local -> {
                            assertThat(local.name()).isEqualTo("x");
                            assertThat(local.valueSummary()).isEqualTo("1");
                        })));
    }

    @Test
    void executesIntegerExpressionsBranchesLoopsAndSwitchLowering() {
        SourceFile sourceFile = new SourceFile("debug-control.mc", """
                int main() {
                    int value = 0;
                    if (value == 0) {
                        value = 2;
                    }
                    while (value < 5) {
                        value = value + 1;
                    }
                    switch (value) {
                        case 5:
                            value = value * 2;
                            break;
                        default:
                            value = 1;
                    }
                    return value;
                }
                """);
        IrModule module = lower(sourceFile);

        DebugSession session = new IrDebugInterpreter().runMain(module, sourceFile);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.currentSnapshot().processSpace().io().stdout()).isEqualTo("return 10");
        assertThat(session.events()).extracting(DebugEvent::type)
                .contains("BINARY", "BRANCH", "JUMP", "CHECK_INITIALIZED", "RETURN");
    }

    @Test
    void failsOnDivisionByZeroCheck() {
        SourceFile sourceFile = new SourceFile("debug-div-zero.mc", """
                int main() {
                    int value = 0;
                    return 1 / value;
                }
                """);
        IrModule module = lower(sourceFile);

        DebugSession session = new IrDebugInterpreter().runMain(module, sourceFile);

        assertThat(session.state()).isEqualTo(DebugExecutionState.FAILED);
        assertThat(session.currentSnapshot().stopReason()).isEqualTo(DebugStopReason.ERROR);
        assertThat(session.events()).extracting(DebugEvent::type).contains("CHECK_NON_ZERO");
    }

    @Test
    void executesNestedAndRecursiveFunctionCallsWithCallStackSnapshots() {
        SourceFile sourceFile = new SourceFile("debug-call.mc", """
                int inc(int value) {
                    return value + 1;
                }

                int sumDown(int value) {
                    if (value == 0) {
                        return 0;
                    }
                    return value + sumDown(value - 1);
                }

                int main() {
                    int x = inc(2);
                    return sumDown(x);
                }
                """);
        IrModule module = lower(sourceFile);

        DebugSession session = new IrDebugInterpreter().runMain(module, sourceFile);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.currentSnapshot().processSpace().io().stdout()).isEqualTo("return 6");
        assertThat(session.events()).extracting(DebugEvent::type).contains("CALL", "RETURN");
        assertThat(session.snapshots())
                .anySatisfy(snapshot -> assertThat(snapshot.callStackSummary()).contains("main", "inc"))
                .anySatisfy(snapshot -> assertThat(snapshot.callStackSummary()).contains("sumDown"));
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
