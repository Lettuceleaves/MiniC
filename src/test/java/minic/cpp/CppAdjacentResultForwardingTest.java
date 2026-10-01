package minic.cpp;

import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.*;
import minic.debug.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppAdjacentResultForwardingTest {
    @TempDir Path temporary;
    @ParameterizedTest(name="{0}: {1}") @MethodSource("minic.cpp.CppBlockCopyPropagationTest#programs")
    void operationResultsCanReuseScalarHomesWithoutChangingSnapshots(LanguageMode mode,String name,String body,boolean removesCopies)throws Exception {
        String text="#include <stdio.h>\nint main(){"+body+"return 0;}";
        var source=new SourceFile(name+".cpp",text);var original=new CompilerApi(source,mode).runToIr();
        var originalFunctions=original.functions();
        var prepare=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new PrivateAddressNormalizationPass(),
                new InitializedCheckEliminationPass(),new LocalScalarPromotionPass(),new ReadOnlyParameterPromotionPass(),new ConstantPropagationPass(),
                new BlockCopyPropagationPass(),new DeadCodeEliminationPass(),new ControlFlowSimplificationPass()));
        var before=prepare.apply(original).ir();
        var forwarding=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new AdjacentResultForwardingPass()));
        var after=forwarding.apply(before).ir();
        if(removesCopies){
            long oldCount=before.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream()).filter(IrMoveInstruction.class::isInstance).count();
            long newCount=after.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream()).filter(IrMoveInstruction.class::isInstance).count();
            assertTrue(newCount<oldCount,"writes to scalar homes must lose actual copy instructions: "+oldCount+" -> "+newCount);
        }
        var report=new CppDifferentialHarness(temporary.resolve("reference"),CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        String expected=report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n");
        var output=temporary.resolve("optimized");var assembler=new Assembler(before,forwarding);
        var object=new ObjBuilder(source,assembler,output,"program");var linker=new Linker(source,object,output,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
        var actual=BoundedProcess.run(List.of(output.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
        assertFalse(actual.timedOut());assertFalse(actual.outputExceeded());assertEquals(0,actual.exitCode(),actual::stderr);
        assertEquals(expected,actual.stdout().replace("\r\n","\n"));
        var debug=DebugApi.fromIr(source,after,"");for(int i=0;debug.canNext()&&i<50000;i++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,debug.current().runtime().stdout().replace("\r\n","\n"));
        assertSame(originalFunctions,original.functions());
    }
}
