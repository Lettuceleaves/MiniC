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

/** Saved for unified acceptance; these cases were not run during feature implementation. */
@Tag("cpp-frontend") @Timeout(120)
final class CppClassStaticMembersTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("shared-storage", """
            struct Box{static int value;int member;};int Box::value=4;
            int main(){Box a={1},b={2};a.value+=3;b.value*=2;
              printf("%d %d %d %llu\\n",Box::value,a.member,b.member,(unsigned long long)sizeof(Box));return 0;}
            """, "14 1 2 4\n"),
        Arguments.of("constant-nonodr", """
            struct Bounds{static const int count=7;};
            int main(){int n=Bounds::count;printf("%d %llu\\n",n,(unsigned long long)sizeof(Bounds));return 0;}
            """, "7 1\n"),
        Arguments.of("constant-odr", """
            struct Bounds{static const int count=7;};const int Bounds::count;
            int main(){const int* p=&Bounds::count;const int& r=Bounds::count;
              printf("%d %d %d\\n",*p,r,p==&r);return 0;}
            """, "7 7 1\n"),
        Arguments.of("mixed-overloads", """
            struct C{static int f(int x){return x+10;}int f(double x)const{return (int)x+20;}};
            int main(){C c;const C d;printf("%d %d %d\\n",c.f(1),d.f(2.5),C::f(3));return 0;}
            """, "11 22 13\n"),
        Arguments.of("receiver-once", """
            int calls=0;struct C{static int f(int x){return x+1;}static int value;};int C::value=8;
            C object;C& get(){++calls;return object;}
            int main(){int a=get().f(3);int b=get().value;printf("%d %d %d\\n",a,b,calls);return 0;}
            """, "4 8 2\n"),
        Arguments.of("static-address", """
            struct C{static int f(int x){return x+10;}static int f(double x){return (int)x+20;}};
            int main(){int (*a)(int)=C::f;int (*b)(double)=&C::f;
              printf("%d %d\\n",a(1),b(2.5));return 0;}
            """, "11 22\n"),
        Arguments.of("out-of-line-scope", """
            class C{static int value;public:static int add(int);};int C::value=2;
            int C::add(int n){value+=n;return value;}
            int main(){printf("%d\\n",C::add(5));return 0;}
            """, "7\n"),
        Arguments.of("template-static-method", """
            template<class T>struct C{typedef T value_type;static T twice(T value){return value+value;}};
            int main(){printf("%d %.1f\\n",C<int>::twice(4),C<double>::twice(2.5));return 0;}
            """, "8 5.0\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void sourceSemanticsAgree(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"),report::describe);
        assertTrue(report.passed(),report::describe);
    }
}
