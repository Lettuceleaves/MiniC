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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Timeout(120)
@Execution(ExecutionMode.SAME_THREAD)
final class CppSmallFunctionInliningTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("returns-merges-and-fixed-input", """
                        #include <stdio.h>
                        int pick(int value){ if(value<0)return 11; return value==0?22:33; }
                        int main(){int n=getchar()-'0'; printf("%d %d %d\\n",pick(-n),pick(0),pick(n));return 0;}
                        """, "3\n", "11 22 33\n"),
                Arguments.of("caller-loop-break-continue", """
                        #include <stdio.h>
                        int twice(int value){return value*2;}
                        int main(){int sum=0;for(int i=0;i<10;i++){if(i==2)continue;if(i==7)break;sum+=twice(i);}printf("%d\\n",sum);return 0;}
                        """, "", "38\n"),
                Arguments.of("formal-mutation-isolation-and-address", """
                        #include <stdio.h>
                        int set(int *p){*p=9;return *p;}
                        int change(int value){value+=2;set(&value);return ++value;}
                        int main(){int value=3;printf("%d %d %d\\n",change(value),change(20),value);return 0;}
                        """, "", "10 10 3\n"),
                Arguments.of("repeated-loop-site-reinitializes-formal-slot", """
                        #include <stdio.h>
                        int bump(int *value){*value+=2;return *value;}
                        int change(int value){bump(&value);return ++value;}
                        int main(){int sum=0;for(int i=0;i<6;i++)sum+=change(i);printf("%d\\n",sum);return 0;}
                        """, "", "33\n"),
                Arguments.of("source-order-side-effects-once", """
                        #include <stdio.h>
                        int calls=0;
                        int next(){calls++;return calls;}
                        int twice(int value){return value*2;}
                        int main(){int result=twice(next());printf("%d %d\\n",result,calls);return 0;}
                        """, "", "2 1\n"),
                Arguments.of("const-this-and-receiver-snapshot", """
                        #include <stdio.h>
                        struct Box{int value; int get()const{return value;} int add(int n){value+=n;return value;}};
                        int replace(Box **slot,Box *other){*slot=other;return 4;}
                        int invoke(Box *slot,Box *other){return slot->add(replace(&slot,other));}
                        int main(){Box a={2};Box b={20};int result=invoke(&a,&b);printf("%d %d %d\\n",result,a.get(),b.get());return 0;}
                        """, "", "6 6 20\n"),
                Arguments.of("struct-value-copy-and-sret", """
                        #include <stdio.h>
                        struct Pair{int x;int y;};
                        Pair copy(Pair value){value.x+=1;return value;}
                        int total(Pair value){value.x+=10;return value.x+value.y;}
                        int main(){Pair a={2,5};Pair b=copy(a);printf("%d %d %d %d\\n",a.x,b.x,b.y,total(b));return 0;}
                        """, "", "2 3 5 18\n"),
                Arguments.of("six-argument-native-abi", """
                        #include <stdio.h>
                        long long sum(int a,int b,int c,int d,int e,int f){return (long long)a+b+c+d+e+f;}
                        int main(){printf("%lld\\n",sum(1,2,3,4,5,6));return 0;}
                        """, "", "21\n"),
                Arguments.of("array-pointer-addressing", """
                        #include <stdio.h>
                        int at(int *values,int index){return values[index];}
                        void set(int *values,int index,int value){values[index]=value;}
                        int main(){int values[3]={2,4,6};set(values,1,9);printf("%d %d\\n",at(values,1),at(values,2));return 0;}
                        """, "", "9 6\n"),
                Arguments.of("floating-and-narrow-values", """
                        #include <stdio.h>
                        double mix(double a,float b){return a+b;}
                        unsigned char narrow(unsigned char value){return value+1;}
                        int main(){printf("%.2f %d\\n",mix(2.5,1.25f),(int)narrow(255));return 0;}
                        """, "", "3.75 0\n"),
                Arguments.of("volatile-pointer-and-external-effects", """
                        #include <stdio.h>
                        int observe(volatile int *p){*p;printf("seen ");return *p;}
                        void write(int *p){*p=7;}
                        int main(){int value=3;write(&value);printf("%d\\n",observe(&value));return 0;}
                        """, "", "seen 7\n")
        );
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void explicitInliningMatchesBaselineSourceDebugAndGxx(String name,String text,String stdin,String expected) throws Exception {
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), LanguageMode.CPP17_ALGORITHM).run(name,text,stdin);
        assertTrue(report.passed(), report::describe);
        report.outcomes().values().forEach(outcome -> assertEquals(expected,normalize(outcome.stdout()),report::describe));
        var source = new SourceFile(name+".cpp",text);
        IrResult original = new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var before = List.copyOf(original.functions());
        IrResult inlined = pipeline().apply(original).ir();
        assertNotEquals(original,inlined,"fixture must actually inline at least one site");
        var run = nativeRun(source,original,pipeline(),"inline",stdin);
        assertEquals(0,run.exitCode(),run::stderr);
        assertEquals(expected,normalize(run.stdout()));
        assertEquals("",run.stderr());
        assertEquals(before,original.functions());
        // Optional optimized-IR interpreter is an independent oracle for renamed CFG/slots, not the source debugger's input.
        var debug = finish(DebugApi.fromIr(source,inlined,stdin));
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,normalize(debug.current().runtime().stdout()));
        assertEquals(0,debug.current().runtime().termination().status());
    }

    static Stream<Arguments> checkedFunctions() {
        return Stream.of(Arguments.of("uninitialized", "int failed(){int value;return value;} int main(){int code=failed();return code==101?17:18;}",17),
                Arguments.of("dividezero", "int failed(int divisor){return 7/divisor;} int main(){int code=failed(0);return code==102?19:20;}",19));
    }
    @ParameterizedTest(name="{0}") @MethodSource("checkedFunctions")
    void checkFailureStillReturnsFromTheCalleeBeforeTheCallerContinues(String name,String text,int expected) throws Exception {
        // Both have undefined C++ behavior; the project's existing native trap convention is the oracle.
        var source = new SourceFile(name+".c",text);
        IrResult original = new CompilerApi(source).runToIr();
        IrResult inlined = pipeline().apply(original).ir();
        assertEquals(original,inlined,"checked bodies retain their call boundary");
        assertEquals(expected,nativeRun(source,original,IrOptimizationPipeline.forLevel(OptimizationLevel.BASELINE),"baseline","").exitCode());
        assertEquals(expected,nativeRun(source,original,pipeline(),"inline","").exitCode());
    }

    @Test void nativeInliningLeavesSourceCallAndReturnHistoryIntact() {
        var source = new SourceFile("history.c","int twice(int value){\nreturn value*2;\n}\nint main(){return twice(3)-6;}");
        IrResult original = new CompilerApi(source).runToIr();
        assertNotEquals(original,pipeline().apply(original).ir());
        var debug = DebugApi.fromIr(source,original,"");
        var history = new ArrayList<Debugger.Context>();
        history.add(debug.current());
        while(debug.canNext() && history.size()<500)history.add(debug.next());
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status());
        assertTrue(history.stream().anyMatch(c->c.stop().range()!=null && c.stop().range().startLine()==2));
        for(int i=history.size()-2;i>=0;i--)assertSame(history.get(i),debug.previous());
        for(int i=1;i<history.size();i++)assertSame(history.get(i),debug.next());
    }

    @Test void priorInitializationProofEnablesLocalSlotsAtARepeatedLoopCallSite() throws Exception {
        String text="""
                #include <stdio.h>
                int helper(int input){int local=input*2;return local+1;}
                int main(){int sum=0;for(int i=0;i<6;i++)sum+=helper(i);printf("%d\\n",sum);return 0;}
                """;
        var source=new SourceFile("proof-and-inline.cpp",text);
        IrResult original=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        assertEquals(original,pipeline().apply(original).ir(),"checked helper is initially ineligible");
        var composed=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new InitializedCheckEliminationPass(),new SmallFunctionInliningPass()));
        IrResult inlined=composed.apply(original).ir();
        var main=inlined.functions().stream().filter(f->f.name().equals("main")).findFirst().orElseThrow();
        assertTrue(main.blocks().stream().flatMap(b->b.instructions().stream()).noneMatch(i->i instanceof minic.compiler.ir.instruction.CallInstruction.IrCallInstruction c
                && !c.calleeName().equals("printf")));
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run("proof-inline",text,"");
        assertTrue(report.passed(),report::describe);
        var nativeResult=nativeRun(source,original,composed,"inline-proven","");
        assertEquals(0,nativeResult.exitCode());assertEquals("36\n",normalize(nativeResult.stdout()));
        var debug=finish(DebugApi.fromIr(source,inlined,""));
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status());
        assertEquals("36\n",normalize(debug.current().runtime().stdout()));
    }

    private BoundedProcess.Result nativeRun(SourceFile source,IrResult ir,IrOptimizationPipeline pipeline,String name,String stdin) throws Exception {
        Path directory=temporary.resolve(name);
        var assembler=new Assembler(ir,pipeline);
        var obj=new ObjBuilder(source,assembler,directory,"program");
        var linker=new Linker(source,obj,directory,"program");
        new CompilerApi(List.of(assembler,obj,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->"asm="+assembler.errors()+", obj="+obj.errors()+", link="+linker.errors());
        var run=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,stdin,Duration.ofSeconds(10),65_536);
        assertFalse(run.timedOut());assertFalse(run.outputExceeded());
        return run;
    }
    private static DebugApi finish(DebugApi debug){for(int i=0;debug.canNext()&&i<20_000;i++)debug.next();assertFalse(debug.canNext());return debug;}
    private static IrOptimizationPipeline pipeline(){return new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new SmallFunctionInliningPass()));}
    private static String normalize(String text){return text.replace("\r\n","\n");}
}
