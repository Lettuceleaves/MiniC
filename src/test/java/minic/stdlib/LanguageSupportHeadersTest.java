package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class LanguageSupportHeadersTest {
    @Test
    void stdalignAndStdnoreturnExposeRealCompilerSemantics() {
        String source = """
                #include "stdalign.mh"
                #include "stdnoreturn.mh"

                struct AlignedValue {
                    char prefix;
                    alignas(16) int value;
                };

                noreturn void stop(void) {
                    while (1) { }
                }

                int main(void) {
                    if (alignof(struct AlignedValue) != 16) return 1;
                    if (sizeof(struct AlignedValue) != 32) return 2;
                    if (__alignas_is_defined != 1 || __alignof_is_defined != 1) return 3;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("language-support-headers.mc", source);
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
