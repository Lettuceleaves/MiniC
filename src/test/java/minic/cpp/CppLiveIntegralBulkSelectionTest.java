package minic.cpp;

import minic.compiler.*;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Verify both actual bulk selection and the volatile/nontrivial/generic fallback boundaries. */
final class CppLiveIntegralBulkSelectionTest {
    static Stream<Arguments> liveRanges(){return Stream.of(
        Arguments.of("raw-pointer-copy","#include <algorithm>\nint main(){int a[4]={1,2,3,4};int b[4]={};std::copy(a,a+4,b);return b[3];}"),
        Arguments.of("vector-erase","#include <vector>\nint main(){std::vector<int> v(8,3);v.erase(v.begin()+2,v.begin()+5);return v[2];}"),
        Arguments.of("vector-copy-assignment","#include <vector>\nint main(){std::vector<int> a(6,3);std::vector<int> b(4,7);a=b;return a[2];}"));}
    @ParameterizedTest(name="{0}") @MethodSource("liveRanges")
    void integralLiveAssignmentActuallySelectsBulkMove(String name,String source){
        assertTrue(ir(name,source).externalFunctionNames().contains("memmove"),"nonempty eligible range must select the bulk path");
    }
    @ParameterizedTest @ValueSource(strings={"nontrivial-fallback.cpp","conversion-fallback.cpp","proxy-fallback.cpp"})
    void otherAssignmentsDoNotEnterBulkPath(String name)throws Exception{
        assertFalse(ir(name,CppLiveIntegralBulkTest.resource(name)).externalFunctionNames().contains("memmove"));
    }
    @Test void recursiveVolatileAccessRemainsElementWiseAndMarkedVolatile()throws Exception{
        var ir=ir("volatile",CppLiveIntegralBulkTest.resource("volatile-fallback.cpp"));
        assertFalse(ir.externalFunctionNames().contains("memmove"));
        var instructions=ir.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream()).toList();
        assertTrue(instructions.stream().anyMatch(i->i instanceof IrLoadPointerInstruction load&&load.volatileAccess()));
        assertTrue(instructions.stream().anyMatch(i->i instanceof IrStorePointerInstruction store&&store.volatileAccess()));
    }
    static IrResult ir(String name,String text){return new CompilerApi(new SourceFile(name+".cpp",text),LanguageMode.CPP17_ALGORITHM).runToIr();}
}
