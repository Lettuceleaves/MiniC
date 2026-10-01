package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.*;
import minic.compiler.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred unified acceptance. These programs are not executed during feature implementation. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppStructuredBindingTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("array-copy","int main(){int a[2]={2,3};auto [x,y]=a;x=7;printf(\"%d %d %d\\n\",x,y,a[0]);return 0;}","7 3 2\n"),
        Arguments.of("array-reference","int main(){int a[2]={2,3};auto&[x,y]=a;x+=y;printf(\"%d %d\\n\",a[0],a[1]);return 0;}","5 3\n"),
        Arguments.of("array-forward-reference","int main(){int a[2]={4,5};auto&&[x,y]=a;y=7;printf(\"%d %d\\n\",x,a[1]);return 0;}","4 7\n"),
        Arguments.of("multidimensional-copy","int main(){int a[2][2]={{1,2},{3,4}};auto [x,y]=a;x[1]=9;printf(\"%d %d %d\\n\",x[1],y[0],a[0][1]);return 0;}","9 3 2\n"),
        Arguments.of("array-expression-once","int calls;int a[2]={3,5};int(&get())[2]{calls++;return a;}int main(){auto [x,y]=get();printf(\"%d %d %d\\n\",x,y,calls);return 0;}","3 5 1\n"),
        Arguments.of("member-copy-and-static-skipped","struct Pair{int first;static int ignored;int second;};int main(){Pair p={2,4};auto [x,y]=p;x=8;printf(\"%d %d %d\\n\",x,y,p.first);return 0;}","8 4 2\n"),
        Arguments.of("member-reference","struct Pair{int first;int second;};int main(){Pair p={3,4};auto&[x,y]=p;y+=x;printf(\"%d %d\\n\",p.first,p.second);return 0;}","3 7\n"),
        Arguments.of("cv-reference-decltype","struct Pair{int first;int second;};int pick(int*){return 1;}int pick(const int*){return 2;}int main(){Pair p={2,4};const auto&[x,y]=p;decltype(x) value=7;decltype((x)) ref=x;printf(\"%d %d %d\\n\",pick(&value),pick(&ref),y);return 0;}","2 2 4\n"),
        Arguments.of("nonreference-decltype","struct Pair{int first;int second;};int main(){Pair p={2,4};auto&[x,y]=p;decltype(x) value=8;decltype((x)) ref=x;ref=9;printf(\"%d %d %d\\n\",value,x,p.first);return 0;}","8 9 9\n"),
        Arguments.of("reference-member-through-const-owner","struct Ref{int&value;int other;Ref(int&x):value(x),other(3){}};int main(){int value=2;const Ref p(value);const auto&[x,y]=p;x=7;decltype(x) r=value;r=8;printf(\"%d %d\\n\",value,y);return 0;}","8 3\n"),
        Arguments.of("temporary-extended-owner","int live;struct Pair{int a;int b;Pair():a(2),b(3){live++;}~Pair(){live--;}};int main(){int sum;{const auto&[x,y]=Pair();sum=x+y;if(live!=1)return 1;}printf(\"%d %d\\n\",sum,live);return 0;}","5 0\n"),
        Arguments.of("backing-copy-constructor","int copies;struct Pair{int a;int b;Pair():a(2),b(5){}Pair(const Pair&p):a(p.a),b(p.b){copies++;}};int main(){Pair p;auto [x,y]=p;printf(\"%d %d %d\\n\",x,y,copies);return 0;}","2 5 1\n"),
        Arguments.of("array-element-copy-and-cleanup","int copies;int deaths;struct Item{int value;Item(int n):value(n){}Item(const Item&o):value(o.value){copies++;}~Item(){deaths++;}};int main(){Item a[2]={Item(2),Item(3)};int sum;{auto[x,y]=a;sum=x.value+y.value;}printf(\"%d %d %d\\n\",sum,copies,deaths);return 0;}","5 2 2\n"),
        Arguments.of("range-member-reference","struct Pair{int first;int second;};int main(){Pair a[2]={{1,2},{3,4}};for(auto&[x,y]:a)y+=x;printf(\"%d %d\\n\",a[0].second,a[1].second);return 0;}","3 7\n"),
        Arguments.of("range-const-copy-and-continue","struct Pair{int first;int second;};int main(){Pair a[3]={{1,2},{3,4},{5,6}};int sum=0;for(const auto&[x,y]:a){if(x==3)continue;sum+=x+y;}printf(\"%d\\n\",sum);return 0;}","14\n"),
        Arguments.of("classic-for-binding","struct Pair{int a;int b;};int main(){Pair p={1,2};int sum=0;for(auto [x,y]=p;x<4;x++)sum+=x+y;printf(\"%d\\n\",sum);return 0;}","12\n"),
        Arguments.of("global-binding-and-namespace-lookup","namespace N{struct Pair{int a;int b;};Pair p={2,5};auto&[x,y]=p;}int main(){N::x=7;printf(\"%d %d\\n\",N::p.a,N::y);return 0;}","7 5\n"),
        Arguments.of("global-array-copy","int a[2]={3,4};auto[x,y]=a;int main(){x=8;printf(\"%d %d %d\\n\",x,y,a[0]);return 0;}","8 4 3\n"),
        Arguments.of("init-capture-binding-is-legal","struct Pair{int a;int b;};int main(){Pair p={2,3};auto[x,y]=p;auto f=[v=x]{return v;};printf(\"%d\\n\",f());return 0;}","2\n"),
        Arguments.of("function-template-local-binding","template<class T>int sum(const T&p){const auto&[x,y]=p;return x+y;}struct Pair{int a;int b;};int main(){Pair p={2,5};printf(\"%d\\n\",sum(p));return 0;}","7\n"),
        Arguments.of("function-template-range-binding","template<class T>int sum(T(&rows)[2]){int total=0;for(const auto&[x,y]:rows)total+=x+y;return total;}struct Pair{int a;int b;};int main(){Pair p[2]={{1,2},{3,4}};printf(\"%d\\n\",sum(p));return 0;}","10\n"),
        Arguments.of("generic-lambda-local-binding","struct Pair{int a;int b;};int main(){auto f=[](const auto&p){const auto&[x,y]=p;return x+y;};Pair p={3,4};printf(\"%d\\n\",f(p));return 0;}","7\n"),
        Arguments.of("nested-public-class-member","struct Inner{int value;};struct Outer{Inner first;int second;};int main(){Outer p={{3},4};auto&[x,y]=p;x.value=7;printf(\"%d %d\\n\",p.first.value,y);return 0;}","7 4\n"),
        Arguments.of("volatile-array-reference","int main(){volatile int values[2]={2,3};auto&[x,y]=values;x=5;printf(\"%d %d\\n\",values[0],y);return 0;}","5 3\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void agreesWithCpp(String name,String source,String expected)throws Exception{agree(temporary,name,"#include <stdio.h>\n"+source,expected);}

    static Stream<Arguments> invalidPrograms(){return Stream.of(
        Arguments.of("count","int main(){int a[2]={};auto[x]=a; // bad\nreturn 0;}"),
        Arguments.of("duplicate","int main(){int a[2]={};auto[x,x]=a; // bad\nreturn 0;}"),
        Arguments.of("scalar","int main(){auto[x]=2; // bad\nreturn 0;}"),
        Arguments.of("missing-initializer","int main(){auto[x,y]; // bad\nreturn 0;}"),
        Arguments.of("const-write","int main(){int a[2]={};const auto&[x,y]=a;x=3; // bad\nreturn 0;}"),
        Arguments.of("private-field","class Pair{int a;public:int b;};int main(){Pair p;auto&[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("union","union Pair{int a;int b;};int main(){Pair p={};auto&[x]=p; // bad\nreturn 0;}"),
        Arguments.of("anonymous-union-member","struct Pair{union{int a;int b;};int c;};int main(){Pair p={};auto&[x,y]=p; // bad\nreturn 0;}"),
        Arguments.of("lref-to-temporary","struct Pair{int a;int b;};int main(){auto&[x,y]=Pair{2,3}; // bad\nreturn 0;}"),
        Arguments.of("point-of-declaration","int main(){int x[2]={2,3};{auto[x,y]=x; // bad\n}return 0;}"),
        Arguments.of("range-body-redeclaration","struct Pair{int a;int b;};int main(){Pair p[1]={};for(auto&[x,y]:p){int x=1; // bad\n}return 0;}"),
        Arguments.of("switch-bypasses-binding","int main(){int values[2]={};switch(1){case 1:auto[x,y]=values;break;case 2:return 1; // bad\n}return 0;}"),
        Arguments.of("binding-not-visible-after-range","int main(){int p[1][2]={{1,2}};for(auto&[x,y]:p){}return x; // bad\n}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void rejectsInvalidBinding(String name,String source)throws Exception{reject(temporary,name,source);}

    @Test void preservesEveryBindingOriginAndHasNoSourceSyntaxInCore(){
        var api=compiler("int main(){int values[2]={1,2};auto&[left,right]=values;left=7;return right-2;}");
        var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var binding=nodes(parser.result().program()).stream().filter(CppStructuredBindingDecl.class::isInstance)
                .map(CppStructuredBindingDecl.class::cast).findFirst().orElseThrow();
        var result=semantic.semanticResult();assertNotNull(result.sourceToCore().get(binding));
        for(var name:binding.names()){
            var core=result.sourceToCore().get(name);assertNotNull(core);assertEquals(name.range(),core.range());
            assertTrue(core instanceof Statement.VarDeclStmt);
        }
        assertEquals(LanguageMode.C,result.program().languageMode());assertNull(AstChildren.firstCppSyntax(result.program()));
        assertTrue(result.displayNames().containsValue("left"));assertTrue(result.displayNames().containsValue("right"));
        assertThrows(UnsupportedOperationException.class,()->binding.names().clear());
    }
}
