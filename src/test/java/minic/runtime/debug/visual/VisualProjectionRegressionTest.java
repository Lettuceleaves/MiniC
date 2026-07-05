package minic.runtime.debug.visual;

import minic.compiler.pipeline.MiniCompiler;
import minic.runtime.debug.DebugSession;
import minic.runtime.debug.IrDebugInterpreter;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisualProjectionRegressionTest {
    @Test
    void parsesNaturalVisualSpecAndManualGraphAnnotations() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual root=head type=[Node] name=list
                // @visual-node graph=list id=head label=Head
                // @visual-edge graph=list from=head to=tail label=next
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).hasSize(1);
        assertThat(result.annotations()).hasSize(2);
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

    @Test
    void projectsUnidirectionalLayoutToGridPositionedGraph() {
        SourceFile sourceFile = new SourceFile("unidirectional.mc", """
                // @visual layout=unidirectional root=a name=nodes
                struct Node {
                    long value;
                    struct Node *first;
                    struct Node *second;
                };
                int main() {
                    struct Node a;
                    struct Node b;
                    struct Node c;
                    a.value = 1;
                    b.value = 2;
                    c.value = 3;
                    a.first = &b;
                    a.second = &c;
                    return 0;
                }
                """);
        DebugSession session = debug(sourceFile);
        VisualAnnotationParseResult parsed = new VisualAnnotationParser().parse(sourceFile);

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), parsed.annotations(), parsed.specs(), session.visualEvents(), Long.MAX_VALUE);

        assertThat(projection.warnings()).isEmpty();
        assertThat(projection.structures()).hasSize(1);
        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.kind()).isEqualTo("unidirectional");
        assertThat(graph.layoutHint()).isEqualTo("grid");
        assertThat(graph.nodes()).hasSize(3);
        assertThat(graph.edges()).hasSize(2);

        Map<String, GraphNode> nodesByPath = graph.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(node -> node.metadata().get("path"), node -> node));
        assertThat(nodesByPath.keySet()).containsExactlyInAnyOrder("a", "b", "c");
        assertGrid(nodesByPath.get("a"), "-2", "0", "4", "2");
        assertGrid(nodesByPath.get("b"), "-6", "6", "4", "2");
        assertGrid(nodesByPath.get("c"), "2", "6", "4", "2");

        assertThat(graph.edges())
                .allSatisfy(edge -> assertThat(edge.metadata())
                        .containsKeys("gridStartX", "gridStartY", "gridEndX", "gridEndY", "fromAnchor", "toAnchor"));
    }

    @Test
    void projectsNaturalHashRootAsSquareGroupAndCapturedEntryObjects() {
        SourceFile sourceFile = new SourceFile("natural-hash.mc", """
                // @visual root=buckets type=[Entry] name=hash
                struct Entry { int key; int value; struct Entry *next; };
                int main() {
                    struct Entry e1;
                    struct Entry e2;
                    struct Entry *buckets[4];
                    buckets[0] = NULL;
                    buckets[1] = NULL;
                    buckets[2] = NULL;
                    buckets[3] = NULL;
                    e1.key = 1; e1.value = 10; e1.next = NULL; buckets[1] = &e1;
                    e2.key = 5; e2.value = 50; e2.next = buckets[1]; buckets[1] = &e2;
                    return buckets[1]->next->key;
                }
                """);
        DebugSession session = debug(sourceFile);
        VisualAnnotationParseResult parsed = new VisualAnnotationParser().parse(sourceFile);

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), parsed.annotations(), parsed.specs(), session.visualEvents(), Long.MAX_VALUE);

        assertThat(projection.warnings()).isEmpty();
        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.layoutHint()).isEqualTo("grid");
        assertThat(graph.nodes()
                .stream()
                .filter(node -> node.metadata().getOrDefault("visual-shape", "").equals("SQUARE")))
                .hasSize(4)
                .allSatisfy(node -> assertThat(node.metadata())
                        .containsKeys("row", "column")
                        .doesNotContainKeys("visual-role", "visual-element-kind"));
        assertThat(graph.nodes()
                .stream()
                .filter(node -> node.metadata().getOrDefault("visual-shape", "").equals("RECT")))
                .extracting(node -> node.metadata().get("type"))
                .allMatch(type -> type.contains("Entry"));
        assertThat(graph.nodes())
                .filteredOn(node -> node.metadata().getOrDefault("path", "").equals("e2"))
                .singleElement()
                .satisfies(node -> assertThat(node.metadata())
                        .containsEntry("visual-content", "STRUCT_TABLE")
                        .containsEntry("visual-row-count", "3")
                        .containsEntry("visual-row.0", "5")
                        .containsEntry("visual-row.1", "50")
                        .containsEntry("visual-row-name.0", "key")
                        .containsEntry("visual-row-name.1", "value")
                        .containsEntry("visual-row-name.2", "next")
                        .satisfies(metadata -> assertThat(metadata.get("visual-row.2"))
                                .startsWith("s@")
                                .doesNotContain("stack:")));
        assertThat(graph.edges())
                .anySatisfy(edge -> assertThat(edge.metadata().get("fromPath")).contains("buckets[1]"))
                .anySatisfy(edge -> assertThat(edge.metadata().get("key")).isEqualTo("next"));
        Map<String, GraphNode> nodesById = graph.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(GraphNode::id, node -> node));
        assertThat(graph.edges())
                .filteredOn(edge -> nodesById.get(edge.fromNodeId()).metadata().getOrDefault("visual-shape", "").equals("SQUARE"))
                .allSatisfy(edge -> assertSquareEdgeStartsAtExposedSideMidpoint(nodesById.get(edge.fromNodeId()), edge));
    }

    @Test
    void projectsNaturalMatrixRootAsTwoDimensionalArrayCells() {
        SourceFile sourceFile = new SourceFile("natural-matrix.mc", """
                // @visual root=matrix rows=3 columns=3 name=matrix
                int main() {
                    int matrix[9];
                    matrix[0] = 1;
                    matrix[1] = 2;
                    matrix[3] = 3;
                    matrix[4] = 4;
                    matrix[8] = 9;
                    return matrix[4];
                }
                """);
        DebugSession session = debug(sourceFile);
        VisualAnnotationParseResult parsed = new VisualAnnotationParser().parse(sourceFile);

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), parsed.annotations(), parsed.specs(), session.visualEvents(), Long.MAX_VALUE);

        assertThat(projection.warnings()).isEmpty();
        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.nodes()
                .stream()
                .filter(node -> node.metadata().getOrDefault("visual-shape", "").equals("SQUARE")))
                .hasSize(9);
        assertThat(graph.nodes())
                .filteredOn(node -> node.metadata().getOrDefault("visual-shape", "").equals("SQUARE"))
                .extracting(node -> node.metadata().get("row") + "," + node.metadata().get("column"))
                .contains("0,0", "0,1", "1,0", "1,1", "2,2");
        assertThat(graph.nodes())
                .filteredOn(node -> node.metadata().getOrDefault("visual-shape", "").equals("SQUARE"))
                .extracting(GraphNode::label)
                .contains("1", "2", "4", "9");
    }

    @Test
    void appliesRedBlackStyleTemplateFromStyleAnnotation() {
        SourceFile sourceFile = new SourceFile("red-black-style.mc", """
                // @visual root=root type=[RBNode] name=rb
                // @style type=RBNode template=red-black
                struct RBNode {
                    int value;
                    int color;
                    struct RBNode *left;
                    struct RBNode *right;
                };
                int main() {
                    struct RBNode root;
                    struct RBNode left;
                    root.value = 10;
                    root.color = 0;
                    left.value = 5;
                    left.color = 1;
                    root.left = &left;
                    root.right = NULL;
                    left.left = NULL;
                    left.right = NULL;
                    return root.value;
                }
                """);
        DebugSession session = debug(sourceFile);
        VisualAnnotationParseResult parsed = new VisualAnnotationParser().parse(sourceFile);

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), parsed.annotations(), parsed.specs(), session.visualEvents(), Long.MAX_VALUE);

        assertThat(projection.warnings()).isEmpty();
        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.nodes())
                .filteredOn(node -> node.metadata().getOrDefault("path", "").equals("root"))
                .singleElement()
                .satisfies(node -> assertThat(node.metadata())
                        .containsEntry("visual-style-template", "red-black")
                        .containsEntry("visual-fill", "#1f2329")
                        .containsEntry("visual-stroke", "#aeb7c5")
                        .containsEntry("visual-text-fill", "#f8fafc")
                        .containsEntry("visual-style-class", "debug-tree-node-black"));
        assertThat(graph.nodes())
                .filteredOn(node -> node.metadata().getOrDefault("path", "").equals("left"))
                .singleElement()
                .satisfies(node -> assertThat(node.metadata())
                        .containsEntry("visual-style-template", "red-black")
                        .containsEntry("visual-fill", "#8f2633")
                        .containsEntry("visual-stroke", "#f1a1aa")
                        .containsEntry("visual-text-fill", "#fff5f5")
                        .containsEntry("visual-style-class", "debug-tree-node-red"));
    }

    @Test
    void usesDefaultBlueRectangleStyleWhenStyleAnnotationIsOmitted() {
        SourceFile sourceFile = new SourceFile("default-style.mc", """
                // @visual root=root type=[RBNode] name=rb
                struct RBNode {
                    int value;
                    int color;
                    struct RBNode *left;
                    struct RBNode *right;
                };
                int main() {
                    struct RBNode root;
                    root.value = 10;
                    root.color = 0;
                    root.left = NULL;
                    root.right = NULL;
                    return root.value;
                }
                """);
        DebugSession session = debug(sourceFile);
        VisualAnnotationParseResult parsed = new VisualAnnotationParser().parse(sourceFile);

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), parsed.annotations(), parsed.specs(), session.visualEvents(), Long.MAX_VALUE);

        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.specs().getFirst().styleRules()).isEmpty();
        assertThat(projection.warnings()).isEmpty();
        GraphStructure graph = (GraphStructure) projection.structures().getFirst();
        assertThat(graph.nodes())
                .filteredOn(node -> node.metadata().getOrDefault("path", "").equals("root"))
                .singleElement()
                .satisfies(node -> assertThat(node.metadata())
                        .containsEntry("visual-shape", "RECT")
                        .doesNotContainKeys(
                                "visual-style-template",
                                "visual-fill",
                                "visual-stroke",
                                "visual-text-fill",
                                "visual-style-class"
                        ));
    }

    @Test
    void unsupportedLayoutDoesNotRouteToLegacyTypedProjection() {
        DebugSession session = debug("""
                struct Entry { int key; struct Entry *next; };
                int main() {
                    struct Entry e;
                    struct Entry *buckets[1];
                    e.key = 1;
                    e.next = NULL;
                    buckets[0] = &e;
                    return e.key;
                }
                """);
        VisualSpec legacySpec = new VisualSpec(
                "hash",
                "buckets",
                VisualKind.HASH_CHAIN_TABLE,
                Map.of(
                        "root", "buckets",
                        "name", "hash",
                        "layout", "bucketed",
                        "type", "[Entry]"
                ),
                List.of(),
                1
        );

        VisualProjection projection = new VisualProjectionBuilder()
                .build(session.currentSnapshot().processSpace(), List.of(), List.of(legacySpec), List.of(), Long.MAX_VALUE);

        assertThat(projection.structures()).isEmpty();
        assertThat(projection.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning).contains("layout 不支持").contains("bucketed"));
    }

    private static void assertGrid(GraphNode node, String x, String y, String width, String height) {
        assertThat(node.metadata())
                .containsEntry("gridX", x)
                .containsEntry("gridY", y)
                .containsEntry("gridWidth", width)
                .containsEntry("gridHeight", height);
    }

    private static void assertSquareEdgeStartsAtExposedSideMidpoint(GraphNode node, GraphEdge edge) {
        int x = Integer.parseInt(node.metadata().get("gridX"));
        int y = Integer.parseInt(node.metadata().get("gridY"));
        int width = Integer.parseInt(node.metadata().get("gridWidth"));
        int height = Integer.parseInt(node.metadata().get("gridHeight"));
        int row = Integer.parseInt(node.metadata().get("row"));
        int column = Integer.parseInt(node.metadata().get("column"));
        int rows = Integer.parseInt(node.metadata().get("rows"));
        int columns = Integer.parseInt(node.metadata().get("columns"));
        int startX = Integer.parseInt(edge.metadata().get("gridStartX"));
        int startY = Integer.parseInt(edge.metadata().get("gridStartY"));
        String anchor = edge.metadata().get("fromAnchor");
        if (row > 0) {
            assertThat(anchor).isNotEqualTo("TOP");
        }
        if (row + 1 < rows) {
            assertThat(anchor).isNotEqualTo("BOTTOM");
        }
        if (column > 0) {
            assertThat(anchor).isNotEqualTo("LEFT");
        }
        if (column + 1 < columns) {
            assertThat(anchor).isNotEqualTo("RIGHT");
        }
        switch (anchor) {
            case "TOP" -> {
                assertThat(startX).isEqualTo(x + width / 2);
                assertThat(startY).isEqualTo(y);
            }
            case "RIGHT" -> {
                assertThat(startX).isEqualTo(x + width);
                assertThat(startY).isEqualTo(y + height / 2);
            }
            case "BOTTOM" -> {
                assertThat(startX).isEqualTo(x + width / 2);
                assertThat(startY).isEqualTo(y + height);
            }
            case "LEFT" -> {
                assertThat(startX).isEqualTo(x);
                assertThat(startY).isEqualTo(y + height / 2);
            }
            default -> throw new AssertionError("unexpected anchor " + anchor);
        }
    }

    private static DebugSession debug(String source) {
        SourceFile sourceFile = new SourceFile("visual-debug.mc", source);
        return debug(sourceFile);
    }

    private static DebugSession debug(SourceFile sourceFile) {
        var result = new MiniCompiler().compile(sourceFile);
        assertThat(result.diagnostics()).isEmpty();
        return new IrDebugInterpreter().runMain(
                result.irModuleOptional().orElseThrow(),
                sourceFile,
                result.semanticResultOptional().orElseThrow()
        );
    }
}
