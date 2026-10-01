package minic.cpp;

import minic.compiler.CompilerApi;
import minic.compiler.LanguageMode;
import minic.compiler.SourceFile;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The existing C function-pointer ABI also backs C++ references to functions. */
@Tag("cpp-differential")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(90)
final class CppFunctionDesignatorTest {
    @TempDir Path temporary;

    static Stream<Arguments> programs() {
        return Stream.of(LanguageMode.values()).flatMap(mode -> Stream.of(
                Arguments.of(mode, "function-designator-decay", """
                        #include <stdio.h>
                        int add(int value){return value+2;}
                        int main(){
                            int (*pointer)(int)=add;
                            int (*copy)(int)=*pointer;
                            printf("%d %d %d\\n", (*pointer)(3), (***pointer)(4), copy == *pointer);
                            return 0;
                        }
                        """, "5 6 1\n"),
                Arguments.of(mode, "variadic-designator-promotions", """
                        #include <stdio.h>
                        #include <stdarg.h>
                        double sum(int count, ...){
                            va_list arguments;
                            va_start(arguments, count);
                            double result=va_arg(arguments,double);
                            result+=va_arg(arguments,double);
                            va_end(arguments);
                            return result;
                        }
                        int main(){
                            double (*operation)(int,...)=sum;
                            float first=1.25f;
                            printf("%.2f\\n", (*operation)(2, first, 2.5));
                            return 0;
                        }
                        """, "3.75\n")));
    }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("programs")
    void functionDesignatorsDecayWithoutLoadingCodeMemory(LanguageMode mode, String name, String source, String output)
            throws Exception {
        var report = new CppDifferentialHarness(temporary, CppDifferentialHarness.referenceCompiler(System.getenv()),
                CppDifferentialHarness.Limits.defaults(), mode).run(name, source, "");
        assertTrue(report.passed(), report::describe);
        assertEquals(output, report.outcomes().get(CppDifferentialHarness.Backend.GXX).stdout().replace("\r\n", "\n"));
    }

    @ParameterizedTest @EnumSource(LanguageMode.class)
    void functionDesignatorStillHasNoObjectLayout(LanguageMode mode) {
        var api = new CompilerApi(new SourceFile("function-layout.mc", """
                int add(int value){return value+2;}
                int main(){int (*pointer)(int)=add;return sizeof(*pointer);}
                """), mode);
        var semantic = CppReferenceTest.stage(api, SemanticAnalyzer.class);
        api.runThrough(semantic);
        assertFalse(semantic.succeeded());
        assertTrue(semantic.errors().stream().anyMatch(error -> error.message().contains("sizeof")),
                () -> semantic.errors().toString());
    }
}
