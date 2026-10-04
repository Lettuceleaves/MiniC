package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class OwnershipReparentTest {
    @Test void finalParentsDetermineLivenessRegardlessOfDetachAttachOrder() {
        for (boolean detachFirst : new boolean[]{true, false}) try (var f = new ModelFixture()) {
            var other = f.root("new parent");
            var child = f.node(f.page(f.r), f.r, "same identity");
            var attach = new AttachOwnership(other, child);
            var detach = new DetachOwnership(f.r, child);
            f.session.modify(detachFirst ? MutationBatch.of(detach, attach) : MutationBatch.of(attach, detach)).requireSuccess();
            assertEquals(other, f.session.model().node(child).parents().selected());
            assertEquals(1, f.session.model().node(child).parents().parents().size());
            f.session.modify(MutationBatch.of(new DetachOwnership(other, child))).requireSuccess();
            assertThrows(IllegalArgumentException.class, () -> f.session.model().node(child));
        }
    }
}
