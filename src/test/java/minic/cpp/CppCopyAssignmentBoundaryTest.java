package minic.cpp;

import minic.compiler.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppCopyAssignmentBoundaryTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("mutable-and-const-assignment","""
            #include <stdio.h>
            int trace;struct B{int n;B(int v):n(v){}B&operator=(B&o){trace=trace*10+1;n=o.n;return *this;}
                B&operator=(const B&o){trace=trace*10+2;n=o.n;return *this;}};
            int main(){B a(1);B b(2);const B c(3);a=b;a=c;printf("%d %d\\n",trace,a.n);return 0;}
            ""","12 3\n"),
        Arguments.of("unrelated-assignment-preserves-implicit-copy","""
            #include <stdio.h>
            struct B{int n;B&operator=(int v){n=v;return *this;}};
            int main(){B a={1};B b={7};a=3;a=b;printf("%d\\n",a.n);return 0;}
            ""","7\n"),
        Arguments.of("const-class-member-with-explicit-containing-assignment","""
            #include <stdio.h>
            struct M{int*p;M(int*q):p(q){}const M&operator=(const M&o)const{*p=*o.p;return *this;}};
            struct O{const M m;O(int*p):m(p){}O&operator=(const O&o){m=o.m;return *this;}};
            int main(){int x=1;int y=9;O a(&x);O b(&y);a=b;printf("%d %d\\n",x,a.m.p==&x);return 0;}
            ""","9 1\n"),
        Arguments.of("implicit-array-assignment","""
            #include <stdio.h>
            struct A{int n[2][3];};
            int main(){A a={{{1,2,3},{4,5,6}}};A b={};A&r=(b=a);b.n[0][0]=8;
                printf("%d %d %d %d\\n",a.n[0][0],b.n[0][0],b.n[1][2],&r==&b);return 0;}
            ""","1 8 6 1\n"),
        Arguments.of("member-assignment-order","""
            #include <stdio.h>
            int trace;struct M{int n;M(int v):n(v){}M&operator=(const M&o){trace=trace*10+o.n;n=o.n;return *this;}};
            struct O{M a;M b;O(int n):a(n),b(n+1){}};
            int main(){O a(1);O b(3);a=b;printf("%d %d %d\\n",trace,a.a.n,a.b.n);return 0;}
            ""","34 3 4\n"),
        Arguments.of("private-assignment-from-own-method","""
            #include <stdio.h>
            class B{int n;B&operator=(const B&o){n=o.n+1;return *this;}public:B(int v):n(v){}
                int copy(const B&o){*this=o;return n;}};
            int main(){B a(1);B b(5);printf("%d\\n",a.copy(b));return 0;}
            ""","6\n"),
        Arguments.of("out-of-line-assignment","""
            #include <stdio.h>
            namespace N{struct B{int n;B&operator=(const B&);};}
            N::B&N::B::operator=(const N::B&o){n=o.n+2;return *this;}
            int main(){N::B a={1};N::B b={4};N::B&r=(a=b);printf("%d %d\\n",a.n,&r==&a);return 0;}
            ""","6 1\n"),
        Arguments.of("non-reference-assignment-result","""
            #include <stdio.h>
            struct B{int n;int operator=(const B&o){n=o.n;return n+1;}};
            int main(){B a={1};B b={6};int result=(a=b);printf("%d %d\\n",result,a.n);return 0;}
            ""","7 6\n"),
        Arguments.of("union-representation-assignment","""
            #include <stdio.h>
            union U{int n;long long wide;};struct O{U u;int tag;};
            int main(){O a={};O b={};a.u.wide=1234567890123LL;a.tag=7;b=a;
                printf("%lld %d\\n",b.u.wide,b.tag);return 0;}
            ""","1234567890123 7\n"),
        Arguments.of("unused-member-assignment-prototype","""
            #include <stdio.h>
            struct M{int n;M&operator=(const M&);};struct O{M m;};
            int main(){O a={{4}};printf("%d\\n",a.m.n);return 0;}
            ""","4\n"),
        Arguments.of("unevaluated-implicit-assignment-needs-no-definition","""
            #include <stdio.h>
            struct M{int n;M&operator=(const M&);};struct O{M m;};
            int main(){O a={{4}};O b={{1}};printf("%d %d\\n",int(sizeof(a=b)),b.m.n);return 0;}
            ""","4 1\n"),
        Arguments.of("volatile-member-assignment","""
            #include <stdio.h>
            struct B{volatile int n;};int main(){B a={3};B b={1};b=a;printf("%d\\n",b.n);return 0;}
            ""","3\n"));}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void copyAssignmentMatchesCpp17(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}

    @Test void implicitAssignmentCanAssignAConstClassMemberWithACallableConstAssignment() throws Exception {
        // N4659 [class.copy.assign]/7.2 concerns const NON-CLASS members. For class members,
        // /7.4 requires a usable assignment operator and /12.1 calls it on the subobject.
        // https://timsong-cpp.github.io/cppwp/n4659/class.copy.assign#7
        // G++ 8 rejects this valid implicit-assignment case. Keep its exact normative source
        // checked by both MiniC backends; the explicit equivalent above also uses the G++ oracle.
        String source = """
            #include <stdio.h>
            struct M{int*p;M(int*q):p(q){}const M&operator=(const M&o)const{*p=*o.p;return *this;}};
            struct O{const M m;O(int*p):m(p){}};
            int main(){int x=1;int y=9;O a(&x);O b(&y);a=b;printf("%d %d\\n",x,a.m.p==&x);return 0;}
            """;
        var file = new minic.compiler.SourceFile("const-class-assignment.cpp", source);
        var ir = compiler(source).runToIr();
        var debug = minic.debug.DebugApi.fromIr(file, ir, "");
        for (int steps = 0; debug.canNext() && steps < 5000; steps++) debug.next();
        assertFalse(debug.canNext(), "The complete program must finish within its step budget");
        assertEquals(minic.debug.Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop()::error);
        assertEquals("9 1\n", debug.current().runtime().stdout().replace("\r\n", "\n"));
        for (var level : minic.compiler.ir.optimize.OptimizationLevel.values()) {
            String name = "const-class-assignment-" + level;
            var assembler = new minic.compiler.asm.Assembler(ir, level);
            var object = new minic.compiler.obj.ObjBuilder(file, assembler, temporary, name);
            var linker = new minic.compiler.link.Linker(file, object, temporary, name);
            new minic.compiler.CompilerApi(java.util.List.of(assembler, object, linker)).runThrough(linker);
            assertTrue(linker.succeeded(), () -> linker.errors().toString());
            var result = minic.cpp.support.BoundedProcess.run(java.util.List.of(temporary.resolve(name + ".exe").toString()),
                    temporary, "", java.time.Duration.ofSeconds(10), 65536);
            assertFalse(result.timedOut());
            assertFalse(result.outputExceeded());
            assertEquals(0, result.exitCode(), result::stderr);
            assertEquals("9 1\n", result.stdout().replace("\r\n", "\n"));
        }
    }
    static Stream<Arguments> invalid(){return Stream.of(
        Arguments.of("private-assignment","class B{B&operator=(const B&);public:B(){}};int main(){B a;B b;a=b; // bad\nreturn 0;}"),
        Arguments.of("implicit-private-member-assignment","class M{M&operator=(const M&);public:M(){}};struct O{M m;};int main(){O a;O b;a=b; // bad\nreturn 0;}"),
        Arguments.of("mutable-member-assignment-const-source","struct M{M&operator=(M&);};struct O{M m;};int main(){O a;const O b={};a=b; // bad\nreturn 0;}"),
        Arguments.of("nontrivial-union-assignment","struct M{int n;M&operator=(const M&);};union U{M m;};int main(){U a={};U b={};a=b; // bad\nreturn 0;}"),
        Arguments.of("free-assignment-is-illegal","struct B{int n;};B&operator=(B&a,const B&b){return a;} // bad\nint main(){return 0;}"),
        Arguments.of("const-destination","struct B{int n;};int main(){const B a={1};B b={2};a=b; // bad\nreturn 0;}"));}
    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void invalidAssignmentIsDiagnosed(String name,String source)throws Exception{
        reject(temporary,name,source);var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals("CPP004")),()->semantic.errors().toString());
    }
    @Test void classArrayAssignmentEmitsCountedLoops()throws Exception{
        String source="struct M{int n;M&operator=(const M&o){n=o.n;return *this;}};struct O{M a[2][3];};void copy(O&a,const O&b){a=b;}int main(){return 0;}";
        CppCopyConstructionTest.oracle(temporary,"class-array-assignment",source);
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        assertTrue(nodes(semantic.semanticResult().program()).stream().anyMatch(minic.compiler.parser.node.Statement.ForStmt.class::isInstance));
        agree(temporary,"class-array-assignment",source,"");
    }
}
