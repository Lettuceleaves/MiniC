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
import minic.compiler.ir.optimize.IrOptimizationPipeline;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.compiler.ir.optimize.OptimizationLevel;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
final class CppComparisonBranchFusionTest {
    @TempDir Path temporary;
    private static String compare(String type){return "int compare("+type+" a,"+type+" b){int bits=0;"
            +"if(a==b)bits|=1;if(a!=b)bits|=2;if(a<b)bits|=4;if(a<=b)bits|=8;if(a>b)bits|=16;if(a>=b)bits|=32;return bits;}";}

    @ParameterizedTest @ValueSource(strings={"float","double"})
    void allRelationsPreserveNanBothSidesInfinityAndSignedZero(String type)throws Exception {
        String suffix=type.equals("float")?"f":"";
        String source="#include <stdio.h>\n#include <math.h>\n"+compare(type)+"int main(){"
                +type+" nan=sqrt"+suffix+"(-1.0"+suffix+");"+type+" inf=exp"+suffix+"(1000.0"+suffix+");"
                +type+" values[]={0.0"+suffix+",-0.0"+suffix+",1.0"+suffix+",-1.0"+suffix+",inf,-inf,nan};"
                +"for(int i=0;i<7;++i)for(int j=0;j<7;++j)printf(\"%d\\n\",compare(values[i],values[j]));return 0;}";
        double[] values={0.0,-0.0,1.0,-1.0,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN};
        var expected=new StringBuilder();for(double a:values)for(double b:values)expected.append((a==b?1:0)|(a!=b?2:0)|(a<b?4:0)|(a<=b?8:0)|(a>b?16:0)|(a>=b?32:0)).append('\n');
        check(type,source,expected.toString(),LanguageMode.CPP17_ALGORITHM);
    }

    @ParameterizedTest @ValueSource(strings={"int","long long","unsigned int","unsigned long long"})
    void integerRelationsKeepSignednessAtTheHighBit(String type)throws Exception {
        boolean wide=type.contains("long"),unsigned=type.startsWith("unsigned");
        String suffix=(unsigned?"U":"")+(wide?"LL":"");
        String high=wide?"9223372036854775808ULL":"2147483648U";
        String minimum=unsigned?high:wide?"(-9223372036854775807LL-1LL)":"(-2147483647-1)";
        String maximum=unsigned?(wide?"18446744073709551615ULL":"4294967295U"):(wide?"9223372036854775807LL":"2147483647");
        String source="#include <stdio.h>\n"+compare(type)+"int main(){"+type+" values[]={0"+suffix+",1"+suffix+","+minimum+","+maximum+"};"
                +"for(int i=0;i<4;++i)for(int j=0;j<4;++j)printf(\"%d\\n\",compare(values[i],values[j]));return 0;}";
        int[] order=unsigned?new int[]{0,1,2,3}:new int[]{1,2,0,3};var expected=new StringBuilder();
        for(int a:order)for(int b:order)expected.append((a==b?1:0)|(a!=b?2:0)|(a<b?4:0)|(a<=b?8:0)|(a>b?16:0)|(a>=b?32:0)).append('\n');
        check(type,source,expected.toString(),LanguageMode.CPP17_ALGORITHM);
    }

    @Test void callsAndVolatileReadsKeepTheirOriginalEvaluationAndBooleanUses()throws Exception {
        check("calls","""
                #include <stdio.h>
                int trace=0;int hit(int n){trace=trace*10+n;return n;}
                int main(){volatile int x=2;int a=hit(1),b=hit(2);int result=0;
                  if(a<b)result+=10;if((x>0)&&hit(3))result+=20;
                  bool saved=x<3;hit(4);if(saved)result+=40;
                  printf("%d %d %d\\n",result,trace,saved);return 0;}
                ""","70 1234 1\n",LanguageMode.CPP17_ALGORITHM);
    }

    @Test void sameArrayPointerRelationsPreserveUnsignedAddressComparisons()throws Exception {
        String source="#include <stdio.h>\n"+compare("int*")+"int main(){int values[4]={1,2,3,4};"
                +"for(int i=0;i<5;++i)for(int j=0;j<5;++j)printf(\"%d\\n\",compare(values+i,values+j));return 0;}";
        var expected=new StringBuilder();for(int a=0;a<5;a++)for(int b=0;b<5;b++)expected.append(bits(a,b)).append('\n');
        check("pointers",source,expected.toString(),LanguageMode.CPP17_ALGORITHM);
    }

    /** Frontend promotions hide these widths, so exercise the raw IR accepted by the emission planner. */
    @ParameterizedTest @EnumSource(value=IrType.class,names={"BOOL","CHAR","SIGNED_CHAR","UNSIGNED_CHAR","SHORT","UNSIGNED_SHORT"})
    void rawNarrowComparisonsKeepSignExtensionAndBooleanNormalization(IrType type)throws Exception {
        var range=new SourceRange(1,0,1,8);
        MiniType declared=switch(type){case BOOL->MiniType.BOOL;case CHAR->MiniType.CHAR;case SIGNED_CHAR->MiniType.SIGNED_CHAR;
            case UNSIGNED_CHAR->MiniType.UNSIGNED_CHAR;case SHORT->MiniType.SHORT;case UNSIGNED_SHORT->MiniType.UNSIGNED_SHORT;default->throw new IllegalArgumentException();};
        String spelling=switch(type){case BOOL->"bool";case CHAR->"char";case SIGNED_CHAR->"signed char";case UNSIGNED_CHAR->"unsigned char";
            case SHORT->"short";case UNSIGNED_SHORT->"unsigned short";default->throw new IllegalArgumentException();};
        long min=type.isSignedInteger()?-(1L<<(type.sizeBytes()*8-1)):0;
        long max=type==IrType.BOOL?1:type.isSignedInteger()?(1L<<(type.sizeBytes()*8-1))-1:(1L<<(type.sizeBytes()*8))-1;
        long[] values=type==IrType.BOOL?new long[]{0,1}:new long[]{min,min+1,0,1,max};
        var operators=List.of(IrBinaryOperator.EQUAL,IrBinaryOperator.NOT_EQUAL,IrBinaryOperator.LESS_THAN,
                IrBinaryOperator.LESS_EQUAL,IrBinaryOperator.GREATER_THAN,IrBinaryOperator.GREATER_EQUAL);
        var tokens=List.of("==","!=","<","<=",">",">=");
        var functions=new ArrayList<IrFunction>();var mainBlocks=new ArrayList<IrBlock>();var reference=new StringBuilder("int main(){");
        int index=0;
        for(int op=0;op<operators.size();op++)for(boolean cast:List.of(false,true)) {
            String name="compare"+op+(cast?"cast":"direct");
            var left=new IrParameter("left",declared,type,range);var right=new IrParameter("right",declared,type,range);
            var comparison=new IrTemporary("comparison",IrType.INT);var truth=new IrTemporary("truth",IrType.BOOL);
            var entry=new ArrayList<IrInstruction>();entry.add(new IrBinaryInstruction(comparison,operators.get(op),left.ref(),right.ref(),range));
            if(cast)entry.add(new IrCastInstruction(truth,comparison,range));
            entry.add(new IrBranchInstruction(cast?truth:comparison,"yes","no",range));
            functions.add(new IrFunction(name,MiniType.INT,List.of(left,right),false,List.of(new IrBlock("entry",entry),
                    new IrBlock("yes",List.of(new IrReturnInstruction(new IrConstant(1),range))),
                    new IrBlock("no",List.of(new IrReturnInstruction(new IrConstant(0),range)))),range));
            for(long a:values)for(long b:values) {
                int expected=(bits(a,b)&(1<<op))==0?0:1;
                var result=new IrTemporary("result"+index,IrType.INT);var mismatch=new IrTemporary("mismatch"+index,IrType.INT);
                mainBlocks.add(new IrBlock(index==0?"entry":"case"+index,List.of(
                    new IrCallInstruction(result,name,List.of(new IrConstant(a,type),new IrConstant(b,type)),false,range),
                    new IrBinaryInstruction(mismatch,IrBinaryOperator.NOT_EQUAL,result,new IrConstant(expected),range),
                    new IrBranchInstruction(mismatch,"failure","case"+(index+1),range))));
                reference.append("if((((").append(spelling).append(")").append(a).append(")")
                        .append(tokens.get(op)).append("((").append(spelling).append(")").append(b).append("))!=").append(expected).append(")return 1;");
                index++;
            }
        }
        mainBlocks.add(new IrBlock("case"+index,List.of(new IrReturnInstruction(new IrConstant(0),range))));
        mainBlocks.add(new IrBlock("failure",List.of(new IrReturnInstruction(new IrConstant(1),range))));
        functions.add(0,new IrFunction("main",MiniType.INT,List.of(),false,mainBlocks,range));
        var ir=new IrResult(functions);reference.append("return 0;}");var source=new SourceFile("raw-narrow.cpp",reference.toString());
        for(var level:OptimizationLevel.values()) {
            var assembler=new Assembler(ir,new IrOptimizationPipeline(level,List.of()));
            var directory=temporary.resolve(level.name());var object=new ObjBuilder(source,assembler,directory,"program");
            var linker=new Linker(source,object,directory,"program");new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
            assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
            assertEquals(0,run(directory.resolve("program.exe")).exitCode(),type+" "+level);
        }
        var debug=DebugApi.fromIr(source,ir,"");for(int steps=0;debug.canNext()&&steps<30_000;steps++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(0,debug.current().runtime().termination().status());
        Path cpp=temporary.resolve("reference.cpp"),exe=temporary.resolve("reference.exe");Files.writeString(cpp,reference.toString());
        var compiled=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-O2",cpp.toString(),"-o",exe.toString()),temporary,"",Duration.ofSeconds(20),65_536);
        assertFalse(compiled.timedOut());assertFalse(compiled.outputExceeded());assertEquals(0,compiled.exitCode(),compiled::stderr);
        assertEquals(0,run(exe).exitCode());
    }

    private BoundedProcess.Result run(Path exe)throws Exception {
        var result=BoundedProcess.run(List.of(exe.toString()),temporary,"",Duration.ofSeconds(5),65_536);
        assertFalse(result.timedOut());assertFalse(result.outputExceeded());return result;
    }
    private static int bits(long a,long b){return (a==b?1:0)|(a!=b?2:0)|(a<b?4:0)|(a<=b?8:0)|(a>b?16:0)|(a>=b?32:0);}

    private void check(String name,String source,String expected,LanguageMode mode)throws Exception {
        var limits=new CppDifferentialHarness.Limits(Duration.ofSeconds(30),Duration.ofSeconds(15),200_000,1_048_576);
        for(var level:OptimizationLevel.values()){
            var report=new CppDifferentialHarness(temporary.resolve(level.name()),CppDifferentialHarness.referenceCompiler(System.getenv()),limits,mode,level).run(name,source,"");
            assertTrue(report.passed(),report::describe);
            for(var outcome:report.outcomes().values())assertEquals(expected,outcome.stdout().replace("\r\n","\n"),outcome.backend().toString());
        }
    }
}
