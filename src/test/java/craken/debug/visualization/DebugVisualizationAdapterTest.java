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
