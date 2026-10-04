package craken.debug;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.debug.visualization.FailingEventCollectors;
import craken.debug.visualization.RuntimeEvent;
import craken.debug.visualization.RuntimeEventCollector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
@Timeout(30)
final class DebugRuntimeEventBatchTest {
    private static final SourceFile SOURCE = new SourceFile("batch-events.mc", """
            int global = 1;
            int main(void) {
                int local = global;
                local = local;
                return local;
            }
            """);

    @Test
    void initializationAndEachNewStopHaveTheirOwnOrderedImmutableBatch() {
        RuntimeEventCollector collector = new RuntimeEventCollector();
        DebugApi api = new DebugApi(new Debugger(SOURCE, "", collector));
        List<Debugger.Context> contexts = run(api);
        assertFalse(contexts.getFirst().events().events().isEmpty(), "Initialization belongs to the ready context");
        assertTrue(contexts.getFirst().events().events().stream().anyMatch(RuntimeEvent.Allocated.class::isInstance));
        List<RuntimeEvent> all = new ArrayList<>();
        for (Debugger.Context context : contexts) {
            assertEquals(context.index(), context.events().contextIndex());
            assertTrue(context.events().monitored());
            assertTrue(context.events().complete(), context.events().diagnostic());
            all.addAll(context.events().events());
        }
        assertTrue(all.stream().anyMatch(RuntimeEvent.Accessed.class::isInstance));
        for (int i = 0; i < all.size(); i++) assertEquals(i + 1L, all.get(i).sequence());
        assertTrue(collector.drain(contexts.size()).events().isEmpty(), "New contexts already drained their interval");
    }

    @Test
    void disabledDebuggerDoesNotRetainAnEventLog() {
        DebugApi api = new DebugApi(new Debugger(SOURCE));
        for (Debugger.Context context : run(api)) {
            assertFalse(context.events().monitored());
            assertTrue(context.events().events().isEmpty());
        }
    }

    @Test
    void internalStorageFailureDoesNotChangeAnyProgramStopOrRuntimeSnapshot() {
        IrResult ir = new CompilerApi(SOURCE).runToIr();
        List<Debugger.Context> ordinary = run(new DebugApi(Debugger.fromIr(SOURCE, ir, "", 100)));
        List<Debugger.Context> observed = run(new DebugApi(
                Debugger.fromIr(SOURCE, ir, "", 100, FailingEventCollectors.failsOnAppend())));
        assertEquals(ordinary.size(), observed.size());
        for (int i = 0; i < ordinary.size(); i++) {
            assertEquals(ordinary.get(i).stop(), observed.get(i).stop());
            assertEquals(ordinary.get(i).runtime(), observed.get(i).runtime());
        }
        assertTrue(observed.stream().anyMatch(context -> !context.events().complete()));
        assertEquals(Debugger.Status.COMPLETED, observed.getLast().stop().status());
    }

    @Test
    void browsingCachedVmContextsDoesNotCollectAgain() {
        RuntimeEventCollector collector = new RuntimeEventCollector();
        DebugApi api = new DebugApi(new Debugger(SOURCE, "", collector));
        Debugger.Context before = api.current();
        Debugger.Context after = api.next();
        assertSame(before, api.previous());
        assertSame(after, api.next());
        assertTrue(collector.drain(100).events().isEmpty());
    }

    private static List<Debugger.Context> run(DebugApi api) {
        List<Debugger.Context> contexts = new ArrayList<>();
        contexts.add(api.current());
        while (api.canNext()) {
            assertTrue(contexts.size() < 1000, "Debugger did not terminate");
            contexts.add(api.next());
        }
        return contexts;
    }
}
