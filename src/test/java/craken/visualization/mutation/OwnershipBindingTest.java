package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.OwnershipBinding;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class OwnershipBindingTest {
    @Test void existingTargetCanAcquireANewParentWithoutRebuildingContent() {
        try (var f = new ModelFixture()) {
            var other = f.root("other");
            var child = f.node(f.page(f.r), f.r, "original");
            var result = f.session.modify(MutationBatch.of(
                    new AddNode(new OperationPath(other, child), ViewNode.Spec.point("ignored"))));
            assertTrue(result.succeeded(), () -> String.valueOf(result.error()));
            var node = f.session.model().node(child);
            assertEquals("original", node.content().label());
            assertEquals(2, node.parents().parents().size());
            assertEquals(other, node.parents().selected());
            assertEquals(2, f.session.model().ownership().size());
        }
    }
    @Test void duplicateSourcesAreIdempotentAndDoNotDuplicateTheEffectiveParent() {
        try (var f = new ModelFixture()) {
            var child = f.node(f.page(f.r), f.r, "child");
            f.session.modify(MutationBatch.of(
                    new AttachOwnership(f.r, child, "extra"),
                    new AttachOwnership(f.r, child, "extra"))).requireSuccess();
            var binding = f.session.model().ownership().get(new OwnershipBinding.Key(f.r, child));
            assertEquals(2, binding.sources().size());
            assertEquals(1, f.session.model().node(child).parents().parents().size());
        }
    }
    @Test void equalValuesStillHaveDistinctIdentitiesAndRootCannotBeOwned() {
        try (var f = new ModelFixture()) {
            var other = f.root("root");
            assertNotEquals(f.r, other);
            var child = f.node(f.page(f.r), f.r, "root");
            var result = f.session.modify(MutationBatch.of(new AttachOwnership(child, other)));
            assertFalse(result.succeeded());
            assertEquals(VisualizationError.Code.INVALID_OWNERSHIP, result.error().code());
        }
    }
}
