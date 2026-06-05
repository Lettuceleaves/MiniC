package minic.compiler.pipeline;

import minic.compiler.toolchain.ExecutableArtifact;
import minic.compiler.toolchain.ToolchainResult;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCompilerPipelineRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void compilesRepresentativeProgramsThroughPipelineAndToolchainSmoke() throws Exception {
        Path includeRoot = tempDir.resolve("include");
        Files.createDirectories(includeRoot);
        Files.writeString(includeRoot.resolve("decls.mh"), "extern int printf(char *fmt, ...);\n");
        SourceFile source = new SourceFile("main.mc", """
                #include "decls.mh"
                int add(int a, int b) { return a + b; }
                int main() {
                    int total = 0;
                    for (int i = 0; i < 3; i++) { total += add(i, 1); }
                    printf("total=%d\\n", total);
                    return total;
                }
                """);

        CompileResult result = new MiniCompiler().compile(
                source,
                new CompileOptions(tempDir, "main", true, (input, assembly, output, artifactName) ->
                        new ToolchainResult(
                                output.resolve(artifactName + ".asm"),
                                output.resolve(artifactName + ".obj"),
                                new ExecutableArtifact(output.resolve(artifactName + ".exe")),
                                List.of()
                        ),
                        List.of(includeRoot))
        );

        assertThat(result.succeeded()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.preprocessResult().sourceFile().content()).contains("extern int printf");
        assertThat(result.lexResultOptional()).isPresent();
        assertThat(result.parseResultOptional()).isPresent();
        assertThat(result.semanticResultOptional()).isPresent();
        assertThat(result.irModuleOptional()).isPresent();
        assertThat(result.assemblySourceOptional()).hasValueSatisfying(assembly ->
                assertThat(assembly.text()).contains("minic$add PROC", "main PROC", "call printf"));
        assertThat(result.toolchainResult().executableArtifactOptional())
                .map(ExecutableArtifact::path)
                .contains(tempDir.resolve("main.exe"));
    }

    @Test
    void stopsAtTheExpectedStageForPreprocessLexParseSemanticAndLoweringDiagnostics() {
        List<Case> cases = List.of(
                new Case("preprocess", new SourceFile("bad.mc", "#include \"bad.h\"\nint main() { return 0; }"), false, false, false, false),
                new Case("lex", new SourceFile("bad.mc", "int main() { return @; }"), true, false, false, false),
                new Case("parse", new SourceFile("bad.mc", "int main( { return 0; }"), true, true, false, false),
                new Case("semantic", new SourceFile("bad.mc", "int main() { return missing; }"), true, true, true, false),
                new Case("lowering", new SourceFile("bad.mc", """
                        int f(int value) {
                            int *p = &value;
                            return value;
                        }
                        int main() { return f(1); }
                        """), true, true, true, false)
        );

        for (Case testCase : cases) {
            CompileResult result = new MiniCompiler().compile(testCase.source());
            assertThat(result.succeeded()).as(testCase.name()).isFalse();
            assertThat(result.diagnostics()).as(testCase.name()).isNotEmpty();
            assertThat(result.lexResultOptional().isPresent()).as(testCase.name() + " lex").isEqualTo(testCase.lex());
            assertThat(result.parseResultOptional().isPresent()).as(testCase.name() + " parse").isEqualTo(testCase.parse());
            assertThat(result.semanticResultOptional().isPresent()).as(testCase.name() + " semantic").isEqualTo(testCase.semantic());
            assertThat(result.irModuleOptional().isPresent()).as(testCase.name() + " ir").isEqualTo(testCase.ir());
            assertThat(result.assemblySourceOptional()).as(testCase.name() + " assembly").isEmpty();
        }
    }

    private record Case(String name, SourceFile source, boolean lex, boolean parse, boolean semantic, boolean ir) {}
}
