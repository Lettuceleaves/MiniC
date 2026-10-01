package minic.cpp;

import minic.SourceRange;
import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppLoopWeightedRegisterTest {
    private static final SourceRange R=new SourceRange(1,0,1,8);
    @TempDir Path temporary;
    static Stream<Arguments> widths(){return Stream.of(IrType.UNSIGNED_CHAR,IrType.SHORT,IrType.UNSIGNED_INT,IrType.LONG_LONG)
            .flatMap(type->Stream.of(false,true).map(call->Arguments.of(type,call)));}

    @ParameterizedTest @MethodSource("widths")
    void lowStaticUseLoopValuesReduceStackTrafficAndKeepValuesAcrossCalls(IrType type,boolean calls)throws Exception {
        var ir=program(type,calls);var plan=GlobalRegisterPlan.allocate(ir.functions().get(1),true);
        assertTrue(plan.calleeSavedRegisters().contains(plan.registers().get("scratch")),plan.registers().toString());
        int expected=expected(type);String spelling=switch(type){case UNSIGNED_CHAR->"unsigned char";case SHORT->"short";
            case UNSIGNED_INT->"unsigned int";case LONG_LONG->"long long";default->throw new IllegalArgumentException();};
        String reference="int effect(){return 17;}int work(){int sum=0;for(int i=0;i<32;++i){"+spelling+" value=("+spelling+")(i+"+seed(type)+");"
                +(calls?"effect();":"")+"sum+=(int)value;}return sum;}int main(){return work()=="+expected+"?0:1;}";
        var source=new SourceFile("loop-register.cpp",reference);int baselineMemory=0;
        for(var level:OptimizationLevel.values()) {
            var assembler=new Assembler(ir,new IrOptimizationPipeline(level,List.of()));String text=assembler.assemble().text();
            assertTrue(assembler.succeeded(),()->assembler.errors().toString());
            String loop=text.substring(text.indexOf("minic$work$loop:"),text.indexOf("minic$work$done:"));
            int memory=(int)loop.lines().filter(line->line.contains("[rbp-")).count();
            if(level==OptimizationLevel.BASELINE)baselineMemory=memory;
            else assertTrue(memory<baselineMemory,"loop must remove actual frame accesses, baseline="+baselineMemory+", optimized="+memory+"\n"+loop);
            var directory=temporary.resolve(level.name());var object=new ObjBuilder(source,assembler,directory,"program");
            var linker=new Linker(source,object,directory,"program");new CompilerApi(List.of(object,linker)).runThrough(linker);
            assertTrue(linker.succeeded(),()->object.errors()+" / "+linker.errors());assertEquals(0,run(directory.resolve("program.exe")).exitCode());
        }
        var debug=DebugApi.fromIr(source,ir,"");for(int step=0;debug.canNext()&&step<20_000;step++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(0,debug.current().runtime().termination().status());
        Path cpp=temporary.resolve("reference.cpp"),exe=temporary.resolve("reference.exe");Files.writeString(cpp,reference);
        var compile=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-O2",cpp.toString(),"-o",exe.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(compile.timedOut());assertFalse(compile.outputExceeded());assertEquals(0,compile.exitCode(),compile::stderr);assertEquals(0,run(exe).exitCode());
    }

    @Test void sourceLoopsWithBranchesPointersNestedLoopsAndCallsKeepTheirResults()throws Exception {
        String source="""
                #include <stdio.h>
                int calls=0;int effect(int value){calls++;return value+1;}
                int main(){unsigned char values[17];for(int i=0;i<17;i++)values[i]=(unsigned char)(250+i);
                  int sum=0;for(int r=0;r<3;r++)for(int i=0;i<17;i++){if(i%3==0)continue;
                    int value=values[i];if(value%2)sum+=effect(value);else sum+=value;}
                  printf("%d %d\\n",sum,calls);return 0;}
                """;
        int sum=0,calls=0;for(int r=0;r<3;r++)for(int i=0;i<17;i++){if(i%3==0)continue;int value=(250+i)&255;if(value%2!=0){sum+=value+1;calls++;}else sum+=value;}
        for(var mode:LanguageMode.values())for(var level:OptimizationLevel.values()) {
            var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(30),Duration.ofSeconds(15),200_000,1_048_576);
            var report=new CppDifferentialHarness(temporary.resolve(mode.name()+level.name()),CppDifferentialHarness.referenceCompiler(System.getenv()),limits,mode,level).run("source-loop",source,"");
            assertTrue(report.passed(),report::describe);for(var outcome:report.outcomes().values())assertEquals(sum+" "+calls+"\n",outcome.stdout().replace("\r\n","\n"));
        }
    }

    private static IrResult program(IrType type,boolean calls) {
        var index=new IrTemporary("index",IrType.INT);var sum=new IrTemporary("sum",IrType.INT);
        var typedIndex=new IrTemporary("typedIndex",type);var scratch=new IrTemporary("scratch",type);var widened=new IrTemporary("widened",IrType.INT);
        var condition=new IrTemporary("condition",IrType.INT);var body=new ArrayList<IrInstruction>();
        body.add(new IrCastInstruction(typedIndex,index,R));
        body.add(new IrBinaryInstruction(scratch,IrBinaryOperator.ADD,typedIndex,new IrConstant(seed(type),type),R));
        if(calls)body.add(new IrIndirectCallInstruction(null,new IrFunctionAddress("effect"),List.of(),false,R));
        body.add(new IrCastInstruction(widened,scratch,R));body.add(new IrBinaryInstruction(sum,IrBinaryOperator.ADD,sum,widened,R));
        body.add(new IrBinaryInstruction(index,IrBinaryOperator.ADD,index,new IrConstant(1),R));
        body.add(new IrBinaryInstruction(condition,IrBinaryOperator.LESS_THAN,index,new IrConstant(32),R));
        body.add(new IrBranchInstruction(condition,"loop","done",R));
        var work=new IrFunction("work",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",List.of(
                new IrMoveInstruction(index,new IrConstant(0),R),new IrMoveInstruction(sum,new IrConstant(0),R),new IrJumpInstruction("loop",R))),
                new IrBlock("loop",body),new IrBlock("done",List.of(new IrReturnInstruction(sum,R)))),R);
        var result=new IrTemporary("result",IrType.INT);var mismatch=new IrTemporary("mismatch",IrType.INT);
        var main=new IrFunction("main",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",List.of(
                new IrCallInstruction(result,"work",List.of(),false,R),new IrBinaryInstruction(mismatch,IrBinaryOperator.NOT_EQUAL,result,new IrConstant(expected(type)),R),new IrReturnInstruction(mismatch,R)))),R);
        var a=new IrTemporary("clobberA",IrType.INT);var b=new IrTemporary("clobberB",IrType.INT);
        var effect=new IrFunction("effect",MiniType.INT,List.of(),false,List.of(new IrBlock("entry",List.of(
                new IrMoveInstruction(a,new IrConstant(10),R),new IrMoveInstruction(b,new IrConstant(7),R),
                new IrBinaryInstruction(a,IrBinaryOperator.ADD,a,b,R),new IrReturnInstruction(a,R)))),R);
        return new IrResult(List.of(main,work,effect));
    }
    private static int seed(IrType type){return switch(type){case UNSIGNED_CHAR->250;case SHORT->-32768;case UNSIGNED_INT->12345;case LONG_LONG->-20000;default->throw new IllegalArgumentException();};}
    private static int expected(IrType type){int sum=0;for(int i=0;i<32;i++){int value=seed(type)+i;if(type==IrType.UNSIGNED_CHAR)value&=255;sum+=value;}return sum;}
    private BoundedProcess.Result run(Path exe)throws Exception {var result=BoundedProcess.run(List.of(exe.toString()),temporary,"",Duration.ofSeconds(5),65536);assertFalse(result.timedOut());assertFalse(result.outputExceeded());return result;}
}
