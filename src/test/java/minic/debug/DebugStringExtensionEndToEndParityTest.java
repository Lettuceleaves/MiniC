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
final class DebugStringExtensionEndToEndParityTest {
    @Test
    void nativeAndDebugShareThePublishedC23StringExtensions() {
        String source = """
                #include "string.mh"
                #include "stdlib.mh"

                int main(void) {
                    char *source = "abcde";
                    char destination[6];
                    memset(destination, 127, 6);
                    void *after = memccpy(destination, source, 'c', 6);
                    if (after != destination + 3) return 1;
                    if (destination[0] != 'a' || destination[1] != 'b'
                            || destination[2] != 'c' || destination[3] != 127) return 2;

                    if (memccpy(destination, source, 'z', 6) != NULL) return 3;
                    if (destination[4] != 'e' || destination[5] != 0) return 4;
                    if (memccpy(NULL, NULL, 'a', 0) != NULL) return 5;

                    char *whole = strdup(source);
                    char *part = strndup(source, 3);
                    char *wide = strndup(source, 20);
                    char *empty = strndup(source, 0);
                    if (whole == NULL || whole == source || strcmp(whole, "abcde") != 0) return 6;
                    if (part == NULL || strcmp(part, "abc") != 0) return 7;
                    if (wide == NULL || strcmp(wide, "abcde") != 0) return 8;
                    if (empty == NULL || empty[0] != 0) return 9;

                    char secret[6];
                    strcpy(secret, "abcde");
                    if (memset_explicit(secret, 'x', 3) != secret) return 10;
                    if (secret[0] != 'x' || secret[1] != 'x' || secret[2] != 'x'
                            || secret[3] != 'd' || secret[4] != 'e' || secret[5] != 0) return 11;

                    free(whole); free(part); free(wide); free(empty);
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("string-extension-debug-native-parity.mc", source);

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
        assertTrue(nativeExecutionStage.errors().isEmpty(), nativeExecutionStage.errors()::toString);

        DebugApi debug = new DebugApi(sourceFile);
        int remaining = 100_000;
        while (debug.canNext() && remaining-- > 0) {
            debug.next();
        }
        if (debug.canNext()) {
            fail("debugger did not complete within the step budget");
        }

        assertEquals(Debugger.Status.COMPLETED, debug.current().stop().status(), debug.current().stop().error());
        assertEquals(nativeExecution.exitCode(), (int) debug.current().runtime().returnValue().integer());
        assertEquals(0, nativeExecution.exitCode());
    }
}
