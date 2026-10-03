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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugStdlibStateParityTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("stdlib-state-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "");
        library = new DebugSystemLibrary();
    }

    @Test
    void reallocPreservesThePrefixAndSupportsNullAndZeroSize() {
        long original = call("malloc", size(4)).integer();
        runtime.writeByte(original, 1);
        runtime.writeByte(original + 1, 2);
        runtime.writeByte(original + 2, 3);
        runtime.writeByte(original + 3, 4);

        long grown = call("realloc", pointer(original), size(8)).integer();
        assertNotEquals(original, grown);
        assertEquals(1, runtime.heap().size());
        assertEquals(4, runtime.heap().getFirst().initializedBytes());
        assertEquals(1, runtime.readUnsignedByte(grown));
        assertEquals(4, runtime.readUnsignedByte(grown + 3));

        long shrunk = call("realloc", pointer(grown), size(2)).integer();
        assertEquals(1, runtime.readUnsignedByte(shrunk));
        assertEquals(2, runtime.readUnsignedByte(shrunk + 1));

        long allocated = call("realloc", pointer(0), size(3)).integer();
        assertNotEquals(0, allocated);
        assertEquals(2, runtime.heap().size());

        assertEquals(0, call("realloc", pointer(allocated), size(0)).integer());
        assertEquals(1, runtime.heap().size());
    }

    @Test
    void reallocFailureLeavesTheOriginalAllocationAliveAndSetsEnomem() {
        long original = call("malloc", size(4)).integer();
        runtime.writeByte(original, 77);

        long result = call("realloc", pointer(original), size(16L * 1024 * 1024 + 1)).integer();

        assertEquals(0, result);
        assertEquals(DebugLibrarySupport.ENOMEM, runtime.errno());
        assertEquals(77, runtime.readUnsignedByte(original));
        assertEquals(1, runtime.heap().size());
    }

    @ParameterizedTest(name = "{0}({1}) == {2}")
    @MethodSource("absoluteCases")
    void implementsTheAbsoluteValueFamily(String function, IrType type, long input, long expected) {
        Value result = call(function, Value.of(type, input));
        assertEquals(type, result.type());
        assertEquals(expected, result.integer());
    }

    @Test
    void randMatchesTheWindowsCrtSequenceAndSrandResetsIt() {
        assertEquals(41, call("rand").integer());
        assertEquals(18467, call("rand").integer());
        assertEquals(6334, call("rand").integer());

        call("srand", Value.of(IrType.UNSIGNED_INT, 1));
        assertEquals(41, call("rand").integer());
    }

    @Test
    void errnoAccessorSharesTheSameCellAsProviderUpdatesAndSnapshots() {
        long errno = call("minic_errno_location").integer();
        RuntimeState initial = runtime.snapshot();
        assertEquals(0, runtime.read(errno, IrType.INT).integer());
        assertEquals(1, initial.libraryMemory().size());

        runtime.write(errno, Value.of(IrType.INT, 7));
        assertEquals(7, runtime.errno());
        RuntimeState written = runtime.snapshot();

        runtime.setErrno(DebugLibrarySupport.ERANGE);
        assertEquals(DebugLibrarySupport.ERANGE, runtime.read(errno, IrType.INT).integer());
        assertEquals(7, written.errno());
        assertEquals(0, initial.errno());
    }

    @Test
    void randomStateIsCapturedByValue() {
        RuntimeState before = runtime.snapshot();
        call("rand");
        RuntimeState after = runtime.snapshot();

        assertEquals(1, before.randomState());
        assertNotEquals(before.randomState(), after.randomState());
        assertEquals(1, before.randomState());
    }

    private Value call(String name, Value... arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, List.of(arguments)).orElseThrow();
        return ((Returned) result).value();
    }

    private Value pointer(long value) {
        return Value.of(IrType.POINTER, value);
    }

    private Value size(long value) {
        return Value.of(IrType.UNSIGNED_LONG_LONG, value);
    }

    private static Stream<Arguments> absoluteCases() {
        return Stream.of(
                Arguments.of("abs", IrType.INT, -7, 7),
                Arguments.of("labs", IrType.LONG, -2_000_000_000L, 2_000_000_000L),
                Arguments.of("llabs", IrType.LONG_LONG, -9_000_000_000L, 9_000_000_000L)
        );
    }
}
