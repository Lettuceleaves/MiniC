package minic.cpp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;

@Tag("cpp-differential") @Timeout(90) @Execution(ExecutionMode.SAME_THREAD)
final class CppAggregateReferenceTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("lvalue-member", "struct A{int& value;};int main(){int n=3;A a={n};a.value=7;printf(\"%d %d\",n,&a.value==&n);return 0;}", "7 1"),
        Arguments.of("rvalue-member-move", "struct A{int&& value;};int main(){int n=6;A a={(int&&)n};A b((A&&)a);b.value=8;printf(\"%d %d\",n,&b.value==&n);return 0;}", "8 1"),
        Arguments.of("const-owner-does-not-const-referent", "struct A{int& value;};int main(){int n=3;const A a={n};a.value=8;printf(\"%d\",n);return 0;}", "8"),
        Arguments.of("nested-and-array", "struct A{int& value;};struct B{A left;A right[2];};int main(){int x=1,y=2,z=3;B b={{x},{{y},{z}}};b.left.value=5;b.right[1].value=9;printf(\"%d %d %d\",x,y,z);return 0;}", "5 2 9"),
        Arguments.of("construction-expression", "struct A{int& value;};A make(int& n){return A{n};}int main(){int n=4;A a=make(n);A{n}.value=6;printf(\"%d %d\",n,a.value);return 0;}", "6 6"),
        Arguments.of("nested-construction-expression", "struct A{int& value;};struct B{A part;};int main(){int n=4;B b=B{{n}};b.part.value=7;printf(\"%d\",n);return 0;}", "7"),
        Arguments.of("once-and-ordered", "int count;int values[2];int& next(){return values[count++];}struct A{int& a;int& b;};int main(){A a={next(),next()};a.a=5;a.b=8;printf(\"%d %d %d\",values[0],values[1],count);return 0;}", "5 8 2"),
        Arguments.of("reference-to-array", "struct A{int(&value)[2];};int main(){int a[2]={2,3};A r={a};r.value[1]=9;printf(\"%d %d\",a[1],&r.value==&a);return 0;}", "9 1"),
        Arguments.of("const-reference", "struct A{const int& value;};int main(){const int n=4;A a={n};printf(\"%d %d\",a.value,&a.value==&n);return 0;}", "4 1")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void initializesReferences(String name,String source,String expected) throws Exception {
        CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,expected);
    }
    static Stream<Arguments> invalid() { return Stream.of(
        Arguments.of("copy-rvalue-reference", "struct A{int&& value;};int main(){int n=1;A a={(int&&)n};A b(a);return 0;}"),
        Arguments.of("lref-prvalue", "struct A{int& value;};int main(){A a={1};return 0;}"),
        Arguments.of("rref-lvalue", "struct A{int&& value;};int main(){int n=1;A a={n};return 0;}"),
        Arguments.of("drop-const", "struct A{int& value;};int main(){const int n=1;A a={n};return 0;}"),
        Arguments.of("missing-reference", "struct A{int& value;};int main(){A a={};return 0;}"),
        Arguments.of("missing-nested-reference", "struct A{int& value;};struct B{A part;};int main(){B b={};return 0;}"),
        Arguments.of("mutate-const-referent", "struct A{const int& value;};int main(){int n=1;A a={n};a.value=2;return 0;}")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void rejectsInvalidBinding(String name,String source) throws Exception { CppReferenceTest.reject(temporary,name,source+" // bad");
        var api=CppReferenceTest.compiler(source);
        var semantic=CppReferenceTest.stage(api,minic.compiler.semantic.SemanticAnalyzer.class);api.runThrough(semantic);
        org.junit.jupiter.api.Assertions.assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals(name.equals("mutate-const-referent")?"SEM001":"CPP004")),()->semantic.errors().toString()); }
}
