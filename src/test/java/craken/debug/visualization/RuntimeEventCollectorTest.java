package craken.debug.visualization;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;

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

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void closeFromTheDisplayThreadBecomesVisibleToTheHotVmRecordingLoop() throws InterruptedException {
        RuntimeEventCollector collector=new RuntimeEventCollector();
        CountDownLatch started=new CountDownLatch(1),stopped=new CountDownLatch(1);
        Thread vm=new Thread(()-> {
            started.countDown();
            while(collector.isRecording())Thread.onSpinWait();
            stopped.countDown();
        },"collector-close-visibility");
        // A failed pre-fix visibility probe must not keep the JUnit console process alive.
        vm.setDaemon(true);vm.start();assertTrue(started.await(2,TimeUnit.SECONDS));
        Thread.sleep(300); // Compile the ordinary VM polling loop before another thread closes it.
        collector.close();
        assertTrue(stopped.await(2,TimeUnit.SECONDS),"VM thread did not observe display close");
        vm.join(1000);assertFalse(vm.isAlive());assertFalse(collector.isRecording());
    }
    @Test
    @org.junit.jupiter.api.Timeout(10)
    void anInFlightFactoryIsDiscardedAndFactoriesAfterCrossThreadCloseAreNotInvoked() throws InterruptedException {
        RuntimeEventCollector collector=new RuntimeEventCollector();
        CountDownLatch factoryEntered=new CountDownLatch(1),factoryMayFinish=new CountDownLatch(1);
        AtomicInteger factories=new AtomicInteger();
        Thread vm=new Thread(()-> {
            collector.record(sequence-> {
                factories.incrementAndGet();factoryEntered.countDown();
                try { if(!factoryMayFinish.await(2,TimeUnit.SECONDS))throw new IllegalStateException("Factory timeout"); }
                catch(InterruptedException failure) {Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
                return allocation(sequence,1);
            });
            collector.record(sequence->{factories.incrementAndGet();return allocation(sequence,2);});
        },"collector-in-flight-close");
        vm.start();assertTrue(factoryEntered.await(2,TimeUnit.SECONDS));collector.close();factoryMayFinish.countDown();
        vm.join(2000);assertFalse(vm.isAlive());assertEquals(1,factories.get());
        assertFalse(collector.drain(0).monitored());assertTrue(collector.drain(1).events().isEmpty());
        assertDoesNotThrow(collector::close);
    }
    @Test
    @org.junit.jupiter.api.Timeout(10)
    void closingDuringAnInFlightDrainDiscardsTheCapturedBatch() throws InterruptedException {
        CountDownLatch copying=new CountDownLatch(1),finishCopy=new CountDownLatch(1);
        ArrayList<RuntimeEvent> storage=new ArrayList<>() {
            @Override public Object[] toArray() {
                Object[] copy=super.toArray();copying.countDown();
                try { if(!finishCopy.await(2,TimeUnit.SECONDS))throw new IllegalStateException("Copy timeout"); }
                catch(InterruptedException failure) {Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
                return copy;
            }
        };
        RuntimeEventCollector collector=new RuntimeEventCollector(true,storage);
        collector.record(sequence->allocation(sequence,1));
        AtomicReference<RuntimeEventBatch> drained=new AtomicReference<>();
        Thread vm=new Thread(()->drained.set(collector.drain(0)),"collector-drain-close");
        vm.start();assertTrue(copying.await(2,TimeUnit.SECONDS));collector.close();finishCopy.countDown();
        vm.join(2000);assertFalse(vm.isAlive());
        assertFalse(drained.get().monitored(),"An interval captured across close must be discarded");
        assertTrue(drained.get().events().isEmpty());
    }
}
