package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugStdlibConversionParityTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("stdlib-conversion-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "");
        library = new DebugSystemLibrary();
    }

    @ParameterizedTest(name = "{0}({1}, base {2})")
    @MethodSource("integerCases")
    void parsesIntegerPrefixesBasesSignsAndStopsAtTheFirstInvalidByte(
            String function,
            String text,
            int base,
            long expected,
            int expectedEndOffset
    ) {
        long input = cString(text);
        long endPointer = pointerSlot();

        Value result = call(function, pointer(input), pointer(endPointer), integer(base));

        assertEquals(expected, result.integer());
        assertEquals(input + expectedEndOffset, readPointer(endPointer));
    }

    @ParameterizedTest(name = "{0}({1})")
    @MethodSource("simpleConversionCases")
    void implementsAtoConversionFamily(String function, String text, long expected) {
        Value result = call(function, pointer(cString(text)));
        assertEquals(expected, result.integer());
    }

    @ParameterizedTest(name = "{0}({1})")
    @MethodSource("floatingCases")
    void parsesFloatingFormsAndWritesEndPointer(
            String function,
            String text,
            double expected,
            int expectedEndOffset
    ) {
        long input = cString(text);
        long endPointer = pointerSlot();

        Value result = call(function, pointer(input), pointer(endPointer));

        assertEquals(expected, result.real());
        assertEquals(input + expectedEndOffset, readPointer(endPointer));
    }

    @Test
    void handlesInfinityNanAndIncompleteExponent() {
        long infinity = cString("-INFINITYtail");
        long infinityEnd = pointerSlot();
        Value infinityResult = call("strtod", pointer(infinity), pointer(infinityEnd));
        assertEquals(Double.NEGATIVE_INFINITY, infinityResult.real());
        assertEquals(infinity + 9, readPointer(infinityEnd));

        long nan = cString("nan(payload)!");
        long nanEnd = pointerSlot();
        Value nanResult = call("strtod", pointer(nan), pointer(nanEnd));
        assertTrue(Double.isNaN(nanResult.real()));
        assertEquals(nan + 12, readPointer(nanEnd));

        long exponent = cString("1e+");
        long exponentEnd = pointerSlot();
        assertEquals(1.0, call("strtod", pointer(exponent), pointer(exponentEnd)).real());
        assertEquals(exponent + 1, readPointer(exponentEnd));
    }

    @Test
    void returnsOriginalPointerWhenNothingConvertsAndRejectsInvalidBaseStably() {
        long input = cString("  +xyz");
        long endPointer = pointerSlot();
        runtime.setErrno(7);

        assertEquals(0, call("strtol", pointer(input), pointer(endPointer), integer(10)).integer());
        assertEquals(input, readPointer(endPointer));
        assertEquals(7, runtime.errno());

        assertEquals(0, call("strtol", pointer(input), pointer(endPointer), integer(1)).integer());
        assertEquals(input, readPointer(endPointer));
        assertEquals(DebugLibrarySupport.EINVAL, runtime.errno());
    }

    @Test
    void clampsIntegerRangeErrorsAndUpdatesErrno() {
        assertRange("strtol", "2147483648", 10, Integer.MAX_VALUE);
        assertRange("strtol", "-2147483649", 10, Integer.MIN_VALUE);
        assertRange("strtoll", "9223372036854775808", 10, Long.MAX_VALUE);
        assertRange("strtoll", "-9223372036854775809", 10, Long.MIN_VALUE);
        assertRange("strtoul", "4294967296", 10, 0xffff_ffffL);
        assertRange("strtoull", "18446744073709551616", 10, -1L);
    }

    @Test
    void reportsFloatingOverflowAndUnderflowWithoutClearingPriorErrnoOnSuccess() {
        runtime.setErrno(7);
        call("strtod", pointer(cString("12.5")), pointer(0));
        assertEquals(7, runtime.errno());

        Value overflow = call("strtod", pointer(cString("1e9999")), pointer(0));
        assertEquals(Double.POSITIVE_INFINITY, overflow.real());
        assertEquals(DebugLibrarySupport.ERANGE, runtime.errno());

        runtime.setErrno(0);
        Value underflow = call("strtof", pointer(cString("1e-9999")), pointer(0));
        assertEquals(0.0, underflow.real());
        assertEquals(DebugLibrarySupport.ERANGE, runtime.errno());
    }

    @Test
    void atofReturnsDouble() {
        Value result = call("atof", pointer(cString(" -2.5tail")));
        assertEquals(IrType.DOUBLE, result.type());
        assertEquals(-2.5, result.real());
    }

    private void assertRange(String function, String text, int base, long expected) {
        runtime.setErrno(0);
        Value result = call(function, pointer(cString(text)), pointer(0), integer(base));
        assertEquals(expected, result.integer());
        assertEquals(DebugLibrarySupport.ERANGE, runtime.errno());
    }

    private Value call(String name, Value... arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, List.of(arguments)).orElseThrow();
        return ((Returned) result).value();
    }

    private long cString(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.ISO_8859_1);
        long address = runtime.allocateZeroed(encoded.length + 1, 1, "heap", "test-string");
        for (int index = 0; index < encoded.length; index++) {
            runtime.writeByte(address + index, encoded[index]);
        }
        return address;
    }

    private long pointerSlot() {
        return runtime.allocateZeroed(IrType.POINTER.sizeBytes(), IrType.POINTER.sizeBytes(),
                "heap", "endptr");
    }

    private long readPointer(long address) {
        return runtime.read(address, IrType.POINTER).integer();
    }

    private Value pointer(long value) {
        return Value.of(IrType.POINTER, value);
    }

    private Value integer(int value) {
        return Value.of(IrType.INT, value);
    }

    private static Stream<Arguments> integerCases() {
        return Stream.of(
                Arguments.of("strtol", "  -0x10tail", 0, -16L, 7),
                Arguments.of("strtol", "0779", 0, 63L, 3),
                Arguments.of("strtol", "z!", 36, 35L, 1),
                Arguments.of("strtoll", "9223372036854775807!", 10, Long.MAX_VALUE, 19),
                Arguments.of("strtoll", "-9223372036854775808!", 10, Long.MIN_VALUE, 20),
                Arguments.of("strtoul", "-1!", 10, 0xffff_ffffL, 2),
                Arguments.of("strtoull", "18446744073709551615!", 10, -1L, 20),
                Arguments.of("strtol", "0x", 0, 0L, 1)
        );
    }

    private static Stream<Arguments> simpleConversionCases() {
        return Stream.of(
                Arguments.of("atoi", " -42tail", -42L),
                Arguments.of("atol", "2147483647", 2147483647L),
                Arguments.of("atoll", "-9223372036854775808", Long.MIN_VALUE)
        );
    }

    private static Stream<Arguments> floatingCases() {
        return Stream.of(
                Arguments.of("strtod", " -12.5tail", -12.5, 6),
                Arguments.of("strtod", "0x1.8p+1z", 3.0, 8),
                Arguments.of("strtof", "1.25rest", 1.25, 4)
        );
    }
}
