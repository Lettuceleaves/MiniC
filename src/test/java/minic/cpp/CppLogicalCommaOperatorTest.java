package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Stored acceptance cases; executed in the final combined feature suite. */
@Tag("cpp-differential") @Timeout(90)
final class CppLogicalCommaOperatorTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("logical-no-short-circuit", "struct V{int n;bool operator&&(const V&r){return n&&r.n;}bool operator||(const V&r){return n||r.n;}};V make(int n){printf(\"%d \",n);return V{n};}int main(){bool a=make(0)&&make(2);printf(\"%d \",a);bool b=make(3)||make(4);printf(\"%d\",b);return 0;}", "0 2 0 3 4 1"),
        Arguments.of("free-logical-return-type", "struct V{int n;};int operator&&(V a,V b){return a.n+b.n;}int main(){V a={2};V b={3};printf(\"%d\",a&&b);return 0;}", "5"),
        Arguments.of("comma-fold-left", "struct V{int n;V operator,(const V&r){printf(\"%d:%d \",n,r.n);return V{n+r.n};}};int main(){V a={1};V b={2};V c={3};V result=(a,b,c);printf(\"%d\",result.n);return 0;}", "1:2 3:3 6"),
        Arguments.of("comma-reference-result", "struct V{int n;int&operator,(int&r){return r;}};int main(){V a={1};int b=2;(a,b)=9;printf(\"%d\",b);return 0;}", "9"),
        Arguments.of("comma-builtin-fallback", "struct V{int n;};int main(){V a={1};V b={2};int k=0;(++k,a,b).n=7;printf(\"%d %d %d\",k,a.n,b.n);return 0;}", "1 1 7"),
        Arguments.of("builtin-short-circuit-retained", "int mark(){printf(\"BAD\");return 1;}int main(){int a=0&&mark();int b=1||mark();printf(\"%d %d\",a,b);return 0;}", "0 1")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void preservesOverloadedAndBuiltinSequencing(String name,String body,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+body,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout()));
    }
}
