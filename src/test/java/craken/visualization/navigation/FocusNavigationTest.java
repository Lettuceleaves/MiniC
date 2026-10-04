package craken.visualization.navigation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.support.ModelFixture;
import org.junit.jupiter.api.*;
import java.util.*;
import static craken.visualization.api.VisualizationCommand.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class FocusNavigationTest {
    @Test void nodePathKeepsRepeatedRootPageOccurrences() {
        try (var f = new ModelFixture()) {
            var a = f.node(f.page(f.r),f.r,"a"); var b = f.node(f.root,a,"b");
            assertEquals(b,f.session.model().focus());
            var occurrences = NavigationResolver.resolve(f.session.model()).occurrences();
            assertEquals(List.of(f.r,a,b),occurrences.stream().map(NavigationFrame.PageOccurrence::node).toList());
            assertEquals(f.root,occurrences.getFirst().page()); assertEquals(f.root,occurrences.getLast().page());
            for (var o : occurrences) assertEquals(Set.of(o.node()),o.highlights());
        }
    }
    @Test void selectedParentRemovalAdvancesThenWraps() {
        try (var f = new ModelFixture()) {
            var b=f.root("b"); var c=f.root("c"); var x=f.node(f.page(f.r),f.r,"x");
            f.session.modify(MutationBatch.of(new AttachOwnership(b,x),new AttachOwnership(c,x),new Touch(new OperationPath(b,x),AccessKind.READ))).requireSuccess();
            f.session.modify(MutationBatch.of(new DetachOwnership(b,x))).requireSuccess();
            assertEquals(c,f.session.model().node(x).parents().selected());
            f.session.modify(MutationBatch.of(new DetachOwnership(c,x))).requireSuccess();
            assertEquals(f.r,f.session.model().node(x).parents().selected());
        }
    }
    @Test void deletingNonfocusedNodeUsesSuppliedParentBeforeFallback() {
        try (var f = new ModelFixture()) {
            var b=f.root("b"); var x=f.node(f.page(f.r),f.r,"x");
            f.session.modify(MutationBatch.of(new AttachOwnership(b,x),new SetFocus(new OperationPath(null,f.r)))).requireSuccess();
            f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(b,x)))).requireSuccess();
            assertEquals(b,f.session.model().focus());
        }
    }
    @Test void disabledNavigationStillRepairsCascadedFocusUsingSavedChain() {
        try (var f = new ModelFixture()) {
            var x=f.node(f.page(f.r),f.r,"x"); var y=f.node(f.page(x),x,"y");
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(false,true)),new DeleteNode(new OperationPath(f.r,x)))).requireSuccess();
            assertEquals(f.r,f.session.model().focus());
            assertThrows(IllegalArgumentException.class,()->f.session.model().node(y));
        }
    }
    @Test void twoOptionsAreIndependentAndInvisibleAccessDoesNotInsertPages() {
        try (var f = new ModelFixture()) {
            var x=f.node(f.page(f.r),f.r,"x"); var y=f.node(f.page(f.r),f.r,"y");
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(true,false)),new Touch(new OperationPath(f.r,x),AccessKind.READ))).requireSuccess();
            var frame=NavigationResolver.resolve(f.session.model());
            assertTrue(frame.occurrences().getFirst().highlights().isEmpty()); assertEquals(Set.of(x),frame.occurrences().getLast().highlights());
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(false,false)),new Touch(new OperationPath(f.r,y),AccessKind.WRITE))).requireSuccess();
            assertEquals(x,f.session.model().focus()); assertEquals(y,f.session.model().interaction().accessed());
            assertTrue(NavigationResolver.resolve(f.session.model()).occurrences().stream().allMatch(o->o.highlights().isEmpty()));
        }
    }
    @Test void contentUpdatePreservesIdentityAndRollbackRejectsKindChanges() {
        try (var f = new ModelFixture()) {
            f.session.modify(MutationBatch.of(new SetContent(new OperationPath(null,f.r),ViewNode.Spec.point("changed")))).requireSuccess();
            assertEquals("changed",f.session.model().node(f.r).content().label());
            var before=f.session.model();
            assertFalse(f.session.modify(MutationBatch.of(new SetContent(new OperationPath(null,f.r),new ViewNode.Spec(ViewNode.Kind.ARRAY,"wrong")))).succeeded());
            assertSame(before,f.session.model());
        }
    }
    @Test void emptyRootStillHasOneOccurrenceAndLastRootDeletionClearsFocus() {
        try (var f = new ModelFixture()) {
            f.session.modify(MutationBatch.of(new DeleteNode(new OperationPath(null,f.r)))).requireSuccess();
            assertNull(f.session.model().focus());
            var frame=NavigationResolver.resolve(f.session.model()); assertEquals(1,frame.occurrences().size()); assertNull(frame.occurrences().getFirst().node());
        }
    }
}
