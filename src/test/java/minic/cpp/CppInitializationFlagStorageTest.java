package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
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

@Timeout(90)
final class CppInitializationFlagStorageTest {
    @TempDir Path temporary;

    static Stream<Arguments> checkedPrograms(){
        return Stream.of(
                Arguments.of("initial-read","int main(){int x;return x;}",101),
                Arguments.of("loop-redeclaration","int main(){int sum=0;for(int i=0;i<2;i++){int x;if(i==0)x=3;sum+=x;}return sum;}",101),
                Arguments.of("callee-return","int broken(int c){int x;if(c)x=7;return x;}int main(){return broken(0)+5;}",106));
    }

    @ParameterizedTest(name="{0}") @MethodSource("checkedPrograms")
    void optimizedFramesPreserveRequiredNativeChecks(String name,String text,int expected)throws Exception{
        // These undefined C++ reads exercise MiniC's diagnostics, so G++ is not an oracle here.
        var source=new SourceFile(name+".cpp",text);
        var ir=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        for(var level:OptimizationLevel.values()){
            Path directory=temporary.resolve(level.name());
            var assembler=new Assembler(ir,IrOptimizationPipeline.forLevel(level));
            var object=new ObjBuilder(source,assembler,directory,"program");
            var linker=new Linker(source,object,directory,"program");
            new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
            assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
            var result=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
            assertFalse(result.timedOut());assertFalse(result.outputExceeded());assertEquals(expected,result.exitCode(),level+" "+result.stderr());
        }
        var debug=DebugApi.fromIr(source,ir,"");
        for(int i=0;debug.canNext()&&i<3000;i++)debug.next();
        assertEquals(Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"),debug.current().stop()::error);
    }

    static Stream<Arguments> validPrograms(){
        return Stream.of(
                Arguments.of("loop-and-shadowing","""
                        #include <stdio.h>
                        int main(){int sum=0;for(int i=0;i<20;i++){int value=i+1;sum+=value;}
                            {int sum=3;printf("%d ",sum);}printf("%d\\n",sum);return 0;}
                        ""","3 210\n"),
                Arguments.of("alias-and-volatile","""
                        #include <stdio.h>
                        void fill(int *p){*p=7;}
                        int main(){int value;fill(&value);volatile int observed=2;observed+=value;
                            printf("%d %d\\n",value,observed);return 0;}
                        ""","7 9\n"));
    }

    @ParameterizedTest(name="{0}") @MethodSource("validPrograms")
    void omittedFlagsDoNotChangeCppResults(String name,String text,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM,OptimizationLevel.OPTIMIZED).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe));
    }
}
