package minic.compiler.link;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class StringC23NativeTest {
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{0}")
    @MethodSource("stringCases")
    void runsC23StringFunctionsAndImportsOnlyReachableDependencies(StringCase testCase) {
        SourceFile sourceFile = new SourceFile("stdlib-native-string-c23-" + testCase.name() + ".mc", testCase.source());
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> testCase.name() + ": pre="
                + session.preprocessor().diagnostics() + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics() + ", semantic="
                + session.semanticAnalyzer().diagnostics() + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        Map<String, Set<String>> imports = PeImportIntegrationTest.readImports(
                session.linker().peImage().orElseThrow().bytes()
        );
        if (testCase.msvcrtExports().isEmpty()) {
            assertFalse(imports.containsKey("msvcrt.dll"), testCase.name());
            assertEquals(Set.of("KERNEL32.dll"), imports.keySet(), testCase.name());
        } else {
            assertEquals(testCase.msvcrtExports(), imports.get("msvcrt.dll"), testCase.name());
            assertEquals(Set.of("msvcrt.dll", "KERNEL32.dll"), imports.keySet(), testCase.name());
        }
        assertEquals(Set.of("ExitProcess"), imports.get("KERNEL32.dll"), testCase.name());

        var artifact = session.linker().result().executableArtifactOptional().orElseThrow();
        var execution = new ExecutableRunner(Duration.ofSeconds(3)).run(sourceFile, artifact);
        assertTrue(execution.diagnostics().isEmpty(), execution.diagnostics()::toString);
        assertEquals(0, execution.exitCode(), () -> testCase.name() + ": " + execution.stderr());
        assertEquals("", execution.stdout(), testCase.name());
        assertEquals("", execution.stderr(), testCase.name());
    }

    static Stream<Arguments> stringCases() {
        return Stream.of(
                stringCase("memccpy", """
                        #include "string.mh"
                        int main(void) {
                            char source[5];
                            source[0] = 'a'; source[1] = 'b'; source[2] = 'c';
                            source[3] = 'd'; source[4] = 0;
                            char matched[5];
                            for (int i = 0; i < 5; i = i + 1) matched[i] = 'x';
                            void *after = memccpy(matched, source, 'c', 4ULL);
                            if (after != &matched[3] || matched[0] != 'a' || matched[1] != 'b'
                                    || matched[2] != 'c' || matched[3] != 'x') return 1;
                            char missed[5];
                            for (int i = 0; i < 5; i = i + 1) missed[i] = 'x';
                            if (memccpy(missed, source, 'z', 4ULL) != NULL) return 2;
                            if (missed[0] != 'a' || missed[1] != 'b'
                                    || missed[2] != 'c' || missed[3] != 'd') return 3;
                            return memccpy(missed, source, 'a', 0ULL) == NULL ? 0 : 4;
                        }
                        """, Set.of("_memccpy")),
                stringCase("strdup", """
                        #include "string.mh"
                        #include "stdlib.mh"
                        int main(void) {
                            char original[4];
                            original[0] = 'a'; original[1] = 'b'; original[2] = 'c'; original[3] = 0;
                            char *copy = strdup(original);
                            if (copy == NULL || copy == original) return 1;
                            original[0] = 'z';
                            if (copy[0] != 'a' || copy[1] != 'b' || copy[2] != 'c' || copy[3] != 0) return 2;
                            copy[1] = 'y';
                            if (original[1] != 'b') return 3;
                            free(copy);
                            return 0;
                        }
                        """, Set.of("_strdup", "free")),
                stringCase("strndup", """
                        #include "string.mh"
                        #include "stdlib.mh"
                        int main(void) {
                            char source[7];
                            source[0] = 'a'; source[1] = 'b'; source[2] = 'c'; source[3] = 'd';
                            source[4] = 'e'; source[5] = 'f'; source[6] = 0;
                            char *truncated = strndup(source, 3ULL);
                            char *complete = strndup("xy", 8ULL);
                            char *empty = strndup("ignored", 0ULL);
                            if (truncated == NULL || complete == NULL || empty == NULL) return 1;
                            if (truncated == source || truncated[0] != 'a' || truncated[1] != 'b'
                                    || truncated[2] != 'c' || truncated[3] != 0) return 2;
                            if (complete[0] != 'x' || complete[1] != 'y' || complete[2] != 0) return 3;
                            if (empty[0] != 0) return 4;
                            source[0] = 'z';
                            if (truncated[0] != 'a') return 5;
                            free(truncated); free(complete); free(empty);
                            return 0;
                        }
                        """, Set.of("free", "malloc", "memcpy")),
                stringCase("memset-explicit", """
                        #include "string.mh"
                        int main(void) {
                            char secret[5];
                            secret[0] = 'a'; secret[1] = 'b'; secret[2] = 'c';
                            secret[3] = 'd'; secret[4] = 0;
                            void *result = memset_explicit(secret, 0, 3ULL);
                            if (result != secret || secret[0] != 0 || secret[1] != 0
                                    || secret[2] != 0 || secret[3] != 'd') return 1;
                            result = memset_explicit(secret, 'x', 0ULL);
                            return result == secret && secret[3] == 'd' ? 0 : 2;
                        }
                        """, Set.of("memset")),
                stringCase("unused-adapters", """
                        #include "string.mh"
                        int main(void) { return 0; }
                        """, Set.of()),
                stringCase("unrelated-string-call", """
                        #include "string.mh"
                        int main(void) { return strlen("abc") == 3ULL ? 0 : 1; }
                        """, Set.of("strlen"))
        );
    }

    private static Arguments stringCase(String name, String source, Set<String> msvcrtExports) {
        return Arguments.of(new StringCase(name, source, msvcrtExports));
    }

    record StringCase(String name, String source, Set<String> msvcrtExports) {
        @Override
        public String toString() {
            return name;
        }
    }
}
