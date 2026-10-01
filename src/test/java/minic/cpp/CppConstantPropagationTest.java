package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.optimize.ConstantPropagationPass;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Executes this pass explicitly so its coverage is independent of the default native pass list. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppConstantPropagationTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("integer-arithmetic", """
                        #include <stdio.h>
                        int main(){printf("%d %d %d\\n",(6*7)+(20/5),((3<<4)|2)^1,(~7)&31);return 0;}
                        """, "46 51 24\n", true),
                Arguments.of("unsigned-width-and-casts", """
                        #include <stdio.h>
                        int main(){printf("%u %llu %llu %d %u\\n",0xffffffffu+1u,
                            0xffffffffffffffffULL/2ULL,0xffffffffffffffffULL%2ULL,
                            0xffffffffffffffffULL>0ULL,(unsigned int)(unsigned char)-1);return 0;}
                        """, "0 9223372036854775807 1 1 255\n", true),
                Arguments.of("loop-memory-and-volatile", """
                        #include <stdio.h>
                        volatile int counter=0;
                        int next(){counter=counter+1;return counter;}
                        int main(){int total=0;int previous=0;
                            for(int i=0;i<4;i++){int current=next();total=total+previous;previous=current;}
                            printf("%d %d %d\\n",total,previous,counter);return 0;}
                        """, "6 4 4\n", false),
                Arguments.of("mutable-parameter-callee-snapshot", """
                        #include <stdio.h>
                        typedef int (*Callback)(int);
                        int first(int value){return value+100;}
                        int second(int value){return value+200;}
                        int change(Callback *slot){*slot=second;return 7;}
                        int run(Callback selected){return selected(change(&selected));}
                        int main(){printf("%d\\n",run(first));return 0;}
                        """, "107\n", false),
                Arguments.of("mutable-scalar-argument-snapshot", """
                        #include <stdio.h>
                        int trace=0;
                        int pair(int first,int second){return first*10+second;}
                        int run(int value){
                            int result=pair((trace=1,value),(trace=2,++value));
                            return (trace==2 && result==12) || (trace==1 && result==22);
                        }
                        int main(){printf("%d\\n",run(1));return 0;}
                        """, "1\n", false),
                Arguments.of("const-method-and-conditional", """
                        #include <stdio.h>
                        struct Box{int value;int read() const{return value+(6*7);}};
                        int choose(int flag){return flag?(3*4):(2*6);}
                        int main(){const Box value={5};printf("%d %d %d\\n",value.read(),choose(0),choose(1));return 0;}
                        """, "47 12 12\n", true)
        );
    }

    @ParameterizedTest(name = "{0}") @MethodSource("programs")
    void transformedNativeAndDebugAgreeWithBaselineAndCpp17(String name, String text, String expected,
                                                           boolean shouldFold) throws Exception {
        var report = new CppDifferentialHarness(temporary,
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(),
                LanguageMode.CPP17_ALGORITHM).run(name, text, "");
        assertTrue(report.passed(), report::describe);
        for (var result : report.outcomes().values()) assertEquals(expected, normalize(result.stdout()), report::describe);

        SourceFile source = new SourceFile(name + ".cpp", text);
        IrResult original = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM).runToIr();
        var pipeline = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new ConstantPropagationPass()));
        var optimized = pipeline.apply(original);
        assertEquals(List.of("constant-propagation"), optimized.passNames());
        if (shouldFold) assertTrue(binaryCount(optimized.ir()) < binaryCount(original), "The explicit pass must perform actual folding");

        Path directory = temporary.resolve("explicit-pass");
        var assembler = new Assembler(original, pipeline);
        var object = new ObjBuilder(source, assembler, directory, "program");
        var linker = new Linker(source, object, directory, "program");
        new CompilerApi(List.of(assembler, object, linker)).runThrough(linker);
        assertTrue(linker.succeeded(), () -> "asm=" + assembler.errors() + ", obj=" + object.errors() + ", link=" + linker.errors());
        var nativeRun = BoundedProcess.run(List.of(directory.resolve("program.exe").toString()), temporary,
                "", Duration.ofSeconds(10), 65_536);
        assertFalse(nativeRun.timedOut(), nativeRun::stderr);
        assertFalse(nativeRun.outputExceeded(), nativeRun::stderr);
        assertEquals(0, nativeRun.exitCode(), nativeRun::stderr);
        assertEquals(expected, normalize(nativeRun.stdout()));

        // A test-only interpreter oracle for transformed IR. Product source debugging still receives original IR.
        var debug = DebugApi.fromIr(source, optimized.ir(), "");
        for (int steps = 0; debug.canNext() && steps < 5000; steps++) debug.next();
        assertFalse(debug.canNext(), "Bounded interpreter step budget");
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(0, debug.current().runtime().termination().status());
        assertEquals(expected, normalize(debug.current().runtime().stdout()));
    }

    private static long binaryCount(IrResult ir) {
        return ir.functions().stream().flatMap(function -> function.blocks().stream())
                .flatMap(block -> block.instructions().stream()).filter(IrBinaryInstruction.class::isInstance).count();
    }
    private static String normalize(String text) { return text.replace("\r\n", "\n"); }
}
