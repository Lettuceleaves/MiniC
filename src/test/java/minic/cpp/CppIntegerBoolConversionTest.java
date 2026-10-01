package minic.cpp;

import minic.compiler.LanguageMode;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppIntegerBoolConversionTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        String[] types={"char","signed char","unsigned char","short","unsigned short","int","unsigned int",
                "long","unsigned long","long long","unsigned long long"};
        String[] values={"127","-1","128","256","512","256","0x80000000U","-65536L","0xffffffffUL",
                "-4294967296LL","0x8000000000000000ULL"};
        StringBuilder widthSource=new StringBuilder("#include <stdio.h>\n");
        for(int i=0;i<types.length;i++) widthSource.append("int convert").append(i).append('(').append(types[i])
                .append(" value){bool converted=(bool)value;return (int)converted;}");
        String format="%d".repeat(types.length)+"\\n";
        String nonzero=IntStream.range(0,types.length).mapToObj(i->"convert"+i+"("+values[i]+")").collect(Collectors.joining(","));
        String zero=IntStream.range(0,types.length).mapToObj(i->"convert"+i+"(0)").collect(Collectors.joining(","));
        widthSource.append("int main(){printf(\"").append(format).append("\",").append(nonzero)
                .append(");printf(\"").append(format).append("\",").append(zero).append(");return 0;}");
        String contexts="""
                #include <stdio.h>
                bool returnAsBool(long long value){return value;}
                int acceptsBool(bool value){return (int)value;}
                int main(){int value=256;bool initialized=value;bool assigned=false;assigned=value;
                    printf("%d%d%d%d\\n",initialized,assigned,returnAsBool(4294967296LL),acceptsBool(value));
                    value=0;initialized=value;assigned=value;
                    printf("%d%d%d%d\\n",initialized,assigned,returnAsBool(0LL),acceptsBool(value));return 0;}
                """;
        String controls="""
                #include <stdio.h>
                #include <math.h>
                bool identity(bool value){return (bool)value;}
                int main(){int value=1;int *pointer=&value;bool present=(bool)pointer;bool absent=(bool)((int*)0);
                    double half=0.5;double nan=sqrt(0.0-1.0);bool fraction=(bool)half;bool unordered=(bool)nan;
                    printf("%d%d%d%d%d%d\\n",identity(true),identity(false),present,absent,fraction,unordered);return 0;}
                """;
        return Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).flatMap(mode->Stream.of(
                Arguments.of(mode,"integer-widths",widthSource.toString(),"1".repeat(types.length)+"\n"+"0".repeat(types.length)+"\n"),
                Arguments.of(mode,"implicit-conversion-sites",contexts,"1111\n0000\n"),
                Arguments.of(mode,"bool-pointer-floating-controls",controls,"101011\n")
        ));
    }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void conversionProducesCanonicalZeroOrOne(LanguageMode mode,String name,String source,String expected) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,source,"");
        assertTrue(report.passed(),report::describe);
        for(var outcome:report.outcomes().values())assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe);
    }
}
