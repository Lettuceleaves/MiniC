package minic.compiler;

import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
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
        var execution = executionStage.run(sourceFile, artifact, "4 -7 1.25 ok\n");
        assertTrue(executionStage.errors().isEmpty(), () -> executionStage.errors().toString());
        assertEquals("", execution.stderr());
        assertEquals(0, execution.exitCode());
        assertEquals("ratio=1.25 read=4 zero=1 abs=7 min=4 sum=10 word=ok\r\n", execution.stdout());
    }
}
