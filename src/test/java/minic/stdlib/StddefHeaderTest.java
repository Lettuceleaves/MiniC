package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class StddefHeaderTest {
    @Test
    void exposesPointerSizedTypesAndRealStructOffsets() {
        String source = """
                #include "stddef.mh"

                struct Layout {
                    char first;
                    int second;
                    long long third;
                };

                int main() {
                    size_t size = sizeof(struct Layout);
                    ptrdiff_t difference = 9;
                    wchar_t wide = 65535U;
                    max_align_t aligned = 1.0;
                    void *nothing = NULL;
                    if (sizeof(size_t) != 8 || sizeof(ptrdiff_t) != 8) return 1;
                    if (sizeof(wchar_t) != 2 || sizeof(max_align_t) != 8) return 2;
                    if (offsetof(struct Layout, first) != 0) return 3;
                    if (offsetof(struct Layout, second) != 4) return 4;
                    if (offsetof(struct Layout, third) != 8) return 5;
                    if (size != 16 || difference != 9 || wide != 65535U) return 6;
                    if (aligned != 1.0 || nothing != NULL) return 7;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("stddef-e2e.mc", source);
        CompilerFixture session = CompilerFixture.fromSource(sourceFile);

        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().errors()
                + ", lex=" + session.lexer().errors()
                + ", parse=" + session.parser().errors()
                + ", semantic=" + session.semanticAnalyzer().errors()
                + ", obj=" + session.objBuilder().errors()
                + ", link=" + session.linker().errors());
        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(sourceFile, artifact, "");
        assertTrue(executionStage.errors().isEmpty(), () -> executionStage.errors().toString());
        assertEquals(0, execution.exitCode(), execution::stderr);
    }
}
