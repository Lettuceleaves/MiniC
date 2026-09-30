package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
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

        CompilerFixture session = CompilerFixture.fromSource(
                new SourceFile("assert-enabled.mc", source)
        );
        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> session.semanticAnalyzer().errors().toString());
    }

    @Test
    void ndebugDoesNotEvaluateExpressionAndCanBeObservedOnReinclude() {
        String source = "#include \"assert.mh\"\n"
                + "#define NDEBUG\n"
                + "#include \"assert.mh\"\n"
                + "int main() { int value = 0; assert(++value); return value; }\n";

        CompilerFixture session = CompilerFixture.fromSource(
                new SourceFile("assert-disabled.mc", source)
        );
        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> session.parser().errors() + " "
                + session.semanticAnalyzer().errors());
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(
                new SourceFile("assert-disabled.mc", source),
                session.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(0, execution.exitCode());
    }
}
