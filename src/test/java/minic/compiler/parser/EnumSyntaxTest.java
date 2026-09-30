package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnumSyntaxTest {
    @Test
    void supportsExplicitImplicitAndTrailingCommaEnumerators() {
        String source = """
                enum Color { RED = 0x10, GREEN, BLUE = (GREEN << 1) + 2, };
                int main(void) {
                    enum Color color = GREEN;
                    return color == 17 && BLUE == 36 ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("enum.mc", source);
        CompilerFixture session = CompilerFixture.fromSource(sourceFile);
        session.compilerApi().runThrough(session.linker());
        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().errors()
                + ", semantic=" + session.semanticAnalyzer().errors());
        var result = new ExecutableRunner().run(
                sourceFile, session.linker().result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
