package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrType;
import craken.debug.visualization.*;
import craken.visualization.api.*;
import craken.visualization.model.ViewNode;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.List;

import static craken.debug.visualization.DebugMemoryReader.ScalarType.*;
import static craken.debug.visualization.DebugStructureDescriptor.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
@Timeout(30)
final class DebugVisualizationAdapterTest {
    @Test void aLargerPhysicalAllocationCannotLegitimizeOverlappingArrayElementSchemas() {
        for(int length:new int[]{0,2}) {
            Fixture f=new Fixture();f.types.register(BuiltinPageTypes.array());
            f.adapter.registerDescriptor(new DebugStructureDescriptor("large","array-0",ViewKind.POINT,8,
                    List.of(new Field("value",0,SIGNED64)),List.of(),null,ReallocationPolicy.RECREATE));
            f.adapter.registerDescriptor(new DebugStructureDescriptor("array","array-0",ViewKind.ARRAY,8,
                    List.of(),List.of(),new ArrayLayout(0,length,4,"large"),ReallocationPolicy.RECREATE));
            long address=f.runtime.allocateZeroed(16,8,"heap","larger allocation");f.adapter.registerRoot(new RootAddress("array",address));
            var state=f.runtime.snapshot();var result=f.project(0);
            assertFalse(result.accepted(),"Each element requires 8 bytes but the declared stride is 4, including an empty array");
            assertTrue(result.diagnostic().contains("stride"),result.diagnostic());assertTrue(f.session.model().pages().isEmpty());
            assertTrue(f.adapter.locations().isEmpty());assertEquals(state,f.runtime.snapshot());
        }
    }
    @Test void explicitlySharedNodePortsMergeOppositeDirectionsWithoutChangingEdgeIdentity() {
        Fixture f=new Fixture();f.types.register(BuiltinPageTypes.graph(true));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("graph","directed-graph",ViewKind.GRAPH,8,List.of(),
                List.of(new Reference("next",0,"graph",Relation.TOPOLOGY,Direction.FORWARD,"node","node")),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(8,8,"heap","a"),b=f.runtime.allocateZeroed(8,8,"heap","b");
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,b));f.runtime.write(b,DebugRuntime.Value.of(IrType.POINTER,a));
        f.adapter.registerRoot(new RootAddress("graph",a));var result=f.project(0);assertTrue(result.accepted(),result.diagnostic());
        var page=f.location(a,"graph").page();var edges=f.session.model().pages().get(page.pageId()).topology();assertEquals(1,edges.size());
        var first=edges.values().iterator().next();assertEquals(craken.visualization.model.relation.TopologyEdge.Direction.BOTH,first.direction());
        f.runtime.write(b,DebugRuntime.Value.of(IrType.POINTER,0));result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        var remaining=f.session.model().pages().get(page.pageId()).topology().values().iterator().next();
        assertEquals(first.id(),remaining.id());assertEquals(craken.visualization.model.relation.TopologyEdge.Direction.FORWARD,remaining.direction());
    }
    @Test void reciprocalPointerFieldsKeepTheirIndependentDirectedEdges() {
        Fixture f=new Fixture();f.types.register(BuiltinPageTypes.graph(true));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("graph","directed-graph",ViewKind.GRAPH,8,List.of(),
                List.of(new Reference("next",0,"graph",Relation.TOPOLOGY,Direction.FORWARD)),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(8,8,"heap","a"),b=f.runtime.allocateZeroed(8,8,"heap","b");
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,b));f.runtime.write(b,DebugRuntime.Value.of(IrType.POINTER,a));
        f.adapter.registerRoot(new RootAddress("graph",a));var result=f.project(0);assertTrue(result.accepted(),result.diagnostic());
        var edges=f.session.model().pages().get(f.location(a,"graph").pageId()).topology().values();
        assertEquals(2,edges.size(),"Each pointer field owns its own concrete port pair");
        assertTrue(edges.stream().anyMatch(edge->edge.direction()==craken.visualization.model.relation.TopologyEdge.Direction.FORWARD));
        assertTrue(edges.stream().anyMatch(edge->edge.direction()==craken.visualization.model.relation.TopologyEdge.Direction.BACKWARD));
    }
    @Test void anUnchangedBackwardCanonicalEdgeRetainsItsIdentityAcrossStops() {
        Fixture f=new Fixture();f.types.register(BuiltinPageTypes.graph(true));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("graph","directed-graph",ViewKind.GRAPH,8,List.of(),
                List.of(new Reference("next",0,"graph",Relation.TOPOLOGY,Direction.FORWARD)),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(8,8,"heap","a"),b=f.runtime.allocateZeroed(8,8,"heap","b");
        f.runtime.write(b,DebugRuntime.Value.of(IrType.POINTER,a));f.adapter.registerRoot(new RootAddress("graph",a));f.adapter.registerRoot(new RootAddress("graph",b));
        assertTrue(f.project(0).accepted());var page=f.location(a,"graph").page();
        var edges=f.session.model().pages().get(page.pageId()).topology();
        var result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        assertEquals(edges,f.session.model().pages().get(page.pageId()).topology(),"No topology event occurred");
    }
    @Test void twoPointerFieldsToTheSameTargetDoNotOverwriteOneAnother() {
        Fixture f=new Fixture();f.types.register(BuiltinPageTypes.graph(true));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("graph","directed-graph",ViewKind.GRAPH,16,List.of(),
                List.of(new Reference("left",0,"graph",Relation.TOPOLOGY,Direction.FORWARD),
                        new Reference("right",8,"graph",Relation.TOPOLOGY,Direction.FORWARD)),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(16,8,"heap","a"),b=f.runtime.allocateZeroed(16,8,"heap","b");
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,b));f.runtime.write(a+8,DebugRuntime.Value.of(IrType.POINTER,b));
        f.adapter.registerRoot(new RootAddress("graph",a));var result=f.project(0);assertTrue(result.accepted(),result.diagnostic());
        var source=f.location(a,"graph");var edges=f.session.model().pages().get(source.pageId()).topology().values();
        assertEquals(2,edges.size());assertEquals(java.util.Set.of("field:left","field:right"),
                edges.stream().map(edge->edge.aPort()).collect(java.util.stream.Collectors.toSet()));
        assertEquals("0x"+Long.toUnsignedString(b,16).toUpperCase(java.util.Locale.ROOT),f.session.model().node(source).content().fields().get("left"));
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,0));result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        edges=f.session.model().pages().get(source.pageId()).topology().values();assertEquals(1,edges.size());assertEquals("field:right",edges.iterator().next().aPort());
    }
    @Test void preservingAnArrayWrapperWhileRecreatingItsReallocatedMembersReplacesOccupiedSlots() {
        Fixture f=new Fixture();f.types.register(BuiltinPageTypes.array());
        f.adapter.registerDescriptor(new DebugStructureDescriptor("int","array-0",ViewKind.POINT,4,
                List.of(new Field("value",0,SIGNED32)),List.of(),null,ReallocationPolicy.RECREATE));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("array","array-0",ViewKind.ARRAY,12,
                List.of(),List.of(),new ArrayLayout(0,3,4,"int"),ReallocationPolicy.PRESERVE_LOCATION));
        long a=f.runtime.allocateZeroed(12,4,"heap","array");for(int i=0;i<3;i++)f.runtime.write(a+i*4,DebugRuntime.Value.of(IrType.INT,i+1));
        f.adapter.registerRoot(new RootAddress("array",a));assertTrue(f.project(0).accepted());
        var wrapper=f.location(a,"array");var first=f.location(a,"int");var previous=f.adapter.publishedSnapshot();
        long resized=f.runtime.reallocate(a,24);var state=f.runtime.snapshot();var result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        assertEquals(wrapper,f.location(resized,"array"));assertNotEquals(first,f.location(resized,"int"));
        var page=f.session.model().pages().get(wrapper.pageId());assertEquals(3,page.composition().size());assertEquals(4,page.nodes().size());
        assertEquals("1",f.session.model().node(f.location(resized,"int")).content().fields().get("value"));assertEquals(state,f.runtime.snapshot());
        assertEquals(4,previous.pages().get(wrapper.pageId()).nodes().size(),"The old history frame stays immutable");
    }
    @Test
    void aManuallyRegisteredSingletonExpandsItsTreeWithAnUpstreamForEveryTreeNode() {
        Fixture f = new Fixture();
        long singleton = f.runtime.allocateZeroed(8, 8, "heap", "singleton");
        long root = f.runtime.allocateZeroed(24, 8, "heap", "root");
        long child = f.runtime.allocateZeroed(24, 8, "heap", "child");
        f.runtime.write(singleton, DebugRuntime.Value.of(IrType.POINTER, root));
        f.runtime.write(root, DebugRuntime.Value.of(IrType.INT, 7));
        f.runtime.write(root + 8, DebugRuntime.Value.of(IrType.POINTER, child));
        f.runtime.write(child, DebugRuntime.Value.of(IrType.INT, 9));
        f.registerTree(singleton);
        var result = f.project(0);
        assertTrue(result.accepted(), result.diagnostic());
        assertEquals(2, f.session.model().pages().size());
        ViewLocation owner = f.location(singleton, "singleton"), a = f.location(root, "tree"), b = f.location(child, "tree");
        assertEquals("7", f.session.model().node(a).content().fields().get("value"));
        assertEquals(List.of(owner), f.session.model().node(a).parents().parents());
        assertEquals(List.of(owner), f.session.model().node(b).parents().parents());
        assertEquals(1, f.session.model().pages().get(a.pageId()).topology().size());
        assertTrue(f.session.model().pages().get(a.pageId()).ready().isEmpty());
    }

    @Test
    void scalarArrayMembersShareACompositePageAndDoNotAliasTheirWrapper() {
        Fixture f = new Fixture();
        f.types.register(BuiltinPageTypes.array());
        f.adapter.registerDescriptor(new DebugStructureDescriptor("int", "array-0", ViewKind.POINT, 4,
                List.of(new Field("value", 0, SIGNED32)), List.of(), null, ReallocationPolicy.RECREATE));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("array", "array-0", ViewKind.ARRAY, 12,
                List.of(), List.of(), new ArrayLayout(0, 3, 4, "int"), ReallocationPolicy.RECREATE));
        long address = f.runtime.allocateZeroed(12, 4, "heap", "array");
        for (int i = 0; i < 3; i++) f.runtime.write(address + i * 4, DebugRuntime.Value.of(IrType.INT, i + 1));
        f.adapter.registerRoot(new RootAddress("array", address));
        assertTrue(f.project(0).accepted());
        var page = f.session.model().pages().values().iterator().next();
        assertEquals(4, page.nodes().size());
        assertEquals(3, page.composition().size());
        assertEquals(1, page.parts().size());
        assertNotEquals(f.location(address, "array"), f.location(address, "int"));
    }

    @Test
    void sharedOwnershipAddsTwoReferencesToOneExistingChildInsteadOfAllocatingItTwice() {
        Fixture f = new Fixture();
        long a = f.runtime.allocateZeroed(8, 8, "heap", "a"), b = f.runtime.allocateZeroed(8, 8, "heap", "b");
        long tree = f.runtime.allocateZeroed(24, 8, "heap", "tree");
        f.runtime.write(a, DebugRuntime.Value.of(IrType.POINTER, tree));
        f.runtime.write(b, DebugRuntime.Value.of(IrType.POINTER, tree));
        f.registerTree(a);
        f.adapter.registerRoot(new RootAddress("singleton", b));
        assertTrue(f.project(0).accepted());
        ViewLocation child = f.location(tree, "tree");
        assertEquals(2, f.session.model().node(child).parents().parents().size());
        assertEquals(3, f.adapter.locations().size());
        assertEquals(2, f.session.model().ownership().size());
    }

    @Test
    void actualReadsAndSameValueWritesHighlightTheNodeAndAContextIsConsumedOnlyOnce() {
        Fixture f = new Fixture();
        long singleton = f.runtime.allocateZeroed(8, 8, "heap", "singleton"), tree = f.runtime.allocateZeroed(24, 8, "heap", "tree");
        f.runtime.write(singleton, DebugRuntime.Value.of(IrType.POINTER, tree));
        f.runtime.write(tree, DebugRuntime.Value.of(IrType.INT, 7));
        f.registerTree(singleton);
        assertTrue(f.project(0).accepted());
        ViewLocation child = f.location(tree, "tree");
        f.runtime.read(tree, IrType.INT);
        var read = f.project(1);
        assertTrue(read.accepted());
        assertEquals(AccessKind.READ, f.session.model().interaction().accessKind());
        assertEquals(child, f.session.model().focus());
        f.runtime.write(tree, DebugRuntime.Value.of(IrType.INT, 7));
        RuntimeEventBatch written = f.events.drain(2);
        var state = f.runtime.snapshot();
        var write = f.adapter.project(2, state, written);
        assertTrue(write.accepted());
        assertEquals(AccessKind.WRITE, f.session.model().interaction().accessKind());
        assertEquals(child, f.session.model().interaction().accessed());
        var snapshot = f.adapter.publishedSnapshot();
        assertSame(write, f.adapter.project(2, state, written));
        assertEquals(snapshot, f.adapter.publishedSnapshot());
        long temporary = f.runtime.allocate(4, "heap", "temporary");
        f.runtime.release(temporary);
        var transientStep = f.project(3);
        assertEquals(2, transientStep.events().events().size());
        assertEquals(2, f.adapter.locations().size());
    }

    @Test
    void explicitAllocationRegistrationShowsReadyNodesUntilTheyAreConnected() {
        Fixture f = new Fixture();
        long singleton = f.runtime.allocateZeroed(8, 8, "heap", "singleton"), tree = f.runtime.allocateZeroed(24, 8, "heap", "tree");
        f.runtime.write(singleton, DebugRuntime.Value.of(IrType.POINTER, tree));
        f.registerTree(singleton);
        assertTrue(f.project(0).accepted());
        ViewLocation owner = f.location(singleton, "singleton"), root = f.location(tree, "tree");
        long fresh = f.runtime.allocateZeroed(24, 8, "heap", "fresh");
        DebugMemoryReader memory = new DebugMemoryReader(f.runtime.snapshot());
        f.adapter.registerObject("tree", memory.resolve(fresh), root.page(), owner);
        assertTrue(f.project(1).accepted());
        ViewLocation ready = f.location(fresh, "tree");
        assertTrue(f.session.model().pages().get(root.pageId()).ready().contains(ready.nodeId()));
        f.runtime.write(tree + 8, DebugRuntime.Value.of(IrType.POINTER, fresh));
        assertTrue(f.project(2).accepted());
        assertEquals(ready, f.location(fresh, "tree"));
        assertFalse(f.session.model().pages().get(root.pageId()).ready().contains(ready.nodeId()));
    }

    @Test
    void illegalOwnershipExpansionKeepsTheLastFrameAndDoesNotAffectTheVm() {
        Fixture f = new Fixture();
        long a = f.runtime.allocateZeroed(8, 8, "heap", "a"), b = f.runtime.allocateZeroed(8, 8, "heap", "b");
        f.runtime.write(a, DebugRuntime.Value.of(IrType.POINTER, b));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("owner", "point", ViewKind.POINT, 8,
                List.of(), List.of(new Reference("child", 0, "owner", Relation.OWNERSHIP, Direction.NONE)), null, ReallocationPolicy.RECREATE));
        f.adapter.registerRoot(new RootAddress("owner", a));
        assertTrue(f.project(0).accepted());
        var published = f.adapter.publishedSnapshot();
        var oldModel = f.session.model();
        f.runtime.write(b, DebugRuntime.Value.of(IrType.POINTER, a));
        var state = f.runtime.snapshot();
        var rejected = f.project(1);
        assertFalse(rejected.accepted());
        assertFalse(rejected.diagnostic().isBlank());
        assertSame(published, f.adapter.publishedSnapshot());
        assertEquals(oldModel.pages(), f.session.model().pages());
        assertEquals(oldModel.ownership(), f.session.model().ownership());
        assertEquals(oldModel.interaction(), f.session.model().interaction());
        assertEquals(state, f.runtime.snapshot());
    }

    @Test
    void addressReuseRequiresAnObservedRewriteBeforeTheOldPointerCanExpandANewGeneration() {
        Fixture f = new Fixture();
        f.registerTreeDescriptors();
        f.adapter.registerRoot(new RootAddress("singleton", 200));
        var initial = new DebugMemoryReader(List.of(pointerBlock(10, 200, 100), treeBlock(1, 100, 7)));
        var first = f.adapter.project(0, initial, batch(0,
                allocated(1, 10, 200, 8), allocated(2, 1, 100, 24), writePointer(3, 10, 200)));
        assertTrue(first.accepted());
        ViewLocation old = f.adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(1, 0, "tree"));
        var reused = new DebugMemoryReader(List.of(pointerBlock(10, 200, 100), treeBlock(2, 100, 9)));
        assertTrue(f.adapter.project(1, reused, batch(1,
                new RuntimeEvent.Released(4, new RuntimeEvent.MemoryRange(1, 100, 24)), allocated(5, 2, 100, 24))).accepted());
        assertEquals(1, f.adapter.locations().size(), "Untouched dangling pointer must not adopt the new allocation");
        assertTrue(f.adapter.project(2, reused, batch(2, writePointer(6, 10, 200))).accepted());
        ViewLocation fresh = f.adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(2, 0, "tree"));
        assertNotNull(fresh);
        assertNotEquals(old, fresh);
        var replacedAgain = new DebugMemoryReader(List.of(pointerBlock(10, 200, 100), treeBlock(3, 100, 11)));
        assertTrue(f.adapter.project(3, replacedAgain, batch(3, writePointer(7, 10, 200),
                new RuntimeEvent.Released(8, new RuntimeEvent.MemoryRange(2, 100, 24)), allocated(9, 3, 100, 24))).accepted());
        assertEquals(1, f.adapter.locations().size(), "A pointer written before free must retain that old generation within the same interval");
    }

    @Test
    void intervalsWithoutMemoryOperationsDoNotInventAWriteHighlight() {
        Fixture f=new Fixture();
        long singleton=f.runtime.allocateZeroed(8,8,"heap","singleton"), tree=f.runtime.allocateZeroed(24,8,"heap","tree");
        f.runtime.write(singleton,DebugRuntime.Value.of(IrType.POINTER,tree)); f.registerTree(singleton); f.project(0);
        f.runtime.read(tree,IrType.INT); f.project(1);
        var before=f.session.model().interaction();
        assertTrue(f.project(2).accepted());
        assertEquals(before,f.session.model().interaction());
    }

    @Test
    void uninitializingAPointerRemovesItsPreviousExpansion() {
        Fixture f=new Fixture(); f.registerTreeDescriptors(); f.adapter.registerRoot(new RootAddress("singleton",200));
        var initialized=new DebugMemoryReader(List.of(pointerBlock(10,200,100),treeBlock(1,100,7)));
        assertTrue(f.adapter.project(0,initialized,batch(0,allocated(1,10,200,8),allocated(2,1,100,24),writePointer(3,10,200))).accepted());
        var raw=pointerBlock(10,200,100);
        var uninitialized=new DebugMemoryReader(List.of(new DebugRuntime.MemoryBlock(200,8,"pointer",raw.bytes(),0,10,""),treeBlock(1,100,7)));
        assertTrue(f.adapter.project(1,uninitialized,batch(1,new RuntimeEvent.Accessed(4,new RuntimeEvent.MemoryRange(10,200,8),RuntimeEvent.Access.UNINITIALIZED,"bytes"))).accepted());
        assertEquals(1,f.adapter.locations().size());
    }

    @Test
    void explicitReallocationPolicyDecidesWhetherAViewLocationSurvives() {
        for(var policy:ReallocationPolicy.values()) {
            Fixture f=new Fixture();
            f.adapter.registerDescriptor(new DebugStructureDescriptor("value","point",ViewKind.POINT,4,
                    List.of(new Field("value",0,SIGNED32)),List.of(),null,policy));
            f.adapter.registerRoot(new RootAddress("value",100));
            var a=new DebugMemoryReader(List.of(valueBlock(1,100,7)));
            assertTrue(f.adapter.project(0,a,batch(0,allocated(1,1,100,4))).accepted());
            var old=f.adapter.locations().values().iterator().next();
            var b=new DebugMemoryReader(List.of(valueBlock(2,300,9)));
            var result=f.adapter.project(1,b,batch(1,allocated(2,2,300,4),
                    new RuntimeEvent.Copied(3,new RuntimeEvent.MemoryRange(1,100,4),new RuntimeEvent.MemoryRange(2,300,4)),
                    new RuntimeEvent.Released(4,new RuntimeEvent.MemoryRange(1,100,4)),
                    new RuntimeEvent.Reallocated(5,new RuntimeEvent.MemoryRange(1,100,4),new RuntimeEvent.MemoryRange(2,300,4),4)));
            assertTrue(result.accepted(),result.diagnostic());
            assertEquals(1,f.adapter.locations().size());
            var next=f.adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(2,0,"value"));
            assertNotNull(next);
            if(policy==ReallocationPolicy.PRESERVE_LOCATION)assertEquals(old,next); else assertNotEquals(old,next);
            assertEquals("9",f.session.model().node(next).content().fields().get("value"));
        }
    }

    @Test
    void malformedLaterExpansionRollsBackAllocationsMappingsAndSelection() {
        Fixture f=new Fixture();
        long singleton=f.runtime.allocateZeroed(8,8,"heap","singleton"), tree=f.runtime.allocateZeroed(24,8,"heap","tree");
        f.runtime.write(singleton,DebugRuntime.Value.of(IrType.POINTER,tree)); f.registerTree(singleton); f.project(0);
        var before=f.session.model(); var mappings=f.adapter.locations(); var published=f.adapter.publishedSnapshot();
        long tooSmall=f.runtime.allocateZeroed(4,4,"heap","too-small");
        f.runtime.write(tree+8,DebugRuntime.Value.of(IrType.POINTER,tooSmall));
        assertFalse(f.project(1).accepted());
        assertEquals(before.pages(),f.session.model().pages()); assertEquals(mappings,f.adapter.locations());
        assertSame(published,f.adapter.publishedSnapshot());
    }
    @Test void aStalePointerDoesNotInterpretASmallerReplacementAllocationAsTheOldType() {
        Fixture f=new Fixture();f.registerTreeDescriptors();f.adapter.registerRoot(new RootAddress("singleton",200));
        var initial=new DebugMemoryReader(List.of(pointerBlock(10,200,100),treeBlock(1,100,7)));
        assertTrue(f.adapter.project(0,initial,batch(0,allocated(1,10,200,8),allocated(2,1,100,24),writePointer(3,10,200))).accepted());
        var reused=new DebugMemoryReader(List.of(pointerBlock(10,200,100),valueBlock(2,100,9)));
        var result=f.adapter.project(1,reused,batch(1,new RuntimeEvent.Released(4,new RuntimeEvent.MemoryRange(1,100,24)),
                allocated(5,2,100,4)));
        assertTrue(result.accepted(),result.diagnostic());assertEquals(1,f.adapter.locations().size());
        var rewritten=f.adapter.project(2,reused,batch(2,writePointer(6,10,200)));
        assertFalse(rewritten.accepted(),"An actual rewrite must still validate the registered tree size");
        assertEquals(1,f.adapter.locations().size());
    }
    @Test void aRootEntryAndAManualObjectWithTheSameIdentityReuseTheAlreadyOwnedNode() {
        Fixture f=new Fixture();
        long singleton=f.runtime.allocateZeroed(8,8,"heap","singleton"),tree=f.runtime.allocateZeroed(24,8,"heap","tree");
        f.runtime.write(singleton,DebugRuntime.Value.of(IrType.POINTER,tree));f.registerTree(singleton);f.project(0);
        var owner=f.location(singleton,"singleton");var root=f.location(tree,"tree");
        var address=new DebugMemoryReader(f.runtime.snapshot()).resolve(tree);
        f.adapter.registerObject("tree",address,root.page(),owner);f.adapter.registerRoot(new RootAddress("tree",tree));
        f.runtime.write(tree,DebugRuntime.Value.of(IrType.INT,9));
        var result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        assertEquals(root,f.location(tree,"tree"));assertEquals(2,f.adapter.locations().size());
        assertEquals(1,f.session.model().node(root).parents().parents().size());
        assertEquals(2,f.session.model().ownership().values().iterator().next().sources().size(),
                "Manual and pointer contributions share one effective ownership binding");
    }
    @Test void removingAnEntireOwnershipSubgraphUsesLiveOperationPathsUntilTheAtomicCommit() {
        Fixture f=new Fixture();
        f.adapter.registerDescriptor(new DebugStructureDescriptor("owner","point",ViewKind.POINT,8,List.of(),
                List.of(new Reference("child",0,"owner",Relation.OWNERSHIP,Direction.NONE)),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(8,8,"heap","a"),b=f.runtime.allocateZeroed(8,8,"heap","b"),c=f.runtime.allocateZeroed(8,8,"heap","c");
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,b));f.runtime.write(b,DebugRuntime.Value.of(IrType.POINTER,c));
        f.adapter.registerRoot(new RootAddress("owner",a));
        assertTrue(f.project(0).accepted());assertEquals(3,f.adapter.locations().size());
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,0));var state=f.runtime.snapshot();
        var result=f.project(1);
        assertTrue(result.accepted(),result.diagnostic());assertEquals(1,f.adapter.locations().size());
        assertEquals(1,f.session.model().pages().size());assertEquals(state,f.runtime.snapshot());
        assertEquals(3,state.heap().size(),"Removing a visualization subgraph never frees VM memory");
    }

    @Test void manuallyRegisteredReadyObjectsCanHaveSeveralIndependentUpstreams() {
        Fixture f=new Fixture();
        long a=f.runtime.allocateZeroed(8,8,"heap","a"),b=f.runtime.allocateZeroed(8,8,"heap","b");
        long root=f.runtime.allocateZeroed(24,8,"heap","tree");
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,root));f.registerTree(a);f.adapter.registerRoot(new RootAddress("singleton",b));
        assertTrue(f.project(0).accepted());
        long fresh=f.runtime.allocateZeroed(24,8,"heap","fresh");
        var address=new DebugMemoryReader(f.runtime.snapshot()).resolve(fresh);
        var page=f.location(root,"tree").page();var ownerA=f.location(a,"singleton");var ownerB=f.location(b,"singleton");
        f.adapter.registerObject("tree",address,page,ownerA);f.adapter.registerObject("tree",address,page,ownerB);
        assertTrue(f.project(1).accepted());
        var child=f.location(fresh,"tree");
        assertEquals(java.util.Set.of(ownerA,ownerB),java.util.Set.copyOf(f.session.model().node(child).parents().parents()));
        assertTrue(f.session.model().pages().get(page.pageId()).ready().contains(child.nodeId()));
        f.runtime.release(a);var surviving=f.project(2);assertTrue(surviving.accepted(),surviving.diagnostic());
        assertEquals(List.of(ownerB),f.session.model().node(child).parents().parents());
        assertTrue(f.project(3).accepted(),"A removed manual parent must not poison later projections");
    }
    @Test void releasingTheLastManualParentDoesNotPermanentlyPoisonLaterFrames() {
        Fixture f=new Fixture();long owner=f.runtime.allocateZeroed(8,8,"heap","owner"),tree=f.runtime.allocateZeroed(24,8,"heap","tree");
        f.runtime.write(owner,DebugRuntime.Value.of(IrType.POINTER,tree));f.registerTree(owner);assertTrue(f.project(0).accepted());
        long fresh=f.runtime.allocateZeroed(24,8,"heap","fresh");var reader=new DebugMemoryReader(f.runtime.snapshot());
        f.adapter.registerObject("tree",reader.resolve(fresh),f.location(tree,"tree").page(),f.location(owner,"singleton"));
        assertTrue(f.project(1).accepted());f.runtime.release(owner);assertTrue(f.project(2).accepted());
        var next=f.project(3);assertTrue(next.accepted(),next.diagnostic());assertTrue(f.adapter.locations().isEmpty());
        assertEquals(2,f.runtime.snapshot().heap().size(),"Visual parent release never frees unrelated VM allocations");
    }
    @Test void severalReallocationsWithinOneIntervalPreserveOnlyTheFinalGenerationLocation() {
        Fixture f=new Fixture();f.adapter.registerDescriptor(new DebugStructureDescriptor("value","point",ViewKind.POINT,4,
                List.of(new Field("value",0,SIGNED32)),List.of(),null,ReallocationPolicy.PRESERVE_LOCATION));
        f.adapter.registerRoot(new RootAddress("value",100));
        assertTrue(f.adapter.project(0,new DebugMemoryReader(List.of(valueBlock(1,100,7))),batch(0,allocated(1,1,100,4))).accepted());
        var original=f.adapter.locations().values().iterator().next();
        var result=f.adapter.project(1,new DebugMemoryReader(List.of(valueBlock(3,500,11))),batch(1,
                allocated(2,2,300,4),new RuntimeEvent.Copied(3,new RuntimeEvent.MemoryRange(1,100,4),new RuntimeEvent.MemoryRange(2,300,4)),
                new RuntimeEvent.Released(4,new RuntimeEvent.MemoryRange(1,100,4)),new RuntimeEvent.Reallocated(5,new RuntimeEvent.MemoryRange(1,100,4),new RuntimeEvent.MemoryRange(2,300,4),4),
                allocated(6,3,500,4),new RuntimeEvent.Copied(7,new RuntimeEvent.MemoryRange(2,300,4),new RuntimeEvent.MemoryRange(3,500,4)),
                new RuntimeEvent.Released(8,new RuntimeEvent.MemoryRange(2,300,4)),new RuntimeEvent.Reallocated(9,new RuntimeEvent.MemoryRange(2,300,4),new RuntimeEvent.MemoryRange(3,500,4),4)));
        assertTrue(result.accepted(),result.diagnostic());
        assertEquals(java.util.Map.of(new DebugObjectIdentityRegistry.ObjectKey(3,0,"value"),original),f.adapter.locations());
        assertEquals("11",f.session.model().node(original).content().fields().get("value"));
    }
    @Test void aRejectedModelStillRemembersSuccessfulPointerWritesAndTheirAllocationGenerations() {
        Fixture f=new Fixture();
        f.adapter.registerDescriptor(new DebugStructureDescriptor("value","point",ViewKind.POINT,4,List.of(new Field("value",0,SIGNED32)),List.of(),null,ReallocationPolicy.RECREATE));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("targetSlot","point",ViewKind.POINT,8,List.of(),
                List.of(new Reference("target",0,"value",Relation.OWNERSHIP,Direction.NONE)),null,ReallocationPolicy.RECREATE));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("cycleSlot","point",ViewKind.POINT,8,List.of(),
                List.of(new Reference("target",0,"owner",Relation.OWNERSHIP,Direction.NONE)),null,ReallocationPolicy.RECREATE));
        f.adapter.registerDescriptor(new DebugStructureDescriptor("owner","point",ViewKind.POINT,8,List.of(),
                List.of(new Reference("child",0,"owner",Relation.OWNERSHIP,Direction.NONE)),null,ReallocationPolicy.RECREATE));
        f.adapter.registerRoot(new RootAddress("targetSlot",200));f.adapter.registerRoot(new RootAddress("cycleSlot",400));
        var before=new DebugMemoryReader(List.of(pointerBlock(10,200,100),valueBlock(1,100,7),pointerBlock(20,400,300),pointerBlock(30,300,500),pointerBlock(50,500,0)));
        assertTrue(f.adapter.project(0,before,batch(0,allocated(1,10,200,8),allocated(2,1,100,4),allocated(3,20,400,8),
                allocated(4,30,300,8),allocated(5,50,500,8),writePointer(6,10,200),writePointer(7,20,400),writePointer(8,30,300))).accepted());
        var published=f.adapter.publishedSnapshot();
        var cyclic=new DebugMemoryReader(List.of(pointerBlock(10,200,100),valueBlock(2,100,9),pointerBlock(20,400,300),pointerBlock(30,300,500),pointerBlock(50,500,300)));
        assertFalse(f.adapter.project(1,cyclic,batch(1,new RuntimeEvent.Released(9,new RuntimeEvent.MemoryRange(1,100,4)),
                allocated(10,2,100,4),writePointer(11,10,200),writePointer(12,50,500))).accepted());
        assertSame(published,f.adapter.publishedSnapshot());
        var recovered=new DebugMemoryReader(List.of(pointerBlock(10,200,100),valueBlock(2,100,9),pointerBlock(20,400,300),pointerBlock(30,300,500),pointerBlock(50,500,0)));
        var result=f.adapter.project(2,recovered,batch(2,writePointer(13,50,500)));
        assertTrue(result.accepted(),result.diagnostic());
        assertNotNull(f.adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(2,0,"value")),
                "A real rewrite in a rejected frame must survive even when the next frame does not rewrite this pointer");
        assertEquals(5,f.adapter.locations().size());
    }
    @Test void closedAdaptersRejectRegistrationAndCannotAccumulateNewOwnedState() {
        Fixture f=new Fixture();f.adapter.close();
        assertThrows(IllegalStateException.class,f::registerTreeDescriptors);
        assertThrows(IllegalStateException.class,()->f.adapter.registerRoot(new RootAddress("value",100)));
        assertThrows(IllegalStateException.class,()->f.adapter.registerObject("value",new DebugMemoryReader.Address(1,0),new PageRef(f.session.model().id(),1),new ViewLocation(f.session.model().id(),1,1)));
        assertDoesNotThrow(f.adapter::close);assertTrue(f.adapter.locations().isEmpty());
    }

    @Test void releasesWithinOneStopNavigateInActualVmOrderRatherThanRegistryInsertionOrder() {
        Fixture f=new Fixture();
        long a=f.runtime.allocateZeroed(8,8,"heap","a"),b=f.runtime.allocateZeroed(8,8,"heap","b");
        long treeA=f.runtime.allocateZeroed(24,8,"heap","tree-a"),treeB=f.runtime.allocateZeroed(24,8,"heap","tree-b");
        f.runtime.write(a,DebugRuntime.Value.of(IrType.POINTER,treeA));f.runtime.write(b,DebugRuntime.Value.of(IrType.POINTER,treeB));
        f.registerTree(a);f.adapter.registerRoot(new RootAddress("singleton",b));assertTrue(f.project(0).accepted());
        var finalOwner=f.location(a,"singleton");
        f.runtime.release(treeB);f.runtime.release(treeA);
        var result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        assertEquals(finalOwner,f.session.model().focus(),"The last actual release belongs to owner a");
        assertEquals(AccessKind.DELETE,f.session.model().interaction().accessKind());
    }

    @Test void aPointerWriteToAKnownButTemporarilyUnreachableObjectKeepsItsNewGeneration() {
        Fixture f=new Fixture();
        f.adapter.registerDescriptor(new DebugStructureDescriptor("owner","point",ViewKind.POINT,8,List.of(),
                List.of(new Reference("child",0,"owner",Relation.OWNERSHIP,Direction.NONE)),null,ReallocationPolicy.RECREATE));
        f.adapter.registerRoot(new RootAddress("owner",200));
        assertTrue(f.adapter.project(0,new DebugMemoryReader(List.of(pointerBlock(10,200,300),pointerBlock(30,300,100),pointerBlock(1,100,0))),
                batch(0,allocated(1,10,200,8),allocated(2,30,300,8),allocated(3,1,100,8),writePointer(4,10,200),writePointer(5,30,300))).accepted());
        var hidden=new DebugMemoryReader(List.of(pointerBlock(10,200,0),pointerBlock(30,300,100),pointerBlock(2,100,0)));
        var interval=f.adapter.project(1,hidden,batch(1,writePointer(6,10,200),new RuntimeEvent.Released(7,new RuntimeEvent.MemoryRange(1,100,8)),
                allocated(8,2,100,8),writePointer(9,30,300)));
        assertTrue(interval.accepted(),interval.diagnostic());assertEquals(1,f.adapter.locations().size());
        var again=new DebugMemoryReader(List.of(pointerBlock(10,200,300),pointerBlock(30,300,100),pointerBlock(2,100,0)));
        var result=f.adapter.project(2,again,batch(2,writePointer(10,10,200)));
        assertTrue(result.accepted(),result.diagnostic());
        assertNotNull(f.adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(2,0,"owner")),
                "The actual write while detached captured generation 2, even without a later rewrite of that field");
        assertEquals(3,f.adapter.locations().size());
    }

    @Test void allocatingANewRootThenReleasingThePreviousRootLeavesTheLastReleaseWithoutFocus() {
        Fixture f=new Fixture();f.adapter.registerDescriptor(new DebugStructureDescriptor("value","point",ViewKind.POINT,4,
                List.of(new Field("value",0,SIGNED32)),List.of(),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(4,4,"heap","a");f.adapter.registerRoot(new RootAddress("value",a));assertTrue(f.project(0).accepted());
        long b=f.runtime.allocateZeroed(4,4,"heap","b");f.adapter.registerRoot(new RootAddress("value",b));f.runtime.release(a);
        var result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        assertEquals(1,f.adapter.locations().size());assertNull(f.session.model().focus());
        assertNull(f.session.model().interaction().accessed());
    }
    @Test void aRootReleaseWithoutUpstreamsDoesNotClearAnUnrelatedFocusWhenAutoNavigationIsDisabled() {
        Fixture f=new Fixture();f.adapter.registerDescriptor(new DebugStructureDescriptor("value","point",ViewKind.POINT,4,
                List.of(new Field("value",0,SIGNED32)),List.of(),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(4,4,"heap","a"),b=f.runtime.allocateZeroed(4,4,"heap","b");
        f.adapter.registerRoot(new RootAddress("value",a));f.adapter.registerRoot(new RootAddress("value",b));assertTrue(f.project(0).accepted());
        var retained=f.location(b,"value");
        assertTrue(f.session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null,retained)),
                new VisualizationCommand.Configure(new VisualizationOptions(false,true)))).succeeded());
        f.runtime.release(a);var result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        assertEquals(retained,f.session.model().focus());assertFalse(f.session.model().interaction().options().autoNavigate());
    }
    @Test void anAccessAfterAParentlessRootReleaseCanEstablishTheLaterFocus() {
        Fixture f=new Fixture();f.adapter.registerDescriptor(new DebugStructureDescriptor("value","point",ViewKind.POINT,4,
                List.of(new Field("value",0,SIGNED32)),List.of(),null,ReallocationPolicy.RECREATE));
        long a=f.runtime.allocateZeroed(4,4,"heap","a"),b=f.runtime.allocateZeroed(4,4,"heap","b");
        f.adapter.registerRoot(new RootAddress("value",a));f.adapter.registerRoot(new RootAddress("value",b));assertTrue(f.project(0).accepted());
        var retained=f.location(b,"value");f.runtime.release(a);f.runtime.write(b,DebugRuntime.Value.of(IrType.INT,7));
        var result=f.project(1);assertTrue(result.accepted(),result.diagnostic());
        assertEquals(retained,f.session.model().focus());assertEquals(AccessKind.WRITE,f.session.model().interaction().accessKind());
    }

    private static final class Fixture {
        final RuntimeEventCollector events = new RuntimeEventCollector();
        final DebugRuntime runtime;
        final DefaultVisualizationSession session = new DefaultVisualizationSession();
        final PageTypeRegistry types = new PageTypeRegistry();
        final DebugVisualizationAdapter adapter = new DebugVisualizationAdapter(session, types);
        Fixture() {
            SourceFile source = new SourceFile("adapter.mc", "");
            runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "", DebugTimeSource.system(),
                    DebugRuntime.DEFAULT_HEAP_CAPACITY, events);
            types.register(BuiltinPageTypes.point());
            types.register(BuiltinPageTypes.tree(false));
        }
        void registerTreeDescriptors() {
            adapter.registerDescriptor(new DebugStructureDescriptor("singleton", "point", ViewKind.POINT, 8,
                    List.of(new Field("root", 0, POINTER)), List.of(new Reference("root", 0, "tree", Relation.OWNERSHIP, Direction.NONE)), null, ReallocationPolicy.RECREATE));
            adapter.registerDescriptor(new DebugStructureDescriptor("tree", "tree", ViewKind.TREE, 24,
                    List.of(new Field("value", 0, SIGNED32)), List.of(new Reference("next", 8, "tree", Relation.TOPOLOGY, Direction.FORWARD)), null, ReallocationPolicy.RECREATE));
        }
        void registerTree(long address) { registerTreeDescriptors(); adapter.registerRoot(new RootAddress("singleton", address)); }
        DebugVisualizationAdapter.ProjectionResult project(int index) { return adapter.project(index, runtime.snapshot(), events.drain(index)); }
        ViewLocation location(long address, String descriptor) {
            var key = new DebugMemoryReader(runtime.snapshot()).resolve(address);
            return adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(key.allocationId(), key.offset(), descriptor));
        }
    }
    private static RuntimeEventBatch batch(int index, RuntimeEvent... events) { return new RuntimeEventBatch(index, true, true, List.of(events), ""); }
    private static RuntimeEvent allocated(long sequence, long id, long address, int size) { return new RuntimeEvent.Allocated(sequence, new RuntimeEvent.MemoryRange(id, address, size), "heap", "fixture"); }
    private static RuntimeEvent writePointer(long sequence, long id, long address) { return new RuntimeEvent.Accessed(sequence, new RuntimeEvent.MemoryRange(id, address, 8), RuntimeEvent.Access.WRITE, "POINTER"); }
    private static DebugRuntime.MemoryBlock pointerBlock(long id, long address, long target) {
        byte[] bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(target).array();
        return new DebugRuntime.MemoryBlock(address, 8, "pointer", HexFormat.of().formatHex(bytes), 8, id, "ff");
    }
    private static DebugRuntime.MemoryBlock treeBlock(long id, long address, int value) {
        byte[] bytes = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
        return new DebugRuntime.MemoryBlock(address, 24, "tree", HexFormat.of().formatHex(bytes), 24, id, "ffffff");
    }
    private static DebugRuntime.MemoryBlock valueBlock(long id,long address,int value) {
        byte[] bytes=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
        return new DebugRuntime.MemoryBlock(address,4,"value",HexFormat.of().formatHex(bytes),4,id,"0f");
    }
}
