package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppCopyListPackTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("empty-and-nonempty", "template<int... N>int size(){int values[]={7,N...};return sizeof(values)/sizeof(int);}int main(){printf(\"%d %d\\n\",size<>(),size<2,4,6>());return 0;}", "1 4\n"),
        Arguments.of("ordered-effects", "template<class... T>int bump(T&... values){int sequence[]={0,(++values)...};return sequence[1]*10+sequence[2];}int main(){int a=1,b=2;int result=bump(a,b);printf(\"%d %d %d\\n\",result,a,b);return 0;}","23 2 3\n"),
        Arguments.of("nested-braces", "struct Pair{int a;int b;};template<int... N>int sum(){Pair values[]={{N,N+1}...};int total=0;for(auto value:values)total+=value.a+value.b;return total;}int main(){printf(\"%d\\n\",sum<2,4>());return 0;}","14\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void copyListExpansionsPreserveElementOrderAndEmptyPacks(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.MINIC_DEBUG).stdout());
    }
}
