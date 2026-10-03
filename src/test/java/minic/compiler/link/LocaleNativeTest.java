package minic.compiler.link;

import minic.compiler.CompilerApi;
import minic.compiler.lexer.Lexer;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class LocaleNativeTest {
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{0}")
    @MethodSource("localeCases")
    void runsLocaleOperationsWithExactPeImports(LocaleCase testCase) {
        SourceFile sourceFile = new SourceFile("stdlib-native-locale-" + testCase.name() + ".mc", testCase.source());
        CompilerApi session = new CompilerApi(sourceFile);
        session.runThrough(session.stage(Linker.class));

        assertTrue(session.stage(Linker.class).succeeded(), () -> testCase.name() + ": pre="
                + session.stage(Preprocessor.class).errors() + ", lex=" + session.stage(Lexer.class).errors()
                + ", parse=" + session.stage(Parser.class).errors() + ", semantic="
                + session.stage(SemanticAnalyzer.class).errors() + ", obj=" + session.stage(ObjBuilder.class).errors()
                + ", link=" + session.stage(Linker.class).errors());
        Map<String, Set<String>> imports = PeImportIntegrationTest.readImports(
                session.stage(Linker.class).peImage().orElseThrow().bytes()
        );
        assertEquals(testCase.msvcrtExports(), imports.get("msvcrt.dll"), testCase.name());
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"), testCase.name());
        assertEquals(Set.of("msvcrt.dll", "KERNEL32.dll"), imports.keySet(), testCase.name());

        var artifact = session.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner(Duration.ofSeconds(3));
        var execution = executionStage.run(sourceFile, artifact);
        assertTrue(executionStage.errors().isEmpty(), executionStage.errors()::toString);
        assertEquals(0, execution.exitCode(), () -> testCase.name() + ": " + execution.stderr());
        assertEquals("", execution.stdout(), testCase.name());
        assertEquals("", execution.stderr(), testCase.name());
    }

    static Stream<Arguments> localeCases() {
        return Stream.of(
                localeCase("setlocale", """
                        #include "locale.mh"

                        int is_c_locale(char *name) {
                            return name != NULL && name[0] == 'C' && name[1] == 0;
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

                            char *host = setlocale(LC_ALL, "");
                            if (host == NULL || host[0] == 0) return 7;
                            char firstByte = host[0];
                            char *hostQuery = setlocale(LC_ALL, NULL);
                            if (hostQuery == NULL || hostQuery[0] == 0 || firstByte == 0) return 8;
                            return is_c_locale(setlocale(LC_ALL, "C")) ? 0 : 9;
                        }
                        """, Set.of("setlocale")),
                localeCase("localeconv", """
                        #include "locale.mh"

                        int empty(char *value) {
                            return value != NULL && value[0] == 0;
                        }

                        int main(void) {
                            if (setlocale(LC_ALL, "C") == NULL) return 1;
                            struct lconv *first = localeconv();
                            struct lconv *second = localeconv();
                            if (first == NULL || second == NULL || first != second) return 2;
                            if (sizeof(struct lconv) != 88) return 3;
                            if (first->decimal_point == NULL || first->decimal_point[0] != '.'
                                    || first->decimal_point[1] != 0) return 4;
                            if (!empty(first->thousands_sep) || !empty(first->grouping)
                                    || !empty(first->int_curr_symbol) || !empty(first->currency_symbol)
                                    || !empty(first->mon_decimal_point) || !empty(first->mon_thousands_sep)
                                    || !empty(first->mon_grouping) || !empty(first->positive_sign)
                                    || !empty(first->negative_sign)) return 5;
                            return first->int_frac_digits == 127 && first->frac_digits == 127
                                && first->p_cs_precedes == 127 && first->p_sep_by_space == 127
                                && first->n_cs_precedes == 127 && first->n_sep_by_space == 127
                                && first->p_sign_posn == 127 && first->n_sign_posn == 127 ? 0 : 6;
                        }
                        """, Set.of("localeconv", "setlocale"))
        );
    }

    private static Arguments localeCase(String name, String source, Set<String> msvcrtExports) {
        return Arguments.of(new LocaleCase(name, source, msvcrtExports));
    }

    record LocaleCase(String name, String source, Set<String> msvcrtExports) {
        @Override
        public String toString() {
            return name;
        }
    }
}
