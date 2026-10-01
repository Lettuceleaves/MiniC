package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Stored for unified acceptance; not executed in the implementation-only phase. */
final class DebugStrtofRoundingTest {
    private final DebugRuntime runtime=new DebugRuntime(new DebugProgram(new SourceFile("strtof.mc",""),new IrResult(List.of())));
    private final DebugSystemLibrary library=new DebugSystemLibrary();
    private long text(String text) {
        int bytes=text.getBytes(StandardCharsets.UTF_8).length+1;
        long address=runtime.allocateZeroed(bytes,1,"heap","text");runtime.writeCString(address,text,bytes);return address;
    }
    private Value call(String name,Value... arguments) {
        return ((DebugLibraryCallResult.Returned)library.invoke(name,runtime,List.of(arguments)).orElseThrow()).value();
    }
    private Value pointer(long address){return Value.of(IrType.POINTER,address);}
    private float parse(String token){return (float)call("strtof",pointer(text(token)),pointer(0)).real();}
    @Test void roundsTheDecimalDirectlyToFloatWithoutADoubleMidpoint() {
        assertEquals(0x3f800001,Float.floatToRawIntBits(parse("1.00000005960464477539062500000000000000000001")));
        assertEquals(0xbf800001,Float.floatToRawIntBits(parse("-1.00000005960464477539062500000000000000000001")));
        assertEquals(0x3f800000,Float.floatToRawIntBits(parse("1.000000059604644775390625")));
    }
    @Test void keepsNegativeZeroAndSetsRangeErrorOnUnderflowAndOverflow() {
        runtime.setErrno(17);assertEquals(0x80000000,Float.floatToRawIntBits(parse("-0")));assertEquals(17,runtime.errno());
        parse("1e-100");assertEquals(DebugLibrarySupport.ERANGE,runtime.errno());
        runtime.setErrno(0);assertTrue(Float.isInfinite(parse("1e100")));assertEquals(DebugLibrarySupport.ERANGE,runtime.errno());
    }
    @Test void ucrtHexWithoutAnExponentAndSuffixPositionsArePreserved() {
        long input=text("  0x1aTAIL");long end=runtime.allocateZeroed(8,8,"heap","end");
        assertEquals(26.0,call("minic_ucrt_strtof",pointer(input),pointer(end)).real());
        assertEquals(input+6,runtime.read(end,IrType.POINTER).integer());
    }
    @Test void adapterAccessorUsesTheSnapshotAwareVirtualErrorCell() {
        long error=call("minic_ucrt_errno_location").integer();
        runtime.write(error,Value.of(IrType.INT,29));assertEquals(29,runtime.errno());
        var before=runtime.snapshot();runtime.setErrno(0);assertEquals(29,before.errno());
    }
}
