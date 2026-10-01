package minic.cpp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.AstChildren;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(120) @Execution(ExecutionMode.SAME_THREAD)
final class CppLocalDeclarationGroupTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() { return Stream.of(
        Arguments.of("sequential-initializers", "int calls=0;int next(int x){++calls;return x;}int main(){int a=next(2),b=next(a+3),c=next(b+4);printf(\"%d %d %d %d\\n\",a,b,c,calls);return 0;}", "2 5 9 3\n"),
        Arguments.of("distinct-declarators", "int plus(int n){return n+1;}int main(){int n=4;int *p=&n,x=2,a[2]={3,5},(*fn)(int)=plus;printf(\"%d %d %d %d\\n\",*p,x,a[1],fn(x));return 0;}", "4 2 5 3\n"),
        Arguments.of("reference-and-pointer", "int main(){int a=2,b=3;int &r=a,*p=&b;r=7;*p=8;printf(\"%d %d\\n\",a,b);return 0;}", "7 8\n"),
        Arguments.of("constructor-list-and-cleanup", "struct Item{int n;Item(int x):n(x){printf(\"c%d \",n);}~Item(){printf(\"d%d \",n);}};int main(){{Item a={1},b={2};printf(\"body%d \",a.n+b.n);}printf(\"end\\n\");return 0;}", "c1 c2 body3 d2 d1 end\n"),
        Arguments.of("for-scalar", "int main(){int sum=0;for(int i=0,j=5;i<j;++i,--j)sum+=i+j;printf(\"%d\\n\",sum);return 0;}", "15\n"),
        Arguments.of("for-object-break", "struct Item{int n;Item(int x):n(x){printf(\"c%d \",n);}~Item(){printf(\"d%d \",n);}};int main(){for(Item a(1),b(2);a.n<4;++a.n){if(a.n==1)continue;printf(\"body%d \",b.n);break;}printf(\"end\\n\");return 0;}", "c1 c2 body2 d2 d2 end\n"),
        Arguments.of("for-object-return", "struct Item{int n;Item(int x):n(x){printf(\"c%d \",n);}~Item(){printf(\"d%d \",n);}};int f(){for(Item a(3),b(4);true;)return a.n+b.n;}int main(){int n=f();printf(\"result%d\\n\",n);return 0;}", "c3 c4 d4 d3 result7\n"),
        Arguments.of("controlled-statement-scope", "struct Item{int n;Item(int x):n(x){printf(\"c%d \",n);}~Item(){printf(\"d%d \",n);}};int main(){if(true)Item a(1),b(2);int a=7,b=8;printf(\"%d\\n\",a+b);return 0;}", "c1 c2 d2 d1 15\n"),
        Arguments.of("constexpr-locals", "constexpr int f(){int a=2,b=a+3;for(int i=0,j=2;i<j;++i)b+=i;return a+b;}static_assert(f()==8);int main(){constexpr int a=3,b=a+4;static_assert(b==7);printf(\"%d\\n\",f()+b);return 0;}", "15\n"),
        Arguments.of("auto-declarators", "int main(){int n=4;auto *p=&n,x=3;const auto a=2,b=5;printf(\"%d\\n\",*p+x+a+b);return 0;}", "14\n"),
        Arguments.of("template-local-visibility", "template<class T>T f(T n){T a=n,b=a+1;return a+b;}int main(){int a=50;auto fn=[a](int n){int a=n,b=a+2;return a+b;};printf(\"%d %d\\n\",f(3),fn(4));return 0;}", "7 10\n"),
        Arguments.of("local-static-group", "int calls=0;int next(){return ++calls;}int f(){static int a=next(),b=next();return a+b;}int main(){int a=f(),b=f();printf(\"%d %d %d\\n\",a,b,calls);return 0;}", "3 3 2\n")
    ); }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void executesInOneScope(String name, String source, String expected) throws Exception {
        agree(temporary,name,"#include <stdio.h>\n"+source,expected);
    }

    static Stream<Arguments> invalid() { return Stream.of(
        Arguments.of("duplicate", "int main(){int a=1,a=2;return a;} // bad\n"),
        Arguments.of("later-declaration", "int main(){int a=b,b=2;return a;} // bad\n"),
        Arguments.of("for-body-redeclaration", "int main(){for(int i=0,j=2;i<j;++i){int j=0;}return 0;} // bad\n"),
        Arguments.of("for-name-does-not-leak", "int main(){for(int i=0,j=2;i<j;++i){}return j;} // bad\n"),
        Arguments.of("different-auto-types", "int main(){auto a=1,b=2.0;return a;} // bad\n")
    ); }
    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void rejectsInvalidScopeOrDeduction(String name, String source) throws Exception { reject(temporary,name,source); }

    @Test void preservesSharedSpecifierIndependentDeclaratorsAndSourceIdentity() {
        String source="int main(){int n=7; int *p=&n, x=3; return *p+x;}";
        var api=compiler(source);var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        var group=nodes(parser.result().program()).stream().filter(DeclGroupStmt.class::isInstance).map(DeclGroupStmt.class::cast).findFirst().orElseThrow();
        assertEquals(2,group.statements().size());
        var pointer=(VarDeclStmt)group.statements().get(0);var integer=(VarDeclStmt)group.statements().get(1);
        assertEquals(MiniType.INT.pointerTo(),pointer.type());assertEquals(MiniType.INT,integer.type());
        assertEquals(List.of(pointer,integer),AstChildren.of(group));
        assertThrows(UnsupportedOperationException.class,()->group.statements().clear());
        assertTrue(pointer.range().startByte()<integer.range().startByte());
        assertNotNull(semantic.semanticResult().sourceToCore().get(pointer));
        assertNotNull(semantic.semanticResult().sourceToCore().get(integer));
        assertNotSame(semantic.semanticResult().sourceToCore().get(pointer),semantic.semanticResult().sourceToCore().get(integer));
    }

    @Test void keepsCDeclarationsInTheEnclosingScope() throws Exception {
        String source="typedef char T;int main(){int T=sizeof(T),n=5;int *p=&n,x=2,a[2]={3,4};for(int i=0,j=2;i<j;++i)x+=i;return *p+x+a[1]==12&&T==4?0:1;}";
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),
            new CppDifferentialHarness.Limits(Duration.ofSeconds(30),Duration.ofSeconds(10),50_000,1_048_576),LanguageMode.C).run("c-scope",source,"");
        assertTrue(report.passed(),report::describe);
    }

    @Test void finiteLoopDoesNotProveARequiredReturn() {
        var api=compiler("int f(){for(int i=0,j=2;i<j;++i){if(i==1)return j;}}int main(){return 0;}");
        var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(diagnostic->diagnostic.message().contains("所有路径返回")),()->semantic.errors().toString());
    }

    @Test void infiniteLoopProofRespectsBreakTargets() {
        for(String body:List.of("for(;;){}","while(true){}","for(;;){while(true){break;}}")) {
            var api=compiler("int f(){"+body+"}int main(){return 0;}");
            var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
            assertTrue(semantic.succeeded(),()->body+semantic.errors());
        }
        for(String body:List.of("for(;;){break;}","while(true){break;}")) {
            var api=compiler("int f(){"+body+"}int main(){return 0;}");
            var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
            assertFalse(semantic.succeeded(),body);
            assertTrue(semantic.errors().stream().anyMatch(diagnostic->diagnostic.message().contains("所有路径返回")),()->semantic.errors().toString());
        }
    }
}
