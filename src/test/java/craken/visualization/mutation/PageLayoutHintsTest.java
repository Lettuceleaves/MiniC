package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.ViewNode;
import craken.visualization.snapshot.SnapshotCodec;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class PageLayoutHintsTest {
    @Test void callerCanChangeTheRootAndSiblingOrderWithoutChangingFocusOrIdentity() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.tree(true));
            var oldRoot = add(session, page); var newRoot = add(session, page); var child = add(session, page);
            session.modify(MutationBatch.of(new Connect(p(oldRoot), p(newRoot)), new Connect(p(newRoot), p(child)))).requireSuccess();
            var interaction = session.model().interaction();
            var hints = new PageLayoutHints(newRoot, List.of(child, oldRoot));
            session.modify(MutationBatch.of(new SetPageLayout(page, hints))).requireSuccess();
            assertEquals(interaction, session.model().interaction());
            assertEquals(hints, session.model().pages().get(page.pageId()).layoutHints());
            session.modify(MutationBatch.of(new Touch(p(child), AccessKind.READ))).requireSuccess();
            var snapshot = session.snapshot();
            assertEquals(hints, SnapshotCodec.toDisplayModel(snapshot).pages().get(page.pageId()).layoutHints());
            session.modify(MutationBatch.of(new SetPageLayout(page, PageLayoutHints.EMPTY))).requireSuccess();
            session.restore(snapshot);
            assertEquals(hints, session.model().pages().get(page.pageId()).layoutHints());
        }
    }
    @Test void releasingAHintedNodePrunesItsHintsWithoutPreventingDeletion() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page); var b = add(session, page);
            session.modify(MutationBatch.of(new SetPageLayout(page, new PageLayoutHints(a, List.of(a, b))))).requireSuccess();
            session.modify(MutationBatch.of(new DeleteNode(p(a)))).requireSuccess();
            assertEquals(new PageLayoutHints(null, List.of(b)), session.model().pages().get(page.pageId()).layoutHints());
        }
    }
    @Test void foreignOrMissingHintsRejectTheEntireBatch() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page);
            var before = session.model();
            for (var bad : List.of(new ViewLocation(page.containerId(), page.pageId(), 999), new ViewLocation(page.containerId()+1, page.pageId(), a.nodeId()))) {
                var result = session.modify(MutationBatch.of(new SetContent(p(a), ViewNode.Spec.point("changed")),
                        new SetPageLayout(page, new PageLayoutHints(bad, List.of()))));
                assertFalse(result.succeeded()); assertSame(before, session.model());
            }
            assertThrows(IllegalArgumentException.class, () -> new PageLayoutHints(null, List.of(a, a)));
        }
    }
    private static OperationPath p(ViewLocation at) { return new OperationPath(null, at); }
    private static ViewLocation add(DefaultVisualizationSession session, PageRef page) {
        var at = session.reserveNodeId(page); session.addNode(p(at), ViewNode.Spec.point("node")); return at;
    }
}
