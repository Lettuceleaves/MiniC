package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
final class DebugSnprintfProviderTest {
    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("snprintf-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "");
        library = new DebugSystemLibrary();
    }

    @Test
    void snprintfReturnsTheRequiredLengthAndAlwaysTerminatesANonEmptyBuffer() {
        long format = cString("value=%d");
        long full = zeroed(32);
        assertEquals(8, call("snprintf", pointer(full), size(32), pointer(format), integer(42)).integer());
        assertEquals("value=42", runtime.readCString(full));

        long truncated = zeroed(6);
        assertEquals(8, call("snprintf", pointer(truncated), size(6), pointer(format), integer(42)).integer());
        assertEquals("value", runtime.readCString(truncated));

        long terminatorOnly = zeroed(1);
        runtime.writeByte(terminatorOnly, 'x');
        assertEquals(8, call("snprintf", pointer(terminatorOnly), size(1),
                pointer(format), integer(42)).integer());
        assertEquals(0, runtime.readUnsignedByte(terminatorOnly));

        assertEquals(8, call("snprintf", pointer(0), size(0), pointer(format), integer(42)).integer());
    }

    @Test
    void unsupportedFormattingIsAStableFailureInsteadOfAnApproximation() {
        long destination = zeroed(32);

        IllegalStateException width = assertThrows(
                IllegalStateException.class,
                () -> call("sprintf", pointer(destination), pointer(cString("%08d")), integer(7))
        );
        assertTrue(width.getMessage().contains("Unsupported printf flags"), width::getMessage);

        IllegalStateException conversion = assertThrows(
                IllegalStateException.class,
                () -> call("sscanf", pointer(cString("7")), pointer(cString("%o")), pointer(destination))
        );
        assertTrue(conversion.getMessage().contains("Unsupported scanf conversion"), conversion::getMessage);
    }

    private Value call(String name, Value... arguments) {
        DebugLibraryCallResult result = library.invoke(name, runtime, List.of(arguments)).orElseThrow();
        return ((Returned) result).value();
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
        return runtime.allocateZeroed(size, 1, "heap", "snprintf-test");
    }

    private Value pointer(long value) {
        return Value.of(IrType.POINTER, value);
    }

    private Value integer(int value) {
        return Value.of(IrType.INT, value);
    }

    private Value size(long value) {
        return Value.of(IrType.UNSIGNED_LONG_LONG, value);
    }
}
