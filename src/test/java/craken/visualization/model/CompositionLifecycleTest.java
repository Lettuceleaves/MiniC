package craken.visualization.model;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.Compose;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class CompositionLifecycleTest {
    private ViewLocation array(DefaultVisualizationSession session, PageRef page) {
        var location = session.reserveNodeId(page);
        session.addNode(new OperationPath(null, location), new ViewNode.Spec(ViewNode.Kind.ARRAY, "array"));
        return location;
    }
    @Test void aChildHasOneCompositionParentAndSlotsAreUnique() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.array(1));
            var first = array(session, page);
            var second = array(session, page);
            var child = array(session, page);
            session.modify(MutationBatch.of(new Compose(first, child, 0))).requireSuccess();
            var before = session.model();
            assertEquals(VisualizationError.Code.COMPOSITION_CONFLICT,
                    session.modify(MutationBatch.of(new Compose(second, child, 0))).error().code());
            assertSame(before, session.model());
            assertEquals(VisualizationError.Code.COMPOSITION_CONFLICT,
                    session.modify(MutationBatch.of(new Compose(first, second, 0))).error().code());
        }
    }
    @Test void containmentCycleIsDistinctFromPageOwnershipCycle() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.array(1));
            var first = array(session, page);
            var second = array(session, page);
            var before = session.model();
            var result = session.modify(MutationBatch.of(new Compose(first, second, 0), new Compose(second, first, 0)));
            assertEquals(VisualizationError.Code.COMPOSITION_CONFLICT, result.error().code());
            assertSame(before, session.model());
        }
    }
}
