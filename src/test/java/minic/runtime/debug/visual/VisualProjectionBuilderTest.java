package minic.runtime.debug.visual;

import minic.runtime.debug.DebugMemoryEntry;
import minic.runtime.debug.DebugProcessSpace;
import minic.runtime.debug.DebugStackFrame;
import minic.runtime.debug.DebugStackSegment;
import minic.runtime.debug.DebugValue;
import minic.runtime.debug.DebugValueElement;
import minic.runtime.debug.DebugValueField;
import minic.runtime.debug.DebugVirtualAddress;
import org.junit.jupiter.api.Test;

import java.util.Map;
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
                VisualEvent.edgeSet(4, "avl", "left", "3", "2"),
                VisualEvent.edgeSet(5, "avl", "right", "3", "0"),
                VisualEvent.edgeSet(6, "avl", "right", "3", "2")
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
        GraphStructure withNull = (GraphStructure) new VisualProjectionBuilder()
                .build(processSpace, annotations, events, 5)
                .structures()
                .getFirst();
        assertThat(withNull.nodes()).anySatisfy(node -> {
            assertThat(node.label()).isEqualTo("null");
            assertThat(node.metadata()).containsEntry("visual-null", "true");
        });
        assertThat(withNull.edges()).anySatisfy(edge ->
                assertThat(edge.metadata()).containsEntry("to", "null-3-right"));
        GraphStructure replacedNull = (GraphStructure) new VisualProjectionBuilder()
                .build(processSpace, annotations, events, 6)
                .structures()
                .getFirst();
        assertThat(replacedNull.nodes()).noneSatisfy(node ->
                assertThat(node.metadata()).containsEntry("id", "null-3-right"));
        assertThat(replacedNull.edges()).anySatisfy(edge ->
                assertThat(edge.metadata()).containsEntry("to", "2"));
    }

    @Test
    void infersSelfLoopAsRuntimeStateMetadata() {
        List<VisualAnnotation> annotations = runtimeGraphAnnotations("ring", "graph");
        List<VisualEvent> events = List.of(
                VisualEvent.nodeCreated(1, "ring", "a", "A"),
                VisualEvent.edgeSet(2, "ring", "next", "a", "a")
        );

        GraphStructure graph = (GraphStructure) new VisualProjectionBuilder()
                .build(processSpace(), annotations, events, 2)
                .structures()
                .getFirst();

        assertThat(graph.edges()).singleElement().satisfies(edge ->
                assertThat(edge.metadata()).containsEntry("visual-state", "self-loop"));
    }

    @Test
    void infersCycleAsRuntimeStateMetadataWithoutSeparateCycleTemplate() {
        List<VisualAnnotation> annotations = runtimeGraphAnnotations("list", "graph");
        List<VisualEvent> events = List.of(
                VisualEvent.nodeCreated(1, "list", "a", "A"),
                VisualEvent.nodeCreated(1, "list", "b", "B"),
                VisualEvent.nodeCreated(1, "list", "c", "C"),
                VisualEvent.edgeSet(2, "list", "next", "a", "b"),
                VisualEvent.edgeSet(3, "list", "next", "b", "c"),
                VisualEvent.edgeSet(4, "list", "next", "c", "a")
        );

        GraphStructure graph = (GraphStructure) new VisualProjectionBuilder()
                .build(processSpace(), annotations, events, 4)
                .structures()
                .getFirst();

        assertThat(graph.kind()).isEqualTo("graph");
        assertThat(graph.edges()).allSatisfy(edge ->
                assertThat(edge.metadata()).containsEntry("visual-state", "cycle"));
    }

    @Test
    void infersSharedNodeAsRuntimeStateMetadata() {
        List<VisualAnnotation> annotations = runtimeGraphAnnotations("tree", "tree");
        List<VisualEvent> events = List.of(
                VisualEvent.nodeCreated(1, "tree", "p", "P"),
                VisualEvent.nodeCreated(1, "tree", "q", "Q"),
                VisualEvent.nodeCreated(1, "tree", "x", "X"),
                VisualEvent.edgeSet(2, "tree", "left", "p", "x"),
                VisualEvent.edgeSet(3, "tree", "right", "q", "x")
        );

        GraphStructure graph = (GraphStructure) new VisualProjectionBuilder()
                .build(processSpace(), annotations, events, 3)
                .structures()
                .getFirst();

        assertThat(graph.nodes()).anySatisfy(node -> {
            assertThat(node.metadata()).containsEntry("id", "x");
            assertThat(node.metadata()).containsEntry("visual-state", "shared-node");
        });
    }

    @Test
    void marksRuntimeEdgeReplacementAsRewireState() {
        List<VisualAnnotation> annotations = runtimeGraphAnnotations("list", "graph");
        List<VisualEvent> events = List.of(
                VisualEvent.nodeCreated(1, "list", "a", "A"),
                VisualEvent.nodeCreated(1, "list", "b", "B"),
                VisualEvent.nodeCreated(1, "list", "c", "C"),
                VisualEvent.edgeSet(2, "list", "next", "a", "b"),
                VisualEvent.edgeSet(3, "list", "next", "a", "c")
        );

        GraphStructure graph = (GraphStructure) new VisualProjectionBuilder()
                .build(processSpace(), annotations, events, 3)
                .structures()
                .getFirst();

        assertThat(graph.edges()).singleElement().satisfies(edge ->
                assertThat(edge.metadata())
                        .containsEntry("to", "c")
                        .containsEntry("visual-state", "rewire"));
    }

    @Test
    void projectsTypedArrayCellsFromDebugValueElements() {
        DebugProcessSpace processSpace = processSpace(arrayEntry("arr", 10, 1, 2, 3));

        VisualProjection projection = buildSpecs(processSpace, spec("arr", "arr", VisualKind.ARRAY));

        assertThat(projection.warnings()).isEmpty();
        ArrayStructure array = (ArrayStructure) projection.structures().getFirst();
        assertThat(array.shape()).isEqualTo(ArrayShape.oneDimensional(3));
        assertThat(array.cells()).hasSize(3);
        assertThat(array.cells()).extracting(ArrayCell::linearIndex).containsExactly(0, 1, 2);
        assertThat(array.cells()).allSatisfy(cell ->
                assertThat(cell.metadata())
                        .containsEntry("root", "arr")
                        .containsEntry("type", "int")
                        .containsKey("address")
                        .containsKey("valueSummary"));
        assertThat(array.cells().get(1).metadata()).containsEntry("indexPath", "[1]");
    }

    @Test
    void projectsTypedMatrixUsingRowsAndColumnsAttributes() {
        DebugProcessSpace processSpace = processSpace(arrayEntry("matrix", 20, 1, 2, 3, 4, 5, 6));
        VisualSpec spec = spec("matrix", "matrix", VisualKind.MATRIX, Map.of("rows", "2", "columns", "3"));

        VisualProjection projection = buildSpecs(processSpace, spec);

        ArrayStructure matrix = (ArrayStructure) projection.structures().getFirst();
        assertThat(matrix.kind()).isEqualTo("matrix");
        assertThat(matrix.dimensions()).isEqualTo(2);
        assertThat(matrix.shape()).isEqualTo(ArrayShape.twoDimensional(2, 3));
        assertThat(matrix.cells()).hasSize(6);
        assertThat(matrix.cells().get(4)).satisfies(cell -> {
            assertThat(cell.row()).isEqualTo(1);
            assertThat(cell.column()).isEqualTo(1);
            assertThat(cell.metadata()).containsEntry("indexPath", "[1][1]");
        });
    }

    @Test
    void fallsBackWhenMatrixDimensionsCannotContainElements() {
        DebugProcessSpace processSpace = processSpace(arrayEntry("matrix", 21, 1, 2, 3, 4, 5, 6));
        VisualSpec spec = spec("matrix", "matrix", VisualKind.MATRIX, Map.of("rows", "1", "columns", "2"));

        VisualProjection projection = buildSpecs(processSpace, spec);

        ArrayStructure matrix = (ArrayStructure) projection.structures().getFirst();
        assertThat(projection.warnings()).singleElement().satisfies(warning ->
                assertThat(warning).contains("矩阵维度不足或过大"));
        assertThat(matrix.shape()).isEqualTo(new ArrayShape(1, 6, 6, 6));
        assertThat(matrix.cells().get(4)).satisfies(cell -> {
            assertThat(cell.row()).isZero();
            assertThat(cell.column()).isEqualTo(4);
            assertThat(cell.metadata())
                    .containsEntry("row", "0")
                    .containsEntry("column", "4")
                    .containsEntry("indexPath", "[0][4]");
        });
    }

    @Test
    void fallsBackWhenMatrixDimensionsOverflow() {
        DebugProcessSpace processSpace = processSpace(arrayEntry("matrix", 22, 1));
        VisualSpec spec = spec("matrix", "matrix", VisualKind.MATRIX, Map.of(
                "rows", Integer.toString(Integer.MAX_VALUE),
                "columns", Integer.toString(Integer.MAX_VALUE)
        ));

        VisualProjection projection = buildSpecs(processSpace, spec);

        ArrayStructure matrix = (ArrayStructure) projection.structures().getFirst();
        assertThat(projection.warnings()).singleElement().satisfies(warning ->
                assertThat(warning).contains("矩阵维度不足或过大"));
        assertThat(matrix.shape()).isEqualTo(new ArrayShape(1, 1, 1, 1));
    }

    @Test
    void projectsTypedStructFieldsAsCompositeParts() {
        DebugProcessSpace processSpace = processSpace(pointEntry("p", 30, 10, 20));

        VisualProjection projection = buildSpecs(processSpace, spec("point", "p", VisualKind.STRUCT));

        CompositeStructure composite = (CompositeStructure) projection.structures().getFirst();
        assertThat(composite.kind()).isEqualTo("struct");
        assertThat(composite.parts()).extracting(part -> part.metadata().get("fieldName"))
                .containsExactly("x", "y");
        assertThat(composite.parts()).allSatisfy(part ->
                assertThat(part.metadata())
                        .containsEntry("root", "p")
                        .containsKey("address")
                        .containsKey("path")
                        .containsKey("valueSummary"));
    }

    @Test
    void projectsStructArrayAsArrayCellsWithFieldMetadata() {
        DebugProcessSpace processSpace = processSpace(new DebugMemoryEntry(
                "points",
                new DebugVirtualAddress("stack", 40),
                "struct Point[3]",
                DebugValue.arrayValue("struct Point[3]", List.of(
                        new DebugValueElement(0, pointValue(1, 2)),
                        new DebugValueElement(1, pointValue(3, 4)),
                        new DebugValueElement(2, pointValue(5, 6))
                ))
        ));

        VisualProjection projection = buildSpecs(processSpace, spec(
                "points",
                "points",
                VisualKind.STRUCT_ARRAY,
                Map.of("fields", "x,y")
        ));

        ArrayStructure array = (ArrayStructure) projection.structures().getFirst();
        assertThat(array.kind()).isEqualTo("struct-array");
        assertThat(array.cells()).hasSize(3);
        assertThat(array.cells()).extracting(ArrayCell::label)
                .containsExactly("points[0] struct Point{x=1, y=2}", "points[1] struct Point{x=3, y=4}", "points[2] struct Point{x=5, y=6}");
        assertThat(array.cells()).allSatisfy(cell ->
                assertThat(cell.metadata()).containsEntry("fieldNames", "x,y"));
    }

    @Test
    void projectsPointerAsGraphWithSourceTargetAndPointerMetadata() {
        DebugMemoryEntry x = new DebugMemoryEntry("x", new DebugVirtualAddress("stack", 50), "int", DebugValue.intValue(42));
        DebugMemoryEntry p = new DebugMemoryEntry(
                "p",
                new DebugVirtualAddress("stack", 51),
                "int *",
                DebugValue.pointerValue("int *", x.address())
        );
        DebugProcessSpace processSpace = processSpace(x, p);

        VisualProjection projection = buildSpecs(processSpace, spec("ptr", "p", VisualKind.POINTER));

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("pointer");
        assertThat(graph.nodes()).extracting(GraphNode::label).contains("p -> " + x.address().display(), "x=42");
        assertThat(graph.edges()).singleElement().satisfies(edge ->
                assertThat(edge.metadata())
                        .containsEntry("from", graph.nodes().get(0).metadata().get("id"))
                        .containsEntry("to", graph.nodes().get(1).metadata().get("id"))
                        .containsEntry("pointerTarget", x.address().display())
                        .containsEntry("address", p.address().display()));
    }

    @Test
    void projectsPointerChainByFollowingPointerTargets() {
        DebugMemoryEntry x = new DebugMemoryEntry("x", new DebugVirtualAddress("stack", 55), "int", DebugValue.intValue(42));
        DebugMemoryEntry q = new DebugMemoryEntry(
                "q",
                new DebugVirtualAddress("stack", 56),
                "int *",
                DebugValue.pointerValue("int *", x.address())
        );
        DebugMemoryEntry p = new DebugMemoryEntry(
                "p",
                new DebugVirtualAddress("stack", 57),
                "int **",
                DebugValue.pointerValue("int **", q.address())
        );
        DebugProcessSpace processSpace = processSpace(x, q, p);

        VisualProjection projection = buildSpecs(processSpace, spec("chain", "p", VisualKind.POINTER_CHAIN));

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("pointer-chain");
        assertThat(graph.nodes()).extracting(GraphNode::label)
                .contains("p -> " + q.address().display(), "q=" + x.address().display(), "x=42");
        assertThat(graph.edges()).hasSize(2);
        assertThat(graph.edges()).extracting(edge -> edge.metadata().get("pointerTarget"))
                .containsExactly(q.address().display(), x.address().display());
    }

    @Test
    void capsPointerChainProjectionDepth() {
        DebugMemoryEntry x = new DebugMemoryEntry("x", new DebugVirtualAddress("stack", 65), "int", DebugValue.intValue(42));
        DebugMemoryEntry q = new DebugMemoryEntry(
                "q",
                new DebugVirtualAddress("stack", 66),
                "int *",
                DebugValue.pointerValue("int *", x.address())
        );
        DebugMemoryEntry p = new DebugMemoryEntry(
                "p",
                new DebugVirtualAddress("stack", 67),
                "int **",
                DebugValue.pointerValue("int **", q.address())
        );
        DebugProcessSpace processSpace = processSpace(x, q, p);

        VisualProjection projection = buildSpecs(processSpace, spec("chain", "p", VisualKind.POINTER_CHAIN, Map.of("max-depth", "1")));

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(projection.warnings()).singleElement().satisfies(warning ->
                assertThat(warning).contains("达到最大深度 1"));
        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.nodes()).extracting(GraphNode::label)
                .contains("p -> " + q.address().display(), "q=" + x.address().display())
                .doesNotContain("x=42");
    }

    @Test
    void projectsStructListByFollowingNextPointerFieldsAcrossStackLocals() {
        DebugMemoryEntry a = nodeEntry("a", 60, 1, new DebugVirtualAddress("stack", 61));
        DebugMemoryEntry b = nodeEntry("b", 61, 2, new DebugVirtualAddress("stack", 62));
        DebugMemoryEntry c = nodeEntry("c", 62, 3, null);
        DebugProcessSpace processSpace = processSpace(a, b, c);
        VisualSpec spec = spec("list", "a", VisualKind.STRUCT_LIST, Map.of("next", "next", "label", "value"));

        VisualProjection projection = buildSpecs(processSpace, spec);

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("struct-list");
        assertThat(graph.nodes()).extracting(GraphNode::label).containsExactly("1", "2", "3");
        assertThat(graph.edges()).extracting(GraphEdge::label).containsExactly("next", "next");
        assertThat(graph.edges()).allSatisfy(edge ->
                assertThat(edge.metadata()).containsKey("pointerTarget"));
    }

    @Test
    void capsStructListProjectionDepth() {
        DebugMemoryEntry a = nodeEntry("a", 70, 1, new DebugVirtualAddress("stack", 71));
        DebugMemoryEntry b = nodeEntry("b", 71, 2, new DebugVirtualAddress("stack", 72));
        DebugMemoryEntry c = nodeEntry("c", 72, 3, null);
        DebugProcessSpace processSpace = processSpace(a, b, c);
        VisualSpec spec = spec("list", "a", VisualKind.STRUCT_LIST, Map.of("next", "next", "label", "value", "max-depth", "1"));

        VisualProjection projection = buildSpecs(processSpace, spec);

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(projection.warnings()).singleElement().satisfies(warning ->
                assertThat(warning).contains("达到最大深度 1"));
        assertThat(graph.nodes()).extracting(GraphNode::label)
                .contains("1", "2")
                .doesNotContain("3");
        assertThat(graph.edges()).hasSize(1);
    }

    @Test
    void projectsDoublyListWithDefaultNextPrimaryAndPrevAuxiliaryEdges() {
        DebugMemoryEntry a = doublyNodeEntry("a", 80, 1, null, new DebugVirtualAddress("stack", 81));
        DebugMemoryEntry b = doublyNodeEntry("b", 81, 2, new DebugVirtualAddress("stack", 80), new DebugVirtualAddress("stack", 82));
        DebugMemoryEntry c = doublyNodeEntry("c", 82, 3, new DebugVirtualAddress("stack", 81), null);
        DebugProcessSpace processSpace = processSpace(a, b, c);

        VisualProjection projection = buildSpecs(processSpace, spec("dll", "a", VisualKind.DOUBLY_LIST, Map.of("label", "value")));

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("doubly-list");
        assertThat(graph.nodes()).extracting(GraphNode::label).containsExactly("1", "2", "3");
        assertThat(graph.edges()).filteredOn(edge -> edge.label().equals("next"))
                .hasSize(2)
                .allSatisfy(edge -> assertThat(edge.metadata()).containsEntry("edge-role", "primary"));
        assertThat(graph.edges()).filteredOn(edge -> edge.label().equals("prev"))
                .hasSize(2)
                .allSatisfy(edge -> assertThat(edge.metadata()).containsEntry("edge-role", "auxiliary"));
    }

    @Test
    void projectsLruListWithHeadAndTailMarkers() {
        DebugMemoryEntry a = doublyNodeEntry("a", 90, 10, null, new DebugVirtualAddress("stack", 91));
        DebugMemoryEntry b = doublyNodeEntry("b", 91, 20, new DebugVirtualAddress("stack", 90), new DebugVirtualAddress("stack", 92));
        DebugMemoryEntry c = doublyNodeEntry("c", 92, 30, new DebugVirtualAddress("stack", 91), null);
        DebugProcessSpace processSpace = processSpace(a, b, c);

        VisualProjection projection = buildSpecs(processSpace, spec("lru", "a", VisualKind.LRU_LIST, Map.of("label", "value")));

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("lru-list");
        assertThat(graph.nodes()).first().satisfies(node ->
                assertThat(node.metadata()).containsEntry("marker", "head"));
        assertThat(graph.nodes()).last().satisfies(node ->
                assertThat(node.metadata()).containsEntry("marker", "tail"));
        assertThat(graph.edges()).filteredOn(edge -> edge.label().equals("prev"))
                .allSatisfy(edge -> assertThat(edge.metadata()).containsEntry("edge-role", "auxiliary"));
    }

    @Test
    void projectsBinaryTreeWithDefaultLeftAndRightEdges() {
        DebugMemoryEntry root = treeNodeEntry("root", 100, 10, new DebugVirtualAddress("stack", 101), new DebugVirtualAddress("stack", 102));
        DebugMemoryEntry left = treeNodeEntry("left", 101, 5, null, null);
        DebugMemoryEntry right = treeNodeEntry("right", 102, 15, null, null);
        DebugProcessSpace processSpace = processSpace(root, left, right);

        VisualProjection projection = buildSpecs(processSpace, spec("tree", "root", VisualKind.BINARY_TREE, Map.of("label", "value")));

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("binary-tree");
        assertThat(graph.layoutHint()).isEqualTo("hierarchical");
        assertThat(graph.nodes()).extracting(GraphNode::label).containsExactly("10", "5", "15");
        assertThat(graph.edges()).extracting(GraphEdge::label).containsExactly("left", "right");
        assertThat(graph.edges()).allSatisfy(edge ->
                assertThat(edge.metadata()).containsEntry("edge-role", "primary"));
    }

    @Test
    void projectsHashChainTableAsBucketedGraphWithVerticalChainMetadata() {
        DebugMemoryEntry buckets = pointerArrayEntry(
                "buckets",
                110,
                new DebugVirtualAddress("stack", 120),
                null,
                new DebugVirtualAddress("stack", 122)
        );
        DebugMemoryEntry e0 = entryNode("e0", 120, 10, new DebugVirtualAddress("stack", 121));
        DebugMemoryEntry e1 = entryNode("e1", 121, 20, null);
        DebugMemoryEntry e2 = entryNode("e2", 122, 30, null);
        DebugProcessSpace processSpace = processSpace(buckets, e0, e1, e2);

        VisualProjection projection = buildSpecs(processSpace, spec("hash", "buckets", VisualKind.HASH_CHAIN_TABLE, Map.of("label", "key")));

        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("hash-chain-table");
        assertThat(graph.layoutHint()).isEqualTo("bucketed");
        assertThat(graph.nodes()).filteredOn(node -> node.metadata().get("visual-role").equals("bucket"))
                .extracting(GraphNode::label)
                .containsExactly("[0]", "[1]", "[2]");
        assertThat(graph.nodes()).filteredOn(node -> node.metadata().get("visual-role").equals("chain-node"))
                .extracting(node -> node.metadata().get("chainDepth"))
                .contains("0", "1");
        assertThat(graph.edges()).allSatisfy(edge ->
                assertThat(edge.metadata()).containsKey("bucketIndex"));
    }

    @Test
    void projectsHeapArrayWithParentAndChildMetadata() {
        DebugProcessSpace processSpace = processSpace(arrayEntry("heap", 130, 50, 30, 40, 10));

        VisualProjection projection = buildSpecs(processSpace, spec("heap", "heap", VisualKind.HEAP));

        ArrayStructure heap = (ArrayStructure) projection.structures().getFirst();
        assertThat(heap.kind()).isEqualTo("heap");
        assertThat(heap.cells().get(0).metadata())
                .containsEntry("leftIndex", "1")
                .containsEntry("rightIndex", "2")
                .doesNotContainKey("parentIndex");
        assertThat(heap.cells().get(1).metadata()).containsEntry("parentIndex", "0");
    }

    @Test
    void projectsFenwickTreeArrayWithLowbitCoverageMetadata() {
        DebugProcessSpace processSpace = processSpace(arrayEntry("bit", 140, 1, 3, 2, 8));

        VisualProjection projection = buildSpecs(processSpace, spec("bit", "bit", VisualKind.FENWICK_TREE));

        ArrayStructure fenwick = (ArrayStructure) projection.structures().getFirst();
        assertThat(fenwick.kind()).isEqualTo("fenwick-tree");
        assertThat(fenwick.cells().get(3).metadata())
                .containsEntry("fenwickIndex", "4")
                .containsEntry("rangeStart", "1")
                .containsEntry("rangeEnd", "4");
    }

    @Test
    void reportsMissingTypedSpecRootAsProjectionWarning() {
        VisualProjection projection = buildSpecs(processSpace(), spec("missing", "absent", VisualKind.ARRAY));

        assertThat(projection.warnings()).singleElement().satisfies(warning ->
                assertThat(warning).contains("未找到 visual root 变量"));
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

    private DebugProcessSpace processSpace(DebugMemoryEntry... entries) {
        return new DebugProcessSpace(
                minic.runtime.debug.DebugCodeSegment.empty(),
                minic.runtime.debug.DebugStaticSegment.empty(),
                new DebugStackSegment(List.of(new DebugStackFrame(
                        "frame-main",
                        "main",
                        List.of(),
                        List.of(entries),
                        null,
                        null
                ))),
                minic.runtime.debug.DebugHeapSegment.empty(),
                minic.runtime.debug.DebugIoSegment.empty()
        );
    }

    private VisualProjection buildSpecs(DebugProcessSpace processSpace, VisualSpec... specs) {
        return new VisualProjectionBuilder().build(processSpace, List.of(), List.of(specs), List.of(), Long.MAX_VALUE);
    }

    private List<VisualAnnotation> runtimeGraphAnnotations(String name, String kind) {
        return new VisualAnnotationParser().parse(new minic.source.SourceFile(name + ".mc", """
                // @visual graph name=%s kind=%s root=root mode=runtime function=build
                int main() { return 0; }
                """.formatted(name, kind))).annotations();
    }

    private VisualSpec spec(String name, String root, VisualKind kind) {
        return spec(name, root, kind, Map.of());
    }

    private VisualSpec spec(String name, String root, VisualKind kind, Map<String, String> attributes) {
        return new VisualSpec(name, root, kind, attributes, VisualSpec.parseFields(attributes.get("fields")), 1);
    }

    private DebugMemoryEntry arrayEntry(String name, long address, int... values) {
        List<DebugValueElement> elements = new java.util.ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            elements.add(new DebugValueElement(index, DebugValue.intValue(values[index])));
        }
        return new DebugMemoryEntry(
                name,
                new DebugVirtualAddress("stack", address),
                "int[" + values.length + "]",
                DebugValue.arrayValue("int[" + values.length + "]", elements)
        );
    }

    private DebugMemoryEntry pointEntry(String name, long address, int x, int y) {
        return new DebugMemoryEntry(name, new DebugVirtualAddress("stack", address), "struct Point", pointValue(x, y));
    }

    private DebugValue pointValue(int x, int y) {
        return DebugValue.structValue("struct Point", List.of(
                new DebugValueField("x", DebugValue.intValue(x)),
                new DebugValueField("y", DebugValue.intValue(y))
        ));
    }

    private DebugMemoryEntry nodeEntry(String name, long address, int value, DebugVirtualAddress next) {
        DebugValue nextValue = next == null ? DebugValue.nullValue("struct Node *") : DebugValue.pointerValue("struct Node *", next);
        return new DebugMemoryEntry(name, new DebugVirtualAddress("stack", address), "struct Node", DebugValue.structValue("struct Node", List.of(
                new DebugValueField("value", DebugValue.intValue(value)),
                new DebugValueField("next", nextValue)
        )));
    }

    private DebugMemoryEntry doublyNodeEntry(
            String name,
            long address,
            int value,
            DebugVirtualAddress prev,
            DebugVirtualAddress next
    ) {
        DebugValue prevValue = prev == null ? DebugValue.nullValue("struct Node *") : DebugValue.pointerValue("struct Node *", prev);
        DebugValue nextValue = next == null ? DebugValue.nullValue("struct Node *") : DebugValue.pointerValue("struct Node *", next);
        return new DebugMemoryEntry(name, new DebugVirtualAddress("stack", address), "struct Node", DebugValue.structValue("struct Node", List.of(
                new DebugValueField("value", DebugValue.intValue(value)),
                new DebugValueField("prev", prevValue),
                new DebugValueField("next", nextValue)
        )));
    }

    private DebugMemoryEntry treeNodeEntry(
            String name,
            long address,
            int value,
            DebugVirtualAddress left,
            DebugVirtualAddress right
    ) {
        DebugValue leftValue = left == null ? DebugValue.nullValue("struct Node *") : DebugValue.pointerValue("struct Node *", left);
        DebugValue rightValue = right == null ? DebugValue.nullValue("struct Node *") : DebugValue.pointerValue("struct Node *", right);
        return new DebugMemoryEntry(name, new DebugVirtualAddress("stack", address), "struct Node", DebugValue.structValue("struct Node", List.of(
                new DebugValueField("value", DebugValue.intValue(value)),
                new DebugValueField("left", leftValue),
                new DebugValueField("right", rightValue)
        )));
    }

    private DebugMemoryEntry pointerArrayEntry(String name, long address, DebugVirtualAddress... targets) {
        List<DebugValueElement> elements = new java.util.ArrayList<>();
        for (int index = 0; index < targets.length; index++) {
            DebugValue value = targets[index] == null
                    ? DebugValue.nullValue("struct Entry *")
                    : DebugValue.pointerValue("struct Entry *", targets[index]);
            elements.add(new DebugValueElement(index, value));
        }
        return new DebugMemoryEntry(
                name,
                new DebugVirtualAddress("stack", address),
                "struct Entry *[" + targets.length + "]",
                DebugValue.arrayValue("struct Entry *[" + targets.length + "]", elements)
        );
    }

    private DebugMemoryEntry entryNode(String name, long address, int key, DebugVirtualAddress next) {
        DebugValue nextValue = next == null ? DebugValue.nullValue("struct Entry *") : DebugValue.pointerValue("struct Entry *", next);
        return new DebugMemoryEntry(name, new DebugVirtualAddress("stack", address), "struct Entry", DebugValue.structValue("struct Entry", List.of(
                new DebugValueField("key", DebugValue.intValue(key)),
                new DebugValueField("next", nextValue)
        )));
    }
}
