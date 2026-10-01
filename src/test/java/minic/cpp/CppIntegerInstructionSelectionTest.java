package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryOperator;
import minic.compiler.ir.optimize.GlobalRegisterPlan;
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppIntegerInstructionSelectionTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("unsigned32-patterns", """
                        #include <stdio.h>
                        unsigned int mix(unsigned int x){
                            x+=2147483647U;x^=4294967295U;x|=2147483648U;x&=4294967294U;
                            return x*17U-128U;
                        }
                        int main(){unsigned int values[7]={0U,1U,127U,128U,2147483647U,2147483648U,4294967295U};
                            for(int i=0;i<7;++i)printf("%u\\n",mix(values[i]));return 0;}
                        """),
                Arguments.of("signed64-negative-immediates", """
                        #include <stdio.h>
                        long long mix(long long x){return (((x+2147483647LL)-(-2147483648LL))*-3LL)^-1LL;}
                        int main(){long long values[5]={-1000LL,-1LL,0LL,1LL,123456LL};
                            for(int i=0;i<5;++i)printf("%lld\\n",mix(values[i]));return 0;}
                        """),
                Arguments.of("unsigned64-fullwidth-fallback", """
                        #include <stdio.h>
                        unsigned long long mix(unsigned long long x){
                            x+=2147483648ULL;x^=4294967295ULL;x-=4294967296ULL;
                            x|=9223372036854775808ULL;x&=18446744073709551615ULL;return x*4294967295ULL;
                        }
                        int main(){unsigned long long values[5]={0ULL,1ULL,4294967296ULL,9223372036854775808ULL,18446744073709551615ULL};
                            for(int i=0;i<5;++i)printf("%llu\\n",mix(values[i]));return 0;}
                        """),
                Arguments.of("register-operands-calls-and-shifts", """
                        #include <stdio.h>
                        int trace=0;
                        int next(int value){trace=trace*10+value;return value;}
                        unsigned int mix(unsigned int a,unsigned int b){
                            return ((a+b)*(a-b)^(a|b))+(a/b)+(a%b)+(a>>b)+(a<<b)+(a<b);
                        }
                        int main(){unsigned int first=next(2);unsigned int second=next(3);
                            printf("%u %u %d\\n",mix(127U,second),mix(first,1U),trace);
                            volatile unsigned int observed=9U;observed=(observed+17U)*3U;
                            printf("%u\\n",observed);return 0;}
                        """));
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void integerEncodingBoundariesAgreeWithDebugAndGxx(String name,String source)throws Exception {
        for (OptimizationLevel level : OptimizationLevel.values()) {
            var report=new CppDifferentialHarness(temporary.resolve(level.name()),
                    CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),
                    LanguageMode.CPP17_ALGORITHM,level).run(name,source,"");
            assertTrue(report.passed(),report::describe);
        }
    }

    @Test void optimizedAssemblerSelectsImmediateInstructionsWithoutAnIrRewrite() {
        var source=new SourceFile("instruction-selection.cpp",
                "unsigned int f(unsigned int x){return (x+17U)*3U;}int main(){return (int)f(5U);}");
        var ir=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        for (OptimizationLevel level : OptimizationLevel.values()) {
            var assembler=new Assembler(ir,new IrOptimizationPipeline(level,List.of()));
            String text=assembler.assemble().text();
            assertTrue(assembler.succeeded(),()->assembler.errors().toString());
            assertSame(ir,assembler.input().irResult());
            if(level==OptimizationLevel.OPTIMIZED){
                var function=ir.functions().stream().filter(f->ir.displayName(f.name()).equals("f")).findFirst().orElseThrow();
                var plan=GlobalRegisterPlan.allocate(function,true);
                var binaries=function.blocks().stream().flatMap(b->b.instructions().stream())
                        .filter(IrBinaryInstruction.class::isInstance).map(IrBinaryInstruction.class::cast).toList();
                assertEquals(2,binaries.size());
                for(var binary:binaries){
                    String home=plan.registers().get(binary.result().name());
                    assertNotNull(home,"fixture must assign each result a register home");
                    String mnemonic=binary.operator()==IrBinaryOperator.ADD?"add":"imul";
                    String immediate=binary.operator()==IrBinaryOperator.ADD?"17":"3";
                    assertTrue(text.contains(mnemonic+" "+home+"d, "+immediate),text);
                }
                assertFalse(text.contains("push rax") || text.contains("pop rax"),text);
            }else{
                assertTrue(text.contains("add eax, ecx") && text.contains("imul eax, ecx"),text);
                assertTrue(text.contains("push rax") && text.contains("pop rax"),text);
            }
        }
    }
}
