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

/** Sources for final unified acceptance; no tests are run during implementation. */
@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppLambdaExecutionTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("captureless-call","#include <stdio.h>\nint main(){auto twice=[](int n){return n*2;};printf(\"%d\\n\",twice(4));return 0;}","8\n"),
        Arguments.of("immediate-call","#include <stdio.h>\nint main(){printf(\"%d\\n\",[](int n){return n+3;}(4));return 0;}","7\n"),
        Arguments.of("copy-capture-snapshot","#include <stdio.h>\nint main(){int x=2;auto f=[x](){return x;};x=7;printf(\"%d %d\\n\",f(),x);return 0;}","2 7\n"),
        Arguments.of("reference-capture","#include <stdio.h>\nint main(){int x=2;auto f=[&x](int n){x+=n;};f(5);printf(\"%d\\n\",x);return 0;}","7\n"),
        Arguments.of("mixed-default-copy","#include <stdio.h>\nint main(){int x=2;int y=3;auto f=[=,&y](){y+=x;return y;};x=9;int n=f();printf(\"%d %d %d\\n\",n,x,y);return 0;}","5 9 5\n"),
        Arguments.of("default-reference","#include <stdio.h>\nint main(){int x=2;int y=3;auto f=[&](){x+=y;};f();printf(\"%d\\n\",x);return 0;}","5\n"),
        Arguments.of("mutable-copy","#include <stdio.h>\nint main(){int x=2;auto f=[x]()mutable{return ++x;};int a=f();int b=f();printf(\"%d %d %d\\n\",a,b,x);return 0;}","3 4 2\n"),
        Arguments.of("init-capture-once","#include <stdio.h>\nint main(){int x=2;auto f=[value=++x](){return value;};int a=f();int b=f();printf(\"%d %d %d\\n\",a,b,x);return 0;}","3 3 3\n"),
        Arguments.of("init-reference-capture","#include <stdio.h>\nint main(){int a[2]={3,4};int i=0;auto f=[&r=a[i++]](){r+=2;};f();printf(\"%d %d\\n\",a[0],i);return 0;}","5 1\n"),
        Arguments.of("captured-array-keeps-bound","#include <stdio.h>\nint main(){int a[2]={3,4};auto f=[a]()mutable{a[0]=8;return (int)sizeof(a)+a[0];};printf(\"%d %d\\n\",f(),a[0]);return 0;}","16 3\n"),
        Arguments.of("trailing-reference-return","#include <stdio.h>\nint main(){int x=2;auto f=[&x]()->int&{return x;};f()=7;printf(\"%d\\n\",x);return 0;}","7\n"),
        Arguments.of("captureless-function-pointer","#include <stdio.h>\nint run(int(*f)(int),int x){return f(x);}int main(){int(*f)(int)=[](int n){return n+4;};printf(\"%d %d\\n\",f(3),run([](int n){return n*3;},2));return 0;}","7 6\n"),
        Arguments.of("returned-closure","#include <stdio.h>\nauto make(int x){return [x](int y){return x+y;};}int main(){auto f=make(4);printf(\"%d\\n\",f(5));return 0;}","9\n"),
        Arguments.of("nested-default-capture","#include <stdio.h>\nint main(){int x=3;auto outer=[=](){return [=](int y){return x+y;};};auto inner=outer();printf(\"%d\\n\",inner(4));return 0;}","7\n"),
        Arguments.of("this-and-private-access","#include <stdio.h>\nclass Box{int x;public:Box(int n):x(n){}auto edit(){return [this](int n){x+=n;return x;};}int get()const{return x;}};int main(){Box b(3);auto f=b.edit();int n=f(4);printf(\"%d %d\\n\",n,b.get());return 0;}","7 7\n"),
        Arguments.of("copy-this-object","#include <stdio.h>\nstruct Box{int x;auto snapshot(){return [*this]()mutable{return ++x;};}};int main(){Box b={3};auto f=b.snapshot();int n=f();printf(\"%d %d\\n\",n,b.x);return 0;}","4 3\n"),
        Arguments.of("sizeof-does-not-capture","#include <stdio.h>\nint main(){int x=7;auto f=[](){return (int)sizeof(x);};printf(\"%d\\n\",f());return 0;}","4\n"),
        Arguments.of("static-local-not-captured","#include <stdio.h>\nint main(){static int x=2;auto f=[](){return ++x;};printf(\"%d\\n\",f());return 0;}","3\n"),
        Arguments.of("global-init-capture","#include <stdio.h>\nauto f=[n=4](){return n;};int main(){printf(\"%d\\n\",f());return 0;}","4\n"),
        Arguments.of("captured-record-copy-and-dtor","#include <stdio.h>\nint copies;int dead;struct X{int n;X(int v):n(v){}X(const X&o):n(o.n){copies++;}~X(){dead++;}};int main(){X x(3);{auto f=[x](){return x.n;};auto g=f;printf(\"%d %d\\n\",f()+g(),copies);}printf(\"%d\\n\",dead);return 0;}","6 2\n2\n")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void agreesWithCpp(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}
    static Stream<Arguments> invalidPrograms(){return Stream.of(
        Arguments.of("missing-capture","int main(){int x=2;auto f=[](){return x; // bad\n};return 0;}"),
        Arguments.of("write-copy-without-mutable","int main(){int x=2;auto f=[x](){x++; // bad\n};return 0;}"),
        Arguments.of("duplicate-capture","int main(){int x=2;auto f=[x,x](){return x;}; // bad\nreturn 0;}"),
        Arguments.of("capture-parameter-conflict","int main(){int x=2;auto f=[x](int x){return x;}; // bad\nreturn 0;}"),
        Arguments.of("capture-global-simple","int x;int main(){auto f=[x](){return x;}; // bad\nreturn 0;}"),
        Arguments.of("missing-this-capture","struct X{int n;auto f(){return [](){return n; // bad\n};}};int main(){return 0;}"),
        Arguments.of("reference-init-prvalue","int main(){auto f=[&x=3](){return x;}; // bad\nreturn 0;}"),
        Arguments.of("default-constructor-deleted","int main(){auto f=[](){};decltype(f)g; // bad\nreturn 0;}"),
        Arguments.of("assignment-deleted","int main(){auto f=[](){};auto g=f;g=f; // bad\nreturn 0;}"),
        Arguments.of("different-closures","int main(){auto f=[](){return 1;};auto g=[](){return 1;};decltype(f)h=g; // bad\nreturn 0;}"),
        Arguments.of("capturing-lambda-no-function-pointer","int main(){int x=2;int(*f)()=[x](){return x;}; // bad\nreturn 0;}"),
        Arguments.of("unevaluated-lambda","int main(){return sizeof([](){}); // bad\n}"),
        Arguments.of("duplicate-default-copy","int main(){int x=1;auto f=[=,x](){return x;}; // bad\nreturn 0;}"),
        Arguments.of("const-mutable-call","int main(){int x=1;const auto f=[x]()mutable{return ++x;};return f(); // bad\n}")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("invalidPrograms")
    void rejectsInvalidClosure(String name,String source)throws Exception{reject(temporary,name,source);}
    @Test void closureKeepsSourceIdentityAndUsesOrdinaryCoreStorage(){
        var api=compiler("int main(){int x=3;auto f=[&x](int y){x+=y;return x;};return f(2)-5;}");
        var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var source=nodes(parser.result().program()).stream().filter(CppLambdaExpr.class::isInstance).map(CppLambdaExpr.class::cast).findFirst().orElseThrow();
        var result=semantic.semanticResult();
        assertNotNull(result.sourceToCore().get(source));assertEquals(source.range(),result.sourceToCore().get(source).range());
        assertNull(AstChildren.firstCppSyntax(result.program()));
        assertTrue(nodes(result.program()).stream().anyMatch(Expression.ObjectInitExpr.class::isInstance));
        assertFalse(nodes(result.program()).stream().filter(Expression.NameExpr.class::isInstance).map(Expression.NameExpr.class::cast)
                .anyMatch(name->name.name().equals("malloc")));
    }
}
