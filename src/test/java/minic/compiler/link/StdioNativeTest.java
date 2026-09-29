package minic.compiler.link;

import minic.compiler.SourceFile;
import minic.compiler.execute.ExecutableRunner;
import minic.session.CompileObservationSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class StdioNativeTest {
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(3);

    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{0}")
    @MethodSource("handleFreeCases")
    void runsEachHandleFreeStdioFunctionWithExactImports(NativeCase testCase) {
        CompiledProgram program = compile(testCase.name(), testCase.source());

        assertImports(program, testCase.msvcrtExports());
        var execution = new ExecutableRunner(PROCESS_TIMEOUT).run(
                program.sourceFile(),
                program.artifact(),
                testCase.standardInput()
        );
        assertTrue(execution.diagnostics().isEmpty(), execution.diagnostics()::toString);
        assertEquals(testCase.exitCode(), execution.exitCode(), testCase.name());
        assertEquals(testCase.stdout(), execution.stdout(), testCase.name());
        assertEquals("", execution.stderr(), testCase.name());
    }

    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @Test
    void removeReportsSuccessAndEnoentAndDeletesTheFile() throws IOException {
        CompiledProgram program = compile("remove", """
                #include "stdio.mh"
                #include "errno.mh"
                int main(void) {
                    if (remove("stdio-remove.tmp") != 0) return 1;
                    errno = 0;
                    if (remove("stdio-missing.tmp") != EOF) return 2;
                    return errno == 2 ? 0 : 3;
                }
                """);
        Path directory = program.artifact().path().getParent();
        Path existing = directory.resolve("stdio-remove.tmp");
        Path missing = directory.resolve("stdio-missing.tmp");
        Files.deleteIfExists(existing);
        Files.deleteIfExists(missing);
        Files.writeString(existing, "payload");
        try {
            assertImports(program, Set.of("_errno", "remove"));
            var execution = new ExecutableRunner(PROCESS_TIMEOUT).run(program.sourceFile(), program.artifact());
            assertTrue(execution.diagnostics().isEmpty(), execution.diagnostics()::toString);
            assertEquals(0, execution.exitCode(), execution::stderr);
            assertFalse(Files.exists(existing));
        } finally {
            Files.deleteIfExists(existing);
            Files.deleteIfExists(missing);
        }
    }

    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @Test
    void renameReportsSuccessEnoentAndExistingTargetWithoutReplacingIt() throws IOException {
        CompiledProgram program = compile("rename", """
                #include "stdio.mh"
                #include "errno.mh"
                int main(void) {
                    if (rename("stdio-old.tmp", "stdio-new.tmp") != 0) return 1;
                    errno = 0;
                    if (rename("stdio-missing.tmp", "stdio-unused.tmp") != EOF || errno != 2) return 2;
                    errno = 0;
                    if (rename("stdio-second.tmp", "stdio-target.tmp") != EOF || errno != 17) return 3;
                    return 0;
                }
                """);
        Path directory = program.artifact().path().getParent();
        Path oldFile = directory.resolve("stdio-old.tmp");
        Path newFile = directory.resolve("stdio-new.tmp");
        Path secondFile = directory.resolve("stdio-second.tmp");
        Path targetFile = directory.resolve("stdio-target.tmp");
        Files.deleteIfExists(oldFile);
        Files.deleteIfExists(newFile);
        Files.deleteIfExists(secondFile);
        Files.deleteIfExists(targetFile);
        Files.writeString(oldFile, "renamed");
        Files.writeString(secondFile, "source");
        Files.writeString(targetFile, "target");
        try {
            assertImports(program, Set.of("_errno", "rename"));
            var execution = new ExecutableRunner(PROCESS_TIMEOUT).run(program.sourceFile(), program.artifact());
            assertTrue(execution.diagnostics().isEmpty(), execution.diagnostics()::toString);
            assertEquals(0, execution.exitCode(), execution::stderr);
            assertFalse(Files.exists(oldFile));
            assertEquals("renamed", Files.readString(newFile));
            assertEquals("source", Files.readString(secondFile));
            assertEquals("target", Files.readString(targetFile));
        } finally {
            Files.deleteIfExists(oldFile);
            Files.deleteIfExists(newFile);
            Files.deleteIfExists(secondFile);
            Files.deleteIfExists(targetFile);
        }
    }

    static Stream<Arguments> handleFreeCases() {
        return Stream.of(
                nativeCase("getchar", """
                        #include "stdio.mh"
                        int main(void) {
                            int character = getchar();
                            int end = getchar();
                            return character == 'Q' && end == EOF ? 0 : 1;
                        }
                        """, Set.of("getchar"), "Q", "", 0),
                nativeCase("putchar", """
                        #include "stdio.mh"
                        int main(void) { return putchar('A') == 'A' ? 0 : 1; }
                        """, Set.of("putchar"), "", "A", 0),
                nativeCase("puts", """
                        #include "stdio.mh"
                        int main(void) { return puts("line") >= 0 ? 0 : 1; }
                        """, Set.of("puts"), "", "line\r\n", 0),
                nativeCase("sprintf", """
                        #include "stdio.mh"
                        int main(void) {
                            char buffer[32];
                            int written = sprintf(buffer, "%s:%d:%.1f", "value", 7, 2.5);
                            return written == 11 && buffer[0] == 'v' && buffer[5] == ':'
                                && buffer[7] == ':' && buffer[10] == '5' && buffer[11] == 0 ? 0 : 1;
                        }
                        """, Set.of("sprintf"), "", "", 0),
                nativeCase("sscanf", """
                        #include "stdio.mh"
                        int main(void) {
                            int integer = 0;
                            double real = 0.0;
                            char word[16];
                            int assigned = sscanf("42 3.5 ok", "%d %lf %15s", &integer, &real, word);
                            return assigned == 3 && integer == 42 && real == 3.5
                                && word[0] == 'o' && word[1] == 'k' && word[2] == 0 ? 0 : 1;
                        }
                        """, Set.of("sscanf"), "", "", 0)
        );
    }

    private static Arguments nativeCase(
            String name,
            String source,
            Set<String> msvcrtExports,
            String standardInput,
            String stdout,
            int exitCode
    ) {
        return Arguments.of(new NativeCase(name, source, msvcrtExports, standardInput, stdout, exitCode));
    }

    private static CompiledProgram compile(String name, String source) {
        SourceFile sourceFile = new SourceFile("stdlib-native-stdio-" + name + ".mc", source);
        CompileObservationSession session = CompileObservationSession.fromSource(sourceFile);
        session.compilerApi().runThrough(session.linker());

        assertTrue(session.linker().succeeded(), () -> name + ": pre="
                + session.preprocessor().diagnostics() + ", lex=" + session.lexer().diagnostics()
                + ", parse=" + session.parser().diagnostics() + ", semantic="
                + session.semanticAnalyzer().diagnostics() + ", obj=" + session.objBuilder().diagnostics()
                + ", link=" + session.linker().diagnostics());
        return new CompiledProgram(
                sourceFile,
                session.linker().result().executableArtifactOptional().orElseThrow(),
                PeImportIntegrationTest.readImports(session.linker().peImage().orElseThrow().bytes())
        );
    }

    private static void assertImports(CompiledProgram program, Set<String> msvcrtExports) {
        assertEquals(msvcrtExports, program.imports().get("msvcrt.dll"));
        assertEquals(Set.of("ExitProcess"), program.imports().get("KERNEL32.dll"));
        assertEquals(Set.of("msvcrt.dll", "KERNEL32.dll"), program.imports().keySet());
    }

    record NativeCase(
            String name,
            String source,
            Set<String> msvcrtExports,
            String standardInput,
            String stdout,
            int exitCode
    ) {
        @Override
        public String toString() {
            return name;
        }
    }

    record CompiledProgram(
            SourceFile sourceFile,
            minic.compiler.link.ExecutableArtifact artifact,
            Map<String, Set<String>> imports
    ) {
    }
}
