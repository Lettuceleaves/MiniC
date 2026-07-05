package minic.runtime.debug.visual;

import minic.runtime.debug.dataflow.DataFlowEvent;
import minic.runtime.debug.dataflow.DataFlowEventType;
import minic.runtime.debug.visual.layout.GridPoint;
import minic.runtime.debug.visual.layout.GridRect;
import minic.runtime.debug.visual.layout.LayoutInput;
import minic.runtime.debug.visual.layout.LayoutPlan;
import minic.runtime.debug.visual.layout.NodeAnchor;
import minic.runtime.debug.visual.layout.NodeMeasure;
import minic.runtime.debug.visual.layout.PlacedEdge;
import minic.runtime.debug.visual.layout.PlacedNode;
import minic.runtime.debug.visual.layout.UnidirectionalLayoutStrategy;
import minic.runtime.debug.visual.layout.VisualLayoutStrategy;
import minic.runtime.debug.visual.layout.VisualMemoryEdge;
import minic.runtime.debug.visual.layout.VisualMemoryMirror;
import minic.runtime.debug.visual.layout.VisualMemoryNode;
import minic.runtime.debug.visual.layout.VisualRootBinder;
import minic.runtime.debug.visual.layout.VisualRootBinding;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisualLayoutFoundationTest {
    @Test
    void parsesRootListWithoutLegacyStyleOptions() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual roots=[a,b,c] name=forest
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).hasSize(1);
        VisualSpec spec = result.specs().getFirst();
        assertThat(spec.name()).isEqualTo("forest");
        assertThat(spec.root()).isEqualTo("a");
        assertThat(spec.roots()).containsExactly("a", "b", "c");
        assertThat(spec.layout()).isEqualTo("natural");
    }

    @Test
    void rejectsLegacyDataStructureSemanticLayouts() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual root=buckets layout=array-chain kind=hash-chain-table name=hash
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.specs()).isEmpty();
        assertThat(result.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning).contains("layout 不支持").contains("array-chain"));
    }

    @Test
    void parsesCaptureTypeListAndDefaultsToNaturalLayout() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual root=buckets type=[Entry,RBNode] name=hash
                // @visual root=head type=Node name=list
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).hasSize(2);
        assertThat(result.specs().get(0).layout()).isEqualTo("natural");
        assertThat(result.specs().get(0).captureTypes()).containsExactly("Entry", "RBNode");
        assertThat(result.specs().get(1).captureTypes()).containsExactly("Node");
    }

    @Test
    void styleTemplateIsOptionalForSingleVisualDeclaration() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual root=root type=[RBNode] name=rb
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).hasSize(1);
        assertThat(result.specs().getFirst().styleRules()).isEmpty();
        assertThat(result.specs().getFirst().style()).isEqualTo("default");
    }

    @Test
    void parsesStyleRulesForNearestVisualSpec() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual root=root type=[RBNode] name=rb
                // @style type=RBNode template=red-black
                // @visual root=buckets type=[Entry] name=hash
                // @style type=Entry template=red-black
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).hasSize(2);
        assertThat(result.specs().get(0).styleRules()).singleElement()
                .satisfies(rule -> {
                    assertThat(rule.type()).isEqualTo("RBNode");
                    assertThat(rule.template()).isEqualTo("red-black");
                });
        assertThat(result.specs().get(1).styleRules()).singleElement()
                .satisfies(rule -> {
                    assertThat(rule.type()).isEqualTo("Entry");
                    assertThat(rule.template()).isEqualTo("red-black");
                });
    }

    @Test
    void rejectsDirectStyleAttributesAndDuplicateStyleTemplates() {
        SourceFile source = new SourceFile("visual.mc", """
                // @visual root=root type=[RBNode] name=rb
                // @style type=RBNode fill=#f6f8fa stroke=#57606a
                // @style type=RBNode template=red-black
                // @style type=RBNode template=red-black
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(source);

        assertThat(result.specs()).hasSize(1);
        assertThat(result.specs().getFirst().styleRules()).singleElement()
                .satisfies(rule -> assertThat(rule.template()).isEqualTo("red-black"));
        assertThat(result.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("@style 只允许 type 和 template"))
                .anySatisfy(warning -> assertThat(warning).contains("每个 @visual 只允许一条 @style"));
    }

    @Test
    void bindsStructRootToFirstLocalAssignmentAddress() {
        List<DataFlowEvent> events = List.of(
                dataEvent(1, DataFlowEventType.WRITE_LOCAL, "root", "stack:0x10", ""),
                dataEvent(2, DataFlowEventType.WRITE_LOCAL, "root", "stack:0x20", "")
        );

        List<VisualRootBinding> bindings = VisualRootBinder.bind(List.of("root"), events);

        assertThat(bindings).extracting(VisualRootBinding::rootAddress).containsExactly("stack:0x10");
    }

    @Test
    void bindsPointerRootToFirstNonNullPointerTarget() {
        List<DataFlowEvent> events = List.of(
                dataEvent(1, DataFlowEventType.POINTER_RETARGET, "head", "stack:0x8", "null"),
                dataEvent(2, DataFlowEventType.POINTER_RETARGET, "head", "stack:0x8", "heap:0x40"),
                dataEvent(3, DataFlowEventType.POINTER_RETARGET, "head", "stack:0x8", "heap:0x80")
        );

        List<VisualRootBinding> bindings = VisualRootBinder.bind(List.of("head"), events);

        assertThat(bindings).extracting(VisualRootBinding::rootAddress).containsExactly("heap:0x40");
    }

    @Test
    void defaultEdgeRouterChoosesNearestOrthogonalAnchors() {
        VisualMemoryMirror mirror = new VisualMemoryMirror(
                List.of(new VisualMemoryNode("A", 16), new VisualMemoryNode("B", 16)),
                List.of(new VisualMemoryEdge("A", "B"))
        );
        LayoutInput input = new LayoutInput(List.of("A"), mirror, "default");

        LayoutPlan plan = new FixedLayoutStrategy(Map.of(
                "A", new GridRect(0, 0, 4, 4),
                "B", new GridRect(10, 0, 4, 4)
        )).build(input);

        assertThat(plan.edges()).hasSize(1);
        PlacedEdge edge = plan.edges().getFirst();
        assertThat(edge.fromAnchor()).isEqualTo(NodeAnchor.RIGHT);
        assertThat(edge.toAnchor()).isEqualTo(NodeAnchor.LEFT);
        assertThat(edge.start()).isEqualTo(new GridPoint(4, 2));
        assertThat(edge.end()).isEqualTo(new GridPoint(10, 2));
    }

    @Test
    void layoutPlanBfsDrawOrderStartsFromRootsAndAvoidsReentry() {
        VisualMemoryMirror mirror = new VisualMemoryMirror(
                List.of(
                        new VisualMemoryNode("A", 16),
                        new VisualMemoryNode("B", 16),
                        new VisualMemoryNode("C", 16)
                ),
                List.of(
                        new VisualMemoryEdge("A", "B"),
                        new VisualMemoryEdge("A", "C"),
                        new VisualMemoryEdge("B", "C"),
                        new VisualMemoryEdge("C", "A")
                )
        );
        LayoutInput input = new LayoutInput(List.of("A"), mirror, "default");

        LayoutPlan plan = new FixedLayoutStrategy(Map.of(
                "A", new GridRect(0, 0, 4, 4),
                "B", new GridRect(0, 8, 4, 4),
                "C", new GridRect(8, 8, 4, 4)
        )).build(input);

        assertThat(plan.breadthFirstNodeIds()).containsExactly("A", "B", "C");
    }

    @Test
    void unidirectionalLayoutCentersEachLayerOnYAxisWithFourGridGaps() {
        VisualMemoryMirror mirror = new VisualMemoryMirror(
                List.of(
                        new VisualMemoryNode("A", 16),
                        new VisualMemoryNode("B", 16),
                        new VisualMemoryNode("C", 16),
                        new VisualMemoryNode("D", 16),
                        new VisualMemoryNode("E", 16),
                        new VisualMemoryNode("F", 16)
                ),
                List.of(
                        new VisualMemoryEdge("A", "B"),
                        new VisualMemoryEdge("A", "C"),
                        new VisualMemoryEdge("B", "D"),
                        new VisualMemoryEdge("B", "E"),
                        new VisualMemoryEdge("C", "F")
                )
        );

        LayoutPlan plan = new UnidirectionalLayoutStrategy()
                .build(new LayoutInput(List.of("A"), mirror, "default"));

        assertThat(bounds(plan, "A")).isEqualTo(new GridRect(-2, 0, 4, 2));
        assertThat(bounds(plan, "B")).isEqualTo(new GridRect(-6, 6, 4, 2));
        assertThat(bounds(plan, "C")).isEqualTo(new GridRect(2, 6, 4, 2));
        assertThat(bounds(plan, "D")).isEqualTo(new GridRect(-10, 12, 4, 2));
        assertThat(bounds(plan, "E")).isEqualTo(new GridRect(-2, 12, 4, 2));
        assertThat(bounds(plan, "F")).isEqualTo(new GridRect(6, 12, 4, 2));
        assertThat(plan.breadthFirstNodeIds()).containsExactly("A", "B", "C", "D", "E", "F");
        PlacedEdge edge = edge(plan, "A", "B");
        assertThat(edge.fromAnchor()).isEqualTo(NodeAnchor.BOTTOM);
        assertThat(edge.toAnchor()).isEqualTo(NodeAnchor.TOP);
        assertThat(edge.start()).isEqualTo(new GridPoint(0, 2));
        assertThat(edge.end()).isEqualTo(new GridPoint(-4, 6));
    }

    @Test
    void unidirectionalLayoutDoesNotPlaceRepeatedNodesTwice() {
        VisualMemoryMirror mirror = new VisualMemoryMirror(
                List.of(
                        new VisualMemoryNode("A", 16),
                        new VisualMemoryNode("B", 16),
                        new VisualMemoryNode("C", 16)
                ),
                List.of(
                        new VisualMemoryEdge("A", "B"),
                        new VisualMemoryEdge("A", "C"),
                        new VisualMemoryEdge("B", "C"),
                        new VisualMemoryEdge("C", "A")
                )
        );

        LayoutPlan plan = new UnidirectionalLayoutStrategy()
                .build(new LayoutInput(List.of("A"), mirror, "default"));

        assertThat(plan.nodes()).extracting(PlacedNode::address).containsExactly("A", "B", "C");
        assertThat(bounds(plan, "C")).isEqualTo(new GridRect(2, 6, 4, 2));
        assertThat(plan.edges()).hasSize(4);
    }

    private static DataFlowEvent dataEvent(
            long snapshotId,
            DataFlowEventType type,
            String lvaluePath,
            String address,
            String pointerTarget
    ) {
        return new DataFlowEvent(
                snapshotId,
                "i" + snapshotId,
                null,
                type,
                lvaluePath + " = value",
                lvaluePath,
                "<old>",
                "<new>",
                address,
                pointerTarget
        );
    }

    private static GridRect bounds(LayoutPlan plan, String address) {
        return plan.nodes().stream()
                .filter(node -> node.address().equals(address))
                .findFirst()
                .orElseThrow()
                .bounds();
    }

    private static PlacedEdge edge(LayoutPlan plan, String fromAddress, String toAddress) {
        return plan.edges().stream()
                .filter(edge -> edge.fromAddress().equals(fromAddress) && edge.toAddress().equals(toAddress))
                .findFirst()
                .orElseThrow();
    }

    private static final class FixedLayoutStrategy extends VisualLayoutStrategy {
        private final Map<String, GridRect> placements;

        private FixedLayoutStrategy(Map<String, GridRect> placements) {
            this.placements = placements;
        }

        @Override
        protected Map<String, GridRect> placeNodes(LayoutInput input, Map<String, NodeMeasure> measures) {
            return placements;
        }
    }
}
