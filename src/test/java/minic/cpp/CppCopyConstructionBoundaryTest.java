package minic.cpp;

import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.parser.Parser;
import minic.compiler.parser.node.Expression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppCopyConstructionBoundaryTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("member-order-and-reference-alias","""
                    #include <stdio.h>
                    int trace;struct M{int n;M(int v):n(v){}M(const M&o):n(o.n){trace=trace*10+n;}};
                    struct O{M first;M second;int&ref;const int tag;O(int&r):first(1),second(2),ref(r),tag(8){}};
                    int main(){int v=3;O a(v);O b=a;b.ref=7;printf("%d %d %d %d\\n",trace,v,b.tag,&a.ref==&b.ref);return 0;}
                    ""","12 7 8 1\n"),
            Arguments.of("union-member-with-nontrivial-neighbor","""
                    #include <stdio.h>
                    struct M{int n;M(int v):n(v){}M(const M&o):n(o.n+1){}};union U{int n;long long wide;};
                    struct O{M m;U u;O():m(2){u.n=7;}};
                    int main(){O a;O b=a;printf("%d %d\\n",b.m.n,b.u.n);return 0;}
                    ""","3 7\n"),
            Arguments.of("reference-member-does-not-copy-referent","""
                    #include <stdio.h>
                    class M{M(const M&);public:M(){}};struct V{M&ref;V(M&r):ref(r){}};
                    int main(){M m;V a(m);V b=a;printf("%d\\n",&a.ref==&b.ref);return 0;}
                    ""","1\n"),
            Arguments.of("unused-member-copy-prototype","""
                    #include <stdio.h>
                    struct M{int n;M(int v):n(v){}M(const M&);};struct O{M m;O():m(4){}};
                    int main(){O a;printf("%d\\n",a.m.n);return 0;}
                    ""","4\n"),
            Arguments.of("unevaluated-implicit-copy-prototype","""
                    #include <stdio.h>
                    struct M{int n;M(int v):n(v){}M(const M&);};struct O{M m;O():m(4){}};
                    int main(){O a;printf("%d\\n",int(sizeof(O(a))));return 0;}
                    ""","4\n"),
            Arguments.of("comma-lvalue-source-once",CppCopyConstructionTest.BOX+"int main(){Box a(3);Box other(7);int calls=0;Box b=(++calls,1?a:other);printf(\"%d %d %d\\n\",b.read(),copies,calls);return 0;}","3 1 1\n"),
            Arguments.of("temporary-subobject-is-not-an-elided-result",CppCopyConstructionTest.BOX+"struct O{Box member;O():member(3){}};int main(){Box b=O().member;printf(\"%d %d\\n\",b.read(),copies);return 0;}","3 1\n"),
            Arguments.of("private-copy-does-not-block-prvalue-elision","""
                    #include <stdio.h>
                    class B{B(const B&);public:int n;B(int v):n(v){}};B make(){return B(5);}
                    int main(){B b=make();printf("%d\\n",b.n);return 0;}
                    ""","5\n"),
            Arguments.of("mutable-only-member-copy","""
                    #include <stdio.h>
                    struct M{int n;M(int v):n(v){}M(M&o):n(o.n+1){}};struct O{M m;O(int n):m(n){}};
                    int main(){O a(6);O b=a;printf("%d\\n",b.m.n);return 0;}
                    ""","7\n"),
            Arguments.of("volatile-scalar-member-copy","""
                    #include <stdio.h>
                    struct O{volatile int n;};int main(){O a={7};O b=a;printf("%d\\n",b.n);return 0;}
                    ""","7\n"));}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void allBackendsAgree(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}
    @Test void arrayCopyUsesCountedElementConstructionWithoutRequiringDefaultConstruction()throws Exception{
        String source="struct M{int n;M(const M&o):n(o.n+1){}};struct O{M values[2][3];};O copy(const O&src){return src;}int main(){return 0;}";
        CppCopyConstructionTest.oracle(temporary,"array-copy-codegen",source);
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        assertTrue(nodes(semantic.semanticResult().program()).stream().anyMatch(minic.compiler.parser.node.Statement.ForStmt.class::isInstance));
        agree(temporary,"array-copy-codegen",source,"");
    }
    @Test void copiedObjectHistoryPreservesSourceParametersAndNeedsNoCompilerHeap(){
        assertDebugHistory(CppCopyConstructionTest.BOX+"int main(){Box a(3);Box b=a;printf(\"%d\\n\",b.read());return 0;}","3\n","Box::Box","other");
    }
    @Test void copiedObjectsKeepTheirFinalAddressAndEachLifetimeEndsOnce()throws Exception{
        String source="""
                #include <stdio.h>
                int live;int copies;int bad;
                struct B{B*self;int n;B(int v):self(this),n(v){++live;}
                    B(const B&o):self(this),n(o.n){++live;++copies;}
                    ~B(){if(self!=this)++bad;--live;}};
                struct O{B b;O():b(7){}};
                B duplicate(const B&b){return b;}int read(B b){return b.n;}
                int main(){{O a;O b=a;B c=duplicate(a.b);const B&r=B(c);int n=read(c);
                    printf("%d %d %d %d\\n",live,copies,bad,n+r.n);}
                    printf("%d %d %d\\n",live,copies,bad);return 0;}
                """;
        agree(temporary,"copy-and-destruction",source,"4 4 0 14\n0 4 0\n");
    }
    static Stream<Arguments> negatives(){return Stream.of(
            Arguments.of("implicit-copy-const-member-private","struct M{M(M&);private:M(const M&);public:M(){}};struct O{M m;};int main(){O a;O b=a; // bad\nreturn 0;}"),
            Arguments.of("implicit-copy-const-source-mutable-member","struct M{M(M&);M(){}};struct O{M m;};int main(){const O a;O b=a; // bad\nreturn 0;}"),
            Arguments.of("implicit-copy-volatile-source","struct O{int n;};int main(){volatile O a={1};O b=a; // bad\nreturn 0;}"));}
    @ParameterizedTest(name="{0}") @MethodSource("negatives")
    void unavailableImplicitCopiesAreRejectedAtUse(String name,String source)throws Exception{
        reject(temporary,name,source);var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP004")),()->semantic.errors().toString());
    }
}
