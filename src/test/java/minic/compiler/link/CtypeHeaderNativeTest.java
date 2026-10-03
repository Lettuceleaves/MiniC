package minic.compiler.link;

import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
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
        CompilerApi session = new CompilerApi(sourceFile);

        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> "pre=" + session.stage(Preprocessor.class).errors()
                + ", lex=" + session.stage(Lexer.class).errors()
                + ", parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors()
                + ", obj=" + session.stage(ObjBuilder.class).errors()
                + ", link=" + session.stage(Linker.class).errors());
        var imports = PeImportIntegrationTest.readImports(session.stage(Linker.class).peImage().orElseThrow().bytes());
        assertEquals(Set.of("_isctype"), imports.get("msvcrt.dll"));
        assertFalse(imports.get("msvcrt.dll").contains("isblank"), "MSVCRT has no isblank export");
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"));

        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(
                sourceFile,
                session.stage(Linker.class).result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(0, execution.exitCode(), execution::stderr);
        assertEquals("", execution.stdout());
    }
}
