package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.CompilerApi;
import minic.compiler.SourceFile;
import minic.compiler.asm.Assembler;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppFloatingTruthTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of("float", "double").flatMap(type -> {
            String suffix = type.equals("float") ? "f" : "";
            String prelude = "#include <stdio.h>\n#include <math.h>\n";
            String values = type + " value=sqrt"+suffix+"(0.0"+suffix+"-1.0"+suffix+");"
                    + type+" zero=0.0"+suffix+";";
            return Stream.of(
                    Arguments.of(type+"-branch-and-conditional", prelude+"int main(){"+values+"""
                            int taken=0;if(value)taken=1;
                            printf("%d %d\\n",taken,value?2:3);return 0;}
                            """, "1 2\n"),
                    Arguments.of(type+"-logical-not", prelude+"int main(){"+values+"""
                            printf("%d %d %d\\n",!value,!zero,!!value);return 0;}
                            """, "0 1 1\n"),
                    Arguments.of(type+"-bool-cast", prelude+"int main(){"+values+type+" half=0.5"+suffix+";"
                            +type+" negativeZero=zero*(zero-1.0"+suffix+");"+"""
                            bool truth=(bool)value;bool absent=(bool)zero;bool fraction=(bool)half;bool negative=(bool)negativeZero;
                            printf("%d %d %d %d\\n",truth?1:0,absent?1:0,fraction?1:0,negative?1:0);return 0;}
                            """, "1 0 1 0\n"),
                    Arguments.of(type+"-short-circuit", prelude+"int trace=0;int hit(int n){trace=trace*10+n;return 1;}int main(){"+values+"""
                            int a=value&&hit(1);int b=value||hit(2);int c=zero&&hit(3);int d=zero||hit(4);
                            int e=1&&value;int f=0||value;
                            printf("%d %d %d %d %d %d %d\\n",trace,a,b,c,d,e,f);return 0;}
                            """, "14 1 1 0 1 1 1\n"),
                    Arguments.of(type+"-nan-comparisons", prelude+"int main(){"+values+"""
                            printf("%d%d%d%d%d%d %d%d%d%d%d%d %d%d%d%d%d%d\\n",
                                value==zero,value!=zero,value<zero,value<=zero,value>zero,value>=zero,
                                zero==value,zero!=value,zero<value,zero<=value,zero>value,zero>=value,
                                value==value,value!=value,value<value,value<=value,value>value,value>=value);return 0;}
                            """, "010000 010000 010000\n"),
                    Arguments.of(type+"-ordered-controls", prelude+"int main(){"+type+" zero=0.0"+suffix+";"
                            +type+" negative=zero-2.0"+suffix+";"+type+" half=0.5"+suffix+";"+"""
                            printf("%d%d%d%d%d%d %d%d%d%d%d%d %d%d%d%d%d%d\\n",
                                negative==zero,negative!=zero,negative<zero,negative<=zero,negative>zero,negative>=zero,
                                zero==zero,zero!=zero,zero<zero,zero<=zero,zero>zero,zero>=zero,
                                half==zero,half!=zero,half<zero,half<=zero,half>zero,half>=zero);return 0;}
                            """, "011100 100101 010011\n"),
                    Arguments.of(type+"-nan-denominator", prelude+"int main(){"+values+type+" quotient=1.0"+suffix+"/value;"+"""
                            printf("%d\\n",quotient!=quotient);return 0;}
                            """, "1\n")
            );
        });
    }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void floatingTruthAndOrderingMatchCpp17(String name,String source,String expected) throws Exception {
        assertProgram(name,source,expected);
    }

    static Stream<Arguments> orderedTruthPrograms() {
        return Stream.of("float","double").map(type->{
            String suffix=type.equals("float")?"f":"";
            String source="#include <stdio.h>\n#include <math.h>\nint main(){"
                    +type+" zero=0.0"+suffix+";"+type+" negative=zero-0.5"+suffix+";"
                    +type+" negativeZero=zero*(zero-1.0"+suffix+");"+type+" half=0.5"+suffix+";"
                    +type+" positiveInfinity=exp"+suffix+"(1000.0"+suffix+");"
                    +type+" negativeInfinity=zero-positiveInfinity;"+"""
                    printf("%d %d %d %d %d %d\\n",negative?1:0,zero?1:0,negativeZero?1:0,half?1:0,positiveInfinity?1:0,negativeInfinity?1:0);
                    printf("%d %d %d %d %d %d\\n",!negative,!zero,!negativeZero,!half,!positiveInfinity,!negativeInfinity);
                    bool a=(bool)negative;bool b=(bool)negativeZero;bool c=(bool)positiveInfinity;bool d=(bool)negativeInfinity;
                    printf("%d %d %d %d\\n",a?1:0,b?1:0,c?1:0,d?1:0);
                    printf("%d %d %d %d %d %d %d %d\\n",positiveInfinity>half,negativeInfinity<negative,
                        positiveInfinity==positiveInfinity,positiveInfinity<=positiveInfinity,negativeInfinity>=negativeInfinity,
                        positiveInfinity==negativeInfinity,negativeZero==zero,negativeZero!=zero);return 0;}
                    """;
            return Arguments.of(type+"-ordered-truth-controls",source,"1 0 0 1 1 1\n0 1 1 0 0 0\n1 0 1 1\n1 1 1 1 1 0 1 0\n");
        });
    }

    @ParameterizedTest(name="{0}") @MethodSource("orderedTruthPrograms")
    void finiteZeroAndInfinityControls(String name,String source,String expected) throws Exception {
        assertProgram(name,source,expected);
    }

    static Stream<Arguments> zeroDenominators() {
        return Stream.of("float","double").flatMap(type->Stream.of(false,true).map(negative->Arguments.of(type,negative)));
    }

    @ParameterizedTest(name="{0}: negative zero={1}") @MethodSource("zeroDenominators")
    void zeroDenominatorsRetainTheExistingMiniCCheckedDivisionPolicy(String type,boolean negative) throws Exception {
        // This tests MiniC's existing checked-division policy, not a claim about C++ floating exceptions.
        String suffix=type.equals("float")?"f":"";
        var source=new SourceFile("checked-floating-zero.cpp","int main(){"+type+" zero=0.0"+suffix+";"
                +(negative?"zero=zero*(zero-1.0"+suffix+");":"")+type+" result=1.0"+suffix+"/zero;return 0;}");
        var ir=new CompilerApi(source,LanguageMode.CPP17_ALGORITHM).runToIr();
        var assembler=new Assembler(ir);
        var object=new ObjBuilder(source,assembler,temporary,"program");
        var linker=new Linker(source,object,temporary,"program");
        new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
        assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
        var nativeRun=BoundedProcess.run(List.of(temporary.resolve("program.exe").toString()),temporary,"",Duration.ofSeconds(10),65536);
        assertFalse(nativeRun.timedOut(),nativeRun::stderr);
        assertFalse(nativeRun.outputExceeded(),nativeRun::stderr);
        assertEquals(102,nativeRun.exitCode());
        var debug=DebugApi.fromIr(source,ir,"");
        for(int steps=0;debug.canNext()&&steps<1000;steps++)debug.next();
        assertFalse(debug.canNext());
        assertEquals(Debugger.Status.FAILED,debug.current().stop().status());
        assertTrue(debug.current().stop().error().contains("zero"),debug.current().stop()::error);
    }

    private void assertProgram(String name,String source,String expected) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,source,"");
        assertTrue(report.passed(),report::describe);
        for(var outcome:report.outcomes().values())
            assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe);
    }
}
