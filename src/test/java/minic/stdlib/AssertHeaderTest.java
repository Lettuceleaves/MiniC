package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class AssertHeaderTest {
    @Test
    void evaluatesEnabledAssertionExactlyOnce() {
        String source = "#include \"assert.mh\"\n"
                + "int main() { int value = 0; assert(++value == 1); return value != 1; }\n";

        CompileObservationSession session = CompileObservationSession.fromSource(
                new SourceFile("assert-enabled.mc", source)
        );
        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> session.semanticAnalyzer().diagnostics().toString());
    }

    @Test
    void ndebugDoesNotEvaluateExpressionAndCanBeObservedOnReinclude() {
        String source = "#include \"assert.mh\"\n"
                + "#define NDEBUG\n"
                + "#include \"assert.mh\"\n"
                + "int main() { int value = 0; assert(++value); return value; }\n";

        CompileObservationSession session = CompileObservationSession.fromSource(
                new SourceFile("assert-disabled.mc", source)
        );
        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> session.parser().diagnostics() + " "
                + session.semanticAnalyzer().diagnostics());
        var execution = new ExecutableRunner().run(
                new SourceFile("assert-disabled.mc", source),
                session.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(execution.diagnostics().isEmpty(), execution.diagnostics()::toString);
        assertEquals(0, execution.exitCode());
    }
}
