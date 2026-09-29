package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.compiler.preprocess.Preprocessor;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HeaderOnlyLibraryTest {
    @Test
    @Tag("stdlib-contract")
    void expandsEveryIso646AlternativeTokenAndTheBoolCompatibilityMacro() {
        String source = """
                #include "iso646.mh"
                #include "stdbool.mh"
                _Bool value = true;
                int result = value and not false;
                result and_eq 1;
                result or_eq 2;
                result xor_eq 3;
                result = (result bitand 1) bitor (compl result);
                result = result xor 7;
                result = result not_eq 0 or false;
                """;

        var result = new Preprocessor().preprocess(new SourceFile("header-only.mc", source));

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        String expanded = result.sourceFile().content();
        assertTrue(expanded.contains("bool value = true;"));
        assertTrue(expanded.contains("value && ! false"));
        assertTrue(expanded.contains("result &= 1"));
        assertTrue(expanded.contains("result |= 2"));
        assertTrue(expanded.contains("result ^= 3"));
        assertTrue(expanded.contains("result & 1"));
        assertTrue(expanded.contains("~ result"));
        assertTrue(expanded.contains("result ^ 7"));
        assertTrue(expanded.contains("result != 0 || false"));
    }

    @Test
    @Tag("stdlib-native")
    void compilesAndRunsIso646AndStdboolWithoutLibraryImports() {
        String source = """
                #include "iso646.mh"
                #include "stdbool.mh"

                int main() {
                    _Bool value = true;
                    int left = 6;
                    int right = 3;
                    int ok = ((left bitand right) == 2)
                        and ((left bitor right) == 7)
                        and ((left xor right) == 5)
                        and not (left == right)
                        and (left not_eq right);
                    int compound = left;
                    compound and_eq right;
                    compound or_eq 4;
                    compound xor_eq 7;
                    return value and ok and compound == 1 ? 0 : 1;
                }
                """;
        SourceFile sourceFile = new SourceFile("header-only-e2e.mc", source);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);

        session.compilerApi().run();

        assertTrue(session.linker().succeeded(), () -> "pre=" + session.preprocessor().diagnostics()
                + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics()
                + ", semantic=" + session.semanticAnalyzer().diagnostics()
                + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var execution = new ExecutableRunner().run(sourceFile, artifact, "");
        assertTrue(execution.diagnostics().isEmpty(), () -> execution.diagnostics().toString());
        assertEquals(0, execution.exitCode());
        assertEquals("", execution.stdout());
        assertEquals("", execution.stderr());
    }
}
