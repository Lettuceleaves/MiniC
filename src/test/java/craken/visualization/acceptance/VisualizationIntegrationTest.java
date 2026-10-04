package craken.visualization.acceptance;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.*;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.navigation.NavigationResolver;
import craken.visualization.support.ModelFixture;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.*;
import java.util.*;
import static craken.visualization.api.VisualizationCommand.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class VisualizationIntegrationTest {
    @Test void arrayCompositionListAndWholePageTreeBindingsReleaseAsOneReachabilityChain() {
        try(var session=new DefaultVisualizationSession()) {
            var arrays=session.initializeRoot(BuiltinPageTypes.array());
            var array=add(session,arrays,null,new ViewNode.Spec(ViewNode.Kind.ARRAY,"buckets"));
            var cell=add(session,arrays,null,ViewNode.Spec.point("bucket[0]"));
            apply(session,new Compose(array,cell,0));
            var lists=session.initializePage(BuiltinPageTypes.linked(false),cell);
            var first=add(session,lists,cell,new ViewNode.Spec(ViewNode.Kind.LINKED,"first"));
            var second=add(session,lists,cell,new ViewNode.Spec(ViewNode.Kind.LINKED,"second"));
            var trees=session.initializePage(BuiltinPageTypes.tree(true),PageBindingRule.Spec.page(lists));
            var leaf=add(session,trees,first,new ViewNode.Spec(ViewNode.Kind.TREE,"shared tree"));
            assertEquals(Set.of(first,second),Set.copyOf(session.model().node(leaf).parents().parents()));
            assertTrue(session.model().pages().get(lists.pageId()).ready().contains(second.nodeId()));
            apply(session,new Connect(path(cell,first),path(cell,second)),new Touch(path(second,leaf),AccessKind.READ));
            assertEquals(List.of(cell,second,leaf),NavigationResolver.resolve(session.model()).occurrences().stream().map(o->o.node()).toList());
            apply(session,new DeleteNode(path(null,array)));
            assertEquals(Set.of(arrays.pageId()),session.model().pages().keySet());
            assertTrue(session.model().pages().get(arrays.pageId()).nodes().isEmpty());
            assertTrue(session.model().ownership().isEmpty());
            assertTrue(session.model().pageRules().isEmpty());
        }
    }

    @Test void singletonTreeReadyConnectSplitAccessSnapshotAndReleaseUsePublicApi() {
        try (var session=new DefaultVisualizationSession()) {
            var rootPage=session.initializeRoot(BuiltinPageTypes.point());
            var owner=add(session,rootPage,null,ViewNode.Spec.point("singleton"));
            var trees=session.initializePage(BuiltinPageTypes.tree(true),owner);
            var root=add(session,trees,owner,new ViewNode.Spec(ViewNode.Kind.TREE,"root"));
            var child=add(session,trees,owner,new ViewNode.Spec(ViewNode.Kind.TREE,"child"));
            assertEquals(Set.of(child.nodeId()),session.model().pages().get(trees.pageId()).ready());
            apply(session,new Connect(path(owner,root),path(owner,child),TopologyEdge.Direction.FORWARD));
            assertTrue(session.model().pages().get(trees.pageId()).ready().isEmpty());
            assertEquals(1,session.model().pages().get(trees.pageId()).parts().size());
            apply(session,new Touch(path(owner,child),AccessKind.READ));
            var occurrences=NavigationResolver.resolve(session.model()).occurrences();
            assertEquals(List.of(owner,child),occurrences.stream().map(o->o.node()).toList());
            assertEquals(Set.of(owner),occurrences.getFirst().highlights());
            assertEquals(Set.of(child),occurrences.getLast().highlights());
            var connected=session.snapshot();
            apply(session,new Disconnect(path(owner,root),path(owner,child)));
            assertEquals(2,session.model().pages().get(trees.pageId()).parts().size());
            assertTrue(session.model().pages().get(trees.pageId()).ready().isEmpty());
            long liveVersion=session.model().version();
            session.restore(connected);
            assertTrue(session.model().version()>liveVersion);
            assertEquals(connected.sourceVersion(),session.snapshot().sourceVersion());
            apply(session,new DeleteNode(path(null,owner)));
            assertEquals(Set.of(rootPage.pageId()),session.model().pages().keySet());
            assertTrue(session.model().pages().get(rootPage.pageId()).nodes().isEmpty());
            assertTrue(session.model().ownership().isEmpty());
            assertTrue(session.model().pageRules().isEmpty());
            assertNull(session.model().focus());
        }
    }

    @Test void sharedOwnershipSurvivesOneParentAndRepeatedPageDoesNotPermitNodeCycle() {
        try(var f=new ModelFixture()) {
            var other=f.root("second singleton");
            var pageA=f.page(f.r); var a1=f.node(pageA,f.r,"a1");
            var pageB=f.page(a1); var b=f.node(pageB,a1,"b");
            var a2=f.node(pageA,b,"a2");
            apply(f.session,new AttachOwnership(other,a2),new Touch(path(b,a2),AccessKind.READ));
            assertEquals(List.of(f.root,pageA,pageB,pageA),NavigationResolver.resolve(f.session.model())
                    .occurrences().stream().map(o->o.page()).toList());
            var before=f.session.model();
            var cycle=f.session.modify(MutationBatch.of(new AttachOwnership(a2,b)));
            assertEquals(VisualizationError.Code.OWNERSHIP_CYCLE,cycle.error().code());
            assertSame(before,f.session.model());
            apply(f.session,new Configure(new VisualizationOptions(false,true)));
            apply(f.session,new DeleteNode(path(null,f.r)));
            assertEquals(Set.of(other),Set.copyOf(f.session.model().node(a2).parents().parents()));
            assertFalse(f.session.model().pages().containsKey(pageB.pageId()));
            assertEquals(a2,f.session.model().focus());
            apply(f.session,new DeleteNode(path(null,other)));
            assertEquals(Set.of(f.root.pageId()),f.session.model().pages().keySet());
            assertTrue(f.session.model().pages().get(f.root.pageId()).nodes().isEmpty());
        }
    }

    private static ViewLocation add(VisualizationSession session,PageRef page,ViewLocation pre,ViewNode.Spec spec) {
        var location=session.reserveNodeId(page); session.addNode(path(pre,location),spec); return location;
    }
    private static OperationPath path(ViewLocation pre,ViewLocation nxt) { return new OperationPath(pre,nxt); }
    private static void apply(VisualizationSession session,VisualizationCommand... commands) {
        session.modify(MutationBatch.of(commands)).requireSuccess();
    }
}
