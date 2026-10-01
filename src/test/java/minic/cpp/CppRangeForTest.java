package minic.cpp;

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

/** Retained for unified execution after the full language implementation. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppRangeForTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("array-reference","#include <stdio.h>\nint main(){int a[3]={1,2,3};for(int&v:a)v*=2;printf(\"%d %d %d\\n\",a[0],a[1],a[2]);return 0;}","2 4 6\n"),
        Arguments.of("array-copy","#include <stdio.h>\nint main(){int a[2]={2,3};int sum=0;for(auto v:a){v++;sum+=v;}printf(\"%d %d\\n\",sum,a[0]);return 0;}","7 2\n"),
        Arguments.of("const-array-reference","#include <stdio.h>\nint main(){const int a[2]={2,5};int sum=0;for(const auto&v:a)sum+=v;printf(\"%d\\n\",sum);return 0;}","7\n"),
        Arguments.of("multidimensional-array","#include <stdio.h>\nint main(){int a[2][2]={{1,2},{3,4}};int sum=0;for(auto&row:a)for(int&v:row){sum+=v;v++;}printf(\"%d %d\\n\",sum,a[1][1]);return 0;}","10 5\n"),
        Arguments.of("string-array-bound","#include <stdio.h>\nint main(){int count=0;for(char c:\"abc\")count++;printf(\"%d\\n\",count);return 0;}","4\n"),
        Arguments.of("outer-name-in-range","#include <stdio.h>\nint main(){int values[2]={3,4};int sum=0;for(int values:values)sum+=values;printf(\"%d %d\\n\",sum,values[0]);return 0;}","7 3\n"),
        Arguments.of("member-begin-end-once","#include <stdio.h>\nint b;int e;struct R{int a[2];int*begin(){b++;return a;}int*end(){e++;return a+2;}};int main(){R r={{2,5}};int sum=0;for(int v:r)sum+=v;printf(\"%d %d %d\\n\",sum,b,e);return 0;}","7 1 1\n"),
        Arguments.of("adl-only-no-ordinary-hijack","#include <stdio.h>\nnamespace N{struct R{int a[2];};const int*begin(const R&r){return r.a;}const int*end(const R&r){return r.a+2;}}int*begin(N::R&r){return r.a+1;}int*end(N::R&r){return r.a+2;}int main(){N::R r={{3,4}};int sum=0;for(int v:r)sum+=v;printf(\"%d\\n\",sum);return 0;}","7\n"),
        Arguments.of("one-member-falls-back-to-adl","#include <stdio.h>\nnamespace N{struct R{int a[2];void begin(){}};int*begin(R&r){return r.a;}int*end(R&r){return r.a+2;}}int main(){N::R r={{3,4}};int sum=0;for(int v:r)sum+=v;printf(\"%d\\n\",sum);return 0;}","7\n"),
        Arguments.of("different-sentinel-type","#include <stdio.h>\nstruct End{int*p;};struct Iter{int*p;int&operator*(){return *p;}Iter&operator++(){++p;return *this;}};bool operator!=(const Iter&i,const End&e){return i.p!=e.p;}struct R{int a[2];Iter begin(){return Iter{a};}End end(){return End{a+2};}};int main(){R r={{4,7}};int sum=0;for(int&v:r){sum+=v;v++;}printf(\"%d %d\\n\",sum,r.a[1]);return 0;}","11 8\n"),
        Arguments.of("temporary-range-lifetime","#include <stdio.h>\nint live;int made;struct R{int a[2];R(){a[0]=2;a[1]=3;live++;made++;}~R(){live--;}int*begin(){return a;}int*end(){return a+2;}};int main(){int sum=0;for(int v:R()){sum+=v;if(live!=1)return 3;}printf(\"%d %d %d\\n\",sum,live,made);return 0;}","5 0 1\n"),
        Arguments.of("range-call-once-and-comma","#include <stdio.h>\nint calls;int a[2]={2,3};struct R{int*begin(){return a;}int*end(){return a+2;}};R make(){calls++;return R{};}int main(){int sum=0;for(int v:(calls+=2,make()))sum+=v;printf(\"%d %d\\n\",sum,calls);return 0;}","5 3\n"),
        Arguments.of("per-iteration-destructor-on-continue-break","#include <stdio.h>\nint copies;int deaths;struct I{int v;I(int x):v(x){}I(const I&o):v(o.v){copies++;}~I(){deaths++;}};int main(){I a[3]={I(1),I(2),I(3)};for(I v:a){if(v.v==1)continue;break;}printf(\"%d %d\\n\",copies,deaths);return 0;}","2 2\n"),
        Arguments.of("return-cleans-range","#include <stdio.h>\nint live;struct R{int a[1];R(){a[0]=7;live++;}~R(){live--;}int*begin(){return a;}int*end(){return a+1;}};int f(){for(int v:R())return v;return 0;}int main(){int n=f();printf(\"%d %d\\n\",n,live);return 0;}","7 0\n"),
        Arguments.of("braced-list-range","#include <stdio.h>\n#include <initializer_list>\nint main(){int sum=0;for(int v:{2,3,5})sum+=v;printf(\"%d\\n\",sum);return 0;}","10\n"),
        Arguments.of("const-member-overload","#include <stdio.h>\nstruct R{int a[2];int*begin(){return a+1;}int*end(){return a+2;}const int*begin()const{return a;}const int*end()const{return a+2;}};int main(){const R r={{3,4}};int sum=0;for(int v:r)sum+=v;printf(\"%d\\n\",sum);return 0;}","7\n"),
        Arguments.of("conditional-header-is-classic-for","#include <stdio.h>\nint main(){int total=0;for(int i=1?0:2;i<3;i++)total+=i;printf(\"%d\\n\",total);return 0;}","3\n"),
        Arguments.of("nested-loop-continue-target","#include <stdio.h>\nint main(){int a[2]={1,2};int sum=0;for(int x:a){for(int y:a){if(y==1)continue;sum+=x+y;}}printf(\"%d\\n\",sum);return 0;}","7\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void matchesReference(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}
    static Stream<Arguments> invalidPrograms(){return Stream.of(
        Arguments.of("pointer-is-not-range","int main(){int*p=nullptr;for(int v:p){} // bad\nreturn 0;}"),
        Arguments.of("write-const-element","int main(){const int a[1]={1};for(auto&v:a){v=2; // bad\n}return 0;}"),
        Arguments.of("range-variable-not-visible-after","int main(){int a[1]={1};for(int v:a){}return v; // bad\n}"),
        Arguments.of("body-redeclaration","int main(){int a[1]={1};for(int v:a){int v=2; // bad\n}return 0;}"),
        Arguments.of("missing-end","struct R{int*begin(){return nullptr;}};int main(){R r;for(int v:r){} // bad\nreturn 0;}"),
        Arguments.of("private-member-no-adl-fallback","struct R{private:int*begin(){return nullptr;}public:int*end(){return nullptr;}};int*begin(R&r){return nullptr;}int*end(R&r){return nullptr;}int main(){R r;for(int v:r){} // bad\nreturn 0;}"),
        Arguments.of("data-members-block-adl","struct R{int begin;int end;};int*begin(R&r){return nullptr;}int*end(R&r){return nullptr;}int main(){R r={};for(int v:r){} // bad\nreturn 0;}"),
        Arguments.of("ordinary-using-does-not-participate","namespace N{struct R{};}namespace Helper{int*begin(N::R&r){return nullptr;}int*end(N::R&r){return nullptr;}}using namespace Helper;int main(){N::R r;for(int v:r){} // bad\nreturn 0;}"),
        Arguments.of("heterogeneous-braces","#include <initializer_list>\nint main(){for(int v:{1,2.0}){} // bad\nreturn 0;}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void rejectsInvalidRange(String name,String source)throws Exception{reject(temporary,name,source);}
    @Test void rangeSyntaxMapsToCoreScopesAndTheSourceDeclaration(){
        var api=compiler("int main(){int values[2]={1,2};for(auto&v:values){v++;}return values[0]-2;}");
        var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var source=nodes(parser.result().program()).stream().filter(CppRangeForStmt.class::isInstance).map(CppRangeForStmt.class::cast).findFirst().orElseThrow();
        var result=semantic.semanticResult();
        assertNotNull(result.sourceToCore().get(source));assertNotNull(result.sourceToCore().get(source.declaration()));
        assertEquals(source.range(),result.sourceToCore().get(source).range());
        assertNull(AstChildren.firstCppSyntax(result.program()));
        assertTrue(nodes(result.program()).stream().anyMatch(Statement.ForStmt.class::isInstance));
        assertFalse(nodes(result.program()).stream().filter(Expression.NameExpr.class::isInstance)
                .map(Expression.NameExpr.class::cast).anyMatch(name->name.name().equals("malloc")));
    }
}
