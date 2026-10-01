package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
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
final class CppBottomUpInliningTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("nested-constant-divisor", LanguageMode.C, """
                        #include <stdio.h>
                        int element_size(){return 4;}
                        int block_size(){return 512/element_size();}
                        int wrapper(int n){return n+block_size();}
                        int main(){int input=getchar()-'0';printf("%d\\n",wrapper(input));return 0;}
                        """, "3", "131\n", true),
                Arguments.of("nested-addressed-parameter-repeated-site", LanguageMode.C, """
                        #include <stdio.h>
                        int leaf(int value){int *p=&value;*p+=2;return value;}
                        int middle(int value){return leaf(value);}
                        int wrapper(int value){return middle(value);}
                        int main(){int sum=0;for(int i=0;i<4;++i)sum+=wrapper(i);printf("%d\\n",sum);return 0;}
                        """, "", "14\n", true),
                Arguments.of("nested-construction-cleanup", LanguageMode.CPP17_ALGORITHM, """
                        #include <stdio.h>
                        int trace=0;
                        struct Item {
                          int value;
                          Item(int n):value(n){trace=trace*10+n;}
                          ~Item(){trace=trace*10+value+4;}
                          int get()const{return value;}
                        };
                        int inner(int n){Item item(n);return item.get();}
                        int wrapper(int n){return inner(n);}
                        int main(){int sum=0;for(int i=1;i<=2;++i)sum+=wrapper(i);printf("%d %d\\n",sum,trace);return 0;}
                        """, "", "3 1526\n", false));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void optimizedNativeAndInterpreterPreserveSourceBehavior(String name, LanguageMode mode,
            String text, String stdin, String expected, boolean noInternalMainCalls) throws Exception {
        SourceFile source = new SourceFile(name + ".cpp", text);
        IrResult original = new CompilerApi(source, mode).runToIr();
        var originalFunctions = List.copyOf(original.functions());
        var pipeline = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,
                List.of(new InitializedCheckEliminationPass(), new SmallFunctionInliningPass(),
                        new ConstantPropagationPass(), new NonZeroCheckEliminationPass(), new DeadCodeEliminationPass()));
        IrResult optimized = pipeline.apply(original).ir();
        if (noInternalMainCalls) {
            var main = optimized.findFunction("main").orElseThrow();
            assertTrue(main.blocks().stream().flatMap(b -> b.instructions().stream())
                    .filter(IrCallInstruction.class::isInstance).map(IrCallInstruction.class::cast)
                    .noneMatch(call -> optimized.findFunction(call.calleeName()).isPresent()),
                    "All nested pure wrappers must disappear from main");
        }
        var report = new CppDifferentialHarness(temporary.resolve("reference"),
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(), mode)
                .run(name, text, stdin);
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(result -> assertEquals(expected, normalize(result.stdout()), report::describe));

        Path output = temporary.resolve("optimized");
        var assembler = new Assembler(original, pipeline);
        var object = new ObjBuilder(source, assembler, output, "program");
        var linker = new Linker(source, object, output, "program");
        new CompilerApi(List.of(assembler, object, linker)).runThrough(linker);
        assertTrue(linker.succeeded(), () -> "asm=" + assembler.errors() + ", obj=" + object.errors() + ", link=" + linker.errors());
        var nativeResult = BoundedProcess.run(List.of(output.resolve("program.exe").toString()), temporary,
                stdin, Duration.ofSeconds(15), 65536);
        assertFalse(nativeResult.timedOut());
        assertFalse(nativeResult.outputExceeded());
        assertEquals(0, nativeResult.exitCode(), nativeResult::stderr);
        assertEquals(expected, normalize(nativeResult.stdout()));
        var debug = DebugApi.fromIr(source, optimized, stdin);
        for (int step=0; debug.canNext() && step<20000; step++) debug.next();
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(expected, normalize(debug.current().runtime().stdout()));
        assertEquals(originalFunctions, original.functions(), "Native optimization must not mutate source/debug IR");
    }

    private static String normalize(String value) { return value.replace("\r\n", "\n"); }
}
