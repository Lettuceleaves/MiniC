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
    @Test void disablingNavigationBeforeTheFirstAllocationStillHighlightsTheDisplayedRootPage() {
        try (var session = new craken.visualization.mutation.DefaultVisualizationSession()) {
            var page = session.initializeRoot(craken.visualization.type.BuiltinPageTypes.point());
            session.modify(MutationBatch.of(new Configure(new VisualizationOptions(false, true)))).requireSuccess();
            var node = session.reserveNodeId(page); session.addNode(new OperationPath(null, node), ViewNode.Spec.point("first"));
            assertNull(session.model().focus());
            var frame = NavigationResolver.resolve(session.model()); assertEquals(1, frame.occurrences().size());
            assertNull(frame.occurrences().getFirst().node()); assertEquals(Set.of(node), frame.occurrences().getFirst().highlights());
        }
    }
    @Test void disabledNavigationStillHighlightsAnotherNodeInTheDisplayedPage() {
        try (var f = new ModelFixture()) {
            var other = f.root("other");
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(false, false)),
                    new SetFocus(new OperationPath(null, f.r)), new Touch(new OperationPath(null, other), AccessKind.READ))).requireSuccess();
            var frame = NavigationResolver.resolve(f.session.model());
            assertEquals(f.r, f.session.model().focus()); assertEquals(List.of(f.r), frame.occurrences().stream().map(NavigationFrame.PageOccurrence::node).toList());
            assertEquals(Set.of(other), frame.occurrences().getFirst().highlights());
        }
    }
    @Test void anUnfocusedRootOccurrenceDoesNotInsertTheAccessedNonRootPage() {
        try (var f = new ModelFixture()) {
            var child = f.node(f.page(f.r), f.r, "child");
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(false, false)), new ClearFocus(),
                    new Touch(new OperationPath(f.r, child), AccessKind.READ))).requireSuccess();
            var frame = NavigationResolver.resolve(f.session.model()); assertEquals(1, frame.occurrences().size());
            assertNull(f.session.model().focus()); assertEquals(f.root, frame.occurrences().getFirst().page());
            assertNull(frame.occurrences().getFirst().node()); assertTrue(frame.occurrences().getFirst().highlights().isEmpty());
        }
    }
    @Test void disabledNavigationHighlightsAnOffPathNodeInAnAlreadyDisplayedUpstreamPage() {
        try (var f = new ModelFixture()) {
            var other = f.root("other"); var child = f.node(f.page(f.r), f.r, "child");
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(false, false)),
                    new Touch(new OperationPath(null, other), AccessKind.WRITE))).requireSuccess();
            var frame = NavigationResolver.resolve(f.session.model());
            assertEquals(child, f.session.model().focus()); assertEquals(Set.of(other), frame.occurrences().getFirst().highlights());
            assertTrue(frame.occurrences().getLast().highlights().isEmpty());
        }
    }
    @Test void offPathAccessTargetsOnlyTheRepeatedPageOccurrenceWithTheClosestParentContext() {
        for (boolean deeper : List.of(false, true)) try (var f = new ModelFixture()) {
            var aPage = f.page(f.r); var a1 = f.node(aPage, f.r, "a1"); var b1 = f.node(f.page(a1), a1, "b1");
            var a2 = f.node(aPage, b1, "a2"); var pre = deeper ? b1 : f.r; var side = f.node(aPage, pre, "side");
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(false, true)),
                    new SetFocus(new OperationPath(b1, a2)), new Touch(new OperationPath(pre, side), AccessKind.READ))).requireSuccess();
            var occurrences = NavigationResolver.resolve(f.session.model()).occurrences();
            assertEquals(List.of(f.r, a1, b1, a2), occurrences.stream().map(NavigationFrame.PageOccurrence::node).toList());
            assertEquals(1, occurrences.stream().filter(o -> o.highlights().contains(side)).count(), "Do not broadcast to every copy of a shared page");
            assertTrue(occurrences.get(deeper ? 3 : 1).highlights().contains(side));
            assertEquals(Set.of(f.r), occurrences.getFirst().highlights());
            if (deeper) { assertEquals(Set.of(a1), occurrences.get(1).highlights()); assertEquals(Set.of(b1), occurrences.get(2).highlights()); }
            else { assertTrue(occurrences.get(2).highlights().isEmpty()); assertTrue(occurrences.get(3).highlights().isEmpty()); }
        }
    }
    @Test void clearingFocusIsAtomicAndPreservesNavigationOptionsAndLiveNodes() {
        try (var f = new ModelFixture()) {
            var options = new VisualizationOptions(false, true);
            f.session.modify(MutationBatch.of(new Configure(options), new ClearFocus())).requireSuccess();
            assertNull(f.session.model().focus()); assertNull(f.session.model().interaction().accessed());
            assertEquals(options, f.session.model().interaction().options()); assertNotNull(f.session.model().node(f.r));
            f.session.modify(MutationBatch.of(new SetFocus(new OperationPath(null, f.r)))).requireSuccess();
            var before = f.session.model();
            assertFalse(f.session.modify(MutationBatch.of(new ClearFocus(), new DeleteNode(new OperationPath(null,
                    new ViewLocation(f.r.containerId(), f.r.pageId(), 999))))).succeeded());
            assertSame(before, f.session.model());
        }
    }
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
    @Test void removingANonselectedBindingPreservesTheSelectedParentIdentity() {
        try (var f = new ModelFixture()) {
            var b = f.root("b"); var c = f.root("c"); var child = f.node(f.page(f.r), f.r, "child");
            f.session.modify(MutationBatch.of(new AttachOwnership(b, child), new AttachOwnership(c, child),
                    new Touch(new OperationPath(f.r, child), AccessKind.READ))).requireSuccess();
            f.session.modify(MutationBatch.of(new DetachOwnership(b, child))).requireSuccess();
            assertEquals(List.of(f.r, c), f.session.model().node(child).parents().parents());
            assertEquals(f.r, f.session.model().node(child).parents().selected());
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
    @Test void deletingAnAncestorThroughAnotherBranchDoesNotRewriteTheDisabledNavigationFallback() {
        for (boolean autoNavigate : new boolean[]{false, true}) try (var f = new ModelFixture()) {
            var other = f.root("other branch");
            var ancestor = f.node(f.page(f.r), f.r, "ancestor");
            var focused = f.node(f.page(ancestor), ancestor, "focused descendant");
            f.session.modify(MutationBatch.of(new AttachOwnership(other, ancestor),
                    new Touch(new OperationPath(f.r, ancestor), AccessKind.READ), new SetFocus(new OperationPath(ancestor, focused)))).requireSuccess();
            f.session.modify(MutationBatch.of(new Configure(new VisualizationOptions(autoNavigate, true)),
                    new DeleteNode(new OperationPath(other, ancestor)))).requireSuccess();
            assertEquals(autoNavigate ? other : f.r, f.session.model().focus());
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
