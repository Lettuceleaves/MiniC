package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(120)
final class CppLoopCallInliningTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() { return Stream.of(
            Arguments.of("effects-stay-in-source-order", """
                    int trace=0;
                    int helper(int value){trace=trace*10+value;return value;}
                    int run(int n){int sum=helper(1);for(int i=0;i<n;++i)sum+=helper(i+2);return sum;}
                    int main(){int answer=run(2);printf("%d %d\\n",answer,trace);return 0;}
                    """, "6 123\n"),
            Arguments.of("actual-parameter-snapshot-before-callee-write", """
                    int helper(int value,int*place){*place+=1;return value;}
                    int run(int p){int sum=helper(p,&p);for(int i=0;i<2;++i)sum+=helper(p,&p);return sum*10+p;}
                    int main(){printf("%d\\n",run(4));return 0;}
                    """, "157\n"),
            Arguments.of("local-lifetime-repeats-at-selected-site", """
                    int helper(int value){int local=value;return local+1;}
                    int run(int n){int sum=helper(0);for(int i=0;i<n;++i)sum+=helper(i+1);return sum;}
                    int main(){printf("%d\\n",run(2));return 0;}
                    """, "6\n"),
            Arguments.of("unentered-loop-does-not-evaluate-selected-call", """
                    int trace=0;
                    int helper(int value){trace+=value;return value;}
                    int run(int n){int sum=helper(1);for(int i=0;i<n;++i)sum+=helper(99);return sum;}
                    int main(){int answer=run(0);printf("%d %d\\n",answer,trace);return 0;}
                    """, "1 1\n")
    ).flatMap(argument->Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).map(mode->{
        Object[] values=argument.get();return Arguments.of(mode,values[0],values[1],values[2]);
    })); }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void hotSitePlanningDoesNotReorderObservableExecution(LanguageMode mode,String name,String body,String expected) throws Exception {
        String text="#include <stdio.h>\n"+body;
        var source=new SourceFile(name+".cpp",text);
        var original=new CompilerApi(source,mode).runToIr();
        var sourceFunctions=original.functions();
        var passes=List.<IrPass>of(new DirectCallResolutionPass(),new InitializedCheckEliminationPass(),
                new ConstantPropagationPass(),new NonZeroCheckEliminationPass(),new DeadCodeEliminationPass(),
                new ControlFlowSimplificationPass());
        var prepared=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,passes).apply(original).ir();
        var limits=new SmallFunctionInliningPass.Limits(24,6,96,512,256,1);
        var selected=new SmallFunctionInliningPass(limits).apply(prepared);
        var runBefore=prepared.findFunction("run").orElseThrow();
        var runAfter=selected.findFunction("run").orElseThrow();
        String helper=prepared.findFunction("helper").orElseThrow().name();
        assertEquals(2,runBefore.blocks().stream().flatMap(b->b.instructions().stream())
                .filter(i->i instanceof IrCallInstruction c&&c.calleeName().equals(helper)).count());
        assertEquals(1,runAfter.blocks().stream().flatMap(b->b.instructions().stream())
                .filter(i->i instanceof IrCallInstruction c&&c.calleeName().equals(helper)).count());

        var report=new CppDifferentialHarness(temporary.resolve("reference"),CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,normalize(outcome.stdout()),report::describe));
        var pipeline=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new SmallFunctionInliningPass(limits),
                new ConstantPropagationPass(),new DeadCodeEliminationPass(),new ControlFlowSimplificationPass()));
        var optimized=pipeline.apply(prepared).ir();
        var output=temporary.resolve("optimized");
        var assembler=new Assembler(prepared,pipeline);
        var object=new ObjBuilder(source,assembler,output,"program");
        var linker=new Linker(source,object,output,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
        var actual=BoundedProcess.run(List.of(output.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(15),65536);
        assertFalse(actual.timedOut());assertFalse(actual.outputExceeded());assertEquals(0,actual.exitCode(),actual::stderr);
        assertEquals(expected,normalize(actual.stdout()));
        var debug=DebugApi.fromIr(source,optimized,"");
        for(int steps=0;debug.canNext()&&steps<20000;steps++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,normalize(debug.current().runtime().stdout()));
        assertSame(sourceFunctions,original.functions());
    }
    private static String normalize(String text) { return text.replace("\r\n","\n"); }
}
