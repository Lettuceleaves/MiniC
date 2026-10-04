package craken.debug;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.debug.visualization.*;
import craken.visualization.api.*;
import craken.visualization.model.ViewNode;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.*;
import java.util.*;
import static craken.debug.visualization.DebugMemoryReader.ScalarType.*;
import static craken.debug.visualization.DebugStructureDescriptor.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executable caller example: schemas and pending-object addresses are configured explicitly. */
@Tag("visualization-adapter")
@Timeout(30)
final class DebugVisualizationEndToEndTest {
    private static final String OPERATIONS="""
            #include "stdlib.mh"
            #include "string.mh"
            struct Node { int value; struct Node *next; };
            struct Node *root;
            struct Node *spare;
            int main(void) {
                root = calloc(1, sizeof(struct Node));
                root->value = 7;
                spare = calloc(1, sizeof(struct Node));
                spare->value = 5;
                root->next = spare;
                root->value = 7;
                int observed = root->value;
                memcpy(&spare->value, &root->value, sizeof(int));
                root = realloc(root, 2 * sizeof(struct Node));
                int answer = observed + spare->value;
                root->next = 0;
                free(spare);
                spare = 0;
                free(root);
                root = 0;
                return answer;
            }
            """;
    private static final String CYCLIC_EXPANSION="""
            #include "stdlib.mh"
            struct Node { int value; struct Node *next; };
            struct Node *root;
            int main(void) {
                root = calloc(1, sizeof(struct Node));
                struct Node *tail = calloc(1, sizeof(struct Node));
                root->value = 7;
                tail->value = 5;
                root->next = tail;
                tail->next = root;
                int answer = root->value + tail->value;
                tail->next = 0;
                root->next = 0;
                free(tail);
                free(root);
                root = 0;
                return answer;
            }
            """;

    @Test void registeredNodeProgramPreservesVmParityThroughEveryOperationAndHistoryRoundTrip() {
        Fixture f=new Fixture(OPERATIONS,false);
        List<Debugger.Context> contexts=new ArrayList<>();
        List<DebugVisualizationHistory.Frame> frames=new ArrayList<>();
        contexts.add(f.observed.current());frames.add(f.show(f.observed.current(),true));
        while(f.observed.canNext()) {
            var actual=f.observed.next();var expected=f.control.next();
            assertEquals(expected.stop(),actual.stop());assertEquals(expected.runtime(),actual.runtime());
            contexts.add(actual);var frame=f.show(actual,true);frames.add(frame);
            assertTrue(frame.accepted(),()->"Context "+actual.index()+": "+frame.diagnostic());
        }
        assertEquals(Debugger.Status.COMPLETED,f.observed.current().stop().status(),f.observed.current().stop().error());
        assertEquals(14,f.observed.current().runtime().returnValue().integer());
        assertTrue(f.observed.current().runtime().heap().isEmpty());
        var events=contexts.stream().flatMap(context->context.events().events().stream()).toList();
        assertTrue(events.stream().anyMatch(RuntimeEvent.Allocated.class::isInstance));
        assertTrue(events.stream().anyMatch(RuntimeEvent.Copied.class::isInstance));
        assertTrue(events.stream().anyMatch(RuntimeEvent.Reallocated.class::isInstance));
        assertTrue(events.stream().anyMatch(RuntimeEvent.Released.class::isInstance));
        assertTrue(frames.stream().anyMatch(frame->!frame.snapshot().pages().values().stream().flatMap(page->page.topology().values().stream()).toList().isEmpty()));
        assertTrue(frames.stream().anyMatch(frame->frame.snapshot().pages().values().stream().anyMatch(page->!page.ready().isEmpty())),
                "Explicit pending Node registration appears in READY before connection");
        assertTrue(frames.stream().anyMatch(frame->frame.snapshot().interaction().accessKind()==AccessKind.READ));
        boolean sameWrite=false;
        for(int i=1;i<frames.size();i++) {
            var before=frames.get(i-1).snapshot();var after=frames.get(i).snapshot();
            var accessed=after.interaction().accessed();
            if(after.interaction().accessKind()!=AccessKind.WRITE||accessed==null)continue;
            var a=before.pages().get(accessed.pageId());var b=after.pages().get(accessed.pageId());
            if(a!=null&&b!=null&&a.nodes().containsKey(accessed.nodeId())&&b.nodes().containsKey(accessed.nodeId())
                    &&a.nodes().get(accessed.nodeId()).content().equals(b.nodes().get(accessed.nodeId()).content())
                    &&b.nodes().get(accessed.nodeId()).content().fields().containsKey("value")
                    &&contexts.get(i).events().events().stream().anyMatch(event->event instanceof RuntimeEvent.Accessed access&&access.access()==RuntimeEvent.Access.WRITE))
                sameWrite=true;
        }
        assertTrue(sameWrite,"Actual same-value field writes remain observable");
        var terminal=f.observed.current();
        for(int i=contexts.size()-2;i>=0;i--) {
            assertSame(contexts.get(i),f.observed.previous());
            assertSame(frames.get(i),f.history.show(f.observed.current()));
            assertEquals(frames.get(i).locations(),f.adapter.locations());
            assertEquals(frames.get(i).snapshot().sourceVersion(),f.session.model().sourceVersion());
        }
        for(int i=1;i<contexts.size();i++) {
            assertSame(contexts.get(i),f.observed.next());assertSame(frames.get(i),f.history.show(f.observed.current()));
        }
        assertSame(terminal,f.observed.current());assertTrue(f.collector.drain(10000).events().isEmpty());f.history.close();
    }
    @Test void rejectingAnOwnershipCycleDoesNotChangeAnyVmStopOrRuntimeAndCanRecoverLater() {
        Fixture f=new Fixture(CYCLIC_EXPANSION,true);
        var first=f.show(f.observed.current(),false);assertTrue(first.accepted());
        boolean rejected=false,recovered=false;var lastAccepted=first.snapshot();
        while(f.observed.canNext()) {
            var actual=f.observed.next();var expected=f.control.next();
            assertEquals(expected.stop(),actual.stop());assertEquals(expected.runtime(),actual.runtime());
            var frame=f.show(actual,false);
            if(!frame.accepted()) {
                rejected=true;assertTrue(frame.diagnostic().contains("OWNERSHIP_CYCLE"),frame.diagnostic());
                assertSame(lastAccepted,frame.snapshot(),"Invalid visualization keeps the previous published frame");
            } else {if(rejected)recovered=true;lastAccepted=frame.snapshot();}
        }
        assertTrue(rejected,"The deliberately cyclic expansion must be rejected");assertTrue(recovered);
        assertEquals(Debugger.Status.COMPLETED,f.observed.current().stop().status(),f.observed.current().stop().error());
        assertEquals(12,f.observed.current().runtime().returnValue().integer());
        assertEquals(f.control.current().runtime(),f.observed.current().runtime());f.history.close();
    }
    private static final class Fixture {
        final IrResult ir;final DebugApi observed,control;final RuntimeEventCollector collector=new RuntimeEventCollector();
        final DefaultVisualizationSession session=new DefaultVisualizationSession();final PageTypeRegistry types=new PageTypeRegistry();
        final DebugVisualizationAdapter adapter=new DebugVisualizationAdapter(session,types);
        final DebugVisualizationHistory history=new DebugVisualizationHistory(adapter,collector,()->{});
        final long rootSlot;final Long spareSlot;final Set<DebugObjectIdentityRegistry.ObjectKey> pending=new HashSet<>();
        Fixture(String text,boolean ownership) {
            SourceFile source=new SourceFile("registered-vm.mc",text);ir=new CompilerApi(source).runToIr();
            observed=new DebugApi(Debugger.fromIr(source,ir,"",10000,collector));control=new DebugApi(Debugger.fromIr(source,ir,"",10000));
            var layout=ir.structLayouts().values().stream().filter(value->value.field("value").isPresent()&&value.field("next").isPresent()).findFirst().orElseThrow();
            // The example schema is manual; verify its target ABI instead of guessing from allocation size.
            assertEquals(16,layout.size());assertEquals(0,layout.field("value").orElseThrow().offset());assertEquals(8,layout.field("next").orElseThrow().offset());
            types.register(BuiltinPageTypes.point());types.register(BuiltinPageTypes.tree(false));
            adapter.registerDescriptor(new DebugStructureDescriptor("root","point",ViewKind.POINT,8,
                    List.of(new Field("pointer",0,POINTER)),List.of(new Reference("entry",0,"node",Relation.OWNERSHIP,Direction.NONE)),null,ReallocationPolicy.RECREATE));
            adapter.registerDescriptor(new DebugStructureDescriptor("node",ownership?"point":"tree",ownership?ViewKind.POINT:ViewKind.TREE,16,
                    List.of(new Field("value",0,SIGNED32)),
                    List.of(new Reference("next",8,"node",ownership?Relation.OWNERSHIP:Relation.TOPOLOGY,ownership?Direction.NONE:Direction.FORWARD)),
                    null,ReallocationPolicy.RECREATE));
            rootSlot=global("root");spareSlot=text.contains("struct Node *spare;")?global("spare"):null;
            adapter.registerRoot(new RootAddress("root",rootSlot));
        }
        long global(String name) {
            String symbol=ir.globalData().stream().map(value->value.label()).filter(label->ir.displayName(label).equals(name)).findFirst().orElseThrow();
            return observed.current().runtime().globalMemory().stream().filter(block->block.label().equals(symbol)).findFirst().orElseThrow().address();
        }
        DebugVisualizationHistory.Frame show(Debugger.Context context,boolean registerPending) {
            if(registerPending&&spareSlot!=null) {
                var reader=new DebugMemoryReader(context.runtime());
                long address=reader.readPointer(reader.resolve(spareSlot),0);
                long root=reader.readPointer(reader.resolve(rootSlot),0);
                if(address!=0&&root!=0) {
                    try {
                        var candidate=reader.resolve(address);
                        var key=new DebugObjectIdentityRegistry.ObjectKey(candidate.allocationId(),candidate.offset(),"node");
                        var owner=adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(reader.resolve(rootSlot).allocationId(),0,"root"));
                        var rootNode=adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(reader.resolve(root).allocationId(),0,"node"));
                        if(rootNode!=null&&owner!=null&&pending.add(key))adapter.registerObject("node",candidate,rootNode.page(),owner);
                    } catch(DebugMemoryReader.MemoryReadException failure) {
                        if(failure.reason()!=DebugMemoryReader.Reason.UNKNOWN_ADDRESS)throw failure;
                    }
                }
            }
            return history.show(context);
        }
    }
}
