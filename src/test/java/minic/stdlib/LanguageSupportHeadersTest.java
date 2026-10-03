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
final class LanguageSupportHeadersTest {
    @Test
    void stdalignAndStdnoreturnExposeRealCompilerSemantics() {
        String source = """
                #include "stdalign.mh"
                #include "stdnoreturn.mh"

                struct AlignedValue {
                    char prefix;
                    alignas(16) int value;
                };

                noreturn void stop(void) {
                    while (1) { }
                }

                int main(void) {
                    if (alignof(struct AlignedValue) != 16) return 1;
                    if (sizeof(struct AlignedValue) != 32) return 2;
                    if (__alignas_is_defined != 1 || __alignof_is_defined != 1) return 3;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("language-support-headers.mc", source);
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
