package minic.cpp;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;
final class CppAssignmentSequencingTest {
    @TempDir Path temporary;
    static final String PREFIX="""
        #include <stdio.h>
        int trace;int copies;int live;int bad;
        struct B{B*self;int n;B(int v):self(this),n(v){++live;trace=trace*10+2;}
            B(const B&o):self(this),n(o.n){++live;++copies;trace=trace*10+2;}
            B&operator=(B other){trace=trace*10+3;if(other.self!=&other)++bad;n=other.n;return *this;}
            ~B(){--live;trace=trace*10+4;}};
        B&target(B&b){trace=trace*10+1;return b;}
        """;
    static Stream<Arguments> programs(){return Stream.of(
        Arguments.of("by-value-copy-before-receiver",PREFIX+"int main(){B a(1);B b(7);trace=0;target(a)=b;printf(\"%d %d %d %d %d\\n\",trace,copies,live,bad,a.n);return 0;}","2134 1 2 0 7\n"),
        Arguments.of("prvalue-in-final-parameter-before-receiver",PREFIX+"int main(){B a(1);trace=0;target(a)=B(7);printf(\"%d %d %d %d %d\\n\",trace,copies,live,bad,a.n);return 0;}","2134 0 1 0 7\n"),
        Arguments.of("explicit-method-call-keeps-receiver-first",PREFIX+"int main(){B a(1);B b(7);trace=0;target(a).operator=(b);printf(\"%d %d %d %d %d\\n\",trace,copies,live,bad,a.n);return 0;}","1234 1 2 0 7\n"),
        Arguments.of("temporary-destruction-reverses-actual-evaluation","""
            #include <stdio.h>
            int trace;struct B{int n;B(int v):n(v){trace=trace*10+n;}
                B&operator=(const B&o){trace=trace*10+3;return *this;}~B(){trace=trace*10+n;}};
            int main(){B(1)=B(2);printf("%d\\n",trace);return 0;}
            ""","21312\n"));}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void sequencingAndFinalStorageMatchCpp17(String name,String source,String expected)throws Exception{agree(temporary,name,source,expected);}
}
