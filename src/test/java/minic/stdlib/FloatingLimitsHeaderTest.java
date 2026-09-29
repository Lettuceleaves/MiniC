package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class FloatingLimitsHeaderTest {
    @Test
    void exposesTheWindowsUcrtFloatingModel() {
        String source = """
                #include "float.mh"

                int main() {
                    if (FLT_RADIX != 2 || FLT_MANT_DIG != 24 || DBL_MANT_DIG != 53) return 1;
                    if (sizeof(float) != 4 || sizeof(double) != 8) return 2;
                    if (!(1.0f + FLT_EPSILON > 1.0f)) return 3;
                    if (!(1.0 + DBL_EPSILON > 1.0)) return 4;
                    if (!(FLT_MAX > 1.0e+38f) || !(DBL_MAX > 1.0e+307)) return 5;
                    if (!(FLT_MIN > 0.0f) || !(DBL_MIN > 0.0)) return 6;
                    if (!(FLT_TRUE_MIN > 0.0f) || !(DBL_TRUE_MIN > 0.0)) return 7;
                    if (FLT_ROUNDS != 1 || FLT_EVAL_METHOD != 0) return 8;
                    return 0;
                }
                """;
        SourceFile sourceFile = new SourceFile("float-limits-e2e.mc", source);
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
        assertEquals(0, execution.exitCode(), execution::stderr);
    }
}
