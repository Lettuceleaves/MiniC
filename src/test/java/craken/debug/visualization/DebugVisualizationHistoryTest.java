package craken.debug;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrType;
import craken.debug.visualization.*;
import craken.visualization.api.*;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static craken.debug.visualization.DebugMemoryReader.ScalarType.SIGNED32;
import static craken.debug.visualization.DebugStructureDescriptor.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
@Timeout(30)
final class DebugVisualizationHistoryTest {
    @Test void backwardAndForwardRestoreExactMappingsFocusAndSourceVersionsWithoutEvents() {
        Fixture f=new Fixture(true,10);long address=f.allocate(7); f.adapter.registerRoot(new RootAddress("value",address));
        var initial=f.show(0);var initialLocations=f.adapter.locations();var oldFrame=initial.snapshot();
        f.runtime.write(address,DebugRuntime.Value.of(IrType.INT,9));var latest=f.show(1);
        long live=f.session.model().version(),epoch=f.session.model().epoch();
        assertSame(initial,f.history.show(0));
        assertEquals(initialLocations,f.adapter.locations()); assertEquals(initial.snapshot().interaction(),f.session.model().interaction());
        assertEquals(initial.snapshot().sourceVersion(),f.session.model().sourceVersion());
        assertTrue(f.session.model().version()>live);assertTrue(f.session.model().epoch()>epoch);
        assertEquals(initial.snapshot().sourceVersion(),f.history.displayedSnapshot().sourceVersion());
        assertEquals(oldFrame,initial.snapshot(),"Saved frame remains an immutable historical value");
        assertSame(latest,f.history.show(1)); assertEquals(latest.locations(),f.adapter.locations());
        assertEquals(latest.snapshot().pages(),f.history.displayedSnapshot().pages());
        assertTrue(f.events.drain(100).events().isEmpty());
    }
    @Test void repeatedContextShowUsesTheStoredFrameAndDoesNotReplayItsAllocationBatch() {
        Fixture f=new Fixture(true,10);long address=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",address));
        Debugger.Context context=f.context(0);var first=f.history.show(context);var mapped=f.adapter.locations();
        assertSame(first,f.history.show(context));assertEquals(mapped,f.adapter.locations());
        assertEquals(List.of(0),f.history.indices());assertTrue(f.events.drain(100).events().isEmpty());
        assertEquals(first.events(),context.events());
    }
    @Test void evictionAndMissingContextsReturnExplicitDiagnostics() {
        Fixture f=new Fixture(true,2);long address=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",address));
        var old=f.context(0);f.history.show(old);f.show(1);f.show(2);
        assertEquals(List.of(1,2),f.history.indices());
        var expired=assertThrows(DebugVisualizationHistory.HistoryUnavailableException.class,()->f.history.show(0));
        assertEquals(DebugVisualizationHistory.Reason.EXPIRED_CONTEXT,expired.reason());
        var oldContext=assertThrows(DebugVisualizationHistory.HistoryUnavailableException.class,()->f.history.show(old));
        assertEquals(DebugVisualizationHistory.Reason.EXPIRED_CONTEXT,oldContext.reason());
        var missing=assertThrows(DebugVisualizationHistory.HistoryUnavailableException.class,()->f.history.show(100));
        assertEquals(DebugVisualizationHistory.Reason.UNKNOWN_CONTEXT,missing.reason());
    }
    @Test void completedDebuggerKeepsFramesAndBrowsingDoesNotChangeTheProgramOrCollectAgain() {
        SourceFile source=new SourceFile("history.mc","""
                int global = 1;
                int main(void) {
                    global = 2;
                    return global;
                }
                """);
        IrResult ir=new CompilerApi(source).runToIr();RuntimeEventCollector collector=new RuntimeEventCollector();
        DebugApi observed=new DebugApi(Debugger.fromIr(source,ir,"",100,collector));
        DebugApi control=new DebugApi(Debugger.fromIr(source,ir,"",100));
        var session=new DefaultVisualizationSession();var types=new PageTypeRegistry();types.register(BuiltinPageTypes.point());
        var adapter=new DebugVisualizationAdapter(session,types);registerValue(adapter);
        adapter.registerRoot(new RootAddress("value",observed.current().runtime().globalMemory().getFirst().address()));
        var history=new DebugVisualizationHistory(adapter,collector,()->{});
        List<Debugger.Context> contexts=new ArrayList<>();contexts.add(observed.current());history.show(observed.current());
        while(observed.canNext()) {
            var actual=observed.next();var ordinary=control.next();contexts.add(actual);history.show(actual);
            assertEquals(ordinary.stop(),actual.stop());assertEquals(ordinary.runtime(),actual.runtime());
        }
        var terminal=observed.current();assertEquals(Debugger.Status.COMPLETED,terminal.stop().status());
        assertFalse(history.indices().isEmpty());
        for(int i=contexts.size()-2;i>=0;i--)assertSame(contexts.get(i),observed.previous());
        history.show(observed.current());
        while(observed.canNext())history.show(observed.next());
        assertSame(terminal,observed.current());assertTrue(collector.drain(1000).events().isEmpty());
        assertEquals(terminal.runtime(),observed.current().runtime());
        history.close();
    }
    @Test void closeIsIdempotentAndCompletesAllCleanupEvenWhenDisplayReleaseThrows() {
        Fixture f=new Fixture(true,10);long address=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",address));f.show(0);
        f.releaseFailure=true;
        assertThrows(IllegalArgumentException.class,f.history::close);
        assertEquals(1,f.releases.get());assertTrue(f.history.indices().isEmpty());assertTrue(f.adapter.locations().isEmpty());
        assertTrue(f.session.model().pages().isEmpty());
        assertFalse(f.events.isRecording());AtomicInteger factories=new AtomicInteger();
        f.events.record(sequence->{factories.incrementAndGet();throw new AssertionError("Closed collector invoked factory");});
        assertEquals(0,factories.get());assertTrue(f.events.drain(100).events().isEmpty());
        assertDoesNotThrow(f.history::close);assertEquals(1,f.releases.get());
        var closed=assertThrows(DebugVisualizationHistory.HistoryUnavailableException.class,()->f.history.show(0));
        assertEquals(DebugVisualizationHistory.Reason.CLOSED,closed.reason());
    }
    @Test void borrowedSessionsRemainOpenButOwnedMappingsAndCollectorAreReleased() {
        Fixture f=new Fixture(false,10);long address=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",address));f.show(0);
        var page=f.session.model().root();f.history.close();
        assertFalse(f.session.model().pages().isEmpty());assertTrue(f.adapter.locations().isEmpty());
        var reserved=f.session.reserveNodeId(page);
        assertDoesNotThrow(()->f.session.addNode(new OperationPath(null,reserved),craken.visualization.model.ViewNode.Spec.point("borrowed")));
        assertFalse(f.events.isRecording());f.session.close();
    }
    @Test void newExecutionAfterBrowsingStartsFromTheLatestIdentityCheckpoint() {
        Fixture f=new Fixture(true,10);long address=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",address));f.show(0);
        long later=f.allocate(9);f.adapter.registerRoot(new RootAddress("value",later));var latest=f.show(1);
        f.history.show(0);f.runtime.write(later,DebugRuntime.Value.of(IrType.INT,11));var next=f.show(2);
        assertTrue(next.accepted(),next.diagnostic());assertEquals(latest.locations(),next.locations());
        assertEquals(2,f.adapter.locations().size());
    }
    @Test void releasedObjectsCanBeBrowsedWithTheirOldIdentityWithoutRevivingVmMemoryOrReusingIds() {
        Fixture f=new Fixture(true,10);long address=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",address));
        var initial=f.show(0);var location=initial.locations().values().iterator().next();
        long spent=f.allocate(9);f.adapter.registerRoot(new RootAddress("value",spent));var allocated=f.show(1);
        long maximum=allocated.snapshot().highWater().nodes().get(location.pageId());
        f.runtime.release(address);var deleted=f.show(2);var actual=f.runtime.snapshot();
        assertEquals(1,deleted.locations().size());
        f.history.show(0);
        assertEquals(initial.locations(),f.adapter.locations());assertEquals(actual,f.runtime.snapshot());
        assertTrue(f.session.reserveNodeId(location.page()).nodeId()>maximum);
        f.history.show(2);assertEquals(deleted.locations(),f.adapter.locations());
        assertEquals(actual,f.runtime.snapshot());assertTrue(f.events.drain(100).events().isEmpty());
    }
    @Test void closingObservationBetweenStopsEvenWithThrowingDisplayCleanupDoesNotChangeFurtherExecution() {
        SourceFile source=new SourceFile("close-observation.mc","""
                int global = 1;
                int main(void) {
                    global = 2;
                    global = 3;
                    return global;
                }
                """);
        var ir=new CompilerApi(source).runToIr();var collector=new RuntimeEventCollector();
        var observed=new DebugApi(Debugger.fromIr(source,ir,"",100,collector));
        var ordinary=new DebugApi(Debugger.fromIr(source,ir,"",100));
        var session=new DefaultVisualizationSession();var types=new PageTypeRegistry();types.register(BuiltinPageTypes.point());
        var adapter=new DebugVisualizationAdapter(session,types);registerValue(adapter);
        adapter.registerRoot(new RootAddress("value",observed.current().runtime().globalMemory().getFirst().address()));
        var history=new DebugVisualizationHistory(adapter,collector,()->{throw new IllegalArgumentException("display");});
        history.show(observed.current());history.show(observed.next());ordinary.next();
        assertThrows(IllegalArgumentException.class,history::close);
        while(observed.canNext()) {
            var a=observed.next();var b=ordinary.next();assertEquals(b.stop(),a.stop());assertEquals(b.runtime(),a.runtime());
            assertFalse(a.events().monitored());assertTrue(a.events().events().isEmpty());
        }
        assertEquals(Debugger.Status.COMPLETED,observed.current().stop().status());assertDoesNotThrow(history::close);
    }
    @Test void pendingManualRegistrationSurvivesReturningFromAnOlderHistoryFrameToNewExecution() {
        Fixture f=new Fixture(true,10);long root=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",root));var first=f.show(0);
        var owner=first.locations().values().iterator().next();
        var childPage=f.session.initializePage(f.types.require("point"),owner);
        f.runtime.write(root,DebugRuntime.Value.of(IrType.INT,9));f.show(1);f.history.show(0);
        long fresh=f.allocate(11);var reader=new DebugMemoryReader(f.runtime.snapshot());
        f.adapter.registerObject("value",reader.resolve(fresh),childPage,owner);
        var next=f.show(2);assertTrue(next.accepted(),next.diagnostic());assertEquals(2,next.locations().size());
        assertNotNull(f.adapter.locations().get(new DebugObjectIdentityRegistry.ObjectKey(reader.resolve(fresh).allocationId(),0,"value")));
    }
    @Test void aNegativeIndexWasNeverRetainedAndIsReportedAsUnknownInsteadOfExpired() {
        Fixture f=new Fixture(true,2);
        var failure=assertThrows(DebugVisualizationHistory.HistoryUnavailableException.class,()->f.history.show(-1));
        assertEquals(DebugVisualizationHistory.Reason.UNKNOWN_CONTEXT,failure.reason());
    }
    @Test void missingIndexesBetweenRetainedContextsAreUnknownEvenAfterEviction() {
        Fixture f=new Fixture(true,1);long root=f.allocate(7);f.adapter.registerRoot(new RootAddress("value",root));
        f.show(0);f.show(2);f.show(4);
        assertEquals(DebugVisualizationHistory.Reason.EXPIRED_CONTEXT,
                assertThrows(DebugVisualizationHistory.HistoryUnavailableException.class,()->f.history.show(2)).reason());
        assertEquals(DebugVisualizationHistory.Reason.UNKNOWN_CONTEXT,
                assertThrows(DebugVisualizationHistory.HistoryUnavailableException.class,()->f.history.show(1)).reason());
    }
    @Test void repeatedIdenticalCleanupFailuresCannotPreventCollectorAndHistoryCleanup() {
        Fixture f=new Fixture(true,10);RuntimeException fault=new IllegalStateException("shared cleanup failure");
        VisualizationSession session=(VisualizationSession)java.lang.reflect.Proxy.newProxyInstance(
                VisualizationSession.class.getClassLoader(),new Class<?>[]{VisualizationSession.class},(proxy,method,arguments)-> {
                    if(method.getName().equals("close")) {f.session.close();throw fault;}
                    return method.invoke(f.session,arguments);
                });
        var adapter=new DebugVisualizationAdapter(session,f.types);registerValue(adapter);
        long address=f.allocate(7);adapter.registerRoot(new RootAddress("value",address));
        var history=new DebugVisualizationHistory(adapter,f.events,()->{throw fault;});history.show(f.context(0));
        var thrown=assertThrows(RuntimeException.class,history::close);
        assertFalse(f.events.isRecording(),"A repeated exception instance must not abort the cleanup sequence");
        assertTrue(history.indices().isEmpty());assertTrue(adapter.locations().isEmpty());
        assertSame(fault,thrown);assertDoesNotThrow(history::close);
    }
    private static void registerValue(DebugVisualizationAdapter adapter) {
        adapter.registerDescriptor(new DebugStructureDescriptor("value","point",ViewKind.POINT,4,
                List.of(new Field("value",0,SIGNED32)),List.of(),null,ReallocationPolicy.RECREATE));
    }
    private static final class Fixture {
        final RuntimeEventCollector events=new RuntimeEventCollector();
        final DebugRuntime runtime=new DebugRuntime(new DebugProgram(new SourceFile("history-fixture.mc",""),new IrResult(List.of())),
                "",DebugTimeSource.system(),DebugRuntime.DEFAULT_HEAP_CAPACITY,events);
        final DefaultVisualizationSession session=new DefaultVisualizationSession();
        final PageTypeRegistry types=new PageTypeRegistry();
        final DebugVisualizationAdapter adapter;
        final DebugVisualizationHistory history;
        final AtomicInteger releases=new AtomicInteger();boolean releaseFailure;
        Fixture(boolean owned,int limit) {
            types.register(BuiltinPageTypes.point());adapter=new DebugVisualizationAdapter(session,types,owned);registerValue(adapter);
            history=new DebugVisualizationHistory(adapter,events,()->{releases.incrementAndGet();if(releaseFailure)throw new IllegalArgumentException("display close");},limit);
        }
        long allocate(int value) {long address=runtime.allocateZeroed(4,4,"heap","value");runtime.write(address,DebugRuntime.Value.of(IrType.INT,value));return address;}
        Debugger.Context context(int index) {return new Debugger.Context(index,new Debugger.Stop(Debugger.Status.PAUSED,null,null,"","",0,"",false),runtime.code(),runtime.snapshot(),events.drain(index));}
        DebugVisualizationHistory.Frame show(int index) {return history.show(context(index));}
    }
}
