package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class ReadyResidenceTest {
    private OperationPath path(ViewLocation n) { return new OperationPath(null, n); }
    private ViewLocation node(DefaultVisualizationSession s, PageRef p, ViewNode.Kind kind) {
        var n = s.reserveNodeId(p);
        s.addNode(path(n), new ViewNode.Spec(kind, kind.name()));
        return n;
    }
    @Test void firstNodeIsShownAndTwoReadyNodesCanJoinTheirOwnNewPart() {
        try (var s = new DefaultVisualizationSession()) {
            var p = s.initializeRoot(BuiltinPageTypes.graph(false));
            var first = node(s, p, ViewNode.Kind.POINT);
            var a = node(s, p, ViewNode.Kind.POINT);
            var b = node(s, p, ViewNode.Kind.POINT);
            assertEquals(Set.of(a.nodeId(), b.nodeId()), s.model().pages().get(p.pageId()).ready());
            assertEquals(Set.of(first.nodeId()), s.model().pages().get(p.pageId()).membership().keySet());
            s.modify(MutationBatch.of(new Connect(path(a), path(b)))).requireSuccess();
            assertTrue(s.model().pages().get(p.pageId()).ready().isEmpty());
            assertEquals(2, s.model().pages().get(p.pageId()).parts().size());
            s.modify(MutationBatch.of(new Disconnect(path(a), path(b)))).requireSuccess();
            assertEquals(3, s.model().pages().get(p.pageId()).parts().size());
            assertTrue(s.model().pages().get(p.pageId()).ready().isEmpty());
        }
    }
    @Test void readyCompositionStaysReadyUntilConnectedAndPromotesTheWholeUnit() {
        try (var s = new DefaultVisualizationSession()) {
            var p = s.initializeRoot(BuiltinPageTypes.composite("ready-array", PageType.Layout.ARRAY, true));
            var first = node(s, p, ViewNode.Kind.POINT);
            var array = node(s, p, ViewNode.Kind.ARRAY);
            var cell = node(s, p, ViewNode.Kind.POINT);
            s.modify(MutationBatch.of(new Compose(array, cell, 0))).requireSuccess();
            assertEquals(Set.of(array.nodeId(), cell.nodeId()), s.model().pages().get(p.pageId()).ready());
            s.modify(MutationBatch.of(new Connect(path(first), path(cell)))).requireSuccess();
            assertTrue(s.model().pages().get(p.pageId()).ready().isEmpty());
            assertEquals(1, s.model().pages().get(p.pageId()).parts().size());
        }
    }
    @Test void mixedCompositionPromotesInBothDirectionsAndNeverDemotes() {
        for (boolean parentFirst : new boolean[]{true, false}) try (var s = new DefaultVisualizationSession()) {
            var p = s.initializeRoot(BuiltinPageTypes.composite("mixed", PageType.Layout.ARRAY, true));
            var first = node(s, p, parentFirst ? ViewNode.Kind.ARRAY : ViewNode.Kind.POINT);
            var second = node(s, p, parentFirst ? ViewNode.Kind.POINT : ViewNode.Kind.ARRAY);
            s.modify(MutationBatch.of(new Compose(parentFirst ? first : second, parentFirst ? second : first, 0))).requireSuccess();
            assertTrue(s.model().pages().get(p.pageId()).ready().isEmpty());
            assertEquals(1, s.model().pages().get(p.pageId()).parts().size());
        }
    }
}
