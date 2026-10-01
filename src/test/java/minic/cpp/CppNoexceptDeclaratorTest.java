package minic.cpp;

import minic.compiler.LanguageMode;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
final class CppNoexceptDeclaratorTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("pointer", "int(*p)()noexcept=f;", "p()"),
        Arguments.of("reference", "int(&p)()noexcept=f;", "p()"),
        Arguments.of("rvalue-reference", "int(&&p)()noexcept=f;", "p()"),
        Arguments.of("conditional", "int(*p)()noexcept((sizeof(int)>1)&&noexcept(f()))=f;", "p()"),
        Arguments.of("false", "int(*p)()noexcept(false)=f;", "p()"),
        Arguments.of("cast", "", "((int(*)()noexcept)f)()"),
        Arguments.of("type-query", "", "sizeof(int(*)()noexcept)==sizeof(void*)?7:0")
    ); }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void functionDeclaratorsKeepTheirExceptionSpecification(String name,String declaration,String result) throws Exception {
        var report=harness().run(name,"#include <stdio.h>\nint f()noexcept{return 7;}int main(){"
                +declaration+"printf(\"%d\\n\","+result+");return 0;}","");
        assertTrue(report.passed(),report::describe);
        assertEquals("7\n",report.outcomes().get(CppDifferentialHarness.Backend.MINIC_DEBUG).stdout());
    }

    @ParameterizedTest @ValueSource(strings={
        "template<class T>struct A{int f()noexcept;};template<class T>int A<T>::f()noexcept(false){return 1;}int main(){A<int>a;return a.f();}",
        "template<class T>struct A{A()noexcept;};template<class T>A<T>::A()noexcept(false){}int main(){A<int>a;return 0;}",
        "template<class T>struct A{~A()noexcept;};template<class T>A<T>::~A()noexcept(false){}int main(){A<int>a;return 0;}"
    })
    void usedTemplateMembersRequireMatchingSpecifications(String source) throws Exception {
        var report=harness().compile("exception-spec-mismatch",source);
        for(var backend:CppDifferentialHarness.Backend.values())
            assertEquals(CppDifferentialHarness.Status.COMPILE_ERROR,report.outcomes().get(backend).status(),report::describe);
    }

    private CppDifferentialHarness harness() {
        return new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM);
    }
}
