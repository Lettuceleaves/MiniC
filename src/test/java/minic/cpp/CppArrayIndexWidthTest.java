package minic.cpp;

import minic.SourceRange;
import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction;
import minic.compiler.ir.instruction.MemoryInstruction.IrElementAddressInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppArrayIndexWidthTest {
    private static final SourceRange R = new SourceRange(1,0,1,10);
    @TempDir Path temporary;

    @ParameterizedTest @EnumSource(OptimizationLevel.class)
    void narrowConstantIndicesDoNotInheritUnrelatedHighRegisterBits(OptimizationLevel level)throws Exception {
        String source="""
                #include <stdio.h>
                int main(){
                    int values[4]={10,20,30,40};
                    volatile unsigned int marker=305419776U;
                    int first=values[(unsigned char)2];
                    marker=305419776U;
                    int second=(values+1)[(signed char)-1];
                    marker=305419776U;
                    int third=values[(unsigned short)3];
                    marker=305419776U;
                    int fourth=(values+2)[(short)-1];
                    printf("%d %d %d %d\\n",first,second,third,fourth);
                    return 0;
                }
                """;
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM,level).run("narrow-indices",source,"");
        assertEquals("30 10 40 20\n",report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),report::describe);
        assertTrue(report.passed(),report::describe);
    }

    static Stream<Arguments> wideIndices(){
        return Stream.of(
                Arguments.of(IrType.UNSIGNED_INT,4294967295L),
                Arguments.of(IrType.UNSIGNED_INT,2147483648L),
                Arguments.of(IrType.LONG_LONG,4294967296L),
                Arguments.of(IrType.LONG_LONG,-4294967296L),
                Arguments.of(IrType.UNSIGNED_LONG_LONG,4294967301L),
                Arguments.of(IrType.INT,-3L),
                Arguments.of(IrType.SHORT,-1L),
                Arguments.of(IrType.UNSIGNED_CHAR,255L));
    }

    @ParameterizedTest @MethodSource("wideIndices")
    void elementAddressIrPreservesIndexWidthAndSignednessWithoutDereferencing(IrType type,long index)throws Exception {
        // IR arithmetic contract: no out-of-bounds C++ pointer expression or giant allocation
        // is executed. Native numeric addresses are compared with the interpreter's IR result.
        long base=17179869184L;
        var pointer=new IrTemporary("address",IrType.POINTER);
        var integer=new IrTemporary("bits",IrType.UNSIGNED_LONG_LONG);
        var equal=new IrTemporary("equal",IrType.INT);
        var function=new IrFunction("main",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",List.of(
                new IrElementAddressInstruction(pointer,new IrConstant(base,IrType.POINTER),new IrConstant(index,type),MiniType.CHAR,1,R),
                new IrCastInstruction(integer,pointer,R),
                new IrBinaryInstruction(equal,IrBinaryOperator.EQUAL,integer,new IrConstant(base+index,IrType.UNSIGNED_LONG_LONG),R),
                new IrReturnInstruction(equal,R)))),R);
        var ir=new IrResult(List.of(function),List.of(),Set.of());
        IrVerifier.verify(ir);
        var source=new SourceFile("ir-address-width.mc","int main(){return 1;}");
        var debug=DebugApi.fromIr(source,ir,"");
        for(int count=0;debug.canNext()&&count<100;count++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(1,debug.current().runtime().termination().status());
        for(OptimizationLevel level:OptimizationLevel.values()){
            var assembler=new Assembler(ir,new IrOptimizationPipeline(level,List.of()));
            Path output=temporary.resolve(level.name());
            var object=new ObjBuilder(source,assembler,output,"address");
            var linker=new Linker(source,object,output,"address");
            new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
            assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
            var result=BoundedProcess.run(List.of(output.resolve("address.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
            assertFalse(result.timedOut());assertFalse(result.outputExceeded());
            assertEquals(1,result.exitCode(),level+" "+type+" "+index+" "+result.stderr());
        }
    }
}
