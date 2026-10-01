package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.IrCallInstruction;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.*;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(180)
final class CppCallSiteConstantInliningTest {
    @TempDir Path temporary;
    private static final String WORD_HELPER="""
            void set_word(unsigned*w,unsigned i,int on){
                if(on)w[i>>5]|=1u<<(i&31);
                else{w[i>>5]&=~(1u<<(i&31));w[(i+1)>>5]^=3u<<(i&31);}
            }
            int run(unsigned*w,unsigned i){set_word(w,i,1);return w[i>>5];}
            int main(){unsigned words[2]={4,0};printf("%d\\n",run(words,1));return 0;}
            """;
    private static List<IrPass> preparation(){return List.of(new DirectCallResolutionPass(),new PrivateAddressNormalizationPass(),
            new InitializedCheckEliminationPass(),new EarlySimplificationPass());}

    @ParameterizedTest @EnumSource(LanguageMode.class)
    void aRealWordUpdateExceedsTwentyFourBeforeLiteralSpecialization(LanguageMode mode)throws Exception{
        String text="#include <stdio.h>\n"+WORD_HELPER;
        var source=new SourceFile("word-update.cpp",text);
        var original=new CompilerApi(source,mode).runToIr();
        var prepared=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,preparation()).apply(original).ir();
        var helper=prepared.findFunction("set_word").orElseThrow();
        assertTrue(count(helper)>24,"generic helper must really exceed the existing instruction limit: "+count(helper));
        var result=new SmallFunctionInliningPass().apply(prepared);
        assertEquals(1,calls(prepared.findFunction("run").orElseThrow(),helper.name()));
        assertEquals(0,calls(result.findFunction("run").orElseThrow(),helper.name()),"literal site should now fit the unchanged budget");
        assertSame(helper,result.findFunction(helper.name()).orElseThrow());
        verify(mode,"word-update",text,"6\n",original,false);
    }

    @Test void actualBitsetSetTrueAndFalseUseTheSameGeneralMechanism()throws Exception{
        String text="""
                #include <bitset>
                #include <stdio.h>
                int run(std::bitset<128>*bits,unsigned i){bits->set(i,true);bits->set(i+1,false);return bits->test(i);}
                int main(){std::bitset<128> bits;int answer=run(&bits,5);printf("%d %llu\\n",answer,(unsigned long long)bits.count());return 0;}
                """;
        var source=new SourceFile("actual-bitset.cpp",text);
        var original=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var prepared=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,preparation()).apply(original).ir();
        var helper=prepared.functions().stream().filter(f->prepared.displayName(f.name()).endsWith("::set")&&f.parameters().size()==3).findFirst().orElseThrow();
        assertTrue(count(helper)>24,"actual generic bitset.set IR size: "+count(helper));
        // Isolate the candidate budget from unrelated library functions. Normal
        // whole-program native/debug execution is independently checked below.
        var run=prepared.findFunction("run").orElseThrow();
        var focused=new IrResult(List.of(helper,run));
        var result=new SmallFunctionInliningPass().apply(focused);
        assertEquals(2,calls(run,helper.name()));
        assertEquals(0,calls(result.findFunction(run.name()).orElseThrow(),helper.name()));
        verify(LanguageMode.CPP17_ALGORITHM,"actual-bitset",text,"1 1\n",original,true);
    }

    static Stream<Arguments> controls(){return Stream.of(
            Arguments.of("unused-actual-effects", """
                    int trace=0;int sink;
                    int tick(){++trace;return 7;}
                    int helper(int on,int unused){if(on)return 9;else{sink=1;sink=2;sink=3;sink=4;sink=5;sink=6;sink=7;sink=8;sink=9;sink=10;sink=11;sink=12;sink=13;sink=14;sink=15;sink=16;sink=17;sink=18;sink=19;sink=20;sink=21;sink=22;sink=23;sink=24;sink=25;}return 2;}
                    int main(){int a=helper(1,tick());printf("%d %d\\n",a,trace);return 0;}
                    """, "9 1\n"),
            Arguments.of("mutable-addressed-parameter", """
                    int helper(int on){int*alias=&on;*alias=0;if(on)return 99;else return on+3;}
                    int main(){int original=1;int result=helper(original);printf("%d %d\\n",result,original);return 0;}
                    """, "3 1\n"),
            Arguments.of("volatile-formal", """
                    int helper(volatile int on){on=0;if(on)return 99;return on+3;}
                    int main(){printf("%d\\n",helper(1));return 0;}
                    """, "3\n"),
            Arguments.of("narrow-actual-and-repeated-lifetime", """
                    int sink;
                    int helper(unsigned char on,int x){if(on){sink=1;sink=2;sink=3;sink=4;sink=5;sink=6;sink=7;sink=8;sink=9;sink=10;sink=11;sink=12;sink=13;sink=14;sink=15;sink=16;sink=17;sink=18;sink=19;sink=20;sink=21;sink=22;sink=23;sink=24;sink=25;return 99;}int local=x;return local+1;}
                    int main(){int sum=0;for(int i=0;i<3;++i)sum+=helper((unsigned char)256,i);printf("%d\\n",sum);return 0;}
                    """, "6\n")
        ).flatMap(a->Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).map(mode->{Object[] v=a.get();return Arguments.of(mode,v[0],v[1],v[2]);}));}
    @ParameterizedTest(name="{0}: {1}") @MethodSource("controls")
    void sourceSemanticsRemainIdentical(LanguageMode mode,String name,String body,String expected)throws Exception{
        String text="#include <stdio.h>\n"+body;
        verify(mode,name,text,expected,new CompilerApi(new SourceFile(name+".cpp",text),mode).runToIr(),false);
    }

    private void verify(LanguageMode mode,String name,String text,String expected,IrResult original,boolean ownLibrary)throws Exception{
        var originalFunctions=original.functions();
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(90),Duration.ofSeconds(30),1_000_000,65536);
        var report=new CppDifferentialHarness(temporary.resolve("reference"),CppDifferentialHarness.referenceCompiler(System.getenv()),limits,mode).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(o->assertEquals(expected,normalize(o.stdout()),report::describe));
        if(ownLibrary){var own=CppOwnLibraryReference.run(temporary.resolve("own"),text,"",limits);assertTrue(own.passed(),own::toString);assertEquals(expected,normalize(own.stdout()));}
        var passes=new ArrayList<>(preparation());passes.add(new SmallFunctionInliningPass());passes.add(new PostInliningScalarPreparationPass());
        passes.add(new LocalScalarPromotionPass());passes.add(new ConstantPropagationPass());passes.add(new DeadCodeEliminationPass());passes.add(new ControlFlowSimplificationPass());
        var pipeline=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,passes);
        var optimized=pipeline.apply(original).ir();
        var output=temporary.resolve("optimized");var source=new SourceFile(name+".cpp",text);
        var assembler=new Assembler(original,pipeline);var object=new ObjBuilder(source,assembler,output,"program");var linker=new Linker(source,object,output,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
        var actual=BoundedProcess.run(List.of(output.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(15),65536);
        assertFalse(actual.timedOut());assertFalse(actual.outputExceeded());assertEquals(0,actual.exitCode(),actual::stderr);assertEquals(expected,normalize(actual.stdout()));
        var execution=DebugApi.execute(source,optimized,"",1_000_000,65536);
        assertFalse(execution.stepLimitReached());assertFalse(execution.outputLimitReached());assertEquals(Debugger.Status.COMPLETED,execution.context().stop().status(),execution.context().stop()::error);
        assertEquals(expected,normalize(execution.context().runtime().stdout()));assertSame(originalFunctions,original.functions());
    }
    private static long count(IrFunction f){return f.blocks().stream().mapToLong(b->b.instructions().size()).sum();}
    private static long calls(IrFunction f,String name){return f.blocks().stream().flatMap(b->b.instructions().stream()).filter(i->i instanceof IrCallInstruction c&&c.calleeName().equals(name)).count();}
    private static String normalize(String text){return text.replace("\r\n","\n");}
}
