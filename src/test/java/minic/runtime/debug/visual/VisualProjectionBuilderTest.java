package minic.runtime.debug.visual;

import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugStackFrame;
import minic.runtime.debug.DebugStackSegment;
import minic.runtime.debug.DebugValue;
import minic.runtime.debug.DebugVirtualAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisualProjectionBuilderTest {
    @Test
    void projectsArrayGraphCompositeAndMergesGraphAnnotations() {
        DebugProcessSpace processSpace = processSpace();
        List<VisualAnnotation> annotations = new VisualAnnotationParser().parse(new minic.source.SourceFile("visual.mc", """
                // @visual array name=arr kind=array root=a
                // @visual graph name=list kind=list root=head
                // @visual-node graph=network id=1 label=a
                // @visual-node graph=network id=2 label=b
                // @visual-edge graph=network from=1 to=2 directed=true
                // @visual composite name=cache kind=hash_table
                int main() { return 0; }
                """)).annotations();

        VisualProjection projection = new VisualProjectionBuilder().build(processSpace, annotations);

        assertThat(projection.warnings()).isEmpty();
        assertThat(projection.structures()).extracting(VisualStructure::type)
                .contains(VisualStructureType.ARRAY, VisualStructureType.GRAPH, VisualStructureType.COMPOSITE);
        assertThat(projection.structures())
                .filteredOn(GraphStructure.class::isInstance)
                .map(GraphStructure.class::cast)
                .anySatisfy(graph -> {
                    assertThat(graph.name()).isEqualTo("network");
                    assertThat(graph.nodes()).hasSize(2);
                    assertThat(graph.edges()).hasSize(1);
                });
    }

    @Test
    void keepsDisconnectedVisualNodesInSameGraphStructure() {
        DebugProcessSpace processSpace = processSpace();
        List<VisualAnnotation> annotations = new VisualAnnotationParser().parse(new minic.source.SourceFile("visual-components.mc", """
                // @visual-node graph=network id=1 label=a
                // @visual-node graph=network id=2 label=b
                // @visual-node graph=network id=3 label=c
                int main() { return 0; }
                """)).annotations();

        VisualProjection projection = new VisualProjectionBuilder().build(processSpace, annotations);

        assertThat(projection.structures()).singleElement().satisfies(structure -> {
            GraphStructure graph = (GraphStructure) structure;
            assertThat(graph.name()).isEqualTo("network");
            assertThat(graph.nodes()).hasSize(3);
            assertThat(graph.components()).singleElement().satisfies(component ->
                    assertThat(component.nodeIds()).hasSize(3));
        });
    }

    @Test
    void reportsMissingRootAsProjectionWarning() {
        DebugProcessSpace processSpace = processSpace();
        List<VisualAnnotation> annotations = new VisualAnnotationParser().parse(new minic.source.SourceFile("visual-warning.mc", """
                // @visual array name=missing kind=array root=absent
                int main() { return 0; }
                """)).annotations();

        VisualProjection projection = new VisualProjectionBuilder().build(processSpace, annotations);

        assertThat(projection.warnings()).singleElement().satisfies(warning ->
                assertThat(warning).contains("未找到 visual root 变量"));
    }

    @Test
    void replaysRuntimeVisualEventsUpToCurrentSnapshot() {
        DebugProcessSpace processSpace = processSpace();
        List<VisualAnnotation> annotations = new VisualAnnotationParser().parse(new minic.source.SourceFile("runtime-visual.mc", """
                // @visual graph name=avl kind=tree root=root mode=runtime function=dfs visit=index
                // @visual-map node graph=avl id=index label=value
                // @visual-map edge graph=avl key=left from=index to=left
                int main() { return 0; }
                """)).annotations();
        List<VisualEvent> events = List.of(
                VisualEvent.nodeCreated(1, "avl", "3", "30"),
                VisualEvent.metaSet(2, "avl", "3", "height", "2"),
                VisualEvent.nodeCreated(3, "avl", "2", "20"),
                VisualEvent.edgeSet(4, "avl", "left", "3", "2")
        );

        VisualProjection partial = new VisualProjectionBuilder().build(processSpace, annotations, events, 2);
        VisualProjection complete = new VisualProjectionBuilder().build(processSpace, annotations, events, 4);

        GraphStructure partialGraph = (GraphStructure) partial.structures().getFirst();
        GraphStructure completeGraph = (GraphStructure) complete.structures().getFirst();
        assertThat(partialGraph.nodes()).singleElement().satisfies(node -> {
            assertThat(node.label()).isEqualTo("30");
            assertThat(node.metadata()).containsEntry("height", "2");
        });
        assertThat(partialGraph.edges()).isEmpty();
        assertThat(completeGraph.nodes()).hasSize(2);
        assertThat(completeGraph.edges()).singleElement().satisfies(edge -> {
            assertThat(edge.fromNodeId()).isEqualTo("avl-node-3");
            assertThat(edge.toNodeId()).isEqualTo("avl-node-2");
            assertThat(edge.metadata()).containsEntry("from", "3");
            assertThat(edge.metadata()).containsEntry("to", "2");
        });
    }

    private DebugProcessSpace processSpace() {
        DebugMemoryEntry a = new DebugMemoryEntry(
                "a",
                new DebugVirtualAddress("stack", 1),
                "int",
                DebugValue.intValue(7)
        );
        DebugMemoryEntry head = new DebugMemoryEntry(
                "head",
                new DebugVirtualAddress("stack", 2),
                "Node *",
                DebugValue.pointerValue("Node *", new DebugVirtualAddress("heap", 100))
        );
        return new DebugProcessSpace(
                minic.runtime.debug.DebugCodeSegment.empty(),
                minic.runtime.debug.DebugStaticSegment.empty(),
                new DebugStackSegment(List.of(new DebugStackFrame(
                        "frame-main",
                        "main",
                        List.of(),
                        List.of(a, head),
                        null,
                        null
                ))),
                minic.runtime.debug.DebugHeapSegment.empty(),
                minic.runtime.debug.DebugIoSegment.empty()
        );
    }
}
