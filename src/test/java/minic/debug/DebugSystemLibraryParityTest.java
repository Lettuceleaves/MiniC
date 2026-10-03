package minic.debug;

import minic.compiler.SourceFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("stdlib-debug")
final class DebugSystemLibraryParityTest {
    @Test
    void interpretsTheSystemLibraryAcceptanceProgram() {
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
                    int *zeroed = calloc(count, sizeof(int));
                    int *values = malloc(count * sizeof(int));
                    int zero = 1;
                    for (int i = 0; i < count; i = i + 1) {
                        if (zeroed[i] != 0) zero = 0;
                        values[i] = i + 1;
                    }
                    int magnitude = abs(raw);
                    int limit = min(magnitude, count);
                    int sum = 0;
                    for (int i = 0; i < limit; i = i + 1) sum += values[i];
                    printf("ratio=%.2f read=%d zero=%d abs=%d min=%d sum=%d word=%s\\n",
                        ratio, read, zero, magnitude, limit, sum, word);
                    release_all(values, zeroed);
                    return zero == 1 && sum == 10 ? 0 : 1;
                }
                """;
        DebugApi api = new DebugApi(new SourceFile("debug-system-library.mc", source), "4 -7 1.25 ok\n");

        int steps = 0;
        while (api.canNext() && steps++ < 10_000) {
            api.next();
        }

        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status(), api.current().stop().error());
        assertEquals(0, api.current().runtime().returnValue().integer());
        assertEquals(
                "ratio=1.25 read=4 zero=1 abs=7 min=4 sum=10 word=ok\n",
                api.current().runtime().stdout()
        );
    }
}
