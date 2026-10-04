package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrType;
import craken.debug.visualization.DebugMemoryReader;
import craken.debug.visualization.RuntimeEvent;
import craken.debug.visualization.RuntimeEventCollector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static craken.debug.visualization.DebugMemoryReader.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class DebugMemoryReaderTest {
    @Test
    void snapshotCarriesAllocationGenerationAndExactPartialInitializationBits() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        long address = runtime.allocate(16, "heap", "partial");
        runtime.write(address + 4, DebugRuntime.Value.of(IrType.INT, 42));
        RuntimeEvent.Allocated allocated = events.drain(0).events().stream().filter(RuntimeEvent.Allocated.class::isInstance)
                .map(RuntimeEvent.Allocated.class::cast).findFirst().orElseThrow();
        DebugRuntime.RuntimeState snapshot = runtime.snapshot();
        DebugRuntime.MemoryBlock block = snapshot.heap().getFirst();
        assertEquals(allocated.range().allocationId(), block.allocationId());
        assertEquals("f0", block.initializedMask());
        assertEquals(4, block.initializedBytes());
        DebugMemoryReader reader = new DebugMemoryReader(snapshot);
        Address object = reader.resolve(address);
        assertEquals("42", reader.readScalar(object, 4, ScalarType.SIGNED32));
        MemoryReadException uninitialized = assertThrows(MemoryReadException.class,
                () -> reader.readScalar(object, 0, ScalarType.SIGNED32));
        assertEquals(Reason.UNINITIALIZED, uninitialized.reason());
    }

    @Test
    void readsUseTheRecordedLittleEndianAbiAndDoNotPublishProgramAccesses() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        long address = runtime.allocateZeroed(40, 8, "heap", "values");
        runtime.write(address, DebugRuntime.Value.of(IrType.INT, -1));
        runtime.write(address + 8, DebugRuntime.Value.of(IrType.LONG_LONG, -1));
        runtime.write(address + 16, DebugRuntime.Value.of(IrType.FLOAT, 1.25));
        runtime.write(address + 24, DebugRuntime.Value.of(IrType.DOUBLE, -2.5));
        runtime.write(address + 32, DebugRuntime.Value.of(IrType.POINTER, address + 8));
        DebugRuntime.RuntimeState state = runtime.snapshot();
        events.drain(0);
        DebugMemoryReader reader = new DebugMemoryReader(state);
        Address object = reader.resolve(address);
        assertEquals("-1", reader.readScalar(object, 0, ScalarType.SIGNED32));
        assertEquals("4294967295", reader.readScalar(object, 0, ScalarType.UNSIGNED32));
        assertEquals("18446744073709551615", reader.readScalar(object, 8, ScalarType.UNSIGNED64));
        assertEquals("1.25", reader.readScalar(object, 16, ScalarType.FLOAT32));
        assertEquals("-2.5", reader.readScalar(object, 24, ScalarType.FLOAT64));
        assertEquals(address + 8, reader.readPointer(object, 32));
        assertTrue(events.drain(1).events().isEmpty());
        assertEquals(state, runtime.snapshot());
    }

    @Test
    void anOldReaderAndItsReturnedByteArraysCannotBeMutatedByFutureExecution() {
        DebugRuntime runtime = runtime(new RuntimeEventCollector());
        long address = runtime.allocateZeroed(8, 8, "heap", "value");
        runtime.write(address, DebugRuntime.Value.of(IrType.INT, 42));
        DebugMemoryReader old = new DebugMemoryReader(runtime.snapshot());
        Address object = old.resolve(address);
        byte[] bytes = old.readBytes(object, 0, 4);
        bytes[0] = 0;
        runtime.write(address, DebugRuntime.Value.of(IrType.INT, 99));
        assertEquals("42", old.readScalar(object, 0, ScalarType.SIGNED32));
        assertEquals("99", new DebugMemoryReader(runtime.snapshot()).readScalar(object, 0, ScalarType.SIGNED32));
    }

    @Test
    void missingIdentitiesInvalidRangesAndLegacySnapshotsFailWithoutTouchingTheVm() {
        DebugRuntime runtime = runtime(new RuntimeEventCollector());
        long address = runtime.allocateZeroed(8, 8, "heap", "bounds");
        DebugMemoryReader reader = new DebugMemoryReader(runtime.snapshot());
        Address object = reader.resolve(address);
        assertEquals(Reason.OUT_OF_BOUNDS, assertThrows(MemoryReadException.class,
                () -> reader.readBytes(object, Integer.MAX_VALUE, 8)).reason());
        assertEquals(Reason.OUT_OF_BOUNDS, assertThrows(MemoryReadException.class,
                () -> reader.readBytes(object, -1, 1)).reason());
        assertEquals(Reason.UNKNOWN_ADDRESS, assertThrows(MemoryReadException.class,
                () -> reader.resolve(address + 8)).reason());
        assertEquals(Reason.UNKNOWN_IDENTITY, assertThrows(MemoryReadException.class,
                () -> reader.readBytes(new Address(Long.MAX_VALUE, 0), 0, 1)).reason());
        DebugRuntime.MemoryBlock legacy = new DebugRuntime.MemoryBlock(100, 4, "legacy", "01000000", 4);
        assertEquals(100, legacy.address());
        assertEquals(4, legacy.initializedBytes());
        assertEquals(0, legacy.allocationId());
        assertEquals(Reason.UNKNOWN_IDENTITY, assertThrows(MemoryReadException.class,
                () -> new DebugMemoryReader(List.of(legacy)).resolve(100)).reason());
    }

    @Test
    void malformedSnapshotsAreRejectedInsteadOfReadingAcrossAllocations() {
        assertEquals(Reason.MALFORMED_SNAPSHOT, assertThrows(MemoryReadException.class,
                () -> new DebugMemoryReader(List.of(new DebugRuntime.MemoryBlock(100, 4, "bad", "00", 4, 1, "0f")))).reason());
        assertEquals(Reason.MALFORMED_SNAPSHOT, assertThrows(MemoryReadException.class,
                () -> new DebugMemoryReader(List.of(new DebugRuntime.MemoryBlock(100, 1, "bad mask", "00", 0, 1, "01")))).reason());
        assertEquals(Reason.MALFORMED_SNAPSHOT, assertThrows(MemoryReadException.class,
                () -> new DebugMemoryReader(List.of(new DebugRuntime.MemoryBlock(100, 4, "a", "00000000", 4, 1, "0f"),
                        new DebugRuntime.MemoryBlock(102, 4, "b", "00000000", 4, 2, "0f")))).reason());
    }

    private static DebugRuntime runtime(RuntimeEventCollector events) {
        SourceFile source = new SourceFile("reader.mc", "");
        return new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "", DebugTimeSource.system(),
                DebugRuntime.DEFAULT_HEAP_CAPACITY, events);
    }
}
