package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
import craken.visualization.support.ModelFixture;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class LifetimePlannerTest {
    @Test void sharedDescendantSurvivesUntilItsLastParentIsReleased() {
        try (var f = new ModelFixture()) {
            var other = f.root("other");
            var child = f.node(f.page(f.r), f.r, "child");
            var grandchild = f.node(f.page(child), child, "grandchild");
            f.session.modify(MutationBatch.of(new AttachOwnership(other, child))).requireSuccess();
            f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(null, f.r)))).requireSuccess();
            assertEquals(other, f.session.model().node(child).parents().selected());
            assertNotNull(f.session.model().node(grandchild));
            f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(null, other)))).requireSuccess();
            assertThrows(IllegalArgumentException.class, () -> f.session.model().node(child));
            assertThrows(IllegalArgumentException.class, () -> f.session.model().node(grandchild));
            assertEquals(1, f.session.model().pages().size());
            assertTrue(f.session.model().ownership().isEmpty());
        }
    }
    @Test void explicitDeletionClearsEveryParentAndDoesNotDeleteATopologicalNeighbor() {
        try (var f = new ModelFixture()) {
            var other = f.root("other");
            var page = f.page(f.r);
            var child = f.node(page, f.r, "child");
            var neighbor = f.node(page, other, "neighbor");
            f.session.modify(MutationBatch.of(new AttachOwnership(other, child),
                    new Connect(new OperationPath(f.r, child), new OperationPath(other, neighbor)))).requireSuccess();
            f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(other, child)))).requireSuccess();
            assertThrows(IllegalArgumentException.class, () -> f.session.model().node(child));
            assertNotNull(f.session.model().node(neighbor));
            var remaining = f.session.model().pages().get(page.pageId());
            assertTrue(remaining.topology().isEmpty());
            assertEquals(1, remaining.parts().size());
            assertFalse(remaining.ready().contains(neighbor.nodeId()));
            assertTrue(f.session.model().ownership().keySet().stream()
                    .noneMatch(key -> key.nxt().equals(child) || key.pre().equals(child)));
        }
    }
    @Test void deletingACompositeAlsoDeletesItsEmbeddedNodesAndTheirOwnedDescendants() {
        try (var s = new DefaultVisualizationSession()) {
            var page = s.initializeRoot(BuiltinPageTypes.array());
            var array = s.reserveNodeId(page);
            var cell = s.reserveNodeId(page);
            s.addNode(new OperationPath(null, array), new ViewNode.Spec(ViewNode.Kind.ARRAY, "array"));
            s.addNode(new OperationPath(null, cell), ViewNode.Spec.point("cell"));
            s.modify(MutationBatch.of(new Compose(array, cell, 0))).requireSuccess();
            var nested = s.initializePage(BuiltinPageTypes.point(), cell);
            var leaf = s.reserveNodeId(nested);
            s.addNode(new OperationPath(cell, leaf), ViewNode.Spec.point("leaf"));
            s.modify(MutationBatch.of(new DeleteNode(new OperationPath(null, array)))).requireSuccess();
            assertTrue(s.model().pages().get(page.pageId()).nodes().isEmpty());
            assertFalse(s.model().pages().containsKey(nested.pageId()));
            assertTrue(s.model().ownership().isEmpty());
        }
    }
    @Test void onlyReadyNodesStillMakeThePageNonemptyAfterTheDisplayedNodeIsDeleted() {
        try (var s = new DefaultVisualizationSession()) {
            var page = s.initializeRoot(BuiltinPageTypes.graph(false));
            var first = s.reserveNodeId(page); s.addNode(new OperationPath(null, first), ViewNode.Spec.point("first"));
            var waiting = s.reserveNodeId(page); s.addNode(new OperationPath(null, waiting), ViewNode.Spec.point("waiting"));
            s.modify(MutationBatch.of(new DeleteNode(new OperationPath(null, first)))).requireSuccess();
            var next = s.reserveNodeId(page); s.addNode(new OperationPath(null, next), ViewNode.Spec.point("next"));
            assertTrue(s.model().pages().get(page.pageId()).parts().isEmpty());
            assertEquals(2, s.model().pages().get(page.pageId()).ready().size());
        }
    }
    @Test void explicitlyDeletedTargetsCannotBeResurrectedLaterInTheSameBatch() {
        try (var f = new ModelFixture()) {
            var child = f.node(f.page(f.r), f.r, "child");
            var before = f.session.model();
            var rejected = f.session.modify(MutationBatch.of(
                    new DeleteNode(new OperationPath(f.r, child)), new AttachOwnership(f.r, child)));
            assertFalse(rejected.succeeded());
            assertSame(before, f.session.model());
        }
    }
}
