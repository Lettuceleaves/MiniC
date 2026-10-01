package minic.cpp;

import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
final class CppDirectCallResolutionTest {
    @TempDir Path temporary;
    @Test void configuredPipelineSimplifiesKnownStaticWrapperWithoutChangingSourceIr() {
        var source=new SourceFile("known-wrapper.cpp","""
            template<class T> struct Policy {
                static unsigned long long block(){return 512/sizeof(T);}
                unsigned long long slots(){return block();}
            };
            int main(){Policy<int> policy;return (int)policy.slots();}
            """);
        var original=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var functions=original.functions();
        var optimized=IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED).apply(original).ir();
        var main=optimized.functions().stream().filter(f->f.name().equals("main")).findFirst().orElseThrow();
        var instructions=main.blocks().stream().flatMap(b->b.instructions().stream()).toList();
        assertTrue(instructions.stream().noneMatch(i->i instanceof minic.compiler.ir.instruction.CallInstruction.IrCallInstruction
                || i instanceof minic.compiler.ir.instruction.CallInstruction.IrIndirectCallInstruction),"constant wrapper chain must collapse");
        assertTrue(instructions.stream().anyMatch(i->i instanceof minic.compiler.ir.instruction.ControlInstruction.IrReturnInstruction r
                && r.value() instanceof minic.compiler.ir.value.IrValue.IrConstant c && c.value()==128));
        assertSame(functions,original.functions());
    }
    static Stream<Arguments> programs(){
        return Stream.of(
            Arguments.of("static-template", """
                #include <cstdio>
                template<class T> struct Policy {
                    static unsigned long long block(){return 512/sizeof(T);}
                    static T value(T input){return input+3;}
                    unsigned long long blocks(){return block();}
                    T invoke(T input){return value(input);}
                };
                int main(){Policy<int> p;std::printf("%llu %d\\n",p.blocks(),p.invoke(9));return 0;}
                """, "128 12\n"),
            Arguments.of("aggregate-result", """
                #include <cstdio>
                struct Record {long long a;long long b;long long c;
                    static Record make(int value){Record result{value,value+1,value+2};return result;}
                    Record invoke(int value){return make(value);}};
                int main(){Record factory{0,0,0};Record value=factory.invoke(20);std::printf("%lld %lld %lld\\n",value.a,value.b,value.c);return 0;}
                """, "20 21 22\n"),
            Arguments.of("mutable-callee-before-argument", """
                #include <cstdio>
                int a(int value){std::printf("a ");return value+1;}
                int b(int value){std::printf("b ");return value+10;}
                typedef int (*Fn)(int); Fn selected=a;
                int change(){selected=b;return 5;}
                struct Source {static int seed(){return 7;}int invoke(){return seed();}};
                int main(){Source source;int first=selected(change());int second=selected(source.invoke());std::printf("%d %d\\n",first,second);return 0;}
                """, "a b 6 17\n"),
            Arguments.of("void-volatile-and-variadic", """
                #include <cstdio>
                volatile int count=0;
                struct Math {static void tick(){count=count+1;} static double half(double value){return value/2.0;}
                    double invoke(double value){tick();tick();return half(value);}};
                int main(){Math math;int (*print)(const char*,...)=std::printf;double value=math.invoke(7.0);print("%d %.2f\\n",(int)count,value);return 0;}
                """, "2 3.50\n")
        );
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void canonicalizedNativeCallsAgreeWithOriginalDebugAndGxx(String name,String text,String expected)throws Exception {
        var report=new CppDifferentialHarness(temporary.resolve("reference"),CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe));
        var source=new SourceFile(name+".cpp",text);
        var original=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var saved=List.copyOf(original.functions());
        var pipeline=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new DirectCallResolutionPass()));
        assertNotEquals(original,pipeline.apply(original).ir(),"fixture must resolve an actual known-address call");
        Path directory=temporary.resolve("resolved");
        var assembler=new Assembler(original,pipeline);
        var object=new ObjBuilder(source,assembler,directory,"program");
        var linker=new Linker(source,object,directory,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->"asm="+assembler.errors()+", obj="+object.errors()+", link="+linker.errors());
        var run=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65_536);
        assertFalse(run.timedOut());assertFalse(run.outputExceeded());assertEquals(0,run.exitCode());
        assertEquals(expected,run.stdout().replace("\r\n","\n"));assertEquals("",run.stderr());
        assertEquals(saved,original.functions(),"source debugger IR must remain unchanged");
    }
}
