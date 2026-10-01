package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
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
final class CppReadOnlyParameterPromotionTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("loop-bound", "int calc(int n){int sum=0;for(int i=0;i<n;++i)sum+=i;return sum+n;}", "", "calc(4)", 10, true),
                Arguments.of("pointer-base", "int calc(int*p,int n){int sum=0;for(int i=0;i<n;++i)sum+=p[i];return sum+p[0];}", "int values[4]={7,2,9,4};", "calc(values,4)", 29, true),
                Arguments.of("sixth-argument", "int calc(int a,int b,int c,int d,int e,int f){return a+b+c+d+e+f+f;}", "", "calc(1,2,3,4,5,6)", 27, true),
                Arguments.of("recursive-frames", "int calc(int n){if(n<1)return 0;int old=n;return old+calc(n-1);}", "", "calc(5)", 15, true),
                Arguments.of("narrow-source-types", "int calc(unsigned char u,short s){return (int)u+u+s+s;}", "", "calc(250,-12)", 476, true),
                Arguments.of("volatile-pointee-keeps-each-access", "int calc(volatile int*p,int n){int sum=0;for(int i=0;i<n;++i){sum+=*p;*p=sum;}return *p;}", "volatile int value=2;", "calc(&value,3)", 8, true),
                Arguments.of("addressed-parameter-keeps-live-home", "int calc(int p){int before=p;int*a=&p;*a=9;return p+before;}", "", "calc(4)", 13, false),
                Arguments.of("volatile-parameter-keeps-live-home", "int calc(volatile int p){return p+p;}", "", "calc(4)", 8, false)
        ).flatMap(value -> Stream.of(LanguageMode.C, LanguageMode.CPP17_ALGORITHM)
                .map(mode -> {
                    Object[] data = value.get();
                    return Arguments.of(mode, data[0], data[1], data[2], data[3], data[4], data[5]);
                }));
    }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void promotionPreservesActualExecution(LanguageMode mode, String name, String declarations, String setup,
            String invocation, int answer, boolean mustPromote) throws Exception {
        String text = "#include <stdio.h>\n" + declarations + "\nint main(){" + setup + "printf(\"%d\\n\"," + invocation + ");return 0;}";
        String expected = answer + "\n";
        var source = new SourceFile(name + ".cpp", text);
        var original = new CompilerApi(source, mode).runToIr();
        var originalFunctions = original.functions();
        var promoted = new ReadOnlyParameterPromotionPass().apply(original);
        var before = original.findFunction("calc").orElseThrow();
        var after = promoted.findFunction("calc").orElseThrow();
        if (mustPromote) assertNotEquals(before, after, "fixture must exercise actual parameter capture");
        else assertSame(before, after, "aliased/volatile parameter is ineligible");
        var reference = new CppDifferentialHarness(temporary.resolve("reference"),
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(), mode)
                .run(name, text, "");
        assertTrue(reference.passed(), reference::describe);
        reference.outcomes().values().forEach(outcome -> assertEquals(expected, normalize(outcome.stdout()), reference::describe));

        var pipeline = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,
                List.of(new ReadOnlyParameterPromotionPass(), new InitializedCheckEliminationPass(),
                        new LocalScalarPromotionPass(), new ConstantPropagationPass(), new DeadCodeEliminationPass()));
        IrResult optimized = pipeline.apply(original).ir();
        Path output = temporary.resolve("optimized");
        var assembler = new Assembler(original, pipeline);
        var object = new ObjBuilder(source, assembler, output, "program");
        var linker = new Linker(source, object, output, "program");
        new CompilerApi(List.of(assembler, object, linker)).runThrough(linker);
        assertTrue(linker.succeeded(), () -> "asm=" + assembler.errors() + ", obj=" + object.errors() + ", link=" + linker.errors());
        var actual = BoundedProcess.run(List.of(output.resolve("program.exe").toString()), temporary, "", Duration.ofSeconds(15), 65536);
        assertFalse(actual.timedOut()); assertFalse(actual.outputExceeded());
        assertEquals(0, actual.exitCode(), actual::stderr);
        assertEquals(expected, normalize(actual.stdout()));

        var debug = DebugApi.fromIr(source, optimized, "");
        for (int steps=0; debug.canNext() && steps<20000; steps++) debug.next();
        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals(expected, normalize(debug.current().runtime().stdout()));
        assertSame(originalFunctions, original.functions());
    }

    private static String normalize(String value) { return value.replace("\r\n", "\n"); }
}
