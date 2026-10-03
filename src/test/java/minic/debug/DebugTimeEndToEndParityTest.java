package minic.debug;

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
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugTimeEndToEndParityTest {
    @Test
    void nativeAndDebugShareThePublishedTimeSurface() {
        String source = """
                #include "time.mh"
                int main(void) {
                    if (CLOCKS_PER_SEC != 1000L) return 1;
                    clock_t ticks = clock();
                    if (ticks == -1L) return 2;

                    time_t now = 0;
                    if (time(&now) != now || now <= 0 || difftime(now, now) != 0.0) return 3;

                    time_t epoch = 0;
                    struct tm *utc = gmtime(&epoch);
                    if (!utc || utc->tm_sec != 0 || utc->tm_min != 0 || utc->tm_hour != 0
                            || utc->tm_mday != 1 || utc->tm_mon != 0 || utc->tm_year != 70
                            || utc->tm_wday != 4 || utc->tm_yday != 0 || utc->tm_isdst != 0) return 4;

                    char text[32];
                    if (strftime(text, 32, "%Y-%m-%d %H:%M:%S", utc) != 19) return 5;
                    if (text[0] != '1' || text[3] != '0' || text[4] != '-'
                            || text[18] != '0' || text[19] != 0) return 6;

                    struct tm *local = localtime(&epoch);
                    if (!local || local != utc || local->tm_mon < 0 || local->tm_mon > 11
                            || local->tm_mday < 1 || local->tm_mday > 31
                            || local->tm_hour < 0 || local->tm_hour > 23) return 7;
                    if (mktime(local) != epoch) return 8;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("time-debug-native-parity.mc", source);

        CompilerApi nativeSession = new CompilerApi(sourceFile);
        nativeSession.runThrough(nativeSession.stage(Linker.class));
        assertTrue(nativeSession.stage(Linker.class).succeeded(), () -> "stage=" + nativeSession.currentStage()
                + ", preprocess=" + nativeSession.stage(Preprocessor.class).errors()
                + ", lexer=" + nativeSession.stage(Lexer.class).errors()
                + ", parser=" + nativeSession.stage(Parser.class).errors()
                + ", semantic=" + nativeSession.stage(SemanticAnalyzer.class).errors()
                + ", obj=" + nativeSession.stage(ObjBuilder.class).errors()
                + ", link=" + nativeSession.stage(Linker.class).errors());
        var nativeExecutionStage = new ExecutableRunner();
        var nativeExecution = nativeExecutionStage.run(
                sourceFile,
                nativeSession.stage(Linker.class).result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(nativeExecutionStage.errors().isEmpty(), nativeExecutionStage.errors()::toString);

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
        assertEquals(0, nativeExecution.exitCode());
    }
}
