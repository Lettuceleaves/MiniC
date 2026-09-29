package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.RuntimeState;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugTimeLibraryParityTest {
    private SequenceTimeSource timeSource;
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("time-provider.mc", "");
        timeSource = new SequenceTimeSource(
                new long[]{1_250, 1_500},
                new long[]{1_700_000_000L, 1_700_000_060L},
                ZoneOffset.UTC
        );
        runtime = new DebugRuntime(
                new DebugProgram(source, new IrResult(List.of())),
                "",
                timeSource
        );
        library = new DebugSystemLibrary();
    }

    @Test
    void clockDifftimeAndTimeUseTheDeclaredWindowsTypesAndSnapshotReadings() {
        RuntimeState initial = runtime.snapshot();

        Value firstClock = call("clock");
        assertEquals(IrType.LONG, firstClock.type());
        assertEquals(1_250, firstClock.integer());
        RuntimeState clocked = runtime.snapshot();

        Value difference = call(
                "difftime",
                Value.of(IrType.LONG_LONG, 90),
                Value.of(IrType.LONG_LONG, 40)
        );
        assertEquals(IrType.DOUBLE, difference.type());
        assertEquals(50.0, difference.real());

        Value firstTime = call("time", pointer(0));
        assertEquals(IrType.LONG_LONG, firstTime.type());
        assertEquals(1_700_000_000L, firstTime.integer());

        long destination = zeroed(IrType.LONG_LONG.sizeBytes());
        Value secondTime = call("time", pointer(destination));
        assertEquals(1_700_000_060L, secondTime.integer());
        assertEquals(1_700_000_060L, runtime.read(destination, IrType.LONG_LONG).integer());

        RuntimeState completed = runtime.snapshot();
        assertEquals(0, initial.clockReads());
        assertEquals(0, initial.timeReads());
        assertEquals(1, clocked.clockReads());
        assertEquals(1_250, clocked.clockTicks());
        assertEquals(1, completed.clockReads());
        assertEquals(2, completed.timeReads());
        assertEquals(1_700_000_060L, completed.epochSeconds());
        assertEquals(1, timeSource.clockCalls);
        assertEquals(2, timeSource.timeCalls);
    }

    @Test
    void calendarFunctionsShareOneStableStaticTmBufferAndNormalizeMktime() {
        long timer = zeroed(IrType.LONG_LONG.sizeBytes());
        runtime.write(timer, Value.of(IrType.LONG_LONG, 0));

        long utc = call("gmtime", pointer(timer)).integer();
        assertTrue(utc != 0);
        assertTm(utc, 0, 0, 0, 1, 0, 70, 4, 0, 0);
        RuntimeState epochSnapshot = runtime.snapshot();

        runtime.write(timer, Value.of(IrType.LONG_LONG, 86_400));
        long local = call("localtime", pointer(timer)).integer();
        assertEquals(utc, local);
        assertTm(local, 0, 0, 0, 2, 0, 70, 5, 1, 0);
        assertEquals(1, snapshotTmField(epochSnapshot, utc, 3));
        assertEquals(utc, epochSnapshot.timeStructPointer());

        writeTm(local, 120, 61, 25, 32, 12, 99, 0, 0, 0);
        long normalized = call("mktime", pointer(local)).integer();
        long expected = LocalDateTime.of(2000, 2, 2, 2, 3)
                .toEpochSecond(ZoneOffset.UTC);
        assertEquals(expected, normalized);
        assertTm(local, 0, 3, 2, 2, 1, 100, 3, 32, 0);

        long output = zeroed(32);
        long format = cString("%Y-%m-%d %H:%M:%S");
        Value length = call("strftime", pointer(output), size(32), pointer(format), pointer(local));
        assertEquals(19, length.integer());
        assertEquals("2000-02-02 02:03:00", runtime.readCString(output));

        long small = zeroed(8);
        assertEquals(0, call("strftime", pointer(small), size(8), pointer(format), pointer(local)).integer());
    }

    @Test
    void unsupportedLocaleDependentStrftimeConversionFailsExplicitly() {
        long value = zeroed(9 * IrType.INT.sizeBytes());
        long output = zeroed(32);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> call(
                        "strftime",
                        pointer(output),
                        size(32),
                        pointer(cString("%c")),
                        pointer(value)
                )
        );

        assertTrue(error.getMessage().contains("Unsupported strftime conversion"), error::getMessage);
    }

    private Value call(String name, Value... arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, List.of(arguments)).orElseThrow();
        return ((Returned) result).value();
    }

    private void assertTm(long address, int... expected) {
        assertEquals(9, expected.length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], runtime.read(address + index * 4L, IrType.INT).integer(),
                    "struct tm field " + index);
        }
    }

    private void writeTm(long address, int... fields) {
        assertEquals(9, fields.length);
        for (int index = 0; index < fields.length; index++) {
            runtime.write(address + index * 4L, Value.of(IrType.INT, fields[index]));
        }
    }

    private int snapshotTmField(RuntimeState state, long address, int field) {
        DebugRuntime.MemoryBlock block = state.libraryMemory().stream()
                .filter(candidate -> candidate.address() == address)
                .findFirst()
                .orElseThrow();
        byte[] bytes = java.util.HexFormat.of().parseHex(block.bytes());
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(field * 4);
    }

    private long cString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        long address = zeroed(bytes.length + 1);
        for (int index = 0; index < bytes.length; index++) {
            runtime.writeByte(address + index, bytes[index]);
        }
        runtime.writeByte(address + bytes.length, 0);
        return address;
    }

    private long zeroed(int size) {
        return runtime.allocateZeroed(size, 1, "heap", "time-test");
    }

    private Value pointer(long value) {
        return Value.of(IrType.POINTER, value);
    }

    private Value size(long value) {
        return Value.of(IrType.UNSIGNED_LONG_LONG, value);
    }

    private static final class SequenceTimeSource implements DebugTimeSource {
        private final long[] clocks;
        private final long[] times;
        private final ZoneId zone;
        private int clockCalls;
        private int timeCalls;

        private SequenceTimeSource(long[] clocks, long[] times, ZoneId zone) {
            this.clocks = clocks;
            this.times = times;
            this.zone = zone;
        }

        @Override
        public long clockTicks() {
            return clocks[clockCalls++];
        }

        @Override
        public long epochSeconds() {
            return times[timeCalls++];
        }

        @Override
        public ZoneId localZone() {
            return zone;
        }
    }
}
