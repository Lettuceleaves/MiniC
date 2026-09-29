package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
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
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);

        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().diagnostics()
                + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var execution = new ExecutableRunner().run(sourceFile, artifact, "");
        assertTrue(execution.diagnostics().isEmpty(), () -> execution.diagnostics().toString());
        assertEquals(0, execution.exitCode(), execution::stderr);
    }
}
