package minic.cpp;

import minic.compiler.*;
import minic.cpp.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Expression substitution acceptance corpus, saved and Java-compiled before the unified run. */
@Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppExpressionSfinaeTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("builtin-assignment-validity", """
            template<class T>T&& value();
            template<class T,class U,class=decltype(value<T>()=value<U>())>int assignable(int){return 1;}
            template<class T,class U>int assignable(...){return 0;}
            int main(){printf("%d %d %d\\n",assignable<int&,int>(0),assignable<const int&,int>(0),assignable<int,int>(0));return 0;}
            ""","1 0 0\n"),
        Arguments.of("builtin-operand-validity", """
            template<class T>T&& value();
            template<class T,class=decltype(value<T>()%2)>int integer(int){return 1;}
            template<class T>int integer(...){return 0;}
            int main(){printf("%d %d %d\\n",integer<int>(0),integer<double>(0),integer<int*>(0));return 0;}
            ""","1 0 0\n"),
        Arguments.of("trailing-return-and-member-access", """
            struct Yes{int read(){return 8;}};struct No{};
            template<class T>auto probe(T& value)->decltype(value.read()){return value.read();}
            int probe(...){return 2;}
            int main(){Yes a;No b;printf("%d %d\\n",probe(a),probe(b));return 0;}
            ""","8 2\n"),
        Arguments.of("class-partial-member-type-substitution", """
            template<class... T>struct Void{typedef void type;};
            template<class T,class=void>struct Trait{static const int value=0;};
            template<class T>struct Trait<T,typename Void<typename T::first,typename T::second>::type>{static const int value=1;};
            struct Good{typedef int first;typedef double second;};struct Half{typedef int first;};
            int main(){printf("%d %d %d\\n",Trait<Good>::value,Trait<Half>::value,Trait<int>::value);return 0;}
            ""","1 0 0\n"),
        Arguments.of("referenceability-partial-pattern", """
            template<class... T>struct Void{typedef void type;};
            template<class T,class=void>struct Ref{static const int value=0;};
            template<class T>struct Ref<T,typename Void<T&>::type>{static const int value=1;};
            int main(){printf("%d %d\\n",Ref<int>::value,Ref<void>::value);return 0;}
            ""","1 0\n"),
        Arguments.of("unevaluated-void-call-and-empty-braces", """
            struct Implicit{Implicit(){}};struct Explicit{explicit Explicit(){}};
            template<class T>void accept(T);
            struct True{static const int value=1;};struct False{static const int value=0;};
            template<class T,class=decltype(accept<T>({}))>True check(int);
            template<class T>False check(...);
            template<class T>int result(){return decltype(check<T>(0))::value;}
            int main(){printf("%d %d\\n",result<Implicit>(),result<Explicit>());return 0;}
            ""","1 0\n"),
        Arguments.of("access-control-is-immediate-context", """
            template<class T>T&& value();
            struct Private{private:int hidden();};struct Public{int hidden();};
            template<class T,class=decltype(value<T>().hidden())>int visible(int){return 1;}
            template<class T>int visible(...){return 0;}
            int main(){printf("%d %d\\n",visible<Private>(0),visible<Public>(0));return 0;}
            ""","0 1\n"),
        Arguments.of("dependent-static-value-and-construction", """
            struct Tag{static const int value=4;};
            template<class T>int read(){return T::value;}
            template<class T>int make(){typename T::type object;return object.value;}
            struct Item{int value;Item():value(9){}};struct Holder{typedef Item type;};
            int main(){printf("%d %d\\n",read<Tag>(),make<Holder>());return 0;}
            ""","4 9\n"),
        Arguments.of("default-nttp-type-distinguishes-overloads", """
            template<bool B,class T=int>struct Enable{};template<class T>struct Enable<true,T>{typedef T type;};
            template<class T,typename Enable<(sizeof(T)==4),int>::type=0>int select(T){return 4;}
            template<class T,typename Enable<(sizeof(T)!=4),long>::type=0>int select(T){return 8;}
            int main(){printf("%d %d\\n",select(1),select(1.0));return 0;}
            ""","4 8\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void immediateFailureRemovesOnlyTheCandidate(String name,String source,String expected)throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM).run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "template<class T>struct Broken{typedef typename T::missing type;};template<class T>typename Broken<T>::type probe(T);int probe(...);int main(){return probe(1);}",
        "template<class T>auto broken(T value){return value.missing();}template<class T>auto probe(T value)->decltype(broken(value));int probe(...);int main(){return probe(1);}",
        "template<class T>int selected(T value){return value.missing();}int selected(...){return 0;}int main(){return selected(1);}",
        "template<class T>int bad(T value){return missing_nondependent;}int main(){return 0;}",
        "int main(){const int value=1;decltype((value=2)) x=value;return 0;}"
    })
    void instantiationBodyErrorsAreNotSwallowed(String source)throws Exception {
        Path file=temporary.resolve("invalid.cpp");Files.writeString(file,source);
        var reference=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()).toString(),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),64*1024);
        assertFalse(reference.timedOut());assertFalse(reference.outputExceeded());assertNotEquals(0,reference.exitCode());
        var api=new CompilerApi(new SourceFile("invalid-sfinae.cpp",source),LanguageMode.CPP17_ALGORITHM);
        var semantic=api.stages().stream().filter(minic.compiler.semantic.SemanticAnalyzer.class::isInstance).findFirst().orElseThrow();api.runThrough(semantic);
        assertTrue(api.stages().stream().anyMatch(stage->!stage.errors().isEmpty()));
    }
}
