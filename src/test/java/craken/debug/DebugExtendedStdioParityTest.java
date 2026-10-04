package craken.debug;

import craken.compiler.SourceFile;
import craken.compiler.ir.IrResult;
import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.RuntimeState;
import craken.debug.DebugRuntime.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugExtendedStdioParityTest {
    @TempDir
    Path temporaryDirectory;

    private DebugRuntime runtime;
    private DebugSystemLibrary library;

    @BeforeEach
    void setUp() {
        SourceFile source = new SourceFile("extended-stdio-provider.mc", "");
        runtime = new DebugRuntime(new DebugProgram(source, new IrResult(List.of())), "A");
        library = new DebugSystemLibrary();
    }

    @Test
    void characterIoUsesSnapshotAwareVirtualStreams() {
        RuntimeState initial = runtime.snapshot();

        assertEquals('A', call("getchar").integer());
        assertEquals(-1, call("getchar").integer());
        assertEquals('B', call("putchar", integer(0x142)).integer());
        assertEquals(0, call("puts", pointer(cString("line"))).integer());

        RuntimeState completed = runtime.snapshot();
        assertEquals(0, initial.stdinCursor());
        assertEquals("", initial.stdout());
        assertEquals(1, completed.stdinCursor());
        assertEquals("Bline\n", completed.stdout());
    }

    @Test
    void sprintfAndSscanfUseTheSameSupportedFormatSubsetAsStandardStreams() {
        long destination = zeroed(64);
        long format = cString("%s:%d:%.1f");
        long text = cString("item");

        Value printed = call(
                "sprintf",
                pointer(destination),
                pointer(format),
                pointer(text),
                integer(-7),
                Value.of(IrType.DOUBLE, 1.26)
        );

        assertEquals("item:-7:1.3", runtime.readCString(destination));
        assertEquals("item:-7:1.3".length(), printed.integer());

        long input = cString("42 2.5 word");
        long scanFormat = cString("%d %lf %15s");
        long number = zeroed(IrType.INT.sizeBytes());
        long ratio = zeroed(IrType.DOUBLE.sizeBytes());
        long word = zeroed(16);

        Value scanned = call(
                "sscanf",
                pointer(input),
                pointer(scanFormat),
                pointer(number),
                pointer(ratio),
                pointer(word)
        );

        assertEquals(3, scanned.integer());
        assertEquals(42, runtime.read(number, IrType.INT).integer());
        assertEquals(2.5, runtime.read(ratio, IrType.DOUBLE).real());
        assertEquals("word", runtime.readCString(word));
        assertEquals(0, runtime.stdinCursor(), "sscanf must not consume virtual stdin");
    }

    @Test
    void removeAndRenameExposeSuccessAndWindowsCrtErrors() throws Exception {
        Path source = temporaryDirectory.resolve("source.txt");
        Path target = temporaryDirectory.resolve("target.txt");
        Files.writeString(source, "content");
        runtime.setErrno(7);

        assertEquals(0, call("rename", pointer(cString(source.toString())),
                pointer(cString(target.toString()))).integer());
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target));
        assertEquals(7, runtime.errno(), "successful calls must not clear errno");

        assertEquals(0, call("remove", pointer(cString(target.toString()))).integer());
        assertFalse(Files.exists(target));

        assertEquals(-1, call("remove", pointer(cString(target.toString()))).integer());
        assertEquals(DebugLibrarySupport.ENOENT, runtime.errno());

        assertEquals(-1, call("rename", pointer(cString(source.toString())),
                pointer(cString(target.toString()))).integer());
        assertEquals(DebugLibrarySupport.ENOENT, runtime.errno());

        Files.writeString(source, "source");
        Files.writeString(target, "target");
        assertEquals(-1, call("rename", pointer(cString(source.toString())),
                pointer(cString(target.toString()))).integer());
        assertEquals(DebugLibrarySupport.EEXIST, runtime.errno());
        assertEquals("source", Files.readString(source));
        assertEquals("target", Files.readString(target));
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
        return runtime.allocateZeroed(size, 1, "heap", "stdio-test");
    }

    private Value pointer(long value) {
        return Value.of(IrType.POINTER, value);
    }

    private Value integer(int value) {
        return Value.of(IrType.INT, value);
    }
}
