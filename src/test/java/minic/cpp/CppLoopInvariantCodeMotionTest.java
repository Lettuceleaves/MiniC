package minic.cpp;

import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppLoopInvariantCodeMotionTest {
    @TempDir Path temporary;
    private record Program(String name,String source,boolean motion) { }
    private static List<Program> programs(){return List.of(
            new Program("zero-and-many-iterations","""
                    #include <stdio.h>
                    int work(int a,int b,int n){int x=a;int y=b;int total=0;
                    for(int i=0;i<n;i++){total=total+x*y;}return total;}
                    int main(){volatile int a=3;volatile int b=7;printf("%d %d\\n",work(a,b,0),work(a,b,9));return 0;}
                    """,true),
            new Program("conditional-invariants","""
                    #include <stdio.h>
                    int work(int a,int b,int n){int x=a;int y=b;int total=0;
                    for(int i=0;i<n;i++){if(i%2)total=total+(x*y+4);else total=total+(x>y);}return total;}
                    int main(){volatile int a=3;volatile int b=7;printf("%d %d\\n",work(a,b,0),work(a,b,9));return 0;}
                    """,true),
            new Program("unsigned32-overflow","""
                    #include <stdio.h>
                    unsigned int work(unsigned int a,unsigned int b,int n){unsigned int x=a;unsigned int y=b;unsigned int total=0;
                    for(int i=0;i<n;i++){total=total+((x*y)^(x-y));}return total;}
                    int main(){volatile unsigned int a=4000000001U;volatile unsigned int b=17U;
                    printf("%u %u\\n",work(a,b,0),work(a,b,9));return 0;}
                    """,true),
            new Program("unsigned64-fullwidth","""
                    #include <stdio.h>
                    unsigned long long work(unsigned long long a,unsigned long long b,int n){unsigned long long x=a;unsigned long long y=b;unsigned long long total=0;
                    for(int i=0;i<n;i++){total=total+((x*y)^(x|y));}return total;}
                    int main(){volatile unsigned long long a=18446744004990074889ULL;volatile unsigned long long b=305419896ULL;
                    printf("%llu %llu\\n",work(a,b,0),work(a,b,9));return 0;}
                    """,true),
            new Program("snapshot-before-parameter-mutation","""
                    #include <stdio.h>
                    int work(int a,int n){int old=a;int total=0;for(int i=0;i<n;i++){a=a+1;total=total+old*7;}return total+a;}
                    int main(){volatile int a=3;printf("%d %d\\n",work(a,0),work(a,4));return 0;}
                    """,true),
            new Program("separate-loops","""
                    #include <stdio.h>
                    int work(int a,int b,int n){int x=a;int y=b;int total=0;
                    for(int i=0;i<n;i++){total=total+x*y;}for(int j=0;j<n;j++){total=total+x+y;}return total;}
                    int main(){volatile int a=3;volatile int b=7;printf("%d %d\\n",work(a,b,0),work(a,b,3));return 0;}
                    """,true),
            new Program("mutable-parameter","""
                    #include <stdio.h>
                    int work(int a,int n){int total=0;for(int i=0;i<n;i++){a=a+1;total=total+a*3;}return total;}
                    int main(){volatile int a=3;printf("%d %d\\n",work(a,0),work(a,4));return 0;}
                    """,false),
            new Program("calls-and-memory","""
                    #include <stdio.h>
                    volatile int observed=1;
                    int effect(int *p){*p=*p+1;observed=observed+1;return *p;}
                    int work(int a,int n){int total=0;for(int i=0;i<n;i++){total=total+effect(&a)*observed;}return total;}
                    int main(){volatile int a=3;printf("%d\\n",work(a,4));return 0;}
                    """,false),
            new Program("division-and-shift-stay-conditional","""
                    #include <stdio.h>
                    int work(int a,int b,int n){int x=a;int y=b;int total=0;
                    for(int i=0;i<n;i++){total=total+x/y;}return total;}
                    int shift(int a,int b,int n){int x=a;int y=b;int total=0;
                    for(int i=0;i<n;i++){total=total+(x>>y);}return total;}
                    int main(){volatile int a=7;printf("%d %d %d %d\\n",work(a,0,0),work(a,2,3),shift(a,-1,0),shift(a,1,3));return 0;}
                    """,false));}

    static Stream<Arguments> sourcePrograms(){return programs().stream().flatMap(program->Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).map(mode->Arguments.of(mode,program.name(),program.source(),program.motion())));}

    @ParameterizedTest(name="{0}: {1}") @MethodSource("sourcePrograms")
    void movedOrRetainedLoopsAgreeWithBaselineSourceDebugAndGxx(LanguageMode mode,String name,String text,boolean motion)throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        String expected=report.outcomes().values().iterator().next().stdout().replace("\r\n","\n");
        var source=new SourceFile(name+".cpp",text);var original=new CompilerApi(source,mode).runToIr();
        // Retain a fixed pre-LICM control as well as independent and default-pipeline motion.
        var prepared=withoutLicm().apply(original).ir();
        var transformed=onlyLicm().apply(prepared).ir();
        var defaultPipeline=IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED).apply(original);
        var currentControl=currentWithoutLicm().apply(original);
        var expectedControlPasses=defaultPipeline.passNames().stream().filter(pass->!pass.equals("loop-invariant-code-motion")).toList();
        assertEquals(defaultPipeline.passNames().size()-1,expectedControlPasses.size(),"default pipeline must contain LICM exactly once");
        assertEquals(expectedControlPasses,currentControl.passNames(),"control must differ from today's default pipeline only by removing LICM");
        var defaultResult=defaultPipeline.ir();
        if(motion){assertNotSame(prepared,transformed);assertTrue(movedComputations(prepared,transformed)>0,"fixture must move a computation from the loop to a preheader");}
        else assertSame(prepared,transformed);
        if(motion)assertTrue(movedComputations(currentControl.ir(),defaultResult)>0,"default pipeline must also move an existing loop computation");
        else assertEquals(currentControl.ir().functions(),defaultResult.functions(),"LICM must not alter this retained-loop fixture in the current pipeline");
        int index=0;
        for(var variant:List.of(prepared,transformed,currentControl.ir(),defaultResult)) {
            Path directory=temporary.resolve("variant-"+index++);
            var result=runNative(source,variant,directory);
            assertEquals(0,result.exitCode(),result::stderr);assertEquals(expected,result.stdout().replace("\r\n","\n"));
        }
        assertEquals(original.displayNames(),transformed.displayNames());
    }

    @Test void sourceDebugHistoryIsUntouchedAndUninitializedLoopLifetimesStillFail()throws Exception {
        var source=new SourceFile("history.cpp",programs().getFirst().source());
        var ir=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();var functions=ir.functions();
        onlyLicm().apply(IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED).apply(ir).ir());
        var debug=DebugApi.fromIr(source,ir,"");var history=new ArrayList<Debugger.Context>();history.add(debug.current());
        for(int steps=0;debug.canNext()&&steps<20000;steps++)history.add(debug.next());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status());
        assertEquals("0 189\n",debug.current().runtime().stdout().replace("\r\n","\n"));
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
        assertSame(functions,ir.functions());
        // This is a MiniC initialization diagnostic, never a C++ undefined-behavior oracle.
        var invalid=new SourceFile("uninitialized.cpp","int main(){int total=0;for(int i=0;i<2;i++){int value;if(i==0)value=3;total=total+value;}return total;}");
        var invalidIr=new CompilerApi(invalid,LanguageMode.CPP17_ALGORITHM).runToIr();
        var optimized=onlyLicm().apply(IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED).apply(invalidIr).ir()).ir();
        assertEquals(101,runNative(invalid,optimized,temporary.resolve("uninitialized")).exitCode());
        var failing=DebugApi.fromIr(invalid,invalidIr,"");for(int steps=0;failing.canNext()&&steps<5000;steps++)failing.next();
        assertEquals(Debugger.Status.FAILED,failing.current().stop().status());assertTrue(failing.current().stop().error().contains("uninitialized"));
    }

    private BoundedProcess.Result runNative(SourceFile source,IrResult ir,Path directory)throws Exception {
        var assembler=new Assembler(ir,new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of()));
        var object=new ObjBuilder(source,assembler,directory,"program");var link=new Linker(source,object,directory,"program");
        new CompilerApi(List.of(assembler,object,link)).runThrough(link);
        assertTrue(link.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+link.errors());
        var result=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
        assertFalse(result.timedOut());assertFalse(result.outputExceeded());return result;
    }
    private static IrOptimizationPipeline onlyLicm(){return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new LoopInvariantCodeMotionPass()));}
    private static IrOptimizationPipeline withoutLicm(){return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(
            new InitializedCheckEliminationPass(),new SmallFunctionInliningPass(),new LocalScalarPromotionPass(),
            new ConstantPropagationPass(),new DeadCodeEliminationPass()));}
    // Explicit current-pipeline control, guarded above by the actual default pass sequence.
    // Keep withoutLicm() fixed: it separately proves the original LICM motion contract.
    private static IrOptimizationPipeline currentWithoutLicm(){return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(
            new DirectCallResolutionPass(),new PrivateAddressNormalizationPass(),new InitializedCheckEliminationPass(),
            new EarlySimplificationPass(),new SmallFunctionInliningPass(),new PostInliningScalarPreparationPass(),
            new LocalScalarPromotionPass(),new ReadOnlyParameterPromotionPass(),new ConstantPropagationPass(),
            new NonZeroCheckEliminationPass(),new BlockCopyPropagationPass(),new DeadCodeEliminationPass(),
            new ControlFlowSimplificationPass(),new AdjacentResultForwardingPass()));}
    private static long movedComputations(IrResult before,IrResult after){
        var locations=new HashMap<String,String>();
        for(var function:before.functions())for(var block:function.blocks())for(var instruction:block.instructions())
            if(instruction instanceof IrBinaryInstruction binary)locations.put(function.name()+":"+binary.result().name(),block.label());
        long count=0;
        for(var function:after.functions())for(var block:function.blocks())for(var instruction:block.instructions())
            if(instruction instanceof IrBinaryInstruction binary){
                String previous=locations.get(function.name()+":"+binary.result().name());
                if(previous!=null&&!block.label().equals(previous))count++;
            }
        return count;
    }
}
