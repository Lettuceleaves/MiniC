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

/** Queued for the final unified run; no stage-specific execution is required. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppTemplateDefaultsTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("pair","""
            template<class T,class U>struct Pair{T first;U second;T get(){return first;}};
            int main(){Pair<int,double> pair={3,2.5};printf("%d %.1f\\n",pair.get(),pair.second);return 0;}
            ""","3 2.5\n"),
        Arguments.of("dependent-default","""
            template<class T=int,class U=T*>struct Box{T value;U pointer;};
            int main(){int value=7;Box<> box={3,&value};Box<double,double> other={2.5,4.5};printf("%d %d %.1f\\n",box.value,*box.pointer,other.pointer);return 0;}
            ""","3 7 4.5\n"),
        Arguments.of("nested-default","""
            template<class T>struct Box{T value;T& get(){return value;}};
            template<class T,class U=Box<T>>struct Outer{U inner;};
            int main(){Outer<int> o={{7}};Box<Box<int>> nested={{8}};printf("%d %d %d\\n",o.inner.get(),nested.get().get(),16>>2);return 0;}
            ""","7 8 4\n"),
        Arguments.of("merged-defaults","""
            template<class T,class U=int>struct Pair;
            template<class T=double,class U>struct Pair{T first;U second;};
            int main(){Pair<> pair={2.5,7};printf("%.1f %d\\n",pair.first,pair.second);return 0;}
            ""","2.5 7\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void defaultsAndNestedTypeIdsUseTheSameStaticInstantiationPath(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout().replace("\r\n","\n")));
    }
}
