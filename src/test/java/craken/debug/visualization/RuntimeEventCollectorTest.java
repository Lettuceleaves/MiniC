package craken.debug.visualization;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class RuntimeEventCollectorTest {
    @Test
    void disabledCollectorDoesNotConstructEvents() {
        RuntimeEventCollector collector = RuntimeEventCollector.disabled();
        AtomicInteger factories = new AtomicInteger();
        collector.record(sequence -> { factories.incrementAndGet(); throw new AssertionError("disabled factory invoked"); });
        assertEquals(0, factories.get());
        RuntimeEventBatch batch = collector.drain(0);
        assertFalse(batch.monitored());
        assertTrue(batch.events().isEmpty());
    }

    @Test
    void intervalSnapshotsAreImmutableAndSequenceContinuesAcrossDrains() {
        RuntimeEventCollector collector = new RuntimeEventCollector();
        collector.record(sequence -> allocation(sequence, 1));
        RuntimeEventBatch first = collector.drain(0);
        collector.record(sequence -> allocation(sequence, 2));
        RuntimeEventBatch second = collector.drain(1);
        assertEquals(1, first.events().size());
        assertEquals(1, first.events().getFirst().sequence());
        assertEquals(2, second.events().getFirst().sequence());
        assertThrows(UnsupportedOperationException.class, () -> first.events().clear());
    }

    @Test
    void factoryAndStorageFailuresSuspendOnlyTheCurrentInterval() {
        RuntimeEventCollector collector = new RuntimeEventCollector();
        collector.record(sequence -> allocation(sequence, 1));
        collector.record(sequence -> { throw new IllegalArgumentException("injected factory failure"); });
        collector.record(sequence -> { throw new AssertionError("factory called while interval suspended"); });
        RuntimeEventBatch failed = collector.drain(0);
        assertFalse(failed.complete());
        assertEquals(1, failed.events().size());
        assertTrue(failed.diagnostic().contains("injected factory failure"));
        collector.record(sequence -> allocation(sequence, 3));
        assertTrue(collector.drain(1).complete());

        RuntimeEventCollector failingStorage = FailingEventCollectors.failsOnAppend();
        assertDoesNotThrow(() -> failingStorage.record(sequence -> allocation(sequence, 1)));
        assertFalse(failingStorage.drain(0).complete());
    }

    @Test
    void inspectionSuppressionIsNestedAndRestoredWhenInspectionFails() {
        RuntimeEventCollector collector = new RuntimeEventCollector();
        assertThrows(IllegalStateException.class, () -> collector.withoutEvents(() ->
                collector.withoutEvents(() -> {
                    collector.record(sequence -> { throw new AssertionError("inspection must not emit"); });
                    throw new IllegalStateException("inspection failed");
                })));
        collector.record(sequence -> allocation(sequence, 1));
        assertEquals(1, collector.drain(0).events().size());
    }

    private static RuntimeEvent allocation(long sequence, long identity) {
        return new RuntimeEvent.Allocated(sequence, new RuntimeEvent.MemoryRange(identity, 100, 4), "heap", "test");
    }

    @Test
    void sinkRejectsDuplicateAndOutOfOrderSequenceNumbersWithoutThrowingIntoTheVm() {
        RuntimeEventCollector collector = new RuntimeEventCollector();
        collector.accept(allocation(1, 1));
        collector.accept(allocation(1, 2));
        RuntimeEventBatch failed = collector.drain(0);
        assertFalse(failed.complete());
        assertEquals(1, failed.events().size());
        collector.record(sequence -> allocation(sequence, 3));
        assertEquals(2, collector.drain(1).events().getFirst().sequence());
    }

}
