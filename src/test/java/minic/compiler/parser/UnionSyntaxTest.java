package minic.compiler.parser;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UnionSyntaxTest {
    @Test
    void laysOutUnionMembersAtTheSameAddressAndSupportsForwardDeclaration() {
        String source = """
                union Value;
                union Value { int integer; unsigned long long wide; };
                int main(void) {
                    union Value value = { .wide = 42ULL, };
                    return sizeof(union Value) == 8 && value.wide == 42ULL ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("union.mc", source);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().run();
        assertTrue(session.linker().succeeded(), () -> "parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics());
        var result = new ExecutableRunner().run(
                sourceFile, session.linker().result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
