package minic.cpp;

import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import static minic.cpp.CppReferenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("cpp-differential") @Timeout(90)
final class CppArrayLifetimeTest {
    @TempDir Path temporary;
    private static final String TRACE="""
            #include <stdio.h>
            int serial;
            struct T{int id;T():id(++serial){printf("C%d ",id);}T(int n):id(n){printf("C%d ",id);}~T(){printf("D%d ",id);}};
            """;
    static Stream<Arguments> programs(){return Stream.of(
            Arguments.of("default-elements",TRACE+"int main(){T values[3];printf(\"B \");return 0;}","C1 C2 C3 B D3 D2 D1 "),
            Arguments.of("list-remainder-default",TRACE+"int main(){T values[3]={{7},{8}};printf(\"B \");return 0;}","C7 C8 C1 B D1 D8 D7 "),
            Arguments.of("empty-list-elements",TRACE+"int main(){T values[2]={};return 0;}","C1 C2 D2 D1 "),
            Arguments.of("elided-element-prvalues",TRACE+"int main(){T values[2]={T(7),T(8)};printf(\"B \");return 0;}","C7 C8 B D8 D7 "),
            Arguments.of("nested-default",TRACE+"int main(){T values[2][2];return 0;}","C1 C2 C3 C4 D4 D3 D2 D1 "),
            Arguments.of("nested-list",TRACE+"int main(){T values[2][2]={{{7}},{{8}}};return 0;}","C7 C1 C8 C2 D2 D8 D1 D7 "),
            Arguments.of("nested-brace-elision",TRACE+"int main(){T values[2][2]={T(7),T(8),T(9)};return 0;}","C7 C8 C9 C1 D1 D9 D8 D7 "),
            Arguments.of("const-elements",TRACE+"int main(){const T values[2]={{7},{8}};return 0;}","C7 C8 D8 D7 "),
            Arguments.of("member-declaration-order",TRACE+"struct Owner{T first[2];T last;Owner():last(9),first{{7},{8}}{printf(\"O \");}~Owner(){printf(\"X \");}};int main(){Owner value;return 0;}","C7 C8 C9 O X D9 D8 D7 "),
            Arguments.of("default-member-array",TRACE+"struct Owner{T values[2]={{7},{8}};};int main(){Owner value;return 0;}","C7 C8 D8 D7 "),
            Arguments.of("member-default-array",TRACE+"struct Owner{T values[2];};int main(){Owner value;return 0;}","C1 C2 D2 D1 "),
            Arguments.of("break-cleans-array",TRACE+"int main(){while(true){T values[2];break;}printf(\"E \");return 0;}","C1 C2 D2 D1 E "),
            Arguments.of("continue-reconstructs-array",TRACE+"int main(){for(int i=0;i<2;++i){T values[2];continue;}return 0;}","C1 C2 D2 D1 C3 C4 D4 D3 "),
            Arguments.of("return-snapshots-before-array-cleanup",TRACE+"int run(){T values[2];return values[0].id;}int main(){printf(\"R%d \",run());return 0;}","C1 C2 D2 D1 R1 "),
            Arguments.of("array-owner-temporary",TRACE+"struct Owner{T values[2];};int main(){Owner();printf(\"E \");return 0;}","C1 C2 D2 D1 E "),
            Arguments.of("array-owner-reference-temporary",TRACE+"struct Owner{T values[2];};int main(){const Owner& value=Owner();printf(\"B \");return 0;}","C1 C2 B D2 D1 "),
            Arguments.of("array-prvalue-reference",TRACE+"typedef T Pair[2];int main(){const Pair& value=Pair{};printf(\"B \");return 0;}","C1 C2 B D2 D1 "),
            Arguments.of("const-members", "#include <stdio.h>\nstruct T{const int value;T():value(9){printf(\"C%d \",value);}~T(){printf(\"D%d \",value);}};int main(){T values[2];return 0;}","C9 C9 D9 D9 "),
            Arguments.of("reference-members", "#include <stdio.h>\nstruct T{int& value;T(int& n):value(n){}~T(){++value;printf(\"D%d \",value);}};int main(){int first=1;int second=2;{T values[2]={T(first),T(second)};values[0].value=5;}printf(\"B%d %d \",first,second);return 0;}","D3 D6 B6 3 "),
            Arguments.of("element-temporary-full-expression",TRACE+"int argument(const T& t){return t.id;}int main(){T values[2]={argument(T(7)),argument(T(8))};printf(\"B \");return 0;}","C7 C7 D7 C8 C8 D8 B D8 D7 ")
    );}
    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void objectArraysAgreeAcrossBackends(String name,String source,String expected)throws Exception{
        var api=compiler(source);var parser=stage(api,Parser.class);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertTrue(parser.succeeded(),()->parser.errors().toString());assertTrue(semantic.succeeded(),()->semantic.errors().toString());
        agree(temporary,name,source,expected);
    }
    @Test @Timeout(150) void independentGxxOracle()throws Exception{
        for(var args:programs().toList()){
            var a=args.get();Path source=temporary.resolve(a[0]+".cpp"),exe=temporary.resolve(a[0]+".exe");Files.writeString(source,(String)a[1]);
            var compile=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors",source.toString(),"-o",exe.toString()),temporary,"",Duration.ofSeconds(20),65536);
            assertFalse(compile.timedOut());assertEquals(0,compile.exitCode(),a[0]+": "+compile.stderr());
            var run=BoundedProcess.run(List.of(exe.toString()),temporary,"",Duration.ofSeconds(5),65536);
            assertFalse(run.timedOut());assertEquals(0,run.exitCode());assertEquals(a[2],run.stdout().replace("\r\n","\n"),a[0].toString());
        }
    }
    @ParameterizedTest @ValueSource(strings={
            "struct T{T(int){}};int main(){T values[2]={{1}}; // bad\nreturn 0;}",
            "class T{T(){}};int main(){T values[2]; // bad\nreturn 0;}",
            "class T{~T(){}};int main(){T values[2]; // bad\nreturn 0;}",
            "struct T{int& r;};int main(){T values[2]={}; // bad\nreturn 0;}",
            "struct T{T(){}};int main(){T values[1]={{},{}}; // bad\nreturn 0;}"
    }) void invalidArrayInitializationRemainsRejected(String source)throws Exception{reject(temporary,"invalid-array",source);}
}
