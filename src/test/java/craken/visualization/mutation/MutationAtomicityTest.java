package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.ViewNode;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class MutationAtomicityTest {
    @Test void laterFailureDiscardsTheWholeDraftAndConsumesReservedIds() {
        try (var f = new ModelFixture()) {
            var page = f.page(f.r);
            var fresh = f.session.reserveNodeId(page);
            var before = f.session.model();
            var result = f.session.modify(MutationBatch.of(
                    new AddNode(new OperationPath(f.r, fresh), ViewNode.Spec.point("new")),
                    new AttachOwnership(new ViewLocation(f.r.containerId(), f.r.pageId(), 999), fresh)));
            assertFalse(result.succeeded());
            assertSame(before, f.session.model());
            assertNull(result.change());
            assertTrue(f.session.reserveNodeId(page).nodeId() > fresh.nodeId());
            var retry = f.session.modify(MutationBatch.of(
                    new AddNode(new OperationPath(f.r, fresh), ViewNode.Spec.point("reuse"))));
            assertEquals(VisualizationError.Code.UNRESERVED_POSITION, retry.error().code());
        }
    }
    @Test void successfulBatchPublishesExactlyOneVersionWithBothNodes() {
        try (var f = new ModelFixture()) {
            var page = f.page(f.r);
            var first = f.session.reserveNodeId(page);
            var second = f.session.reserveNodeId(page);
            long before = f.session.model().version();
            var result = f.session.modify(MutationBatch.of(
                    new AddNode(new OperationPath(f.r, first), ViewNode.Spec.point("one")),
                    new AddNode(new OperationPath(f.r, second), ViewNode.Spec.point("two"))));
            assertTrue(result.succeeded(), () -> String.valueOf(result.error()));
            assertEquals(before + 1, result.version());
            assertEquals(2, result.created().size());
            assertEquals(2, f.session.model().pages().get(page.pageId()).nodes().size());
        }
    }
}
