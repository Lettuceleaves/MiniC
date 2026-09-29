package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
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
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().run();
        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics());
        var result = new ExecutableRunner().run(
                sourceFile, session.linker().result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
