package minic.compiler;

import minic.debug.DebugApi;
import minic.debug.Debugger;
import minic.session.CompileObservationSession;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GlobalVariableEndToEndTest {
    private static final String SOURCE = """
            extern int counter;
            int counter = 4;
            int values[4] = { 1, 2, [3] = 7 };
            struct Pair { int left; int right; };
            struct Pair pair = { .right = 5, .left = 3 };

            int bump() { counter += pair.left; return counter; }
            int main() {
                if (bump() != 7) return 1;
                values[2] = counter;
                return values[0] + values[1] + values[2] + values[3] + pair.right == 22 ? 0 : 2;
            }
            """;

    @Test
    void lowersWritableGlobalsAndExecutesThemInDebugger() {
        SourceFile source = new SourceFile("globals.mc", SOURCE);
        var session = CompileObservationSession.fromSource(source);
        session.compilerApi().run();
        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        assertEquals(3, session.irLowerer().result().globalData().size());
        var nativeRun = new ExecutableRunner().run(source,
                session.linker().result().executableArtifactOptional().orElseThrow());
        assertTrue(nativeRun.diagnostics().isEmpty(), () -> nativeRun.diagnostics().toString());
        assertEquals(0, nativeRun.exitCode());

        DebugApi api = new DebugApi(source);
        for (int budget = 20_000; api.canNext() && budget > 0; budget--) api.next();
        assertFalse(api.canNext());
        assertEquals(Debugger.Status.COMPLETED, api.current().stop().status(), api.current().stop().error());
        assertEquals(0, api.current().runtime().returnValue().integer());
        assertEquals(3, api.current().runtime().globalMemory().size());
    }
}
