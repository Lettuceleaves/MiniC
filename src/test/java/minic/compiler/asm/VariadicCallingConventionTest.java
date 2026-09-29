package minic.compiler.asm;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VariadicCallingConventionTest {
    @Test
    @Tag("stdlib-abi")
    void appliesDefaultPromotionsAndDuplicatesFloatingRegisterArguments() {
        String source = """
                #include "stdio.mh"
                int main() {
                    long long big = 5000000000LL;
                    float ratio = 1.5f;
                    char tag = 'A';
                    int printed = printf("%I64d %.1f %d %s\\n", big, ratio, tag, "ok");
                    return printed > 0 ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("variadic-abi.mc", source);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);

        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> session.linker().diagnostics().toString());
        String assembly = session.assembler().result().text();
        assertTrue(assembly.contains("movq r8, xmm2"), assembly);
        var execution = new ExecutableRunner().run(
                sourceFile,
                session.linker().result().executableArtifactOptional().orElseThrow()
        );
        assertEquals(0, execution.exitCode());
        assertEquals("5000000000 1.5 65 ok\r\n", execution.stdout());
    }
}
