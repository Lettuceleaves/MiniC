package minic.compiler.parser;

import minic.compiler.CompilerApi;
import minic.compiler.link.Linker;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DesignatedInitializerTest {
    @Test
    void supportsFieldAndArrayDesignatorsAndTrailingCommas() {
        String source = """
                struct Pair { int left; int right; };
                int main(void) {
                    struct Pair pair = { .right = 8, .left = 3, };
                    int values[4] = { [3] = 9, [1] = 4, };
                    return pair.left == 3 && pair.right == 8
                            && values[1] == 4 && values[3] == 9 ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("designated.mc", source);
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(Linker.class).succeeded(), () -> "parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors());
        var result = new ExecutableRunner().run(
                sourceFile, session.stage(Linker.class).result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
