package minic.compiler;

import minic.compiler.lexer.Lexer;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SystemLibraryEndToEndTest {
    @Test
    @Tag("stdlib-native")
    void linksAndRunsWindowsCrtFunctions() {
        String source = """
                #include "stdlib.mh"
                #include "stdio.mh"
                #include "minwindef.mh"

                void release_all(void *first, void *second) {
                    free(first);
                    free(second);
                    free(NULL);
                }

                int main() {
                    int count = 0;
                    int raw = 0;
                    double ratio = 0.0;
                    char word[16];
                    int read = scanf("%d %d %lf %15s", &count, &raw, &ratio, word);
                    if (read != 4) return 20;

                    int *zeroed = calloc(count, sizeof(int));
                    if (zeroed == NULL) return 21;
                    int zero = 1;
                    for (int i = 0; i < count; i = i + 1) {
                        if (zeroed[i] != 0) zero = 0;
                    }

                    int *values = malloc(count * sizeof(int));
                    if (values == NULL) return 22;
                    for (int i = 0; i < count; i = i + 1) values[i] = i + 1;

                    int magnitude = abs(raw);
                    int limit = min(magnitude, count);
                    int sum = 0;
                    for (int i = 0; i < limit; i = i + 1) sum += values[i];

                    int printed = printf(
                        "ratio=%.2f read=%d zero=%d abs=%d min=%d sum=%d word=%s\\n",
                        ratio, read, zero, magnitude, limit, sum, word
                    );
                    release_all(values, zeroed);
                    return printed > 0 && zero == 1 && sum == 10 ? 0 : 23;
                }
                """;
        SourceFile sourceFile = new SourceFile("system-library-e2e.mc", source);
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
        var execution = executionStage.run(sourceFile, artifact, "4 -7 1.25 ok\n");
        assertTrue(executionStage.errors().isEmpty(), () -> executionStage.errors().toString());
        assertEquals("", execution.stderr());
        assertEquals(0, execution.exitCode());
        assertEquals("ratio=1.25 read=4 zero=1 abs=7 min=4 sum=10 word=ok\r\n", execution.stdout());
    }
}
