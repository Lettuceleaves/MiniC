package minic.compiler.parser;

import minic.compiler.CompilerApi;
import minic.compiler.link.Linker;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
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
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));
        assertTrue(session.stage(Linker.class).succeeded(), () -> "parse=" + session.stage(Parser.class).errors()
                + ", semantic=" + session.stage(SemanticAnalyzer.class).errors());
        var result = new ExecutableRunner().run(
                sourceFile, session.stage(Linker.class).result().executableArtifactOptional().orElseThrow());
        assertEquals(0, result.exitCode());
    }
}
