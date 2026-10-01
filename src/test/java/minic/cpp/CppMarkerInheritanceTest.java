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

/** Generic marker-class inheritance; data-bearing and virtual bases remain diagnosed. */
@Timeout(120)
final class CppMarkerInheritanceTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("nearest-base-value","struct A{};struct B:public A{};struct C:public B{};int pick(A){return 1;}int pick(B){return 2;}int main(){C c;printf(\"%d\\n\",pick(c));return 0;}","2\n"),
        Arguments.of("pointer-and-reference","struct A{};struct B:public A{};struct C:public B{};int pick(const A*){return 1;}int pick(const B*){return 2;}int pick(void*){return 3;}int take(const A& a){return 7;}int main(){C c;const A& r=c;A* p=&c;printf(\"%d %d %d\\n\",pick(&c),take(C()),p==&r);return 0;}","2 7 1\n"),
        Arguments.of("rvalue-reference","struct A{};struct B:public A{};int pick(const A&){return 1;}int pick(A&&){return 2;}int main(){B b;printf(\"%d %d\\n\",pick(b),pick(B()));return 0;}","1 2\n"),
        Arguments.of("temporary-evaluated-once","int calls;struct A{};struct B:public A{};B make(){++calls;return B();}int take(A){return 9;}int main(){int result=take(make());printf(\"%d %d\\n\",result,calls);return 0;}","9 1\n"),
        Arguments.of("list-base-conversion","int calls;struct A{};struct B:public A{};B make(){++calls;return B();}int take(A){return 9;}int main(){A a{make()};int result=take({make()});printf(\"%d %d\\n\",result,calls);return 0;}","9 2\n"),
        Arguments.of("generic-tag-dispatch","#include <iterator>\nint pick(std::input_iterator_tag){return 1;}int pick(std::bidirectional_iterator_tag){return 2;}struct Category:std::random_access_iterator_tag{};int main(){printf(\"%d\\n\",pick(Category()));return 0;}","2\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void markerConversionsMatchReference(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "struct A{};struct B:public A{};int main(){const B b;A* p=&b;return 0;} // bad",
        "struct A{};struct B:public A{};int main(){B b;A&& r=b;return 0;} // bad",
        "struct A{};struct B:public A{};int main(){A a;B* p=&a;return 0;} // bad"
    })
    void invalidMarkerBindingsRetainStandardDiagnostics(String source)throws Exception{
        CppReferenceTest.reject(temporary,"invalid-marker",source);
    }
    @ParameterizedTest @ValueSource(strings={
        "struct A{int x;};struct B:public A{};int main(){return 0;}",
        "struct A{};struct B:private A{};int main(){return 0;}",
        "struct A{};struct B:virtual public A{};int main(){return 0;}",
        "struct A{};struct B{};struct C:public A,public B{};int main(){return 0;}"
    })
    void unsupportedInheritanceIsExplicitlyDiagnosed(String source){
        var api=CppReferenceTest.compiler(source);
        var semantic=CppReferenceTest.stage(api,minic.compiler.semantic.SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP005")),()->semantic.errors().toString());
    }
}
