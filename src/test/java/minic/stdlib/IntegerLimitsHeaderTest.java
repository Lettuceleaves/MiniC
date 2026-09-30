package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class IntegerLimitsHeaderTest {
    @Test
    void exposesWindowsLlp64IntegerLimits() {
        String source = """
                #include "limits.mh"

                int main() {
                    if (CHAR_BIT != 8) return 1;
                    if (sizeof(char) != 1 || sizeof(short) != 2) return 2;
                    if (sizeof(int) != 4 || sizeof(long) != 4 || sizeof(long long) != 8) return 3;
                    if (SCHAR_MIN != -128 || SCHAR_MAX != 127 || UCHAR_MAX != 255U) return 4;
                    if (SHRT_MIN != -32768 || SHRT_MAX != 32767 || USHRT_MAX != 65535U) return 5;
                    if (INT_MIN != (-2147483647 - 1) || INT_MAX != 2147483647) return 6;
                    if (UINT_MAX != 4294967295U) return 7;
                    if (LONG_MIN != (-2147483647L - 1L) || LONG_MAX != 2147483647L) return 8;
                    if (ULONG_MAX != 4294967295UL) return 9;
                    if (LLONG_MIN != (-9223372036854775807LL - 1LL)) return 10;
                    if (LLONG_MAX != 9223372036854775807LL) return 11;
                    if (ULLONG_MAX != ~0ULL) return 12;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("limits-e2e.mc", source);
        CompilerFixture session = CompilerFixture.fromSource(sourceFile);

        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().errors()
                + ", lex=" + session.lexer().errors()
                + ", parse=" + session.parser().errors()
                + ", semantic=" + session.semanticAnalyzer().errors()
                + ", obj=" + session.objBuilder().errors()
                + ", link=" + session.linker().errors());
        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(sourceFile, artifact, "");
        assertTrue(executionStage.errors().isEmpty(), () -> executionStage.errors().toString());
        assertEquals(0, execution.exitCode(), execution::stderr);
    }
}
