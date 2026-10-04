package craken.visualization.model;

import craken.visualization.api.*;
import craken.visualization.api.VisualizationCommand.Compose;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class SemanticNestingTest {
    private ViewLocation add(DefaultVisualizationSession session, PageRef page, ViewNode.Kind kind) {
        var position = session.reserveNodeId(page);
        session.addNode(new OperationPath(null, position), new ViewNode.Spec(kind, kind.name()));
        return position;
    }
    @Test void primitiveCellsDoNotConsumeTheOneDimensionalArraysNestingBudget() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.array());
            var array = add(session, page, ViewNode.Kind.ARRAY);
            var first = add(session, page, ViewNode.Kind.POINT);
            var second = add(session, page, ViewNode.Kind.POINT);
            var before = session.model();
            session.modify(MutationBatch.of(new Compose(array, second, 1), new Compose(array, first, 0))).requireSuccess();
            assertEquals(List.of(first, second), session.model().node(array).children());
            assertTrue(before.node(array).children().isEmpty());
        }
    }
    @Test void twoDimensionalArrayIsAllowedAndAThirdArrayLevelIsRejectedAtomically() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.array(1));
            var outer = add(session, page, ViewNode.Kind.ARRAY);
            var row = add(session, page, ViewNode.Kind.ARRAY);
            var cell = add(session, page, ViewNode.Kind.POINT);
            var inner = add(session, page, ViewNode.Kind.ARRAY);
            session.modify(MutationBatch.of(new Compose(outer, row, 0), new Compose(row, cell, 0))).requireSuccess();
            var before = session.model();
            var rejected = session.modify(MutationBatch.of(new Compose(row, inner, 1)));
            assertEquals(VisualizationError.Code.NESTING_LIMIT, rejected.error().code());
            assertSame(before, session.model());
            assertEquals(List.of(cell), session.model().node(row).children());
        }
    }
    @Test void defaultArrayTypeDoesNotSilentlyEnableNesting() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.array());
            var outer = add(session, page, ViewNode.Kind.ARRAY);
            var inner = add(session, page, ViewNode.Kind.ARRAY);
            var rejected = session.modify(MutationBatch.of(new Compose(outer, inner, 0)));
            assertEquals(VisualizationError.Code.NESTING_LIMIT, rejected.error().code());
        }
    }
    @Test void semanticDepthDoesNotLimitTheNumberOfViewNodeKindsOrWrapperObjects() {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.composite("hash-chains", PageType.Layout.ARRAY, true));
            var array = add(session, page, ViewNode.Kind.ARRAY);
            var list = add(session, page, ViewNode.Kind.LINKED);
            var value = add(session, page, ViewNode.Kind.POINT);
            session.modify(MutationBatch.of(new Compose(array, list, 0), new Compose(list, value, 0))).requireSuccess();
            assertEquals(List.of(list), session.model().node(array).children());
            assertEquals(List.of(value), session.model().node(list).children());
        }
    }
}
