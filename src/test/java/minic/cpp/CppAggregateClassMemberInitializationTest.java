package minic.cpp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;

@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppAggregateClassMemberInitializationTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("class-field", "struct T{int n;T(int x):n(x){printf(\"C%d \",n);}~T(){printf(\"D%d \",n);}};struct D{T value;~D(){printf(\"R \");}};int main(){{D d{T{1}};printf(\"M%d \",d.value.n);}printf(\"E\");return 0;}","C1 M1 R D1 E"),
        Arguments.of("nested-and-arrays", "struct T{int n;T(int x):n(x){}};struct A{T items[2];};struct B{A a;T t;};int main(){B b{{{T{2},T{3}}},T{4}};printf(\"%d %d %d\",b.a.items[0].n,b.a.items[1].n,b.t.n);return 0;}","2 3 4"),
        Arguments.of("omitted-default-members", "struct A{int x=4;int y=x+3;};int main(){A a{2};A b{};printf(\"%d %d %d %d\",a.x,a.y,b.x,b.y);return 0;}","2 5 4 7"),
        Arguments.of("nested-construction-expression", "struct T{int n;T(int x):n(x){}};struct A{T item;};int main(){A a=A{T{7}};printf(\"%d\",a.item.n);return 0;}","7"),
        Arguments.of("omitted-class-member", "struct T{int n;T():n(8){}};struct A{int x;T item;};int main(){A a{2};printf(\"%d %d\",a.x,a.item.n);return 0;}","2 8"),
        Arguments.of("aggregate-copy-single-element", "struct T{int n;T(int x):n(x){}};struct A{T item;};int main(){A a{T{4}};A b{a};printf(\"%d\",b.item.n);return 0;}","4"),
        Arguments.of("implicit-class-conversion", "struct T{int n;T(int x):n(x){}};struct A{T item;};int main(){A a{5};printf(\"%d\",a.item.n);return 0;}","5"),
        Arguments.of("direct-field-list-constructor", "struct T{int n;T(int x,int y):n(x+y){}};struct A{T item;};int main(){A a{{2,5}};printf(\"%d\",a.item.n);return 0;}","7"),
        Arguments.of("reference-with-class-member", "struct T{int n;T(int x):n(x){}};struct A{int& ref;T item;};int main(){int x=1;A a{x,T{4}};a.ref=9;printf(\"%d %d\",x,a.item.n);return 0;}","9 4"),
        Arguments.of("return-braced-aggregate", "struct V{int value;explicit operator bool()const{return value!=0;}};V make(int x){return {x>0};}int main(){V v=make(2);printf(\"%d\",v.value);return 0;}","1"),
        Arguments.of("return-braced-class-fields", "struct T{int n;T(int x):n(x){}};struct A{T item;};A make(){return {T{6}};}int main(){A a=make();printf(\"%d\",a.item.n);return 0;}","6"),
        Arguments.of("return-empty-scalar", "int f(){return {};}int main(){printf(\"%d\",f());return 0;}","0"),
        Arguments.of("brace-elided-record", "struct T{int n;T(int x):n(x){}};struct A{T item;int n;};struct B{A item;int tail;};int main(){B b{3,4,5};printf(\"%d %d %d\",b.item.item.n,b.item.n,b.tail);return 0;}","3 4 5"),
        Arguments.of("brace-elided-array-field", "struct T{int n;T(int x):n(x){}};struct A{int items[2];T item;};int main(){A a{3,4,T{5}};printf(\"%d %d %d\",a.items[0],a.items[1],a.item.n);return 0;}","3 4 5"),
        Arguments.of("brace-elided-record-array", "struct T{int n;T(int x):n(x){}};struct A{T item;int n;};int main(){A a[2]={1,2,3,4};printf(\"%d %d %d %d\",a[0].item.n,a[0].n,a[1].item.n,a[1].n);return 0;}","1 2 3 4"),
        Arguments.of("array-string-appertainment", "struct T{int n;T(int x):n(x){}};struct A{char text[3];const char* labels[2];T item;};int main(){A a{\"hi\",\"a\",\"b\",T{4}};printf(\"%s %s %s %d\",a.text,a.labels[0],a.labels[1],a.item.n);return 0;}","hi a b 4"),
        Arguments.of("brace-elision-single-evaluation", "int calls=0;int next(){return ++calls;}struct T{int n;T(int x):n(x){}};struct A{T item;int n;};struct B{A item;int tail;};int main(){B b{next(),next(),next()};printf(\"%d %d %d %d\",b.item.item.n,b.item.n,b.tail,calls);return 0;}","1 2 3 3"),
        Arguments.of("in-place-class-field", "struct T{T* self;int n;T(int x):self(this),n(x){}};struct A{T item;};int main(){A a{T{8}};printf(\"%d %d\",a.item.self==&a.item,a.item.n);return 0;}","1 8"),
        Arguments.of("aggregate-union-zero", "union U{int n;double d;};int main(){U u=U{};printf(\"%d\",u.n);return 0;}","0"),
        Arguments.of("nested-plain-record-expression", "struct T{int n;};struct A{T item;int tail;};int main(){A a=A{{5}};printf(\"%d %d\",a.item.n,a.tail);return 0;}","5 0"),
        Arguments.of("aggregate-deleted-default-constructor", "struct A{A()=delete;int value;};int main(){A a{5};printf(\"%d\",a.value);return 0;}","5")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void initializesMembers(String name,String source,String expected)throws Exception{
        CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,expected);
    }
    @Test void referenceTemporaryOutlivesConstructedValueMember() throws Exception {
        // CWG2256: the completed aggregate is destroyed before its lifetime-extended temporary.
        // G++ 8.1 predates the DR and orders those final two destructor calls differently.
        String source="#include <stdio.h>\nstruct T{int n;T(int x):n(x){printf(\"C%d \",n);}~T(){printf(\"D%d \",n);}};struct A{T item;const T& ref;};int main(){{A a{T{1},T{2}};printf(\"M%d%d \",a.item.n,a.ref.n);}printf(\"E\");return 0;}";
        var report=new minic.cpp.support.CppDifferentialHarness(temporary,
                minic.cpp.support.CppDifferentialHarness.referenceCompiler(System.getenv()),
                minic.cpp.support.CppDifferentialHarness.Limits.defaults(),minic.compiler.LanguageMode.CPP17_ALGORITHM)
                .run("class-and-reference-lifetime",source,"");
        for(var backend:java.util.List.of(minic.cpp.support.CppDifferentialHarness.Backend.MINIC_NATIVE,
                minic.cpp.support.CppDifferentialHarness.Backend.MINIC_DEBUG)) {
            var outcome=report.outcomes().get(backend);
            org.junit.jupiter.api.Assertions.assertEquals(minic.cpp.support.CppDifferentialHarness.Status.OK,outcome.status(),report::describe);
            org.junit.jupiter.api.Assertions.assertEquals("C1 C2 M12 D1 D2 E",outcome.stdout(),report::describe);
        }
        org.junit.jupiter.api.Assertions.assertEquals(minic.cpp.support.CppDifferentialHarness.Status.OK,
                report.outcomes().get(minic.cpp.support.CppDifferentialHarness.Backend.GXX).status(),report::describe);
    }
    @Test void predicateLibraryPreservesCustomComparisonResult() throws Exception {
        var library=new CppLibrarySourcesTest();library.temporary=temporary;
        library.libraryProgramsAgree("library-contract/predicate-return-contract.cpp", "");
    }
    static Stream<Arguments> invalid(){return Stream.of(
        Arguments.of("private-field", "struct T{T(int){}};class A{T item;};int main(){A a{T{1}};return 0;}"),
        Arguments.of("excess", "struct T{T(int){}};struct A{T item;};int main(){A a{T{1},T{2}};return 0;}"),
        Arguments.of("narrowing-scalar", "struct T{T(int){}};struct A{int n;T item;};int main(){A a{1.5,T{2}};return 0;}"),
        Arguments.of("explicit-field-copy", "struct T{explicit T(int){}};struct A{T item;};int main(){A a{1};return 0;}"),
        Arguments.of("deleted-field-constructor", "struct T{T(int)=delete;};struct A{T item;};int main(){A a{1};return 0;}"),
        Arguments.of("return-narrowing", "struct A{int value;};A make(){return {1.5};}int main(){return 0;}"),
        Arguments.of("explicit-field-copy-list", "struct T{explicit T(int){}};struct A{T item;};int main(){A a{{1}};return 0;}"),
        Arguments.of("return-explicit-copy-list", "struct A{explicit A(int){}};A make(){return {1};}int main(){return 0;}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void rejectsInvalidInitializers(String name,String source)throws Exception{CppReferenceTest.reject(temporary,name,source+" // bad");}
}
