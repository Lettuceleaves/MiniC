package minic.cpp;

import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.*;
import minic.debug.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppBlockCopyPropagationTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of("hash-loop","unsigned long long h=14695981039346656037ULL;for(int i=0;i<32;++i){unsigned long long old=h;h=(old^(unsigned long long)i)*1099511628211ULL;}printf(\"%llu\\n\",h);",true),
                Arguments.of("snapshot-after-overwrite","int x=2;int a=x;x+=3;int b=x;int c=a;x*=2;printf(\"%d\\n\",a*10000+b*100+c*10+x);",false),
                Arguments.of("branch-join-loop","int x=1;int sum=0;for(int i=0;i<9;++i){int old=x;if(i%2)x+=i;else x-=i;sum+=old+x;}printf(\"%d %d\\n\",sum,x);",true),
                Arguments.of("pointer-volatile","int values[3]={2,7,9};int*p=values;int*q=p;p+=2;volatile int*slot=q;*slot+=3;printf(\"%d %d\\n\",*q,*p);",false),
                Arguments.of("floating-snapshot","double value=-0.0;double old=value;value=3.5;double later=value;value=-2.0;printf(\"%.1f %.1f %.1f\\n\",old,later,value);",false),
                Arguments.of("narrow-loop","unsigned char value=250;int sum=0;for(int i=0;i<20;++i){unsigned char old=value;value=(unsigned char)(value+1);sum+=(int)old;}printf(\"%d %u\\n\",sum,(unsigned int)value);",true)
        ).flatMap(args->Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).map(mode->{var a=args.get();return Arguments.of(mode,a[0],a[1],a[2]);}));
    }
    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void localSnapshotsKeepTheirOriginalValuesAndReduceLoopCopies(LanguageMode mode,String name,String body,boolean removesCopies)throws Exception {
        String text="#include <stdio.h>\nint main(){"+body+"return 0;}";
        var source=new SourceFile(name+".cpp",text);var original=new CompilerApi(source,mode).runToIr();
        var originalFunctions=original.functions();
        var prepare=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new PrivateAddressNormalizationPass(),
                new InitializedCheckEliminationPass(),new LocalScalarPromotionPass(),new ReadOnlyParameterPromotionPass(),new ConstantPropagationPass(),new DeadCodeEliminationPass()));
        var before=prepare.apply(original).ir();
        var cleanup=new IrOptimizationPipeline(OptimizationLevel.OPTIMIZED,List.of(new BlockCopyPropagationPass(),new DeadCodeEliminationPass()));
        var after=cleanup.apply(before).ir();
        if(removesCopies){
            long oldCount=before.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream()).filter(IrMoveInstruction.class::isInstance).count();
            long newCount=after.functions().stream().flatMap(f->f.blocks().stream()).flatMap(b->b.instructions().stream()).filter(IrMoveInstruction.class::isInstance).count();
            assertTrue(newCount<oldCount,"loop snapshots must lose actual copy instructions: "+oldCount+" -> "+newCount);
        }
        var report=new CppDifferentialHarness(temporary.resolve("reference"),CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,text,"");
        assertTrue(report.passed(),report::describe);
        String expected=report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n");
        var output=temporary.resolve("optimized");var assembler=new Assembler(before,cleanup);
        var object=new ObjBuilder(source,assembler,output,"program");var linker=new Linker(source,object,output,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
        var actual=BoundedProcess.run(List.of(output.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
        assertFalse(actual.timedOut());assertFalse(actual.outputExceeded());assertEquals(0,actual.exitCode(),actual::stderr);
        assertEquals(expected,actual.stdout().replace("\r\n","\n"));
        var debug=DebugApi.fromIr(source,after,"");for(int i=0;debug.canNext()&&i<50000;i++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(expected,debug.current().runtime().stdout().replace("\r\n","\n"));
        assertSame(originalFunctions,original.functions());
    }
}
