package minic.compiler.asm;

import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StdargIntrinsicTest {
    @Test
    @Tag("stdlib-abi")
    void consumesRegisterAndStackArgumentsWithWindowsX64Types() {
        String source = """
                #include "stdarg.mh"

                int inspect(int marker, ...) {
                    va_list arguments;
                    va_start(arguments, marker);
                    double real_value = va_arg(arguments, double);
                    int signed_value = va_arg(arguments, int);
                    unsigned int unsigned_value = va_arg(arguments, unsigned int);
                    long long wide_value = va_arg(arguments, long long);
                    int *pointer_value = va_arg(arguments, int *);
                    va_end(arguments);
                    if (signed_value != -7) return 1;
                    if (unsigned_value != 4000000000U) return 2;
                    if (wide_value != 5000000000LL) return 3;
                    if (real_value != 2.5) return 4;
                    if (*pointer_value != 91) return 5;
                    return 0;
                }

                int main(void) {
                    int value = 91;
                    return inspect(0, 2.5, -7, 4000000000U, 5000000000LL, &value);
                }
                """;

        assertNativeExit(source, 0);
    }

    @Test
    @Tag("stdlib-abi")
    void copiedListsAdvanceIndependentlyAndSurviveNestedCalls() {
        String source = """
                #include "stdarg.mh"

                int nested_sum(int marker, ...) {
                    va_list values;
                    va_start(values, marker);
                    int first = va_arg(values, int);
                    int second = va_arg(values, int);
                    int third = va_arg(values, int);
                    va_end(values);
                    return first + second + third;
                }

                int relay(int marker, ...) {
                    va_list original;
                    va_list copied;
                    va_start(original, marker);
                    va_copy(copied, original);
                    int first = va_arg(original, int);
                    int second = va_arg(original, int);
                    int copied_first = va_arg(copied, int);
                    va_end(copied);
                    va_end(original);
                    return nested_sum(0, first, second, copied_first);
                }

                int main(void) {
                    return relay(0, 7, 11) == 25 ? 0 : 1;
                }
                """;

        assertNativeExit(source, 0);
    }

    @Test
    void diagnosesNonVariadicUseWrongLastParameterAndPromotedTypes() {
        String source = """
                #include "stdarg.mh"

                int fixed(int last) {
                    va_list values;
                    va_start(values, last);
                    return 0;
                }

                int broken(int first, int last, ...) {
                    va_list values;
                    va_start(values, first);
                    float value = va_arg(values, float);
                    int invalid_list = 0;
                    va_end(invalid_list);
                    return value;
                }

                int main(void) { return 0; }
                """;
        CompilerApi session = new CompilerApi(
                new SourceFile("stdarg-diagnostics.mc", source));

        session.runThrough(session.stage(SemanticAnalyzer.class));
        String diagnostics = session.stage(Preprocessor.class).errors().toString()
                + session.stage(Lexer.class).errors()
                + session.stage(Parser.class).errors()
                + session.stage(SemanticAnalyzer.class).errors();
        assertTrue(diagnostics.contains("variadic"), diagnostics);
        assertTrue(diagnostics.contains("last"), diagnostics);
        assertTrue(diagnostics.contains("float") || diagnostics.contains("默认提升"), diagnostics);
        assertTrue(diagnostics.contains("va_list"), diagnostics);
    }

    private void assertNativeExit(String source, int expectedExit) {
        SourceFile sourceFile = new SourceFile("stdarg-native.mc", source);
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> session.stage(Preprocessor.class).errors().toString()
                + session.stage(Lexer.class).errors()
                + session.stage(Parser.class).errors()
                + session.stage(SemanticAnalyzer.class).errors()
                + session.stage(Linker.class).errors());
        var execution = new ExecutableRunner().run(
                sourceFile,
                session.stage(Linker.class).result().executableArtifactOptional().orElseThrow()
        );
        assertEquals(expectedExit, execution.exitCode(), execution::stderr);
    }
}
