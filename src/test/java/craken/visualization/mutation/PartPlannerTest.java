package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.TopologyEdge.Direction;
import craken.visualization.support.ReferenceComponents;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class PartPlannerTest {
    private OperationPath path(ViewLocation location) { return new OperationPath(null, location); }
    private ViewLocation node(DefaultVisualizationSession s, PageRef page) {
        var location = s.reserveNodeId(page);
        s.addNode(path(location), ViewNode.Spec.point("node"));
        return location;
    }
    private Set<Set<Long>> components(DefaultVisualizationSession s, PageRef page) {
        return s.model().pages().get(page.pageId()).parts().values().stream().map(Part::members).collect(Collectors.toSet());
    }
    @Test void deletingANonBridgeDoesNotSplitButDeletingABridgeDoes() {
        try (var s = new DefaultVisualizationSession()) {
            var page = s.initializeRoot(BuiltinPageTypes.composite("no-ready", PageType.Layout.STRESS, false));
            var a = node(s, page); var b = node(s, page); var c = node(s, page); var d = node(s, page);
            assertEquals(4, components(s, page).size());
            s.modify(MutationBatch.of(new Connect(path(a), path(b), Direction.FORWARD),
                    new Connect(path(b), path(c)), new Connect(path(c), path(a)),
                    new Connect(path(c), path(d)))).requireSuccess();
            assertEquals(Set.of(Set.of(a.nodeId(), b.nodeId(), c.nodeId(), d.nodeId())), components(s, page));
            s.modify(MutationBatch.of(new Disconnect(path(a), path(b)))).requireSuccess();
            assertEquals(1, components(s, page).size());
            s.modify(MutationBatch.of(new Disconnect(path(c), path(d)))).requireSuccess();
            assertEquals(Set.of(Set.of(a.nodeId(), b.nodeId(), c.nodeId()), Set.of(d.nodeId())), components(s, page));
            var model = s.model().pages().get(page.pageId());
            model.parts().forEach((id, part) -> part.members().forEach(member -> assertEquals(id, model.membership().get(member))));
        }
    }
    @Test void fixedRandomUpdatesMatchAnIndependentConnectivityOracle() {
        try (var s = new DefaultVisualizationSession()) {
            var page = s.initializeRoot(BuiltinPageTypes.composite("random", PageType.Layout.STRESS, false));
            var vertices = new ArrayList<ViewLocation>();
            for (int i = 0; i < 12; i++) vertices.add(node(s, page));
            var expectedEdges = new LinkedHashMap<String, long[]>();
            var random = new Random(91731);
            for (int step = 0; step < 80; step++) {
                var a = vertices.get(random.nextInt(vertices.size()));
                var b = vertices.get(random.nextInt(vertices.size()));
                long low = Math.min(a.nodeId(), b.nodeId()), high = Math.max(a.nodeId(), b.nodeId());
                String key = low + ":" + high;
                if (expectedEdges.remove(key) != null)
                    s.modify(MutationBatch.of(new Disconnect(path(a), path(b)))).requireSuccess();
                else {
                    expectedEdges.put(key, new long[]{low, high});
                    s.modify(MutationBatch.of(new Connect(path(a), path(b), Direction.BACKWARD))).requireSuccess();
                }
                var ids = vertices.stream().map(ViewLocation::nodeId).collect(Collectors.toSet());
                assertEquals(ReferenceComponents.compute(ids, new ArrayList<>(expectedEdges.values())),
                        components(s, page), "seed=91731 step=" + step);
            }
        }
    }
}
