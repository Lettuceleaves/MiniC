package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120) final class CppRepeatedTemplateDeductionTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={
        "template<class T>int f(T a,T b){return 0;}int main(){return f(1,2.0);}",
        "template<class T>int f(T* a,T* b){return 0;}int main(){int a=1;double b=2;return f(&a,&b);}",
        "template<class T>int f(T& a,T& b){return 0;}int main(){int a=1;const int b=2;return f(a,b);}",
        "template<class T>int f(T&& a,T&& b){return 0;}int main(){int a=1;return f(a,2);}",
        "template<int N>int f(int(&a)[N],int(&b)[N]){return 0;}int main(){int a[2]={};int b[3]={};return f(a,b);}",
        "template<class T,class U>int f(T a,U b,U c){return 0;}int main(){return f<int>(1,2,3.0);}"
    }) void conflictingDeductionsAreNotImplicitConversions(String source)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
            CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).compile("conflict",source);
        report.outcomes().values().forEach(outcome->assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,outcome.status(),report::describe));
    }
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("explicit-conversion","template<class T>int f(T a,T b){return (int)(a+b);}int main(){printf(\"%d\\n\",f<int>(1,2.5));return 0;}","3\n"),
        Arguments.of("value-cv","template<class T>int f(T a,T b){return a+b;}int main(){int a=1;const int b=2;printf(\"%d\\n\",f(a,b));return 0;}","3\n"),
        Arguments.of("pack-prefix","template<class... T>int f(T... values){return sizeof...(T);}int main(){printf(\"%d %d\\n\",f<int>(2.5,3.5),f(1,2.0));return 0;}","2 2\n"),
        Arguments.of("deduction-fallback","template<class T>int f(T a,T b){return 1;}int f(...){return 2;}int main(){printf(\"%d %d\\n\",f(1,2),f(1,2.0));return 0;}","1 2\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void explicitArgumentsAndIndependentPacksStillPermitConversions(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,expected);
    }
}
