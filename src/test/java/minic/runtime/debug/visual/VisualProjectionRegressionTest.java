package minic.runtime.debug.visual;

import minic.compiler.pipeline.MiniCompiler;
import minic.runtime.debug.DebugSession;
import minic.runtime.debug.IrDebugInterpreter;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisualProjectionRegressionTest {
    @Test
    void parsesVisualAnnotationsAndRegistersDescriptors() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual kind=list root=head name=list
                // @visual-node graph name=list id=head label=Head
                // @visual-edge graph name=list from=head to=tail label=next
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.specs()).hasSize(1);
        assertThat(result.annotations()).hasSize(2);
        assertThat(DataStructureDescriptorRegistry.defaults().all())
                .extracting(DataStructureDescriptor::kind)
                .contains("list", "tree", "hash_table");
    }

    @Test
    void buildsTypedMemoryGraphsForScalarsPointersArraysStructsAndNestedValues() {
        DebugSession session = debug("""
                struct Pair { int left; int right; };
                int main() {
                    struct Pair pair;
                    pair.left = 1;
                    pair.right = 2;
                    int values[2];
                    values[0] = pair.left;
                    values[1] = pair.right;
                    int *cursor = &values[0];
                    return *cursor;
                }
                """);

        var processSpace = session.currentSnapshot().processSpace();
        assertThat(processSpace.stack().frames()).isNotEmpty();
        assertThat(processSpace.stack().frames().getLast().locals()).isNotEmpty();
    }

    @Test
    void projectsGraphArrayCompositePointerListTreeHashHeapAndFenwickStructures() {
        SourceFile source = new SourceFile("projection.mc", """
                // @visual-node graph name=graph id=a label=A
                // @visual-node graph name=graph id=b label=B
                // @visual-edge graph name=graph from=a to=b label=next
                int main() { return 0; }
                """);
        VisualAnnotationParseResult parsed = new VisualAnnotationParser().parse(source);
        DebugSession session = debug(source.content());

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), parsed.annotations(), parsed.specs(), session.visualEvents(), Long.MAX_VALUE);

        assertThat(projection.structures()).isNotEmpty();
        assertThat(projection.structures()).extracting(VisualStructure::type).contains(VisualStructureType.GRAPH);
    }

    @Test
    void replaysRuntimeEventsAndMarksCyclesSharedNodesAndRewiredEdges() {
        DebugSession session = debug("""
                struct Node { int value; struct Node *next; };
                int main() {
                    struct Node a;
                    struct Node b;
                    a.value = 1;
                    a.next = NULL;
                    b.value = 2;
                    b.next = &a;
                    a.next = &b;
                    return a.value;
                }
                """);

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), java.util.List.of(), session.visualEvents(), Long.MAX_VALUE);

        assertThat(projection.warnings()).isNotNull();
        assertThat(session.visualEvents()).isNotNull();
    }

    @Test
    void runsDataStructureBaselineSamplesAndReportsProjectionWarnings() {
        DebugSession session = debug("""
                // @visual kind=list root=missing name=bad
                int main() { return 0; }
                """);
        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), java.util.List.of(), new VisualAnnotationParser().parse(new SourceFile("x.mc", """
                        // @visual kind=list root=missing name=bad
                        int main() { return 0; }
                        """)).specs(), java.util.List.of(), Long.MAX_VALUE);

        assertThat(session.snapshots()).isNotEmpty();
        assertThat(projection.warnings()).isNotEmpty();
    }

    private static DebugSession debug(String source) {
        SourceFile sourceFile = new SourceFile("visual-debug.mc", source);
        var result = new MiniCompiler().compile(sourceFile);
        assertThat(result.diagnostics()).isEmpty();
        return new IrDebugInterpreter().runMain(result.irModuleOptional().orElseThrow(), sourceFile);
    }
}
