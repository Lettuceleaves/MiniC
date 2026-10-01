package minic.cpp;
import minic.compiler.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppCopyInitializationContextTest {
    @TempDir Path temporary;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("aggregate-direct-copy","#include <stdio.h>\nstruct P{int n;};int main(){P a={3};P b(a);P c{b};printf(\"%d %d\\n\",b.n,c.n);return 0;}","3 3\n"),
        Arguments.of("explicit-copy-direct-contexts","""
                #include <stdio.h>
                int copies;struct B{int n;B(int v):n(v){}explicit B(const B&o):n(o.n){++copies;}};
                int take(B b){return b.n;}int main(){B a(3);B b(a);B c{a};int n=take(B(a));printf("%d %d %d %d\\n",b.n,c.n,n,copies);return 0;}
                ""","3 3 3 3\n"),
        Arguments.of("implicit-member-copy-is-direct-init","""
                #include <stdio.h>
                struct B{int n;B(int v):n(v){}explicit B(const B&o):n(o.n+1){}};struct O{B b;O():b(3){}};
                int main(){O a;O b=a;printf("%d\\n",b.b.n);return 0;}
                ""","4\n"),
        Arguments.of("assignment-lvalue-copy",CppCopyConstructionTest.BOX+"int main(){Box a(2);Box b(3);Box c=(b=a);printf(\"%d %d\\n\",c.read(),copies);return 0;}","2 1\n"),
        Arguments.of("unevaluated-then-evaluated-implicit-copy","""
                #include <stdio.h>
                int copies;struct M{int n;M(int v):n(v){}M(const M&o):n(o.n){++copies;}};struct O{M m;O():m(4){}};
                int main(){O a;int size=sizeof(O(a));O b=a;printf("%d %d %d\\n",size,b.m.n,copies);return 0;}
                ""","4 4 1\n"));}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void contextsMatchCpp17(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}
    @org.junit.jupiter.api.Test void anonymousNontrivialCopyKeepsTheExistingConstructionBoundaryWithoutCrashing()throws Exception{
        String source="struct M{int n;M(int v):n(v){}M(const M&o):n(o.n+1){}};struct O{M m;union{int n;long long wide;};O():m(2){n=7;}};int main(){O a;O b=a;return b.n;}";
        CppCopyConstructionTest.oracle(temporary,"anonymous-copy-boundary",source);
        // Anonymous aggregate construction was already an explicit F06 boundary. F08 must
        // preserve that diagnostic rather than synthesizing an impossible blank field access.
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals("CPP005")),()->semantic.errors().toString());
    }
    static Stream<Arguments> invalid(){String prefix="struct B{B(){}explicit B(const B&){} };";return Stream.of(
        Arguments.of("copy-init-excludes-explicit",prefix+"int main(){B a;B b=a; // bad\nreturn 0;}"),
        Arguments.of("copy-list-rejects-explicit",prefix+"int main(){B a;B b={a}; // bad\nreturn 0;}"),
        Arguments.of("argument-copy-excludes-explicit",prefix+"void take(B b){}int main(){B a;take(a); // bad\nreturn 0;}"),
        Arguments.of("return-copy-excludes-explicit",prefix+"B copy(B&b){return b; // bad\n}int main(){return 0;}"));}
    @ParameterizedTest(name="{0}") @MethodSource("invalid")
    void copyContextsCannotInvokeExplicitCopy(String name,String source)throws Exception{
        reject(temporary,name,source);var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(semantic.errors().stream().anyMatch(error->error.code().equals("CPP004")),()->semantic.errors().toString());
    }
}
