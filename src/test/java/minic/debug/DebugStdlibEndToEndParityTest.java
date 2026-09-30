package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugStdlibEndToEndParityTest {
    @Test
    void executesTheVerifiedStdlibAndErrnoSurfaceNativelyAndInTheDebugger() {
        String source = """
                #include "stdlib.mh"
                #include "errno.mh"
                int main() {
                    if (atof("2.5") != 2.5) return 1;
                    if (atoi("-17tail") != -17 || atol("2000000000") != 2000000000L) return 2;
                    if (atoll("-5000000000") != -5000000000LL) return 3;

                    char *end = NULL;
                    if (strtod("3.25tail", &end) != 3.25 || *end != 't') return 4;
                    if (strtol("0x20tail", &end, 0) != 32 || *end != 't') return 5;
                    if (strtoll("-5000000000!", &end, 10) != -5000000000LL || *end != '!') return 6;
                    if (strtoul("4000000000!", &end, 10) != 4000000000UL || *end != '!') return 7;
                    if (strtoull("5000000000!", &end, 10) != 5000000000ULL || *end != '!') return 8;

                    errno = 0;
                    if (strtol("2147483648", &end, 10) != 2147483647L || errno != ERANGE) return 9;
                    if (abs(-7) != 7 || labs(-2000000000L) != 2000000000L
                            || llabs(-5000000000LL) != 5000000000LL) return 10;

                    srand(1);
                    if (rand() != 41 || rand() != 18467) return 11;

                    int *numbers = malloc(2 * sizeof(int));
                    numbers[0] = 7;
                    numbers[1] = 9;
                    numbers = realloc(numbers, 3 * sizeof(int));
                    if (numbers == NULL || numbers[0] != 7 || numbers[1] != 9) return 12;
                    numbers[2] = 11;
                    if (numbers[2] != 11) return 13;
                    free(numbers);
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("stdlib-errno-parity.mc", source);

        CompilerFixture nativeSession = CompilerFixture.fromSource(sourceFile);
        nativeSession.compilerApi().runThrough(nativeSession.linker());
        assertTrue(nativeSession.linker().succeeded(), () -> "stage=" + nativeSession.compilerApi().currentStage()
                + ", preprocess=" + nativeSession.preprocessor().errors()
                + ", lexer=" + nativeSession.lexer().errors()
                + ", parser=" + nativeSession.parser().errors()
                + ", semantic=" + nativeSession.semanticAnalyzer().errors()
                + ", obj=" + nativeSession.objBuilder().errors()
                + ", link=" + nativeSession.linker().errors());
        var nativeExecutionStage = new ExecutableRunner();
        var nativeExecution = nativeExecutionStage.run(
                sourceFile,
                nativeSession.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(nativeExecutionStage.errors().isEmpty(), () -> nativeExecutionStage.errors().toString());

        DebugApi debug = new DebugApi(sourceFile);
        int remaining = 50_000;
        while (debug.canNext() && remaining-- > 0) {
            debug.next();
        }
        if (debug.canNext()) {
            fail("debugger did not complete within the step budget");
        }

        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop().error());
        assertEquals(nativeExecution.exitCode(), (int) debug.current().runtime().returnValue().integer());
        assertEquals(nativeExecution.stdout().replace("\r\n", "\n"), debug.current().runtime().stdout());
        assertEquals(0, nativeExecution.exitCode());
    }
}
