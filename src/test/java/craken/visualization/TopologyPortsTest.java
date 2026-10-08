package craken.visualization;

import craken.visualization.api.*;
import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge.Direction;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.style.EdgeStyle;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
final class TopologyPortsTest {
    private static OperationPath p(ViewLocation node) { return new OperationPath(null, node); }
    private static ViewLocation add(VisualizationSession session, PageRef page, Map<String, String> fields) {
        var location = session.reserveNodeId(page); session.addNode(p(location), new ViewNode.Spec(ViewNode.Kind.POINT, "node", fields)); return location;
    }
    @Test void concretePortPairsHaveIndependentIdsAndReverseUpdatesPreserveCanonicalDirection() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page, Map.of("next", "b", "other", "b")); var b = add(session, page, Map.of());
            var style = new EdgeStyle("#7bb0df", "#D6A64F", "next");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(p(a), p(b), Direction.FORWARD, "field:next", "west", style),
                    new VisualizationCommand.Connect(p(a), p(b), Direction.NONE, "field:other", "north", EdgeStyle.DEFAULT))).succeeded());
            var edges = session.model().pages().get(page.pageId()).topology(); assertEquals(2, edges.size());
            var next = edges.values().stream().filter(e -> e.aPort().equals("field:next")).findFirst().orElseThrow();
            assertEquals(style, next.style());
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(p(b), p(a), Direction.FORWARD, "west", "field:next", style))).succeeded());
            var updated = session.model().pages().get(page.pageId()).topology().get(next.id()); assertEquals(Direction.BACKWARD, updated.direction());
            assertEquals(2, session.model().pages().get(page.pageId()).topology().size());
            var snapshot = session.snapshot(); session.restore(snapshot); assertEquals(updated, session.model().pages().get(page.pageId()).topology().get(next.id()));
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Disconnect(p(b), p(a), "west", "field:next"))).succeeded());
            assertEquals(1, session.model().pages().get(page.pageId()).topology().size());
        }
    }
    @Test void unknownFieldsRejectTheWholeBatchBeforeAnyEdgeAppears() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page, Map.of("next", "b")); var b = add(session, page, Map.of());
            var before = session.model();
            assertFalse(session.modify(MutationBatch.of(new VisualizationCommand.Connect(p(a), p(b)),
                    new VisualizationCommand.Connect(p(a), p(b), Direction.FORWARD, "field:missing", "node", EdgeStyle.DEFAULT))).succeeded());
            assertSame(before, session.model());
            assertFalse(session.modify(MutationBatch.of(
                    new VisualizationCommand.Connect(p(a), p(b), Direction.FORWARD, "field-west:missing", "node",
                            EdgeStyle.DEFAULT))).succeeded());
            assertTrue(session.modify(MutationBatch.of(
                    new VisualizationCommand.Connect(p(a), p(b), Direction.FORWARD, "field-west:next", "node",
                            EdgeStyle.DEFAULT))).succeeded(), "西侧同名字段端口必须合法");
            assertEquals(1, session.model().pages().get(page.pageId()).topology().size());
        }
    }
    @Test void removingAConnectedFieldRequiresDisconnectInTheSameBatch() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page, Map.of("next", "b")); var b = add(session, page, Map.of());
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(p(a), p(b), Direction.FORWARD, "field:next", "node", EdgeStyle.DEFAULT))).succeeded());
            var before = session.snapshot();
            assertFalse(session.modify(MutationBatch.of(new VisualizationCommand.SetContent(p(a), ViewNode.Spec.point("changed")))).succeeded());
            assertEquals(before, session.snapshot());
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Disconnect(p(a), p(b), "field:next", "node"),
                    new VisualizationCommand.SetContent(p(a), ViewNode.Spec.point("changed")))).succeeded());
            assertTrue(session.model().pages().get(page.pageId()).topology().isEmpty());
        }
    }
    @Test void edgeColorsAreOpaqueSrgbAndArrowInheritanceIsExplicit() {
        assertEquals("#7BB0DF", new EdgeStyle("#7bb0df", null, "label").effectiveArrowColor());
        for (var color : List.of("rgba(1,2,3,.5)", "#FFFFFF80", "red", "#XYZ123")) assertThrows(IllegalArgumentException.class, () -> new EdgeStyle(color, null, ""));
        assertEquals("#D6A64F", new EdgeStyle("#FFFFFF", "#D6A64F", "").effectiveArrowColor());
    }
}
