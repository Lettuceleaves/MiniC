package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.model.IrType;
import minic.debug.DebugRuntime.Value;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Pending unified acceptance: compile only during the feature implementation phase. */
final class DebugScanfPrefixTest {
    private final DebugSystemLibrary library=new DebugSystemLibrary();
    private DebugRuntime runtime(String input){return new DebugRuntime(new DebugProgram(new SourceFile("scanf.mc",""),new IrResult(List.of())),input);}
    private Value pointer(long address){return Value.of(IrType.POINTER,address);}
    private Value call(DebugRuntime runtime,String name,Value... args){return ((DebugLibraryCallResult.Returned)library.invoke(name,runtime,List.of(args)).orElseThrow()).value();}
    private long text(DebugRuntime runtime,String value){int n=value.getBytes(StandardCharsets.UTF_8).length+1;long at=runtime.allocateZeroed(n,1,"heap","text");runtime.writeCString(at,value,n);return at;}
    private long storage(DebugRuntime runtime){return runtime.allocateZeroed(8,8,"heap","result");}
    @Test void decimalAndLiteralDelimitersLeaveTheSuffixForGetchar() {
        DebugRuntime runtime=runtime("12,34x");long a=storage(runtime),b=storage(runtime);
        assertEquals(2,call(runtime,"scanf",pointer(text(runtime,"%d,%d")),pointer(a),pointer(b)).integer());
        assertEquals(12,runtime.read(a,IrType.INT).integer());assertEquals(34,runtime.read(b,IrType.INT).integer());
        assertEquals('x',call(runtime,"getchar").integer());
    }
    @Test void baseDetectionUnsignedSignsAndFieldWidthsUseNumericPrefixes() {
        DebugRuntime runtime=runtime("-0x2a! -1? 123");long value=storage(runtime);
        assertEquals(1,call(runtime,"scanf",pointer(text(runtime,"%i")),pointer(value)).integer());
        assertEquals(-42,runtime.read(value,IrType.INT).integer());assertEquals('!',call(runtime,"getchar").integer());
        assertEquals(1,call(runtime,"scanf",pointer(text(runtime,"%u")),pointer(value)).integer());
        assertEquals(0xffffffffL,runtime.read(value,IrType.UNSIGNED_INT).integer());assertEquals('?',call(runtime,"getchar").integer());
        assertEquals(1,call(runtime,"scanf",pointer(text(runtime,"%2d")),pointer(value)).integer());
        assertEquals(12,runtime.read(value,IrType.INT).integer());assertEquals('3',call(runtime,"getchar").integer());
    }
    @Test void matchingFailureLeavesItsConflictingCharacterAndEofReturnsEof() {
        DebugRuntime runtime=runtime("x");long value=storage(runtime);
        assertEquals(0,call(runtime,"scanf",pointer(text(runtime,"%d")),pointer(value)).integer());
        assertEquals(0,call(runtime,"scanf",pointer(text(runtime,"?"))).integer());
        assertEquals('x',call(runtime,"getchar").integer());
        assertEquals(-1,call(runtime,"scanf",pointer(text(runtime,"%d")),pointer(value)).integer());
    }
    @Test void floatingMantissaMustExistAndIncompleteExponentIsAnInputItem() {
        DebugRuntime runtime=runtime("e+1 1e+x");long value=storage(runtime);
        assertEquals(0,call(runtime,"scanf",pointer(text(runtime,"%lf")),pointer(value)).integer());
        assertEquals('e',call(runtime,"getchar").integer());
        assertEquals(1,call(runtime,"scanf",pointer(text(runtime,"%lf")),pointer(value)).integer());
        assertEquals(1.0,runtime.read(value,IrType.DOUBLE).real());
        assertEquals(0,call(runtime,"scanf",pointer(text(runtime,"%lf")),pointer(value)).integer());
        assertEquals('x',call(runtime,"getchar").integer());
    }
    @Test void floatScanfDoesNotRoundViaDoubleAndSscanfSharesTheScanner() {
        DebugRuntime runtime=runtime("");long value=storage(runtime),suffix=storage(runtime);
        long input=text(runtime,"1.00000005960464477539062500000000000000000001z");
        assertEquals(2,call(runtime,"sscanf",pointer(input),pointer(text(runtime,"%f%c")),pointer(value),pointer(suffix)).integer());
        assertEquals(0x3f800001,Float.floatToRawIntBits((float)runtime.read(value,IrType.FLOAT).real()));
        assertEquals('z',runtime.read(suffix,IrType.CHAR).integer());assertEquals(0,runtime.stdinCursor());
    }
}
