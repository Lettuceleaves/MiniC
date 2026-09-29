package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugStdioEndToEndParityTest {
    @Test
    void nativeAndDebugShareThePublishedHandleFreeStdioSubset() {
        String source = """
                #include "stdio.mh"
                int main(void) {
                    int character = getchar();
                    int end = getchar();
                    if (character != 'Q' || end != EOF) return 1;
                    if (putchar(character) != character || puts("line") < 0) return 2;

                    char formatted[32];
                    int written = sprintf(formatted, "%s:%d:%.1f", "value", 7, 2.5);
                    if (written != 11 || formatted[0] != 'v' || formatted[10] != '5'
                            || formatted[11] != 0) return 3;

                    int integer = 0;
                    double real = 0.0;
                    char word[16];
                    int assigned = sscanf("42 3.5 ok", "%d %lf %15s", &integer, &real, word);
                    if (assigned != 3 || integer != 42 || real != 3.5
                            || word[0] != 'o' || word[1] != 'k' || word[2] != 0) return 4;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("stdio-debug-native-parity.mc", source);

        CompileObservationSession nativeSession = CompileObservationSession.fromSource(sourceFile);
        nativeSession.compilerApi().run();
        assertTrue(nativeSession.linker().succeeded(), () -> "stage=" + nativeSession.currentStage()
                + ", preprocess=" + nativeSession.preprocessor().diagnostics()
                + ", lexer=" + nativeSession.lexer().diagnostics()
                + ", parser=" + nativeSession.parser().diagnostics()
                + ", semantic=" + nativeSession.semanticAnalyzer().diagnostics()
                + ", obj=" + nativeSession.objBuilder().diagnostics()
                + ", link=" + nativeSession.linker().diagnostics());
        var nativeExecution = new ExecutableRunner().run(
                sourceFile,
                nativeSession.linker().result().executableArtifactOptional().orElseThrow(),
                "Q"
        );
        assertTrue(nativeExecution.diagnostics().isEmpty(), nativeExecution.diagnostics()::toString);

        DebugApi debug = new DebugApi(sourceFile, "Q");
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
