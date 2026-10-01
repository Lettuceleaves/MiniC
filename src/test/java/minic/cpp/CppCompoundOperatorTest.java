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

@Tag("cpp-differential") @Timeout(90)
final class CppCompoundOperatorTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("all-members", "struct V{int n;V&operator+=(int x){n+=x;return *this;}V&operator-=(int x){n-=x;return *this;}V&operator*=(int x){n*=x;return *this;}V&operator/=(int x){n/=x;return *this;}V&operator%=(int x){n%=x;return *this;}V&operator^=(int x){n^=x;return *this;}V&operator&=(int x){n&=x;return *this;}V&operator|=(int x){n|=x;return *this;}V&operator<<=(int x){n<<=x;return *this;}V&operator>>=(int x){n>>=x;return *this;}};int main(){V a={5};a+=7;a-=2;a*=4;a/=2;a%=13;a^=3;a&=6;a|=1;a<<=2;a>>=1;printf(\"%d %d\",a.n,&(a+=0)==&a);return 0;}", "10 1"),
        Arguments.of("free-candidate", "namespace N{struct V{int n;};V&operator+=(V&v,int n){v.n+=n;return v;}}int main(){N::V a={3};N::V&r=(a+=4);printf(\"%d %d\",a.n,&r==&a);return 0;}", "7 1"),
        Arguments.of("scalar-left-class-right", "struct V{int n;};int&operator+=(int&left,const V&right){left+=right.n;return left;}int main(){int n=3;V v={8};n+=v;printf(\"%d\",n);return 0;}", "11"),
        Arguments.of("rhs-before-receiver", "struct V{int n;V&operator+=(int x){n+=x;return *this;}};V&left(V&v){printf(\"L \");return v;}int right(){printf(\"R \");return 4;}int main(){V a={3};left(a)+=right();printf(\"%d\",a.n);return 0;}", "R L 7"),
        Arguments.of("rhs-final-parameter-address", "struct R{R*self;R():self(this){}R(const R&v):self(this){}~R(){printf(\"D \");}};struct V{int n;V&operator+=(R r){printf(\"%d \",r.self==&r);++n;return *this;}};V&left(V&v){printf(\"L \");return v;}R right(){printf(\"R \");return R();}int main(){V a={0};left(a)+=right();printf(\"%d\",a.n);return 0;}", "R L 1 D 1")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void usesOrdinaryOverloadsWithAssignmentSequencing(String name,String body,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+body,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout()));
    }
}
