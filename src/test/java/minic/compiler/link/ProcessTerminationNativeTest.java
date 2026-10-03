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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-native")
final class ProcessTerminationNativeTest {
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{0}")
    @MethodSource("terminationCases")
    void executesEachTerminationFunctionInATimeLimitedChildProcess(TerminationCase testCase) {
        SourceFile sourceFile = new SourceFile("stdlib-native-" + testCase.name() + ".mc", testCase.source());
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
        assertEquals(testCase.kernelExports(), imports.get("KERNEL32.dll"), testCase.name());
        assertEquals(Set.of("msvcrt.dll", "KERNEL32.dll"), imports.keySet(), testCase.name());

        var artifact = session.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
        var executionStage = new ExecutableRunner(Duration.ofSeconds(3));
        var execution = executionStage.run(sourceFile, artifact);
        assertTrue(executionStage.errors().isEmpty(), () -> testCase.name() + ": " + executionStage.errors());
        assertEquals(testCase.stdout(), execution.stdout(), testCase.name());
        if (!testCase.abnormal()) {
            assertEquals("", execution.stderr(), testCase.name());
        }
        assertEquals(testCase.exitCode(), execution.exitCode(), testCase.name());
        if (testCase.abnormal()) {
            assertNotEquals(0, execution.exitCode(), "abort must terminate abnormally");
        }
    }

    static Stream<Arguments> terminationCases() {
        return Stream.of(
                terminationCase(
                        "exit-flush",
                        "exit-buffer",
                        "exit(23);",
                        Set.of("exit", "printf"),
                        Set.of("ExitProcess"),
                        23,
                        "exit-buffer",
                        false
                ),
                terminationCase(
                        "immediate-exit-no-flush",
                        "immediate-buffer",
                        "_Exit(24);",
                        Set.of("printf"),
                        Set.of("ExitProcess", "TerminateProcess"),
                        24,
                        "",
                        false
                ),
                terminationCase(
                        "abort-abnormal",
                        "",
                        "minic_set_process_error_mode(3U); abort();",
                        Set.of("abort"),
                        Set.of("ExitProcess", "SetErrorMode"),
                        3,
                        "",
                        true
                )
        );
    }

    private static Arguments terminationCase(
            String name,
            String bufferedText,
            String terminationStatement,
            Set<String> msvcrtExports,
            Set<String> kernelExports,
            int exitCode,
            String stdout,
            boolean abnormal
    ) {
        String outputStatement = bufferedText.isEmpty()
                ? ""
                : "    printf(\"" + bufferedText + "\");\n";
        String source = "#include \"stdlib.mh\"\n"
                + "extern int printf(char *format, ...);\n"
                + "extern unsigned int minic_set_process_error_mode(unsigned int mode);\n"
                + "int main() {\n"
                + outputStatement
                + "    " + terminationStatement + "\n"
                + "    return 99;\n"
                + "}\n";
        return Arguments.of(new TerminationCase(
                name,
                source,
                msvcrtExports,
                kernelExports,
                exitCode,
                stdout,
                abnormal
        ));
    }

    record TerminationCase(
            String name,
            String source,
            Set<String> msvcrtExports,
            Set<String> kernelExports,
            int exitCode,
            String stdout,
            boolean abnormal
    ) {
        @Override
        public String toString() {
            return name;
        }
    }
}
