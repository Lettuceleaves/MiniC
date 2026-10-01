package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.MemoryInstruction.IrAddressOfLocalInstruction;
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
final class CppPrivateAddressNormalizationTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("compound-local-and-parameter", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int n){int sum=0;while(n>0){sum+=n;--n;}return sum;}", "calc(4)", 10, true),
                Arguments.of("compound-c-mode", LanguageMode.C,
                        "int calc(int n){int sum=0;while(n>0){sum+=n;--n;}return sum;}", "calc(4)", 10, true),
                Arguments.of("declaration-repeats-in-loop", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int n){int sum=0;for(int i=0;i<n;++i){int local=i;local+=2;sum+=local;}return sum;}", "calc(4)", 14, true),
                Arguments.of("parameter-earlier-read", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int n){int before=n;n+=2;return before*10+n;}", "calc(4)", 46, true),
                Arguments.of("six-argument-home", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int a,int b,int c,int d,int e,int f){int before=f;f+=a;return before*10+f;}", "calc(1,2,3,4,5,6)", 67, true),
                Arguments.of("recursive-parameter-isolation", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int n){if(n<1)return 0;int before=n;n-=1;return before+calc(n);}", "calc(4)", 10, true),
                Arguments.of("pointer-value-versus-pointer-slot", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int n){int values[4]={1,2,3,4};int *p=values;p+=n;return *p;}", "calc(2)", 3, true),
                Arguments.of("whole-first-indirect-write", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int n){int value;*(&value)=n;return value;}", "calc(7)", 7, true),
                Arguments.of("escaped-local", LanguageMode.CPP17_ALGORITHM,
                        "void change(int*p){*p=9;} int calc(int n){int value=n;change(&value);value+=1;return value;}", "calc(4)", 10, false),
                Arguments.of("escaped-parameter", LanguageMode.CPP17_ALGORITHM,
                        "void change(int*p){*p=9;} int calc(int n){int before=n;change(&n);n+=1;return before*10+n;}", "calc(4)", 50, false),
                Arguments.of("volatile-control", LanguageMode.CPP17_ALGORITHM,
                        "int calc(int n){volatile int value=n;value+=2;return value;}", "calc(4)", 6, false));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void privateAddressOptimizationPreservesNativeDebugAndGxx(String name, LanguageMode mode,
            String declarations, String invocation, int value, boolean mustNormalize) throws Exception {
        String text = "#include <stdio.h>\n" + declarations + "\nint main(){printf(\"%d\\n\"," + invocation + ");return 0;}";
        String expected = value + "\n";
        SourceFile source = new SourceFile(name + ".cpp", text);
        IrResult original = new CompilerApi(source, mode).runToIr();
        var originalFunctions = List.copyOf(original.functions());
        IrResult normalized = new PrivateAddressNormalizationPass().apply(original);
        var originalCalc = original.findFunction("calc").orElseThrow();
        var normalizedCalc = normalized.findFunction("calc").orElseThrow();
        if (mustNormalize) assertNotEquals(originalCalc, normalizedCalc, "fixture must exercise exact private address normalization");
        else assertSame(originalCalc, normalizedCalc, "escaping/volatile object retains its source operation");
        var pipeline = new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,
                List.of(new PrivateAddressNormalizationPass(), new InitializedCheckEliminationPass(),
                        new LocalScalarPromotionPass(), new ConstantPropagationPass(), new DeadCodeEliminationPass()));
        IrResult optimized = pipeline.apply(original).ir();
        var report = new CppDifferentialHarness(temporary.resolve("reference"),
                CppDifferentialHarness.referenceCompiler(System.getenv()), CppDifferentialHarness.Limits.defaults(), mode)
                .run(name, text, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(result -> assertEquals(expected, normalize(result.stdout()), report::describe));
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
        assertEquals(originalFunctions, original.functions());
    }

    private static String normalize(String value) { return value.replace("\r\n", "\n"); }
}
