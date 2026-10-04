package craken.visualization.snapshot;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.PageBindingRule;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.*;
import java.util.*;
import static craken.visualization.api.VisualizationCommand.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
class SnapshotCodecTest {
    ViewLocation add(DefaultVisualizationSession s,PageRef p,ViewLocation pre,String label) {
        var n=s.reserveNodeId(p); s.addNode(new OperationPath(pre,n),ViewNode.Spec.point(label)); return n;
    }
    @Test void roundTripRebuildsPartsAndPreservesRulesSelectionAndContents() {
        try(var s=new DefaultVisualizationSession()) {
            var r=s.initializeRoot(BuiltinPageTypes.point()); var a=add(s,r,null,"a"); var b=add(s,r,null,"b");
            var p=s.initializePage(BuiltinPageTypes.graph(true),PageBindingRule.Spec.page(r)); var x=add(s,p,a,"x"); var y=add(s,p,b,"y");
            s.modify(MutationBatch.of(new Connect(new OperationPath(a,x),new OperationPath(b,y)))).requireSuccess();
            var snap=s.snapshot(); var oldVersion=s.model().version(); var oldEpoch=s.model().epoch();
            s.modify(MutationBatch.of(new SetContent(new OperationPath(a,x),ViewNode.Spec.point("changed")),new DeleteNode(new OperationPath(b,y)))).requireSuccess();
            s.restore(snap); var restored=s.snapshot();
            assertEquals(snap.pages(),restored.pages()); assertEquals(snap.ownership(),restored.ownership()); assertEquals(snap.pageRules(),restored.pageRules()); assertEquals(snap.interaction(),restored.interaction());
            assertEquals(snap.sourceVersion(),restored.sourceVersion(),"Restoring advances the live revision, not the historic source version");
            assertTrue(s.model().version()>oldVersion); assertTrue(s.model().epoch()>oldEpoch);
            assertEquals(1,s.model().pages().get(p.pageId()).parts().size());
            assertThrows(UnsupportedOperationException.class,()->snap.pages().clear());
        }
    }
    @Test void restoreNeverReusesAllocatedOrReservedNodeAndPageIds() {
        try(var s=new DefaultVisualizationSession()) {
            var r=s.initializeRoot(BuiltinPageTypes.point()); var a=add(s,r,null,"a"); var snap=s.snapshot();
            var reserved=s.reserveNodeId(r); var discarded=s.initializePage(BuiltinPageTypes.point(),a);
            s.restore(snap);
            assertTrue(s.reserveNodeId(r).nodeId()>reserved.nodeId());
            assertTrue(s.initializePage(BuiltinPageTypes.point(),a).pageId()>discarded.pageId());
            assertFalse(s.modify(MutationBatch.of(new AddNode(new OperationPath(null,reserved),ViewNode.Spec.point("stale")))).succeeded());
        }
    }
    @Test void restoredEmptyRuleStillBindsNewParentsAndReadyNodes() {
        try(var s=new DefaultVisualizationSession()) {
            var r=s.initializeRoot(BuiltinPageTypes.point()); var a=add(s,r,null,"a");
            var p=s.initializePage(BuiltinPageTypes.graph(true),PageBindingRule.Spec.page(r)); var snap=s.snapshot();
            var rule=s.model().pageRules().values().iterator().next(); s.modify(MutationBatch.of(new UnbindPage(rule.id()))).requireSuccess();
            s.restore(snap); var b=add(s,r,null,"b"); var x=add(s,p,a,"x"); var y=add(s,p,a,"y");
            assertEquals(Set.of(a,b),Set.copyOf(s.model().node(y).parents().parents()));
            assertEquals(Set.of(y.nodeId()),s.model().pages().get(p.pageId()).ready()); assertNotNull(s.model().node(x));
        }
    }
    @Test void foreignSnapshotAndDanglingOwnershipFailBeforePublication() {
        try(var s=new DefaultVisualizationSession();var other=new DefaultVisualizationSession()) {
            var r=s.initializeRoot(BuiltinPageTypes.point()); var a=add(s,r,null,"a"); var p=s.initializePage(BuiltinPageTypes.point(),a); add(s,p,a,"x");
            var before=s.model(); assertThrows(IllegalArgumentException.class,()->s.restore(other.snapshot())); assertSame(before,s.model());
            var valid=s.snapshot();
            var broken=new VisualizationSnapshot(valid.containerId(),valid.root(),valid.pages(),Map.of(),valid.pageRules(),valid.interaction(),valid.sourceVersion(),valid.epoch(),valid.sourceStep(),valid.highWater());
            assertThrows(RuntimeException.class,()->s.restore(broken)); assertSame(before,s.model());
        }
    }
    @Test void snapshotPreservesCompositionWithNoMutableViewNodes() {
        try(var s=new DefaultVisualizationSession()) {
            var r=s.initializeRoot(BuiltinPageTypes.array(1)); var a=s.reserveNodeId(r); s.addNode(new OperationPath(null,a),new ViewNode.Spec(ViewNode.Kind.ARRAY,"a"));
            var b=add(s,r,null,"b"); s.modify(MutationBatch.of(new Compose(a,b,0))).requireSuccess();
            var snap=s.snapshot(); s.modify(MutationBatch.of(new DeleteNode(new OperationPath(null,a)))).requireSuccess(); s.restore(snap);
            assertEquals(List.of(b),s.model().node(a).children());
            assertEquals(VisualizationSnapshot.NodeState.class,snap.pages().get(r.pageId()).nodes().get(a.nodeId()).getClass());
        }
    }
}
