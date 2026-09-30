package minic.debug;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.testing.CompilerFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("stdlib-debug")
@Tag("stdlib-parity")
@Timeout(30)
final class DebugLocaleEndToEndParityTest {
    @Test
    void nativeAndDebugShareThePublishedCLocaleSurface() {
        String source = """
                #include "locale.mh"

                int is_c_locale(char *name) {
                    return name != NULL && name[0] == 'C' && name[1] == 0;
                }

                int empty(char *value) {
                    return value != NULL && value[0] == 0;
                }

                int main(void) {
                    if (LC_ALL != 0 || LC_COLLATE != 1 || LC_CTYPE != 2
                            || LC_MONETARY != 3 || LC_NUMERIC != 4 || LC_TIME != 5) return 1;
                    if (!is_c_locale(setlocale(LC_ALL, NULL))) return 2;
                    for (int category = LC_ALL; category <= LC_TIME; category = category + 1) {
                        if (!is_c_locale(setlocale(category, "C"))) return 3;
                        if (!is_c_locale(setlocale(category, NULL))) return 4;
                    }
                    if (setlocale(LC_ALL, "MiniC_invalid_locale_name_!") != NULL) return 5;
                    if (!is_c_locale(setlocale(LC_ALL, NULL))) return 6;

                    struct lconv *first = localeconv();
                    struct lconv *second = localeconv();
                    if (first == NULL || first != second || sizeof(struct lconv) != 88) return 7;
                    if (first->decimal_point == NULL || first->decimal_point[0] != '.'
                            || first->decimal_point[1] != 0) return 8;
                    if (!empty(first->thousands_sep) || !empty(first->grouping)
                            || !empty(first->int_curr_symbol) || !empty(first->currency_symbol)
                            || !empty(first->mon_decimal_point) || !empty(first->mon_thousands_sep)
                            || !empty(first->mon_grouping) || !empty(first->positive_sign)
                            || !empty(first->negative_sign)) return 9;
                    return first->int_frac_digits == 127 && first->frac_digits == 127
                        && first->p_cs_precedes == 127 && first->p_sep_by_space == 127
                        && first->n_cs_precedes == 127 && first->n_sep_by_space == 127
                        && first->p_sign_posn == 127 && first->n_sign_posn == 127 ? 0 : 10;
                }
                """;
        SourceFile sourceFile = new SourceFile("locale-debug-native-parity.mc", source);

        CompilerFixture nativeSession = CompilerFixture.fromSource(sourceFile);
        nativeSession.compilerApi().runThrough(nativeSession.linker());
        assertTrue(nativeSession.linker().succeeded(), () -> "stage=" + nativeSession.compilerApi().currentStage()
                + ", preprocess=" + nativeSession.preprocessor().errors()
                + ", lexer=" + nativeSession.lexer().errors()
                + ", parser=" + nativeSession.parser().errors()
                + ", semantic=" + nativeSession.semanticAnalyzer().errors()
                + ", obj=" + nativeSession.objBuilder().errors()
                + ", link=" + nativeSession.linker().errors());
        var nativeExecutionStage = new ExecutableRunner();
        var nativeExecution = nativeExecutionStage.run(
                sourceFile,
                nativeSession.linker().result().executableArtifactOptional().orElseThrow()
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
