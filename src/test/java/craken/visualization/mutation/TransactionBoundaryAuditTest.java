package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.ViewNode;
import craken.visualization.support.ModelFixture;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static craken.visualization.api.VisualizationCommand.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class TransactionBoundaryAuditTest {
    @Test void deletingASharedChildReportsEveryFormerUpstreamPage() {
        try (var f = new ModelFixture()) {
            var a = f.node(f.page(f.r), f.r, "a");
            var b = f.node(f.page(f.r), f.r, "b");
            var child = f.node(f.page(a), a, "child");
            f.session.modify(MutationBatch.of(new AttachOwnership(b, child))).requireSuccess();
            var result = f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(a, child))));
            result.requireSuccess();
            assertTrue(result.change().pages().containsAll(Set.of(a.page(), b.page(), child.page())),
                    "Deleting incoming bindings changes the reverse index in both upstream pages");
        }
    }

    @Test void withdrawingARuleReportsItsParentEvenWhenAnExplicitSourceKeepsTheBinding() {
        try (var f = new ModelFixture()) {
            var page = f.session.initializePage(BuiltinPageTypes.point(), f.r);
            var child = f.session.reserveNodeId(page); f.session.addNode(new OperationPath(f.r, child), ViewNode.Spec.point("child"));
            var rule = f.session.model().pageRules().values().stream().filter(r -> r.child().equals(page)).findFirst().orElseThrow();
            var result = f.session.modify(MutationBatch.of(new UnbindPage(rule.id())));
            result.requireSuccess();
            assertEquals(Set.of(f.root, page), result.change().pages());
            assertNotNull(f.session.model().node(child));
        }
    }

    @Test void explicitlyDeletedCompositionSlotCanBeReplacedWithinOneAtomicBatch() {
        try (var s = new DefaultVisualizationSession()) {
            var page = s.initializeRoot(BuiltinPageTypes.array());
            var array = s.reserveNodeId(page); s.addNode(new OperationPath(null, array), new ViewNode.Spec(ViewNode.Kind.ARRAY, "array"));
            var old = s.reserveNodeId(page); s.addNode(new OperationPath(null, old), ViewNode.Spec.point("old"));
            s.modify(MutationBatch.of(new Compose(array, old, 0))).requireSuccess();
            var next = s.reserveNodeId(page); var version = s.model().version();
            var result = s.modify(MutationBatch.of(new DeleteNode(new OperationPath(null, old)),
                    new AddNode(new OperationPath(null, next), ViewNode.Spec.point("next")), new Compose(array, next, 0)));
            result.requireSuccess();
            assertEquals(version + 1, s.model().version());
            assertEquals(List.of(next), s.model().node(array).children());
            assertThrows(IllegalArgumentException.class, () -> s.model().node(old));
            var composed = s.snapshot(); s.restore(composed);
            assertEquals(composed.pages(), s.snapshot().pages());
        }
    }
}
