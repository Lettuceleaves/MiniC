package minic.cpp;

import minic.compiler.*;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Queued for final unified acceptance, including reference compiler comparison. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppTemplateMemberTypesTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("ordinary-member-types","""
            struct Record { typedef int value_type; value_type value; value_type get(){return value;} };
            int main(){Record::value_type n=7;Record r={n};printf("%d\\n",r.get());return 0;}
            ""","7\n"),
        Arguments.of("dependent-type-chain","""
            template<class T>struct Identity{typedef T type;};
            template<class T>struct Wrapper{typedef typename Identity<T>::type value_type;value_type value;value_type get(){return value;}};
            int main(){typename Wrapper<int>::value_type n=7;Wrapper<int> a={n};Wrapper<double> b={2.5};printf("%d %.1f\\n",a.get(),b.get());return 0;}
            ""","7 2.5\n"),
        Arguments.of("argument-member-alias","""
            struct Record{typedef double value_type;};
            template<class T>struct Box{typename T::value_type value;typename T::value_type get(){return value;}};
            int main(){Box<Record> box={3.5};printf("%.1f\\n",box.get());return 0;}
            ""","3.5\n"),
        Arguments.of("nested-type-alias","""
            template<class T>struct Identity{typedef T type;};
            template<class T>struct Box{typedef typename Identity<typename Identity<T>::type>::type type;type value;};
            int main(){Box<int>::type n=9;Box<int> box={n};printf("%d\\n",box.value);return 0;}
            ""","9\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void memberTypesAreResolvedPerInstance(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
}
