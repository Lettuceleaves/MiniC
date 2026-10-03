package minic.compiler.parser;

import minic.compiler.CompilerApi;
import minic.compiler.link.Linker;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
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
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(Linker.class).succeeded(), () -> "parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors());
        var result = new ExecutableRunner().run(
                sourceFile, session.stage(Linker.class).result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
