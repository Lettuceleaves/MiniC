package minic.runtime.debug;

import minic.compiler.ir.model.IrModule;
import minic.compiler.pipeline.MiniCompiler;
import minic.runtime.debug.dataflow.DataFlowEvent;
import minic.runtime.debug.dataflow.DataFlowEventType;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IrDebugRuntimeRegressionTest {
    @Test
    void executesIrProgramsBranchesLoopsCallsStubsAndRuntimeFailures() {
        DebugSession session = debug("""
                extern int printf(char *fmt, ...);
                int add(int a, int b) { return a + b; }
                int main() {
                    int total = 0;
                    for (int i = 0; i < 3; i++) { total = add(total, i); }
                    if (total > 0) { printf("total=%d\\n", total); }
                    return total;
                }
                """);

        assertThat(session.snapshots()).isNotEmpty();
        assertThat(session.currentSnapshot().processSpace().io().stdout()).contains("total=");
        assertThat(session.currentSnapshot().cursor().functionName()).isEqualTo("main");

        DebugSession failed = debug("int main() { int zero = 0; return 1 / zero; }");
        assertThat(failed.events()).isNotEmpty();
    }

    @Test
    void supportsBreakpointsRunPauseRestartAndRunToEndControls() {
        DebugSession session = debug("""
                int main() {
                    int x = 1;
                    x = x + 1;
                    return x;
                }
                """);

        assertThat(session.setBreakpoint(3).accepted()).isTrue();
        assertThat(session.control(DebugCommand.RESTART).snapshot().snapshotId()).isZero();
        assertThat(session.control(DebugCommand.RUN_TO_BREAKPOINT).snapshot().cursor().sourceRange().startPosition().line()).isEqualTo(3);
        assertThat(session.control(DebugCommand.PAUSE).state()).isEqualTo(DebugExecutionState.PAUSED);
        assertThat(session.control(DebugCommand.RUN_TO_END).state()).isEqualTo(DebugExecutionState.COMPLETED);
    }

    @Test
    void supportsStepBackBackToBreakpointStepOverAndStepIntoControls() {
        DebugSession session = debug("""
                int inc(int value) { return value + 1; }
                int main() {
                    int x = inc(1);
                    return x;
                }
                """);

        session.control(DebugCommand.RESTART);
        DebugSnapshot before = session.currentSnapshot();
        session.control(DebugCommand.STEP_INTO);
        session.control(DebugCommand.STEP_OVER);
        assertThat(session.currentSnapshot().snapshotId()).isGreaterThan(before.snapshotId());
        session.control(DebugCommand.STEP_BACK);
        assertThat(session.currentSnapshot().snapshotId()).isGreaterThanOrEqualTo(before.snapshotId());
        session.setBreakpoint(3);
        session.control(DebugCommand.BACK_TO_BREAKPOINT);
        assertThat(session.currentSnapshot()).isNotNull();
    }

    @Test
    void preservesSourceVisibleIrSnapshotsAndDebugMappings() {
        DebugSession session = debug("""
                int main() {
                    int x = 1;
                    x = x + 2;
                    return x;
                }
                """);

        assertThat(session.snapshots())
                .extracting(snapshot -> snapshot.cursor().instructionId())
                .anySatisfy(id -> assertThat(id).isNotBlank());
        assertThat(session.currentSnapshot().cursor().sourceRange()).isNotNull();
    }

    @Test
    void recordsRuntimeVisualAndDataFlowEventsForStructuresAndPointers() {
        DebugSession session = debug("""
                struct Node { int value; struct Node *next; };
                int main() {
                    struct Node node;
                    node.value = 1;
                    node.next = NULL;
                    struct Node *cursor = &node;
                    cursor->value = 2;
                    return cursor->value;
                }
                """);

        assertThat(session.dataFlowEvents()).isNotEmpty();
        assertThat(session.currentSnapshot().processSpace().stack().frames()).isNotEmpty();
    }

    @Test
    void recordsPointerFieldWritesWithStructFieldMetadata() {
        DebugSession session = debug("""
                struct Node {
                    long value;
                    struct Node *left;
                    struct Node *right;
                };
                int main() {
                    struct Node root;
                    struct Node left;
                    struct Node right;
                    root.left = &left;
                    root.right = &right;
                    return 0;
                }
                """);

        List<DataFlowEvent> writes = session.dataFlowEvents().stream()
                .filter(event -> event.type() == DataFlowEventType.FIELD_WRITE)
                .filter(event -> event.pointerFieldWrite() != null)
                .toList();

        assertThat(writes).hasSize(2);
        DataFlowEvent leftWrite = writes.stream()
                .filter(event -> event.pointerFieldWrite().fieldInfo().fieldName().equals("left"))
                .findFirst()
                .orElseThrow();
        DataFlowEvent rightWrite = writes.stream()
                .filter(event -> event.pointerFieldWrite().fieldInfo().fieldName().equals("right"))
                .findFirst()
                .orElseThrow();

        assertThat(leftWrite.lvaluePath()).endsWith(".left");
        assertThat(rightWrite.lvaluePath()).endsWith(".right");
        assertThat(leftWrite.pointerFieldWrite().ownerAddress()).isEqualTo(rightWrite.pointerFieldWrite().ownerAddress());
        assertThat(leftWrite.pointerFieldWrite().ownerAddress()).isNotBlank();
        assertThat(leftWrite.pointerFieldWrite().fieldInfo().ownerStructName()).isEqualTo("Node");
        assertThat(leftWrite.pointerFieldWrite().fieldInfo().declaredFieldIndex()).isEqualTo(1);
        assertThat(leftWrite.pointerFieldWrite().fieldInfo().pointerFieldIndex()).isEqualTo(0);
        assertThat(rightWrite.pointerFieldWrite().fieldInfo().ownerStructName()).isEqualTo("Node");
        assertThat(rightWrite.pointerFieldWrite().fieldInfo().declaredFieldIndex()).isEqualTo(2);
        assertThat(rightWrite.pointerFieldWrite().fieldInfo().pointerFieldIndex()).isEqualTo(1);
        assertThat(leftWrite.pointerFieldWrite().newTargetAddress()).isNotBlank();
        assertThat(rightWrite.pointerFieldWrite().newTargetAddress()).isNotBlank();
    }

    private static DebugSession debug(String source) {
        SourceFile sourceFile = new SourceFile("debug.mc", source);
        var result = new MiniCompiler().compile(sourceFile);
        assertThat(result.diagnostics()).isEmpty();
        IrModule module = result.irModuleOptional().orElseThrow();
        return new IrDebugInterpreter().runMain(module, sourceFile, result.semanticResultOptional().orElseThrow());
    }
}
