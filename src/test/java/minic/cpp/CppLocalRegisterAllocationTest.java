package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppLocalRegisterAllocationTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("calls-stack-arguments-and-live-snapshots", """
                        #include <stdio.h>
                        int churn(int a,int b,int c,int d,int e,int f){int part=(a+b)*(c+d);return (part^e)+f;}
                        int scale(int x){return x*9+4;}
                        int execute(int (*fn)(int,int,int,int,int,int),int seed){
                            return scale(seed)+fn(seed+1,2,3,4,5,6)+scale(seed+2);}
                        int main(){printf("%d\\n",execute(churn,5));return 0;}
                        """, "183\n"),
                Arguments.of("callee-snapshot-before-argument-mutation", """
                        #include <stdio.h>
                        int twice(int x){return x*2;} int triple(int x){return x*3;}
                        int change(int (**slot)(int)){*slot=triple;return 4;}
                        int invoke(int (*operation)(int)){int first=operation(change(&operation));return first+operation(2);}
                        int main(){printf("%d\\n",invoke(twice));return 0;}
                        """, "14\n"),
                Arguments.of("narrow-wide-divide-shift-and-bool", """
                        #include <stdio.h>
                        unsigned char bump(unsigned char x){return (unsigned char)(x+17);}
                        short negate(short x){return (short)(0-x);}
                        long long wide(long long x){return (x/7)*3+x%7;}
                        int main(){unsigned long long high=0x8000000000000040ULL;bool present=(bool)high;
                            printf("%d %d %d %d %d\\n",bump(255),negate(-32767),
                                (int)(high>>63),wide(-100)==-44,(int)present);return 0;}
                        """, "16 32767 1 1 1\n"),
                Arguments.of("aggregate-copy-hidden-arguments-and-loop", """
                        #include <stdio.h>
                        struct Pair{int left;int right;Pair changed(int a,int b,int c,int d,int e)const{
                            Pair result={left+a+b+c,right+d+e};return result;}};
                        int main(){Pair first={1,2};Pair second=first.changed(3,4,5,6,7);int sum=0;
                            for(int i=0;i<4;i++){Pair copy=second;copy.left+=i;
                                if(i&1)sum+=copy.right;else sum+=copy.left;}
                            printf("%d %d %d\\n",first.left,second.right,sum);return 0;}
                        """, "1 15 58\n"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("programs")
    void registerNativeMatchesOriginalDebugAndGxx(String name, String text, String expected) throws Exception {
        var source = new SourceFile(name + ".cpp", text);
        var ir = new CompilerApi(source, LanguageMode.CPP17_ALGORITHM).runToIr();
        var registerOnly = new Assembler(ir, new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED, List.of()));
        String assembly = registerOnly.assemble().text();
        assertTrue(registerOnly.succeeded(), () -> registerOnly.errors().toString());
        assertTrue(assembly.contains("r10"), "fixture must exercise actual register placement");
        assertSame(ir, registerOnly.input().irResult(), "placement must not mutate the source debugger's IR");
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), LanguageMode.CPP17_ALGORITHM, OptimizationLevel.OPTIMIZED)
                .run(name, text, "");
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals(expected, outcome.stdout().replace("\r\n", "\n"), report::describe));
    }
}
