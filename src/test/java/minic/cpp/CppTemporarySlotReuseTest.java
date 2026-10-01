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
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Timeout(120)
@Execution(ExecutionMode.SAME_THREAD)
final class CppTemporarySlotReuseTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs(){
        return Stream.of(
                Arguments.of("live-value-across-dead-computation", """
                        #include <stdio.h>
                        int left(){return 40;}
                        int right(){return 2;}
                        int main(){int value=3;int result=left()+((value+1),right());printf("%d\\n",result);return 0;}
                        """, "", "42\n"),
                Arguments.of("narrow-floating-integer-and-pointer-widths", """
                        #include <stdio.h>
                        int mix(unsigned char a,short b,int c,long long d,float f,double g,int *p){
                            *p+=(int)a+b+c+(int)d+(int)f+(int)g;return *p;}
                        int main(){int value=1;int result=mix((unsigned char)250,(short)-3,4,5LL,6.5f,7.25,&value);
                            printf("%d %d %.2f %lld\\n",result,value,2.5+1.25,(long long)result*10000000000LL);return 0;}
                        """, "", "270 270 3.75 2700000000000\n"),
                Arguments.of("non-ssa-branch-loop-break-continue", """
                        #include <stdio.h>
                        int pick(int n){return n<0?11:n==0?22:33;}
                        int main(){int n=getchar()-'0';int sum=0;for(int i=-n;i<=n;i++){if(i==-1)continue;
                            sum+=pick(i);if(i==2)break;}printf("%d\\n",sum);return 0;}
                        """, "3\n", "110\n"),
                Arguments.of("sret-this-and-copy-objects", """
                        #include <stdio.h>
                        struct Pair{int x;int y;int sum()const{return x+y;}void add(int n){x+=n;}};
                        Pair changed(Pair value,int n){value.add(n);return value;}
                        int main(){Pair a={2,5};Pair b=changed(a,4);printf("%d %d %d %d\\n",a.x,b.x,a.sum(),b.sum());return 0;}
                        """, "", "2 6 7 11\n"),
                Arguments.of("indirect-call-live-across-values", """
                        #include <stdio.h>
                        int twice(int x){return x*2;}
                        int triple(int x){return x*3;}
                        int change(int (**slot)(int)){*slot=triple;return 4;}
                        int run(int (*function)(int),int base){int first=function(change(&function));return base+first+function(2);}
                        int main(){printf("%d\\n",run(twice,30));return 0;}
                        """, "", "44\n"),
                Arguments.of("recursion-and-addressed-parameters", """
                        #include <stdio.h>
                        int bump(int *value){*value+=2;return *value;}
                        int sum(int n){if(n==0)return 0;return n+sum(n-1);}
                        int mutate(int value){int old=value;bump(&value);return old*100+value;}
                        int main(){printf("%d %d\\n",sum(12),mutate(3));return 0;}
                        """, "", "78 305\n"),
                Arguments.of("global-alias-and-volatile-storage", """
                        #include <stdio.h>
                        int global=3;
                        int observe(volatile int *pointer,int value){*pointer+=value;return *pointer;}
                        int main(){volatile int local=4;int a=observe(&local,2);int b=observe(&global,5);
                            printf("%d %d %d %d\\n",a,b,(int)local,global);return 0;}
                        """, "", "6 8 6 8\n"),
                Arguments.of("reference-aliases-and-reference-return", """
                        #include <stdio.h>
                        int &choose(int &left,int &right,int choice){if(choice)return left;return right;}
                        void add(int &value){value+=10;}
                        int main(){int left=2;int right=5;const int &view=left;int &alias=choose(left,right,0);
                            alias+=3;add(left);printf("%d %d %d\\n",left,right,view);return 0;}
                        """, "", "12 8 12\n")
        );
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void baselineNativeAllocationFullOptimizationSourceDebugAndGxxAgree(String name,String text,String stdin,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,text,stdin);
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,normalize(outcome.stdout()),report::describe));
        var source=new SourceFile(name+".cpp",text);
        IrResult original=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var originalFunctions=original.functions();
        // Empty IR passes still select native stack-slot reuse and local register allocation.
        var allocationOnly=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of());
        for(var pipeline:List.of(allocationOnly,IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED))){
            var result=runNative(source,original,pipeline,pipeline==allocationOnly?"native-allocation":"full-opt",stdin);
            assertEquals(0,result.exitCode(),result::stderr);assertEquals(expected,normalize(result.stdout()));assertEquals("",result.stderr());
        }
        assertSame(originalFunctions,original.functions(),"backend placement must not rewrite the source/debug IR");
    }

    @Test void uninitializedAndDivisorFailuresKeepTheNativeExitConvention()throws Exception{
        for(var item:List.of(new Object[]{"uninit","int main(){int value;return value;}",101},
                new Object[]{"zero","int main(){int zero=0;return 9/zero;}",102})){
            var source=new SourceFile(item[0]+".c",(String)item[1]);
            IrResult ir=new CompilerApi(source).runToIr();
            for(OptimizationLevel level:OptimizationLevel.values()){
                var result=runNative(source,ir,IrOptimizationPipeline.forLevel(level),item[0]+"-"+level,"");
                assertEquals(item[2],result.exitCode());
            }
        }
    }

    @Test void variadicIncomingArgumentAreaKeepsTheNativeAbiAndMatchesSourceDebugAndGxx()throws Exception{
        String text="""
                #include <stdio.h>
                #include <stdarg.h>
                int sum(int count,...){va_list values;va_start(values,count);int total=0;
                    for(int i=0;i<count;i++)total+=va_arg(values,int);va_end(values);return total;}
                int main(){printf("%d\\n",sum(7,1,2,3,4,5,6,7));return 0;}
                """;
        Path path=temporary.resolve("varargs.cpp"),exe=temporary.resolve("gxx.exe");Files.writeString(path,text);
        var compiled=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-O2",
                path.toString(),"-o",exe.toString()),temporary,"",Duration.ofSeconds(20),65_536);
        assertEquals(0,compiled.exitCode(),compiled::stderr);assertFalse(compiled.timedOut());assertFalse(compiled.outputExceeded());
        var reference=BoundedProcess.run(List.of(exe.toString()),temporary,"",Duration.ofSeconds(10),65_536);
        assertEquals(0,reference.exitCode());assertEquals("28\n",normalize(reference.stdout()));
        var source=new SourceFile("varargs.cpp",text);IrResult ir=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var debug=DebugApi.fromIr(source,ir,"");
        for(int step=0;debug.canNext()&&step<10_000;step++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals("28\n",normalize(debug.current().runtime().stdout()));
        var pipelines=List.of(IrOptimizationPipeline.forLevel(OptimizationLevel.BASELINE),
                new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of()),IrOptimizationPipeline.forLevel(OptimizationLevel.OPTIMIZED));
        for(int index=0;index<pipelines.size();index++){
            var run=runNative(source,ir,pipelines.get(index),"varargs-"+index,"");
            assertEquals(0,run.exitCode());assertEquals("28\n",normalize(run.stdout()));assertEquals("",run.stderr());
        }
    }

    private BoundedProcess.Result runNative(SourceFile source,IrResult ir,IrOptimizationPipeline pipeline,String name,String stdin)throws Exception{
        Path directory=temporary.resolve(name);
        var assembler=new Assembler(ir,pipeline);
        var obj=new ObjBuilder(source,assembler,directory,"program");
        var linker=new Linker(source,obj,directory,"program");
        new CompilerApi(List.of(assembler,obj,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->"asm="+assembler.errors()+", obj="+obj.errors()+", link="+linker.errors());
        var run=BoundedProcess.run(List.of(directory.resolve("program.exe").toString()),temporary,stdin,Duration.ofSeconds(10),65_536);
        assertFalse(run.timedOut());assertFalse(run.outputExceeded());return run;
    }
    private static String normalize(String text){return text.replace("\r\n","\n");}
}
