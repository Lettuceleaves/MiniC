package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Preserved for the final unified run; not executed during feature implementation. */
final class DebugStandardStreamsTest {
    private final DebugSystemLibrary library = new DebugSystemLibrary();

    private DebugRuntime runtime(String input) {
        return new DebugRuntime(new DebugProgram(new SourceFile("streams.mc", ""), new IrResult(List.of())), input);
    }
    private Value call(DebugRuntime runtime, String name, Value... args) {
        return ((DebugLibraryCallResult.Returned) library.invoke(name, runtime, List.of(args)).orElseThrow()).value();
    }
    private Value integer(int value) { return Value.of(IrType.INT, value); }
    private Value pointer(long value) { return Value.of(IrType.POINTER, value); }
    private long text(DebugRuntime runtime, String text) {
        long address = runtime.allocateZeroed(text.getBytes(StandardCharsets.UTF_8).length + 1, 1, "heap", "text");
        runtime.writeCString(address, text, text.getBytes(StandardCharsets.UTF_8).length + 1);
        return address;
    }

    @Test void streamIdentityAndOutputDestinationsAreStable() {
        DebugRuntime runtime = runtime("");
        long base = call(runtime,"minic_iob_base").integer();
        assertEquals(base,call(runtime,"minic_iob_base").integer());
        assertEquals('A',call(runtime,"fputc",integer('A'),pointer(base+48)).integer());
        assertEquals('B',call(runtime,"fputc",integer('B'),pointer(base+96)).integer());
        assertEquals("A",runtime.stdout()); assertEquals("B",runtime.stderr());
        assertEquals(0,call(runtime,"fflush",pointer(0)).integer());
        assertEquals(0,call(runtime,"fflush",pointer(base+48)).integer());
    }

    @Test void scanfAndGetcharConsumeTheSameUngetcByteAsFgetc() {
        DebugRuntime runtime = runtime("A12 ");
        long base=call(runtime,"minic_iob_base").integer();
        assertEquals('A',call(runtime,"fgetc",pointer(base)).integer());
        assertEquals('7',call(runtime,"ungetc",integer('7'),pointer(base)).integer());
        long value=runtime.allocateZeroed(4,4,"heap","number");
        assertEquals(1,call(runtime,"scanf",pointer(text(runtime,"%d")),pointer(value)).integer());
        assertEquals(712,runtime.read(value,IrType.INT).integer());
        assertEquals(' ',call(runtime,"getchar").integer());
    }

    @Test void eofCanBeClearedByOneByteOfPushbackWithoutChangingItsSnapshot() {
        DebugRuntime runtime=runtime("");
        long base=call(runtime,"minic_iob_base").integer();
        assertEquals(-1,call(runtime,"fgetc",pointer(base)).integer());
        var eof=runtime.snapshot();
        assertTrue(eof.stdio().inputEof());
        assertEquals(255,call(runtime,"ungetc",integer(255),pointer(base)).integer());
        var pending=runtime.snapshot();
        assertFalse(pending.stdio().inputEof());assertEquals(255,pending.stdio().inputPushback());
        assertEquals(-1,call(runtime,"ungetc",integer('X'),pointer(base)).integer());
        assertEquals(255,call(runtime,"getchar").integer());
        assertEquals(-1,call(runtime,"getchar").integer());
        assertEquals(255,pending.stdio().inputPushback());assertTrue(eof.stdio().inputEof());
    }

    @Test void narrowIoPreservesUtf8AcrossIndividualCallsAndHistory() {
        DebugRuntime runtime=runtime("你好");
        long base=call(runtime,"minic_iob_base").integer();
        for(int i=0;i<6;++i) {
            int value=(int)call(runtime,"fgetc",pointer(base)).integer();
            assertTrue(value>=128 && value<=255);
            call(runtime,"fputc",integer(value),pointer(base+48));
        }
        assertEquals("你好",runtime.stdout());assertEquals(6,runtime.stdinCursor());
        assertEquals("e4bda0e5a5bd",runtime.snapshot().stdio().stdoutBytes());
        assertEquals(-1,call(runtime,"fgetc",pointer(base)).integer());
    }

    @Test void failedPushbackOfEofDoesNotChangeTheInput() {
        DebugRuntime runtime=runtime("X");
        long base=call(runtime,"minic_iob_base").integer();
        assertEquals(-1,call(runtime,"ungetc",integer(-1),pointer(base)).integer());
        assertEquals('X',call(runtime,"getchar").integer());
    }
}
