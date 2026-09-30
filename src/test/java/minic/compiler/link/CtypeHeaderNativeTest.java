package minic.compiler.link;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
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
        CompilerFixture session = CompilerFixture.fromSource(sourceFile);

        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().errors()
                + ", lex=" + session.lexer().errors()
                + ", parse=" + session.parser().errors()
                + ", semantic=" + session.semanticAnalyzer().errors()
                + ", obj=" + session.objBuilder().errors()
                + ", link=" + session.linker().errors());
        var imports = PeImportIntegrationTest.readImports(session.linker().peImage().orElseThrow().bytes());
        assertEquals(Set.of("_isctype"), imports.get("msvcrt.dll"));
        assertFalse(imports.get("msvcrt.dll").contains("isblank"), "MSVCRT has no isblank export");
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"));

        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(
                sourceFile,
                session.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(0, execution.exitCode(), execution::stderr);
        assertEquals("", execution.stdout());
    }
}
