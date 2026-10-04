package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.CompilerApi;
import craken.SourceRange;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrFunction;
import craken.compiler.ir.model.IrLocal;
import craken.compiler.ir.model.IrParameter;
import craken.compiler.ir.model.IrType;
import craken.compiler.type.CrakenType;
import craken.debug.visualization.RuntimeEvent;
import craken.debug.visualization.RuntimeEventBatch;
import craken.debug.visualization.RuntimeEventCollector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
@Timeout(30)
final class RuntimeEventCoverageTest {
    @Test
    void allocationAndSuccessfulAccessAreObservedInOrderEvenForSameValueWrites() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        long address = runtime.allocate(8, "heap", "object");
        runtime.write(address, DebugRuntime.Value.of(IrType.INT, 7));
        runtime.write(address, DebugRuntime.Value.of(IrType.INT, 7));
        assertEquals(7, runtime.read(address, IrType.INT).integer());
        runtime.release(address);

        RuntimeEventBatch batch = events.drain(0);
        assertEquals(5, batch.events().size(), "Successful alloc, same-value writes, read and free need distinct events");
        assertInstanceOf(RuntimeEvent.Allocated.class, batch.events().get(0));
        assertInstanceOf(RuntimeEvent.Released.class, batch.events().get(4));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), batch.events().stream().map(RuntimeEvent::sequence).toList());
        List<RuntimeEvent.Accessed> accesses = batch.events().stream().filter(RuntimeEvent.Accessed.class::isInstance)
                .map(RuntimeEvent.Accessed.class::cast).toList();
        assertEquals(List.of(RuntimeEvent.Access.WRITE, RuntimeEvent.Access.WRITE, RuntimeEvent.Access.READ),
                accesses.stream().map(RuntimeEvent.Accessed::access).toList());
        long identity = ((RuntimeEvent.Allocated) batch.events().getFirst()).range().allocationId();
        assertTrue(accesses.stream().allMatch(e -> e.range().allocationId() == identity));
        assertEquals(identity, ((RuntimeEvent.Released) batch.events().getLast()).range().allocationId());
        assertTrue(batch.complete());
    }

    @Test
    void allocationsReleasedWithinOneIntervalAndGenerationsAreNotLost() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        long first = runtime.allocate(8, "heap", "temporary");
        runtime.release(first);
        long second = runtime.allocate(8, "heap", "next");
        List<RuntimeEvent> batch = events.drain(0).events();
        assertEquals(3, batch.size());
        assertTrue(((RuntimeEvent.Allocated) batch.get(2)).range().allocationId()
                > ((RuntimeEvent.Allocated) batch.get(0)).range().allocationId());
        assertNotEquals(first, second);
        assertEquals(1, runtime.heap().size());
    }

    @Test
    void invalidOperationsDoNotPublishSuccessEvents() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        long address = runtime.allocate(4, "heap", "uninitialized");
        events.drain(0);
        assertThrows(IllegalStateException.class, () -> runtime.read(address, IrType.INT));
        assertThrows(IllegalStateException.class, () -> runtime.write(address + 4, DebugRuntime.Value.of(IrType.INT, 9)));
        assertThrows(IllegalStateException.class, () -> runtime.release(address + 1));
        assertTrue(events.drain(1).events().isEmpty());
    }

    @Test
    void zeroingFillByteWritesCopyAndReallocationHaveExplicitEvents() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        long source = runtime.allocateZeroed(8, 8, "heap", "source");
        runtime.fill(source, 65, 8);
        runtime.writeByte(source + 1, 66);
        assertEquals(66, runtime.readUnsignedByte(source + 1));
        long destination = runtime.allocate(8, "heap", "destination");
        runtime.copy(destination, source, 8);
        long replacement = runtime.reallocate(destination, 16);

        List<RuntimeEvent> batch = events.drain(0).events();
        assertTrue(batch.stream().anyMatch(e -> e instanceof RuntimeEvent.Accessed a
                && a.access() == RuntimeEvent.Access.INITIALIZED && a.range().address() == source));
        assertEquals(2, batch.stream().filter(RuntimeEvent.Copied.class::isInstance).count(),
                "copy and realloc copy are both memory transfers");
        RuntimeEvent.Reallocated realloc = batch.stream().filter(RuntimeEvent.Reallocated.class::isInstance)
                .map(RuntimeEvent.Reallocated.class::cast).findFirst().orElseThrow();
        assertEquals(destination, realloc.previous().address());
        assertEquals(replacement, realloc.replacement().address());
        assertNotEquals(realloc.previous().allocationId(), realloc.replacement().allocationId());
        assertEquals(8, realloc.copiedBytes());
        assertTrue(batch.stream().anyMatch(e -> e instanceof RuntimeEvent.Released released
                && released.range().address() == destination));
    }

    @Test
    void snapshotsDoNotCreateProgramAccessEvents() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        runtime.errnoAddress();
        events.drain(0);
        runtime.snapshot();
        runtime.stack();
        runtime.snapshot();
        assertTrue(events.drain(1).events().isEmpty(), "Inspecting a snapshot must not read on behalf of the program");
    }

    @Test
    void directLibraryBufferWritesAreCaptured() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        runtime.setStrerrorMessage("first");
        events.drain(0);
        long address = runtime.setStrerrorMessage("second");
        int size = runtime.libraryMemory().stream().filter(block -> block.address() == address)
                .findFirst().orElseThrow().size();
        List<RuntimeEvent> batch = events.drain(1).events();
        assertTrue(batch.stream().anyMatch(e -> e instanceof RuntimeEvent.Accessed a
                && a.access() == RuntimeEvent.Access.WRITE && a.range().address() == address
                && a.range().size() == size));
    }

    @Test
    void globalStringAndRelocationInitializationAreInTheInitialInterval() {
        SourceFile source = new SourceFile("initial-events.mc", """
                int value = 42;
                int *pointer = &value;
                char *text = "hello";
                int main(void) { return *pointer == 42 ? 0 : 1; }
                """);
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = new DebugRuntime(new DebugProgram(source, new CompilerApi(source).runToIr()),
                "", DebugTimeSource.system(), DebugRuntime.DEFAULT_HEAP_CAPACITY, events);
        List<RuntimeEvent> batch = events.drain(0).events();
        assertTrue(batch.stream().anyMatch(e -> e instanceof RuntimeEvent.Allocated a && a.storage().equals("static")));
        assertTrue(batch.stream().anyMatch(e -> e instanceof RuntimeEvent.Allocated a && a.storage().equals("global")));
        long pointerAddress = runtime.symbol("pointer");
        assertTrue(batch.stream().anyMatch(e -> e instanceof RuntimeEvent.Accessed a
                && a.access() == RuntimeEvent.Access.WRITE && a.range().address() == pointerAddress
                && a.valueType().equals("POINTER")));
        assertEquals(runtime.symbol("value"), runtime.read(pointerAddress, IrType.POINTER).integer());
    }

    @Test
    void declarationsParametersAndVarargStorageEndAtFrameReturnExactlyOnce() {
        SourceRange range = new SourceRange(1, 0, 1, 1);
        IrFunction function = new IrFunction("call", CrakenType.INT,
                List.of(new IrParameter("arg", CrakenType.INT, IrType.INT, range)), true, List.of(), range);
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        runtime.push(function, List.of(DebugRuntime.Value.of(IrType.INT, 7), DebugRuntime.Value.of(IrType.INT, 8)), null);
        DebugRuntime.Frame frame = runtime.stack.getLast();
        IrLocal local = new IrLocal("local", "local", CrakenType.INT, IrType.INT, 4, 4, range);
        runtime.declareLocal(frame, local);
        long localAddress = runtime.local(frame, local);
        runtime.write(localAddress, DebugRuntime.Value.of(IrType.INT, 1));
        runtime.declareLocal(frame, local);
        runtime.parameterAddress(frame, "arg");
        runtime.local(frame, IrLocal.incomingArgumentArea(1, range));
        events.drain(0);
        runtime.stack();
        runtime.snapshot();
        assertTrue(events.drain(1).events().isEmpty());
        runtime.pop(DebugRuntime.Value.of(IrType.INT, 0));
        List<RuntimeEvent> released = events.drain(2).events();
        assertEquals(3, released.size(), "local, addressed parameter, and entire vararg area each release once");
        assertTrue(released.stream().allMatch(RuntimeEvent.Released.class::isInstance));
        assertTrue(runtime.stackMemory().isEmpty());
    }

    @Test
    void declarationResetAndTerminationAreObserved() {
        SourceRange range = new SourceRange(1, 0, 1, 1);
        IrFunction function = new IrFunction("frame", CrakenType.INT, List.of(), false, List.of(), range);
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        for (int i = 0; i < 2; i++) {
            runtime.push(function, List.of(), null);
            DebugRuntime.Frame frame = runtime.stack.getLast();
            IrLocal local = new IrLocal("slot", "slot", CrakenType.INT, IrType.INT, 4, 4, range);
            runtime.declareLocal(frame, local);
            runtime.write(runtime.local(frame, local), DebugRuntime.Value.of(IrType.INT, 1));
            runtime.declareLocal(frame, local);
        }
        List<RuntimeEvent> before = events.drain(0).events();
        assertEquals(4, before.stream().filter(e -> e instanceof RuntimeEvent.Accessed a
                && a.access() == RuntimeEvent.Access.UNINITIALIZED).count());
        runtime.terminate(0, "test");
        List<RuntimeEvent> after = events.drain(1).events();
        assertEquals(2, after.stream().filter(RuntimeEvent.Released.class::isInstance).count());
        assertTrue(runtime.stackMemory().isEmpty());
    }
    private static DebugRuntime runtime(RuntimeEventCollector events) {
        SourceFile source = new SourceFile("runtime-events.mc", "");
        return new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "",
                DebugTimeSource.system(), DebugRuntime.DEFAULT_HEAP_CAPACITY, events);
    }

    @Test
    void reallocNullAndUnchangedSizeDescribeOnlyTheTransfersThatActuallyHappened() {
        RuntimeEventCollector events = new RuntimeEventCollector();
        DebugRuntime runtime = runtime(events);
        long address = runtime.reallocate(0, 8);
        RuntimeEvent.Reallocated initial = events.drain(0).events().stream()
                .filter(RuntimeEvent.Reallocated.class::isInstance).map(RuntimeEvent.Reallocated.class::cast)
                .findFirst().orElseThrow();
        assertNull(initial.previous());
        assertEquals(0, initial.copiedBytes());
        assertEquals(address, runtime.reallocate(address, 8));
        List<RuntimeEvent> unchanged = events.drain(1).events();
        assertEquals(1, unchanged.size());
        RuntimeEvent.Reallocated event = assertInstanceOf(RuntimeEvent.Reallocated.class, unchanged.getFirst());
        assertEquals(event.previous(), event.replacement());
        assertEquals(0, event.copiedBytes(), "Same-size realloc preserved memory without copying any bytes");
    }

}
