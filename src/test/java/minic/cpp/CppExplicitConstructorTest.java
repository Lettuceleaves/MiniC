package minic.cpp;

import minic.compiler.LanguageMode;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
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

/** Explicit affects constructor selection according to the initialization context. */
@Tag("cpp-differential") @Timeout(90)
final class CppExplicitConstructorTest {
    @TempDir Path temporary;
    private static final String BOX="struct Box{int value;explicit Box(int x):value(x){}};";
    static Stream<Arguments> programs() { return Stream.of(
            Arguments.of("direct-paren",BOX+"int main(){Box b(3);printf(\"%d\\n\",b.value);return 0;}","3\n"),
            Arguments.of("direct-list",BOX+"int main(){Box b{4};printf(\"%d\\n\",b.value);return 0;}","4\n"),
            Arguments.of("functional-paren",BOX+"int main(){Box b=Box(5);printf(\"%d\\n\",b.value);return 0;}","5\n"),
            Arguments.of("functional-list",BOX+"int main(){Box b=Box{6};printf(\"%d\\n\",b.value);return 0;}","6\n"),
            Arguments.of("default-and-value", "struct Box{int value;explicit Box():value(7){}};int main(){Box a;Box b{};printf(\"%d %d\\n\",a.value,b.value);return 0;}","7 7\n"),
            Arguments.of("copy-excludes-explicit", "struct Box{int value;explicit Box(int x):value(1){} Box(double x):value(2){}};int main(){Box a=4;Box b(4);printf(\"%d %d\\n\",a.value,b.value);return 0;}","2 1\n"),
            Arguments.of("member-paren",BOX+"struct Owner{Box b;Owner():b(8){}};int main(){Owner o;printf(\"%d\\n\",o.b.value);return 0;}","8\n"),
            Arguments.of("default-member-list",BOX+"struct Owner{Box b{9};};int main(){Owner o;printf(\"%d\\n\",o.b.value);return 0;}","9\n"),
            Arguments.of("qualified-definition","namespace N{struct Box{int value;explicit Box(int);};}N::Box::Box(int x):value(x){}int main(){N::Box b(10);printf(\"%d\\n\",b.value);return 0;}","10\n"),
            Arguments.of("ordinary-copy-of-explicit-class",BOX+"int main(){Box a(11);Box b=a;printf(\"%d\\n\",b.value);return 0;}","11\n"),
            Arguments.of("direct-explicit-versus-nonexplicit", "struct Box{int value;explicit Box(int x):value(1){} Box(double x):value(2){}};int main(){Box a{1.5};Box b{2};printf(\"%d %d\\n\",a.value,b.value);return 0;}","2 1\n")); }

    @ParameterizedTest(name="{0}") @MethodSource("programs")
    void selectsConstructorsAccordingToInitializationForm(String name,String body,String expected) throws Exception {
        var report=new CppDifferentialHarness(temporary,CppDifferentialHarness.referenceCompiler(System.getenv()),CppDifferentialHarness.Limits.defaults(),LanguageMode.CPP17_ALGORITHM)
                .run(name,"#include <stdio.h>\n"+body,"");
        assertTrue(report.passed(),report::describe);
        report.outcomes().values().forEach(outcome->assertEquals(expected,outcome.stdout().replace("\r\n","\n")));
    }

    @ParameterizedTest @ValueSource(strings={
            "struct Box{explicit Box(int){}};int main(){Box b=3;return 0;}",
            "struct Box{explicit Box(int){}};int main(){Box b={3};return 0;}",
            "struct Box{explicit Box(){}};int main(){Box b={};return 0;}",
            "struct Box{explicit Box(int){} Box(double){}};int main(){Box b={3};return 0;}",
            "struct Box{explicit Box(int){}};struct Owner{Box b={2};};int main(){Owner o;return 0;}",
            "struct Box{explicit Box(int){}};void f(Box b){}int main(){f(3);return 0;}",
            "struct Box{explicit Box(int){}};Box f(){return 3;}int main(){return 0;}",
            "class Box{explicit Box(int){}};int main(){Box b(3);return 0;}",
            "struct Box{explicit Box(int){}};int main(){Box b{2.5};return 0;}"})
    void rejectedFormsHaveCppSemanticDiagnostics(String source) throws Exception {
        Path cpp=temporary.resolve("invalid.cpp");Files.writeString(cpp,source);
        var oracle=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",cpp.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(oracle.timedOut());assertNotEquals(0,oracle.exitCode());
        var api=compiler(source);var semantic=stage(api,SemanticAnalyzer.class);api.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertFalse(semantic.errors().isEmpty());
        assertTrue(semantic.errors().stream().noneMatch(error->error.code().equals("CPP005")),()->semantic.errors().toString());
    }
}
