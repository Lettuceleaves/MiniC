package minic.compiler.parser;

import minic.compiler.CompilerApi;
import minic.compiler.link.Linker;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructForwardDeclarationTest {
    @Test
    void completesAForwardDeclaredStructAndUsesSelfPointers() {
        String source = """
                struct Node;
                struct Node { int value; struct Node *next; };
                int main(void) {
                    struct Node tail = { 2, NULL, };
                    struct Node head = { 1, &tail, };
                    return head.next->value == 2 ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("forward.mc", source);
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(Linker.class).succeeded(), () -> "parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors());
        var result = new ExecutableRunner().run(
                sourceFile, session.stage(Linker.class).result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
