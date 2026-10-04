package craken.visualization.snapshot;

import craken.visualization.api.*;
import craken.visualization.model.ViewNode;
import craken.visualization.model.ParentSelection;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class SnapshotInputContractTest {
    @Test void registeredCustomHighlightRuleRoundTripsWithCompositionChildren() {
        try (var s = new DefaultVisualizationSession()) {
            PageType type = new PageType() {
                public String key() { return "highlight-composition"; }
                public boolean readyEnabled() { return false; }
                public int maximumNesting() { return 0; }
                public Layout layout() { return Layout.ARRAY; }
                public Set<ViewNode.Kind> nodeKinds() { return Set.of(ViewNode.Kind.POINT); }
                public ViewNode create(ViewLocation at, ViewNode.Spec spec, ViewNode.Retention retention, ParentSelection parents) {
                    return new HighlightNode(at, spec, retention, parents, List.of());
                }
            };
            var page = s.initializeRoot(type); var parent = s.reserveNodeId(page); var child = s.reserveNodeId(page);
            s.addNode(new OperationPath(null, parent), ViewNode.Spec.point("parent"));
            s.addNode(new OperationPath(null, child), ViewNode.Spec.point("child"));
            s.modify(MutationBatch.of(new VisualizationCommand.Compose(parent, child, 0))).requireSuccess();
            var snapshot = s.snapshot(); s.restore(snapshot);
            assertEquals(Set.of(parent, child), s.model().node(parent).highlights());
            assertEquals(snapshot.pages(), s.snapshot().pages());
        }
    }
    @Test void liveRestoreCannotDiscardADanglingCapturedHighlight() {
        try (var s = new DefaultVisualizationSession()) {
            var page = s.initializeRoot(BuiltinPageTypes.point()); var node = s.reserveNodeId(page);
            s.addNode(new OperationPath(null, node), ViewNode.Spec.point("node"));
            var bad = withHighlights(s.snapshot(), node, Set.of(new ViewLocation(node.containerId(), node.pageId(), 999)));
            var before = s.model(); var high = s.snapshot().highWater();
            assertThrows(IllegalArgumentException.class, () -> s.restore(bad));
            assertSame(before, s.model()); assertEquals(high, s.snapshot().highWater());
            assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.toDisplayModel(bad));
        }
    }

    @Test void liveRestoreRejectsAChangedHighlightContractInsteadOfChangingTheCapturedFrame() {
        try (var s = new DefaultVisualizationSession()) {
            var page = s.initializeRoot(BuiltinPageTypes.point()); var node = s.reserveNodeId(page);
            s.addNode(new OperationPath(null, node), ViewNode.Spec.point("node"));
            var bad = withHighlights(s.snapshot(), node, Set.of());
            var before = s.model();
            assertThrows(IllegalArgumentException.class, () -> s.restore(bad));
            assertSame(before, s.model());
            assertTrue(SnapshotCodec.toDisplayModel(bad).node(node).highlights().isEmpty(),
                    "A display DTO preserves captured values; live restore must also match its registered type");
        }
    }

    private static VisualizationSnapshot withHighlights(VisualizationSnapshot snapshot, ViewLocation location, Set<ViewLocation> highlights) {
        var page = snapshot.pages().get(location.pageId()); var node = page.nodes().get(location.nodeId());
        var changed = new VisualizationSnapshot.NodeState(location, node.content(), node.retention(), node.parents(), highlights);
        var nodes = new LinkedHashMap<>(page.nodes()); nodes.put(location.nodeId(), changed);
        var pages = new LinkedHashMap<>(snapshot.pages());
        pages.put(location.pageId(), new VisualizationSnapshot.PageState(page.ref(), page.type(), nodes, page.anchor(), page.composition(), page.topology(), page.ready(), page.layoutHints()));
        return new VisualizationSnapshot(snapshot.containerId(), snapshot.root(), pages, snapshot.ownership(), snapshot.pageRules(),
                snapshot.interaction(), snapshot.sourceVersion(), snapshot.epoch(), snapshot.sourceStep(), snapshot.highWater());
    }

    private static final class HighlightNode extends ViewNode {
        HighlightNode(ViewLocation at, Spec content, Retention retention, ParentSelection parents, List<ViewLocation> children) {
            super(at, content, retention, parents, children);
        }
        public Set<ViewLocation> highlights() { var values = new HashSet<>(children()); values.add(location()); return Set.copyOf(values); }
        public ViewNode withState(Spec content, ParentSelection parents) { return new HighlightNode(location(), content, retention(), parents, children()); }
        public ViewNode withChildren(List<ViewLocation> children) { return new HighlightNode(location(), content(), retention(), parents(), children); }
    }
}
