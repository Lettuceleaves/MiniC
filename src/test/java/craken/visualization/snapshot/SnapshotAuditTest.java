package craken.visualization.snapshot;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class SnapshotAuditTest {
    @Test void duplicateConcreteEdgesInAnExternalSnapshotAreRejectedBeforeRestore() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.graph(true));
            var a = add(session, root); var b = add(session, root);
            session.modify(MutationBatch.of(new Connect(new OperationPath(null, a), new OperationPath(null, b)))).requireSuccess();
            var good = session.snapshot(); var page = good.pages().get(root.pageId());
            var edges = new LinkedHashMap<>(page.topology()); var original = edges.values().iterator().next();
            // Reversing both endpoints must not disguise an identical undirected endpoint pair.
            var duplicate = new TopologyEdge(original.id() + 1, original.b(), original.a(), original.direction().reversed(),
                    original.bPort(), original.aPort(), original.style());
            edges.put(duplicate.id(), duplicate);
            var bad = replace(good, new VisualizationSnapshot.PageState(root, page.type(), page.nodes(), null, page.composition(), edges, page.ready()));
            var before = session.model();
            assertThrows(IllegalArgumentException.class, () -> session.restore(bad));
            assertSame(before, session.model());
        }
    }
    @Test void displaySnapshotsAcceptDeepNestingAndRejectNegativeDepth() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point());
            var good = session.snapshot(); var page = good.pages().get(root.pageId());
            // 嵌套深度没有默认上限（只要求非负），多层数组需要显式声明。
            var deep = new VisualizationSnapshot.TypeDescription("point", PageType.Layout.POINT, false, 999, Set.of(ViewNode.Kind.POINT));
            var deepModel = replace(good, new VisualizationSnapshot.PageState(root, deep, page.nodes(), null,
                    page.composition(), page.topology(), page.ready()));
            assertEquals(999, SnapshotCodec.toDisplayModel(deepModel).pages().get(root.pageId()).type().maximumNesting());
            var invalid = new VisualizationSnapshot.TypeDescription("point", PageType.Layout.POINT, false, -1, Set.of(ViewNode.Kind.POINT));
            var bad = replace(good, new VisualizationSnapshot.PageState(root, invalid, page.nodes(), null, page.composition(), page.topology(), page.ready()));
            assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.toDisplayModel(bad));
        }
    }
    @Test void connectedNodesCannotBeRestoredAsReadyAndHideAnExistingPart() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.graph(true));
            var a = add(session, root); var b = add(session, root);
            session.modify(MutationBatch.of(new Connect(new OperationPath(null, a), new OperationPath(null, b)))).requireSuccess();
            var good = session.snapshot(); var page = good.pages().get(root.pageId());
            var bad = replace(good, new VisualizationSnapshot.PageState(root, page.type(), page.nodes(), null, page.composition(), page.topology(), Set.of(a.nodeId(), b.nodeId())));
            var before = session.model();
            assertThrows(IllegalArgumentException.class, () -> session.restore(bad));
            assertSame(before, session.model());
        }
    }
    @Test void restoreCannotSilentlyNormalizeMixedCompositionResidence() {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.composite("ready-array", PageType.Layout.ARRAY, true));
            var a = add(session, root); var b = add(session, root);
            session.modify(MutationBatch.of(new Compose(a, b, 0))).requireSuccess();
            var good = session.snapshot(); var page = good.pages().get(root.pageId());
            var bad = replace(good, new VisualizationSnapshot.PageState(root, page.type(), page.nodes(), null, page.composition(), page.topology(), Set.of(b.nodeId())));
            var before = session.model();
            assertThrows(IllegalArgumentException.class, () -> session.restore(bad));
            assertSame(before, session.model());
        }
    }
    private static ViewLocation add(DefaultVisualizationSession s, PageRef p) {
        var at = s.reserveNodeId(p); s.addNode(new OperationPath(null, at), ViewNode.Spec.point("x")); return at;
    }
    private static VisualizationSnapshot replace(VisualizationSnapshot original, VisualizationSnapshot.PageState page) {
        return new VisualizationSnapshot(original.containerId(), original.root(), Map.of(page.ref().pageId(), page),
                original.ownership(), original.pageRules(), original.interaction(), original.sourceVersion(), original.epoch(), original.sourceStep(), original.highWater());
    }
}
