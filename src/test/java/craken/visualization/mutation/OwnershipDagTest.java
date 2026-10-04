package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class OwnershipDagTest {
    @Test void pageCycleIsAllowedButNodeCycleRollsBackAllChanges() {
        try (var f = new ModelFixture()) {
            var aPage = f.page(f.r);
            var a = f.node(aPage, f.r, "a");
            var b = f.node(f.page(a), a, "b");
            var a2 = f.node(aPage, b, "a2");
            var before = f.session.model();
            var result = f.session.modify(MutationBatch.of(new AttachOwnership(a2, b)));
            assertFalse(result.succeeded());
            assertEquals(VisualizationError.Code.OWNERSHIP_CYCLE, result.error().code());
            assertSame(before, f.session.model());
            assertNull(result.change());
            assertEquals(b, f.session.model().node(a2).parents().selected());
        }
    }
    @Test void cycleDetectionIncludesUnselectedParents() {
        try (var f = new ModelFixture()) {
            var a = f.node(f.page(f.r), f.r, "a");
            var b = f.node(f.page(a), a, "b");
            f.session.modify(MutationBatch.of(new AttachOwnership(f.r, b))).requireSuccess();
            assertEquals(f.r, f.session.model().node(b).parents().selected());
            var result = f.session.modify(MutationBatch.of(new AttachOwnership(b, a)));
            assertEquals(VisualizationError.Code.OWNERSHIP_CYCLE, result.error().code());
        }
    }
}
