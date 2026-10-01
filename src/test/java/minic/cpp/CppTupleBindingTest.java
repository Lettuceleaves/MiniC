package minic.cpp;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static minic.cpp.CppReferenceTest.*;

/** User-defined tuple protocol, without library-name or pair-layout special cases. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppTupleBindingTest {
    @TempDir Path temporary;
    static final String TRAITS="namespace std{template<class T>struct tuple_size;template<unsigned long long I,class T>struct tuple_element;}\n";
    static String traits(String name,int count,String element){return "namespace std{template<>struct tuple_size<"+name+">{static const int value="+count+";};template<unsigned long long I>struct tuple_element<I,"+name+">{typedef "+element+" type;};}\n";}
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("adl-get","namespace N{struct Pair{int a;int b;};template<unsigned long long I>int&get(Pair&p){if(I==0)return p.a;return p.b;}}"+traits("N::Pair",2,"int")+"int main(){N::Pair p={2,3};auto&[x,y]=p;y=7;printf(\"%d %d\\n\",x,p.b);return 0;}","2 7\n"),
        Arguments.of("member-get-preferred","int calls;struct Pair{int a;int b;template<unsigned long long I>int&get(){calls++;if(I==0)return b;return a;}};template<unsigned long long I>int&get(Pair&p){return p.a;}"+traits("Pair",2,"int")+"int main(){Pair p={2,5};auto&[x,y]=p;printf(\"%d %d %d\\n\",x,y,calls);return 0;}","5 2 2\n"),
        Arguments.of("tuple-precedes-member-count","struct Pair{int data[2];template<unsigned long long I>int&get(){return data[I];}};"+traits("Pair",2,"int")+"int main(){Pair p={{3,7}};auto&[x,y]=p;printf(\"%d %d\\n\",x,y);return 0;}","3 7\n"),
        Arguments.of("tuple-rvalue-forwarding","int category;namespace N{struct Pair{int a;int b;};template<unsigned long long I>int&get(Pair&p){category=1;if(I==0)return p.a;return p.b;}template<unsigned long long I>int&&get(Pair&&p){category=2;if(I==0)return(int&&)p.a;return(int&&)p.b;}}"+traits("N::Pair",2,"int")+"int main(){N::Pair p={2,3};auto[x,y]=p;int first=category;auto&[a,b]=p;printf(\"%d %d %d %d\\n\",first,category,x,b);return 0;}","2 1 2 3\n"),
        Arguments.of("tuple-prvalue-result-reference-lifetime","int calls;struct Pair{int a;int b;template<unsigned long long I>int get(){calls++;if(I==0)return a;return b;}};"+traits("Pair",2,"int")+"int main(){Pair p={3,4};auto[x,y]=p;int waste[3]={8,9,10};x+=2;printf(\"%d %d %d\\n\",x,y,calls);return waste[0]-8;}","5 4 2\n"),
        Arguments.of("tuple-reference-element-decltype","struct Pair{int a;int b;template<unsigned long long I>int&get(){if(I==0)return a;return b;}};"+traits("Pair",2,"int&")+"int main(){Pair p={3,4};auto&[x,y]=p;decltype(x) r=y;r=8;printf(\"%d %d\\n\",x,p.b);return 0;}","3 8\n"),
        Arguments.of("tuple-const-E","namespace N{struct Pair{int a;int b;};template<unsigned long long I>const int&get(const Pair&p){if(I==0)return p.a;return p.b;}}"+traits("const N::Pair",2,"const int")+"int main(){N::Pair p={4,5};const auto&[x,y]=p;printf(\"%d %d\\n\",x,y);return 0;}","4 5\n"),
        Arguments.of("tuple-incomplete-trait-falls-back","struct Pair{int a;int b;};int main(){Pair p={4,7};auto[x,y]=p;printf(\"%d %d\\n\",x,y);return 0;}","4 7\n"),
        Arguments.of("tuple-ordinary-get-not-considered","namespace N{struct Pair{int a;int b;};template<unsigned long long I>int&get(Pair&p){if(I==0)return p.a;return p.b;}}template<unsigned long long I>int&get(N::Pair&p){return p.b;}"+traits("N::Pair",2,"int")+"int main(){N::Pair p={2,5};auto&[x,y]=p;printf(\"%d %d\\n\",x,y);return 0;}","2 5\n"),
        Arguments.of("tuple-type-template-get-does-not-block-adl","namespace N{struct Pair{int a;int b;template<class T>int get(){return 99;}};template<unsigned long long I>int&get(Pair&p){if(I==0)return p.a;return p.b;}}"+traits("N::Pair",2,"int")+"int main(){N::Pair p={2,7};auto&[x,y]=p;printf(\"%d %d\\n\",x,y);return 0;}","2 7\n"),
        Arguments.of("tuple-temporary-elements-destroyed-at-scope","int live;struct Item{int n;Item(int x):n(x){live++;}~Item(){live--;}};struct Pair{int a;int b;template<unsigned long long I>Item get(){if(I==0)return Item(a);return Item(b);}};"+traits("Pair",2,"Item")+"int main(){Pair p={2,5};int sum;{auto[x,y]=p;sum=x.n+y.n;if(live!=2)return 2;}printf(\"%d %d\\n\",sum,live);return 0;}","7 0\n"),
        Arguments.of("tuple-range","struct Pair{int a;int b;template<unsigned long long I>int&get(){if(I==0)return b;return a;}};"+traits("Pair",2,"int")+"int main(){Pair p[2]={{1,2},{3,4}};int sum=0;for(auto&[x,y]:p){x+=y;sum+=x;}printf(\"%d %d %d\\n\",sum,p[0].b,p[1].b);return 0;}","10 3 7\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void agreesWithCpp(String name,String source,String expected)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+TRAITS+source,expected);}

    static Stream<Arguments> invalidPrograms(){return Stream.of(
        Arguments.of("tuple-count","struct Pair{int a;int b;};"+traits("Pair",1,"int")+"int main(){Pair p={};auto[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("tuple-missing-value","struct Pair{int a;int b;};namespace std{template<>struct tuple_size<Pair>{};}int main(){Pair p={};auto[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("tuple-private-get","class Pair{int a;int b;template<unsigned long long I>int&get(){return a;}};"+traits("Pair",2,"int")+"int main(){Pair p;auto&[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("tuple-ordinary-only-get-is-ignored","namespace N{struct Pair{int a;int b;};}template<unsigned long long I>int&get(N::Pair&p){return p.a;}"+traits("N::Pair",2,"int")+"int main(){N::Pair p={};auto&[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("tuple-missing-element-type","struct Pair{int a;int b;template<unsigned long long I>int&get(){return a;}};namespace std{template<>struct tuple_size<Pair>{static const int value=2;};}int main(){Pair p={};auto&[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("tuple-incompatible-reference","struct Pair{int a;int b;template<unsigned long long I>int get(){return a;}};"+traits("Pair",2,"int&")+"int main(){Pair p={};auto&[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("tuple-const-reference-write","struct Pair{int a;int b;template<unsigned long long I>const int&get()const{return a;}};"+traits("const Pair",2,"const int")+"int main(){Pair p={};const auto&[x,y]=p;x=2; // bad\nreturn 0;}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void rejectsInvalidTuple(String name,String source)throws Exception{reject(temporary,name,TRAITS+source);}
}
