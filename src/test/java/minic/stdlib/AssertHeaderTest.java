package minic.stdlib;

import minic.compiler.CompilerApi;
import minic.compiler.link.Linker;
import minic.compiler.parser.Parser;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
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

        CompilerApi session = new CompilerApi(
                new SourceFile("assert-enabled.mc", source)
        );
        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> session.stage(SemanticAnalyzer.class).errors().toString());
    }

    @Test
    void ndebugDoesNotEvaluateExpressionAndCanBeObservedOnReinclude() {
        String source = "#include \"assert.mh\"\n"
                + "#define NDEBUG\n"
                + "#include \"assert.mh\"\n"
                + "int main() { int value = 0; assert(++value); return value; }\n";

        CompilerApi session = new CompilerApi(
                new SourceFile("assert-disabled.mc", source)
        );
        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> session.stage(Parser.class).errors() + " "
                + session.stage(SemanticAnalyzer.class).errors());
        var executionStage = new ExecutableRunner();
        var execution = executionStage.run(
                new SourceFile("assert-disabled.mc", source),
                session.stage(Linker.class).result().executableArtifactOptional().orElseThrow()
        );
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(0, execution.exitCode());
    }
}
