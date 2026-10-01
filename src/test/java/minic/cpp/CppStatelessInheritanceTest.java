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

/** Queued source-level acceptance for stateless class bases; never relies on STL names. */
@Timeout(180)
final class CppStatelessInheritanceTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("inherited-call-and-conversion","struct B{int operator()()const{return 4;} operator int()const{return 5;}};struct D:B{};int main(){D d;printf(\"%d %d\\n\",d(),(int)d);return 0;}","4 5\n"),
        Arguments.of("inherited-qualified-types","struct B{typedef int value_type;typedef B type;static const int value=7;};const int B::value;struct D:B{};int main(){D::value_type x=D::value;const int* a=&D::value;const int* b=&B::value;printf(\"%d %d\\n\",x,a==b);return 0;}","7 1\n"),
        Arguments.of("nondependent-base-type-in-body","struct B{typedef int value_type;static const int value=9;};struct D:B{value_type get()const{return value;}};int main(){D d;printf(\"%d\\n\",d.get());return 0;}","9\n"),
        Arguments.of("nondependent-base-type-outside","struct B{typedef int value_type;};struct D:B{value_type get()const;};int D::get()const{value_type x=11;return x;}int main(){D d;printf(\"%d\\n\",d.get());return 0;}","11\n"),
        Arguments.of("dependent-base","template<class T,int V>struct B{typedef T value_type;static const int value=V;T operator()()const{return V;}};template<class T>struct D:B<T,13>{};int main(){D<int> d;D<int>::value_type x=d();printf(\"%d %d\\n\",x,D<int>::value);return 0;}","13 13\n"),
        Arguments.of("dependent-base-qualified-alias","template<class T>struct B{typedef T type;};template<class T>struct D:B<T>{typedef typename B<T>::type result;};int main(){D<int>::result x=17;printf(\"%d\\n\",x);return 0;}","17\n"),
        Arguments.of("nearest-inherited-member","struct A{int get()const{return 1;}};struct B:A{int get()const{return 2;}};struct D:B{};int take(const A& a){return a.get();}int main(){D d;printf(\"%d %d\\n\",d.get(),take(d));return 0;}","2 1\n"),
        Arguments.of("static-receiver-once","int calls;struct B{static int get(){return 19;}};struct D:B{};D make(){++calls;return D();}int main(){int n=make().get();printf(\"%d %d\\n\",n,calls);return 0;}","19 1\n"),
        Arguments.of("mutable-inherited-overload","struct B{int get(){return 1;}int get()const{return 2;}};struct D:B{};int main(){D a;const D b{};printf(\"%d %d\\n\",a.get(),b.get());return 0;}","1 2\n"),
        Arguments.of("base-associated-namespace","namespace n{struct B{};int use(const B&){return 23;}}struct D:n::B{};int main(){D d;printf(\"%d\\n\",use(d));return 0;}","23\n"),
        Arguments.of("member-template","struct B{template<class T>T value(T x)const{return x;}};struct D:B{};int main(){D d;printf(\"%d\\n\",d.value(29));return 0;}","29\n"),
        Arguments.of("conversion-hiding","struct B{operator int()const{return 1;}};struct D:B{operator int()const{return 2;}};int main(){D d;printf(\"%d\\n\",(int)d);return 0;}","2\n")
        ,Arguments.of("inherited-conversion-ranking","struct B{operator int()const{return 3;}operator double()const{return 4.5;}};struct D:B{};int main(){D d;printf(\"%d %.1f\\n\",(int)d,(double)d);return 0;}","3 4.5\n")
        ,Arguments.of("implicit-derived-this","struct B{int get()const{return 5;}};struct D:B{int use()const{return get();}};int main(){D d;printf(\"%d\\n\",d.use());return 0;}","5\n")
        ,Arguments.of("implicit-derived-this-lambda","struct B{int get()const{return 6;}};struct D:B{int use()const{auto f=[this](){return get();};return f();}};int main(){D d;printf(\"%d\\n\",d.use());return 0;}","6\n")
        ,Arguments.of("protected-derived-access","struct B{protected:typedef int P;static const int n=2;int get()const{return 7;}};struct D:B{P use()const{return this->get()+n;}};int main(){D d;printf(\"%d\\n\",d.use());return 0;}","9\n")
        ,Arguments.of("protected-outside-signature","struct B{protected:typedef int P;int get()const{return 3;}};struct D:B{int use(P)const;};int D::use(P n)const{return get()+n;}int main(){D d;printf(\"%d\\n\",d.use(4));return 0;}","7\n")
        ,Arguments.of("nondependent-template-base-alias","template<class T>struct B{typedef T P;};struct D:B<int>{P get()const{return 10;}};int main(){D d;printf(\"%d\\n\",d.get());return 0;}","10\n")
        ,Arguments.of("nondependent-partial-base-alias","template<class T>struct B{};template<class T>struct B<T*>{typedef T P;};struct D:B<int*>{P get()const{return 11;}};int main(){D d;printf(\"%d\\n\",d.get());return 0;}","11\n")
        ,Arguments.of("nondependent-base-first-stage-lookup","struct B{int get()const{return 12;}};template<class T>struct D:B{int use()const{return get();}};int main(){D<int>d;printf(\"%d\\n\",d.use());return 0;}","12\n")
        ,Arguments.of("nondependent-base-outside-template-method","struct B{int get()const{return 13;}};template<class T>struct D:B{int use()const;};template<class U>int D<U>::use()const{return get();}int main(){D<int>d;printf(\"%d\\n\",d.use());return 0;}","13\n")
        ,Arguments.of("method-template-hides-namespace-type","typedef double convert;struct B{template<class T>int convert(T)const{return 14;}};struct D:B{int use()const{return convert(1);}};int main(){D d;printf(\"%d\\n\",d.use());return 0;}","14\n")
        ,Arguments.of("inherited-injected-class-name","namespace N{struct B{};}struct D:N::B{B* self(){return this;}};int main(){D d;N::B*p=d.self();printf(\"%d\\n\",p==(N::B*)&d);return 0;}","1\n")
        ,Arguments.of("inherited-qualified-injected-template-name","namespace N{template<class T>struct B{};}struct D:N::B<int>{B* self(){return this;}};int main(){D d;D::B*p=d.self();printf(\"%d\\n\",p==(N::B<int>*)&d);return 0;}","1\n")
        ,Arguments.of("constexpr-marker-static-assert","struct B{static constexpr int value=7;static_assert(value==7,\"base\");};struct D:B{static_assert(value==7,\"derived\");constexpr int get()const{return value;}};int main(){D d;printf(\"%d\\n\",d.get());return 0;}","7\n")
        ,Arguments.of("constexpr-template-marker","template<class T>struct B{static constexpr int value=sizeof(T);static_assert(sizeof(T)>0,\"base\");};template<class T>constexpr int B<T>::value;struct D:B<int>{static_assert(value==sizeof(int),\"derived\");};int main(){printf(\"%d\\n\",D::value==(int)sizeof(int));return 0;}","1\n")
        ,Arguments.of("protected-outside-return-type","struct B{protected:typedef int P;};struct D:B{P get()const;};D::P D::get()const{return 31;}int main(){D d;printf(\"%d\\n\",d.get());return 0;}","31\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void inheritedMembersMatchCpp(String name,String source,String expected)throws Exception{
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+source,"");
        assertTrue(report.passed(),report::describe);
        assertEquals(expected,report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n","\n"));
    }
    @ParameterizedTest @ValueSource(strings={
        "struct B{private:static const int value=1;};struct D:B{};int main(){return D::value;}",
        "struct B{private:int get()const{return 1;}};struct D:B{};int main(){D d;return d.get();}",
        "struct B{private:typedef int type;};struct D:B{};int main(){D::type x=1;return x;}",
        "struct B{int get(int)const{return 1;}};struct D:B{int get()const{return 2;}};int main(){D d;return d.get(1);}",
        "struct B{typedef int type;};struct D:B{static int type;};int main(){D::type x=1;return x;}",
        "struct B{int get(){return 1;}};struct D:B{};int main(){const D d{};return d.get();}",
        "struct B{protected:int get(){return 1;}};struct D:B{int bad(B& b){return b.get();}};int main(){return 0;}",
        "struct B{private:int get(){return 1;}};struct D:B{int bad(){return get();}};int main(){return 0;}",
        "int item=7;struct B{typedef int item;};struct D:B{int bad(){return item;}};int main(){D d;return d.bad();}",
        "typedef int convert;struct B{template<class T>T convert(T x)const{return x;}};struct D:B{convert bad();};int main(){return 0;}",
        "struct B{static_assert(false,\"base assertion\");};struct D:B{};int main(){return 0;}",
        "struct B{};struct D:B{static_assert(false,\"derived assertion\");};int main(){return 0;}"
    })
    void inheritedLookupKeepsAccessHidingAndCv(String source)throws Exception{
        CppReferenceTest.reject(temporary,"invalid-stateless-inheritance",source);
    }
    @ParameterizedTest @ValueSource(strings={
        "struct B{B(){}};struct D:B{};int main(){return 0;}",
        "struct B{~B(){}};struct D:B{};int main(){return 0;}",
        "struct B{B& operator=(const B&){return *this;}};struct D:B{};int main(){return 0;}",
        "struct B{};struct D:B{int data;};int main(){return 0;}"
    })
    void statefulLifetimeAndLayoutRemainExplicitlyDiagnosed(String source){
        var api=CppReferenceTest.compiler(source);
        var semantic=CppReferenceTest.stage(api,minic.compiler.semantic.SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP005")),()->semantic.errors().toString());
    }
}
