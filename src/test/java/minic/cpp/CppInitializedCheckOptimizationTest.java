package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.IrCheckInitializedInstruction;
import minic.compiler.ir.optimize.InitializedCheckEliminationPass;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppInitializedCheckOptimizationTest {
    @TempDir Path temporary;
    private static final String LOOP = """
            #include <stdio.h>
            int main(){
                int total=0;
                for(int i=0;i<20;i++){
                    int value;
                    if(i%2) value=i; else value=i+1;
                    total+=value;
                }
                printf("%d\\n",total);
                return 0;
            }
            """;

    static Stream<Arguments> validPrograms() {
        return Stream.of(Arguments.of("all-paths-loop", LOOP, "200\n"),
                Arguments.of("volatile-and-alias", """
                        #include <stdio.h>
                        int change(int *p){*p+=4;return *p;}
                        int main(){int value=3;volatile int observed=7;int changed=change(&value);
                        int sum=value+observed+changed;printf("%d %d\\n",sum,value);return 0;}
                        """, "21 7\n"),
                Arguments.of("nested-shadowing-and-short-circuit", """
                        #include <stdio.h>
                        int main(){int x=2;int sum=0;for(int i=0;i<4;i++){
                        int x=i+5;if(x>5 && x<8)sum+=x;}printf("%d %d\\n",x,sum);return 0;}
                        """, "2 13\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("validPrograms")
    void nativeOptimizationMatchesOriginalDebugAndGpp(String name, String text, String expected) throws Exception {
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), LanguageMode.CPP17_ALGORITHM).run(name, text, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals(expected, normalize(outcome.stdout()), report::describe));
        var source = new SourceFile(name+".cpp", text);
        IrResult original = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM).runToIr();
        IrResult optimized = pipeline().apply(original).ir();
        assertTrue(checks(optimized) < checks(original));
        var run = runNative(source, optimized, name+"-optimized");
        assertEquals(0, run.exitCode(), run::stderr);
        assertEquals(expected, normalize(run.stdout()));
    }

    static Stream<Arguments> failingPrograms() {
        return Stream.of(
                Arguments.of("initial-read", "int main(){int value;return value;}", 101),
                Arguments.of("loop-redeclare", "int main(){int sum=0;for(int i=0;i<2;i++){int value;if(i==0)value=3;sum+=value;}return sum;}",101),
                Arguments.of("callee-trap-return", "int broken(int condition){int value;if(condition)value=7;return value;}int main(){return broken(0)+5;}",106));
    }

    @ParameterizedTest(name="{0}") @MethodSource("failingPrograms")
    void unknownFlagsStillTakeTheSameNativeTrapAndSourceDebugFailure(String name, String text, int expected) throws Exception {
        // These are MiniC's diagnostic extensions; undefined C++ reads are not compared with G++.
        var source = new SourceFile(name+".cpp",text);
        var original = new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var optimized = pipeline().apply(original).ir();
        assertTrue(checks(optimized)>0);
        assertEquals(expected,runNative(source,original,name+"-baseline").exitCode());
        assertEquals(expected,runNative(source,optimized,name+"-optimized").exitCode());
        var debug=DebugApi.fromIr(source,original,"");
        for(int steps=0;debug.canNext() && steps<2000;steps++)debug.next();
        assertEquals(Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"), debug.current().stop()::error);
    }

    @Test void sourceIrAndDebugReplayRemainUnchanged() {
        var source=new SourceFile("checks.cpp", LOOP);
        var original=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var functions=original.functions();
        long count=checks(original);
        var assembler=new Assembler(original,pipeline());
        assembler.assemble();
        assertTrue(assembler.succeeded(),()->assembler.errors().toString());
        assertTrue(checks(assembler.input().irResult())<count);
        assertSame(functions,original.functions());
        assertEquals(count,checks(original));
        var debug=DebugApi.fromIr(source,original,"");
        var history=new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for(int steps=0;debug.canNext() && steps<10000;steps++)history.add(debug.next());
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status());
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
    }

    private BoundedProcess.Result runNative(SourceFile source, IrResult ir, String name) throws Exception {
        Path directory=temporary.resolve(name);
        var assembler=new Assembler(ir);
        var obj=new ObjBuilder(source,assembler,directory,"program");
        var linker=new Linker(source,obj,directory,"program");
        new CompilerApi(List.of(assembler,obj,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+obj.errors()+" / "+linker.errors());
        var run=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
        assertFalse(run.timedOut(),run::stderr);
        assertFalse(run.outputExceeded(),run::stderr);
        return run;
    }
    private static IrOptimizationPipeline pipeline(){
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new InitializedCheckEliminationPass()));
    }
    private static long checks(IrResult ir){
        return ir.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream())
                .filter(IrCheckInitializedInstruction.class::isInstance).count();
    }
    private static String normalize(String text){return text.replace("\r\n","\n");}
}
