package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
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
        CompilerFixture session = CompilerFixture.fromSource(sourceFile);
        session.compilerApi().runThrough(session.linker());
        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().errors()
                + ", semantic=" + session.semanticAnalyzer().errors());
        var result = new ExecutableRunner().run(
                sourceFile, session.linker().result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
