package minic.stdlib;

import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
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
        CompilerApi session = new CompilerApi(sourceFile);

        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> "pre=" + session.stage(Preprocessor.class).errors()
                + ", lex=" + session.stage(Lexer.class).errors()
                + ", parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors()
                + ", obj=" + session.stage(ObjBuilder.class).errors()
                + ", link=" + session.stage(Linker.class).errors());
        var artifact = session.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(sourceFile, artifact, "");
        assertTrue(executionStage.errors().isEmpty(), () -> executionStage.errors().toString());
        assertEquals(0, execution.exitCode(), execution::stderr);
    }
}
