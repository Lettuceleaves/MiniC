package minic.cpp;

import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.*;
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
final class CppLocalScalarPromotionTest {
    @TempDir Path temporary;
    private record Program(String name, String source, String stdout) { }
    private static List<Program> programs() { return List.of(
            new Program("loop-and-branch", """
                    #include <stdio.h>
                    int main(){int total=0;for(int i=0;i<20;i++){int value;
                    if(i%2)value=i;else value=i+1;total+=value;}printf("%d\\n",total);return 0;}
                    """, "200\n"),
            new Program("continue-and-redeclaration", """
                    #include <stdio.h>
                    int main(){int total=0;for(int i=0;i<8;i++){int value;
                    if(i%2==0)continue;value=i*3;total+=value;}printf("%d\\n",total);return 0;}
                    """, "48\n"),
            new Program("snapshot-across-local-and-parameter-alias", """
                    #include <stdio.h>
                    int touch(int *p){*p+=5;return *p;}
                    int probe(int p){int old=p;touch(&p);return old*100+p;}
                    int main(){int x=7;int old=x;touch(&x);printf("%d %d\\n",old+x,probe(3));return 0;}
                    """, "19 308\n"),
            new Program("narrow-and-unsigned-width", """
                    #include <stdio.h>
                    int main(){unsigned char u=250;short s=-300;unsigned int w=4000000000U;
                    unsigned long long big=4294967301ULL;bool b=0;
                    for(int i=0;i<4;i++){u=(unsigned char)(u+3);s=(short)(s+2);w+=10U;big+=4294967296ULL;b=!b;}
                    printf("%u %d %u %llu %d\\n",(unsigned int)u,(int)s,w,big,(int)b);return 0;}
                    """, "6 -292 4000000040 21474836485 0\n"),
            new Program("private-pointer-and-escaped-array", """
                    #include <stdio.h>
                    int main(){int values[4]={2,4,6,8};int *p=values;int total=0;
                    for(int i=0;i<4;i++){total+=*p;p++;}printf("%d %d\\n",total,p==values+4);return 0;}
                    """, "20 1\n"),
            new Program("volatile-and-shadowed-locals", """
                    #include <stdio.h>
                    int main(){volatile int value=2;int total=0;for(int i=0;i<4;i++){
                    int value=i+3;total+=value;}value=value+1;printf("%d %d\\n",value,total);return 0;}
                    """, "3 18\n"),
            new Program("zero-iteration-and-multiple-return", """
                    #include <stdio.h>
                    int sum(int n){int total=5;for(int i=0;i<n;i++){total+=i;if(total>9)return total;}return total;}
                    int main(){printf("%d %d\\n",sum(0),sum(10));return 0;}
                    """, "5 11\n")); }

    static Stream<Arguments> validPrograms() {
        return programs().stream().flatMap(program -> Stream.of(LanguageMode.C, LanguageMode.CPP17_ALGORITHM)
                .map(mode -> Arguments.of(mode, program.name(), program.source(), program.stdout())));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("validPrograms")
    void promotedNativeMatchesBaselineCurrentOptimizedOriginalDebugAndCpp(
            LanguageMode mode, String name, String text, String expected) throws Exception {
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), mode).run(name, text, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals(expected, normalize(outcome.stdout()), report::describe));
        var source = new SourceFile(name + ".cpp", text);
        var original = new CompilerApi(source, mode).runToIr();
        var functions = original.functions();
        var proof = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new InitializedCheckEliminationPass())).apply(original).ir();
        var promotion = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new LocalScalarPromotionPass())).apply(proof).ir();
        assertTrue(accesses(promotion) < accesses(proof), "fixture must perform actual promotion");
        int variant = 0;
        for (var pipeline : List.of(IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED), promotedPipeline())) {
            var result = runNative(source, original, pipeline, "optimized-" + variant++);
            assertEquals(0, result.exitCode(), result::stderr);
            assertEquals(expected, normalize(result.stdout()));
        }
        assertSame(functions, original.functions());
        assertTrue(accesses(original) > 0, "source/debug IR must retain original objects");
    }

    static Stream<Arguments> invalidPrograms() {
        return Stream.of(LanguageMode.C, LanguageMode.CPP17_ALGORITHM).flatMap(mode -> Stream.of(
                Arguments.of(mode, "redeclared-on-backedge", "int main(){int total=0;for(int i=0;i<2;i++){int value;if(i==0)value=3;total+=value;}return total;}"),
                Arguments.of(mode, "missing-branch-assignment", "int main(){int value;if(0)value=7;return value;}")));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("invalidPrograms")
    void unknownInitializationStillTrapsWithoutManufacturingAValue(LanguageMode mode, String name, String text) throws Exception {
        // MiniC diagnostic extension only: uninitialized C/C++ programs are not G++ oracles.
        var source = new SourceFile(name + ".cpp", text);
        var original = new CompilerApi(source, mode).runToIr();
        var optimized = promotedPipeline().apply(original).ir();
        assertTrue(optimized.functions().stream().flatMap(function -> function.blocks().stream()).flatMap(block -> block.instructions().stream())
                .anyMatch(IrCheckInitializedInstruction.class::isInstance));
        assertEquals(101, runNative(source, original, IrOptimizationPipeline.forLevel(OptimizationLevel.BASELINE), "baseline").exitCode());
        assertEquals(101, runNative(source, original, promotedPipeline(), "promoted").exitCode());
        var debug = DebugApi.fromIr(source, original, "");
        for (int step = 0; debug.canNext() && step < 5000; step++) debug.next();
        assertEquals(Debugger.Status.FAILED, debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("uninitialized"), debug.current().stop()::error);
    }

    @Test void nativePromotionDoesNotRemoveSourceObjectsFromDebugHistory() {
        var source = new SourceFile("history.cpp", programs().getFirst().source());
        var original = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM).runToIr();
        var assembler = new Assembler(original, promotedPipeline()); assembler.assemble();
        assertTrue(assembler.succeeded(), () -> assembler.errors().toString());
        assertTrue(accesses(assembler.optimizationResult().ir()) < accesses(original));
        var debug = DebugApi.fromIr(source, original, ""); var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        for (int step = 0; debug.canNext() && step < 20_000; step++) history.add(debug.next());
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status());
        assertEquals("200\n", normalize(debug.current().runtime().stdout()));
        for (int index = history.size() - 2; index >= 0; index--) assertSame(history.get(index), debug.previous());
        for (int index = 1; index < history.size(); index++) assertSame(history.get(index), debug.next());
    }

    private BoundedProcess.Result runNative(SourceFile source, IrResult original, IrOptimizationPipeline pipeline, String name) throws Exception {
        Path directory = temporary.resolve(name);
        var assembler = new Assembler(original, pipeline);
        var object = new ObjBuilder(source, assembler, directory, "program");
        var linker = new Linker(source, object, directory, "program");
        new CompilerApi(List.of(assembler, object, linker)).runThrough(linker);
        assertTrue(linker.succeeded(), () -> assembler.errors() + " / " + object.errors() + " / " + linker.errors());
        var result = BoundedProcess.run(List.of(directory.resolve("program.exe").toString()), temporary, "", Duration.ofSeconds(10), 65_536);
        assertFalse(result.timedOut()); assertFalse(result.outputExceeded()); return result;
    }

    private static IrOptimizationPipeline promotedPipeline() {
        return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of(new InitializedCheckEliminationPass(),
                new SmallFunctionInliningPass(), new LocalScalarPromotionPass(), new ConstantPropagationPass(), new DeadCodeEliminationPass()));
    }
    private static long accesses(IrResult ir) {
        return ir.functions().stream().flatMap(function -> function.blocks().stream()).flatMap(block -> block.instructions().stream())
                .filter(instruction -> instruction instanceof IrDeclareLocalInstruction || instruction instanceof IrLoadLocalInstruction || instruction instanceof IrStoreLocalInstruction).count();
    }
    private static String normalize(String text) { return text.replace("\r\n", "\n"); }
}
