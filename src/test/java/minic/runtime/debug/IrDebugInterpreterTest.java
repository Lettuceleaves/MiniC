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

    @Test
    void recordsRuntimeVisualNodeEventsFromRecursiveFunctionVisits() {
        SourceFile sourceFile = new SourceFile("debug-runtime-visual.mc", """
                // @visual graph name=avl kind=tree root=root mode=runtime function=dfs visit=index
                // @visual-map node graph=avl id=index label=index
                int dfs(int index) {
                    if (index == 0) {
                        return 0;
                    }
                    return index + dfs(index - 1);
                }

                int main() {
                    int root = 3;
                    return dfs(root);
                }
                """);
        IrModule module = lower(sourceFile);

        DebugSession session = new IrDebugInterpreter().runMain(module, sourceFile);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.visualEvents()).extracting(event -> event.graphName() + ":" + event.nodeId())
                .containsExactly("avl:3", "avl:2", "avl:1");
        assertThat(session.visualEvents()).allSatisfy(event ->
                assertThat(event.snapshotId()).isGreaterThan(0));
    }

    @Test
    void dispatchesExternalPrintfThroughDebugStub() {
        SourceFile sourceFile = new SourceFile("debug-printf.mc", """
                extern int printf(char *format, ...);

                int main() {
                    int count = printf("value=%d\\n", 7);
                    return count;
                }
                """);
        IrModule module = lower(sourceFile);

        DebugSession session = new IrDebugInterpreter().runMain(module, sourceFile);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.currentSnapshot().processSpace().io().stdout()).isEqualTo("value=7\nreturn 8");
        assertThat(session.currentSnapshot().processSpace().staticData().stringLiterals())
                .anySatisfy(stringLiteral -> {
                    assertThat(stringLiteral.name()).startsWith("__minic$str$");
                    assertThat(stringLiteral.value().summary()).isEqualTo("array[9]");
                });
        assertThat(session.events()).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("CALL_EXTERNAL");
            assertThat(event.title()).isEqualTo("调用外部函数");
            assertThat(event.description()).contains("printf 输出");
        });
    }

    @Test
    void executesForLoopWithPostIncrementAndPrintf() {
        SourceFile sourceFile = new SourceFile("debug-for-printf.mc", """
                extern int printf(char *format, ...);

                int main() {
                    int a = 0;
                    for (int i = 0; i < 100; i++) {
                        a += i;
                    }
                    printf("value = %d\\n", a);
                    return 42;
                }
                """);
        IrModule module = lower(sourceFile);

        DebugSession session = new IrDebugInterpreter().runMain(module, sourceFile);

        assertThat(session.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(session.currentSnapshot().processSpace().io().stdout()).isEqualTo("value = 4950\nreturn 42");
        assertThat(session.snapshots()).hasSizeLessThan(3_000);
    }

    @Test
    void supportsBreakpointsAndForwardControls() {
        SourceFile sourceFile = new SourceFile("debug-breakpoint.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);
        DebugSession session = new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
        session.control(DebugCommand.RESTART);

        DebugBreakpointResult breakpointResult = session.setBreakpoint(3);
        DebugControlResult runResult = session.control(DebugCommand.RUN_TO_BREAKPOINT);

        assertThat(breakpointResult.accepted()).isTrue();
        assertThat(runResult.state()).isEqualTo(DebugExecutionState.PAUSED);
        assertThat(runResult.snapshot().stopReason()).isEqualTo(DebugStopReason.BREAKPOINT);
        assertThat(runResult.snapshot().breakpointHit()).isTrue();
        assertThat(runResult.snapshot().cursor().sourceRange().startPosition().line()).isEqualTo(3);

        DebugControlResult stepResult = session.control(DebugCommand.STEP_OVER);

        assertThat(stepResult.snapshot().visibleStepIndex()).isGreaterThan(runResult.snapshot().visibleStepIndex());
    }

    @Test
    void sourceVisibleStepKeepsIrSnapshotsForExpressionDetail() {
        SourceFile sourceFile = new SourceFile("debug-visible-step.mc", """
                int main() {
                    int value = 1;
                    value = value + 2;
                    return value;
                }
                """);
        DebugSession session = new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
        session.control(DebugCommand.RESTART);
        session.setBreakpoint(3);
        DebugControlResult breakpoint = session.control(DebugCommand.RUN_TO_BREAKPOINT);

        long visibleStep = breakpoint.snapshot().visibleStepIndex();
        long irSnapshotsInExpression = session.snapshots().stream()
                .filter(snapshot -> snapshot.cursor().sourceRangeOptional()
                        .map(range -> range.startPosition().line() == 3)
                        .orElse(false))
                .filter(snapshot -> snapshot.visibleStepIndex() == visibleStep)
                .count();
        DebugControlResult next = session.control(DebugCommand.STEP_OVER);

        assertThat(irSnapshotsInExpression).isGreaterThan(1);
        assertThat(next.snapshot().cursor().sourceRange().startPosition().line()).isEqualTo(4);
    }

    @Test
    void runsToCompletionWithoutBreakpointsAndCanRestartWithBreakpointsKept() {
        SourceFile sourceFile = new SourceFile("debug-run.mc", """
                int main() {
                    int value = 1;
                    return value;
                }
                """);
        DebugSession session = new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
        session.control(DebugCommand.RESTART);
        session.setBreakpoint(3);
        session.clearBreakpoint(3);

        DebugControlResult runResult = session.control(DebugCommand.RUN_TO_BREAKPOINT);

        assertThat(runResult.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(runResult.snapshot().processSpace().io().stdout()).isEqualTo("return 1");

        session.setBreakpoint(3);
        DebugControlResult restartResult = session.control(DebugCommand.RESTART);

        assertThat(restartResult.state()).isEqualTo(DebugExecutionState.PAUSED);
        assertThat(session.breakpoints()).extracting(DebugBreakpoint::line).containsExactly(3);
        assertThat(session.currentSnapshot().stopReason()).isEqualTo(DebugStopReason.START);
    }

    @Test
    void pauseRequestOnlyStopsContinuousRun() {
        SourceFile sourceFile = new SourceFile("debug-pause.mc", """
                int main() {
                    int value = 1;
                    value = value + 1;
                    return value;
                }
                """);
        DebugSession session = new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
        session.control(DebugCommand.RESTART);

        DebugControlResult pauseResult = session.control(DebugCommand.PAUSE);
        DebugControlResult stepResult = session.control(DebugCommand.STEP_OVER);

        assertThat(pauseResult.state()).isEqualTo(DebugExecutionState.PAUSED);
        assertThat(stepResult.snapshot().stopReason()).isEqualTo(DebugStopReason.STEP);

        session.control(DebugCommand.RESTART);
        session.control(DebugCommand.PAUSE);
        DebugControlResult runResult = session.control(DebugCommand.FAST_FORWARD);

        assertThat(runResult.state()).isEqualTo(DebugExecutionState.PAUSED);
        assertThat(runResult.snapshot().stopReason()).isEqualTo(DebugStopReason.PAUSE_REQUESTED);
    }

    @Test
    void supportsStepBackAndBackToBreakpointSnapshots() {
        SourceFile sourceFile = new SourceFile("debug-back.mc", """
                extern int printf(char *format, ...);

                int main() {
                    int value = 1;
                    printf("value=%d\\n", value);
                    value = value + 1;
                    return value;
                }
                """);
        DebugSession session = new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
        session.control(DebugCommand.RESTART);
        session.setBreakpoint(5);
        DebugControlResult breakpointResult = session.control(DebugCommand.RUN_TO_BREAKPOINT);
        session.clearBreakpoint(5);
        DebugControlResult completedResult = session.control(DebugCommand.FAST_FORWARD);

        assertThat(completedResult.state()).isEqualTo(DebugExecutionState.COMPLETED);
        assertThat(completedResult.snapshot().processSpace().io().stdout()).isEqualTo("value=1\nreturn 2");

        DebugControlResult stepBackResult = session.control(DebugCommand.STEP_BACK);
        DebugControlResult backToBreakpointResult = session.control(DebugCommand.BACK_TO_BREAKPOINT);

        assertThat(stepBackResult.snapshot().visibleStepIndex()).isLessThan(completedResult.snapshot().visibleStepIndex());
        assertThat(backToBreakpointResult.snapshot()).isEqualTo(breakpointResult.snapshot());
        assertThat(backToBreakpointResult.snapshot().processSpace().io().stdout())
                .isEqualTo(breakpointResult.snapshot().processSpace().io().stdout());
    }

    @Test
    void returnsBackToCallSiteSnapshot() {
        SourceFile sourceFile = new SourceFile("debug-back-call.mc", """
                int inc(int value) {
                    int next = value + 1;
                    return next;
                }

                int main() {
                    int value = inc(1);
                    return value;
                }
                """);
        DebugSession session = new IrDebugInterpreter().runMain(lower(sourceFile), sourceFile);
        session.control(DebugCommand.RESTART);

        DebugControlResult insideCall = null;
        while (session.state() != DebugExecutionState.COMPLETED) {
            DebugControlResult result = session.control(DebugCommand.STEP_INTO);
            if (result.snapshot().callStackSummary().contains("inc")) {
                insideCall = result;
                break;
            }
        }

        assertThat(insideCall).isNotNull();
        DebugControlResult callSite = session.control(DebugCommand.BACK_TO_CALL_SITE);

        assertThat(callSite.snapshot().callStackSummary()).containsExactly("main");
        assertThat(callSite.snapshot().visibleStepIndex()).isLessThan(insideCall.snapshot().visibleStepIndex());
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
