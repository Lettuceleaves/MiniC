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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Object bytes are accessed through unsigned char on the supported little-endian Windows x64 target. */
@Tag("cpp-differential") @Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppFloatingNegationTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(LanguageMode.C,LanguageMode.CPP17_ALGORITHM).flatMap(mode->Stream.of("float","double").flatMap(type->{
            String suffix=type.equals("float")?"f":"";
            String header="#include <stdio.h>\n#include <math.h>\n";
            String sign="int sign("+type+" value){unsigned char *bytes=(unsigned char*)&value;return bytes[sizeof(value)-1]>>7;}";
            String finite=header+sign+"int main(){"+type+" positive=1.5"+suffix+";"+type+" zero=0.0"+suffix+";"
                    +type+" negative=zero-positive;"+type+" negativeZero=zero*(zero-1.0"+suffix+");"
                    +type+" positiveInfinity=exp"+suffix+"(1000.0"+suffix+");"+type+" negativeInfinity=zero-positiveInfinity;"
                    +type+" a=-positive;"+type+" b=-negative;"+type+" c=-zero;"+type+" d=-negativeZero;"
                    +type+" e=-positiveInfinity;"+type+" f=-negativeInfinity;"+"""
                    printf("%d %d %d %d %d %d\\n",a<zero,b>zero,sign(c),sign(d),sign(e),sign(f));
                    printf("%d %d %d %d\\n",e==negativeInfinity,f==positiveInfinity,c==zero,d==zero);return 0;}
                    """;
            String nan=header+"int main(){for(int sign=0;sign<2;sign++){"+type+" value=0.0"+suffix+";"
                    +"unsigned char *bytes=(unsigned char*)&value;bytes[0]=52;bytes[1]=18;"
                    +"bytes[sizeof(value)-2]="+(type.equals("float")?192:248)+";bytes[sizeof(value)-1]=127+128*sign;"
                    +type+" result=-value;unsigned char *output=(unsigned char*)&result;"+"""
                    int same=1;for(int i=0;i<sizeof(value)-1;i++){if(bytes[i]!=output[i])same=0;}
                    if((bytes[sizeof(value)-1]&127)!=(output[sizeof(value)-1]&127))same=0;
                    printf("%d %d %d\\n",result!=result,output[sizeof(value)-1]>>7,same);}return 0;}
                    """;
            return Stream.of(Arguments.of(mode,type+"-finite-zero-infinity",finite,"1 1 1 0 1 0\n1 1 1 1\n"),
                    Arguments.of(mode,type+"-nan-sign-and-payload",nan,"1 1 1\n1 0 1\n"));
        }));
    }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void negationFlipsOnlyTheFloatingSignBit(LanguageMode mode,String name,String source,String expected) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),mode).run(name,source,"");
        assertTrue(report.passed(),report::describe);
        for(var outcome:report.outcomes().values())assertEquals(expected,outcome.stdout().replace("\r\n","\n"),report::describe);
    }
}
