package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Retained for the final unified acceptance run; not executed during implementation. */
final class DebugPrintfFormattingTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach void setup() {
        runtime = new DebugRuntime(new DebugProgram(new SourceFile("printf-format.mc", ""), new IrResult(List.of())), "");
        library = new DebugSystemLibrary();
    }

    @Test void signedFlagsWidthAndIntegerPrecisionRespectCPrecedence() {
        assertEquals("+0000042|      0042|42      | 7", format("%+08d|%010.4d|%-8d|% d", integer(42), integer(42), integer(42), integer(7)));
        assertEquals("|0|0xff|0XFF|0755", format("%.0d|%#.0o|%#x|%#X|%#o", integer(0), integer(0), integer(255), integer(255), integer(493)));
    }

    @Test void dynamicWidthPrecisionConsumeArgumentsBeforeTheValue() {
        assertEquals("12.50     |4.500000|      0023", format("%*.*f|%.*f|%*.*d", integer(-10), integer(2), real(12.5), integer(-1), real(4.5), integer(10), integer(4), integer(23)));
        assertEquals("+2.|3.000|3", format("%+#.0f|%#.*g|%.0g", real(2.25), integer(4), real(3), real(3)));
    }

    @Test void floatingFormatsUseExactBinaryValueAndLegacyExponentDigits() {
        assertEquals("2.67|3|0|-0.00", format("%.2f|%.0f|%.0f|%.2f", real(2.675), real(2.5), real(0.25), real(-0.0)));
        assertEquals("1.250e+001|1.250E-003|1.23e+006|0.000123|1.00e+003", format("%.3e|%.3E|%.3g|%.3g|%.2e", real(12.5), real(0.00125), real(1234567), real(0.000123), real(999.99)));
        assertEquals("1.#INF00|-1.#INF00|1.#INF|1.#INFe+000", format("%f|%f|%g|%.4e", real(Double.POSITIVE_INFINITY), real(Double.NEGATIVE_INFINITY), real(Double.POSITIVE_INFINITY), real(Double.POSITIVE_INFINITY)));
    }

    @Test void integerLengthsFollowWindowsLlp64AndNarrowPromotions() {
        assertEquals("-1|255|-1|65535|1|4294967297|18446744073709551615", format("%hhd|%hhu|%hd|%hu|%ld|%lld|%llu", integer(255), integer(-1), integer(65535), integer(-1), wide(4294967297L), wide(4294967297L), wide(-1)));
        assertEquals("000000000000002A", format("%p", pointer(42)));
    }

    @Test void narrowStringsPreserveUtf8BytesWidthAndPrecision() {
        long text = bytes("你xy");
        assertEquals("[     你][你xy]", decoded(format("[%8.3s][%s]", pointer(text), pointer(text))));
        long format = bytes("你:%s");
        assertEquals(9, call("printf", pointer(format), pointer(text)).integer());
        assertEquals("你:你xy", runtime.stdout());
        assertEquals(0, call("puts", pointer(text)).integer());
        assertEquals("你:你xy你xy\n", runtime.stdout());
        long tiny = runtime.allocateZeroed(3, 1, "heap", "partial-code-unit");
        assertEquals(3, call("snprintf", pointer(tiny), wide(3), pointer(bytes("%.3s")), pointer(text)).integer());
        assertEquals(0xe4, runtime.readUnsignedByte(tiny));
        assertEquals(0xbd, runtime.readUnsignedByte(tiny + 1));
        assertEquals(0, runtime.readUnsignedByte(tiny + 2));
    }

    @Test void boundedStringPrecisionDoesNotReadPastANonterminatedArray() {
        long text = runtime.allocateZeroed(2, 1, "heap", "not-terminated");
        runtime.writeByte(text, 'a'); runtime.writeByte(text + 1, 'b');
        assertEquals("ab", format("%.2s", pointer(text)));
    }

    @Test void sscanfReadsTheOriginalNarrowBytesWithoutASecondUtf8Encoding() {
        long destination = runtime.allocateZeroed(8, 1, "heap", "word");
        assertEquals(1, call("sscanf", pointer(bytes("你!")), pointer(bytes("%3s")), pointer(destination)).integer());
        assertEquals("你", decoded(runtime.readCString(destination)));
    }

    @Test void malformedOrOversizedFormatsRemainExplicitFailures() {
        assertThrows(IllegalStateException.class, () -> format("%*d", integer(3)));
        assertThrows(IllegalStateException.class, () -> format("%1000000000d", integer(1)));
        assertThrows(IllegalStateException.class, () -> format("%ls", pointer(0)));
        assertThrows(IllegalStateException.class, () -> format("%n", pointer(0)));
    }

    private String format(String specification, Value... values) {
        long out = runtime.allocateZeroed(4096, 1, "heap", "formatted");
        Value[] arguments = new Value[values.length + 2];
        arguments[0] = pointer(out); arguments[1] = pointer(bytes(specification));
        System.arraycopy(values, 0, arguments, 2, values.length);
        call("sprintf", arguments);
        return runtime.readCString(out);
    }
    private Value call(String name, Value... values) {
        return ((DebugLibraryCallResult.Returned) library.invoke(name, runtime, List.of(values)).orElseThrow()).value();
    }
    private long bytes(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        long address = runtime.allocateZeroed(bytes.length + 1, 1, "heap", "format");
        for (int i = 0; i < bytes.length; i++) runtime.writeByte(address + i, bytes[i]);
        return address;
    }
    private String decoded(String bytes) { return new String(bytes.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8); }
    private Value pointer(long value) { return Value.of(IrType.POINTER, value); }
    private Value integer(long value) { return Value.of(IrType.INT, value); }
    private Value wide(long value) { return Value.of(IrType.LONG_LONG, value); }
    private Value real(double value) { return Value.of(IrType.DOUBLE, value); }
}
