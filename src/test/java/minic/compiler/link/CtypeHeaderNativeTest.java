package minic.compiler.link;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class CtypeHeaderNativeTest {
    @Test
    void headerOwnedIsblankEvaluatesItsArgumentOnceAndUsesTheVerifiedPrivateAdapter() {
        String source = """
                #include "ctype.mh"

                int next_character(int *calls, int character) {
                    *calls += 1;
                    return character;
                }

                int main(void) {
                    int calls = 0;
                    if (!isblank(next_character(&calls, '\\t')) || calls != 1) return 1;
                    if (!isblank(next_character(&calls, ' ')) || calls != 2) return 2;
                    if (isblank(next_character(&calls, '\\n')) || calls != 3) return 3;
                    if (isblank(-1)) return 4;

                    int (*predicate)(int) = isblank;
                    if (!predicate(' ') || predicate('x')) return 5;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("ctype-isblank-native.mc", source);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);

        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().diagnostics()
                + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        var imports = PeImportIntegrationTest.readImports(session.linker().peImage().orElseThrow().bytes());
        assertEquals(Set.of("_isctype"), imports.get("msvcrt.dll"));
        assertFalse(imports.get("msvcrt.dll").contains("isblank"), "MSVCRT has no isblank export");
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"));

        var execution = new ExecutableRunner().run(
                sourceFile,
                session.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(execution.diagnostics().isEmpty(), execution.diagnostics()::toString);
        assertEquals(0, execution.exitCode(), execution::stderr);
        assertEquals("", execution.stdout());
    }
}
