package minic.compiler.pipeline;

import minic.compiler.codegen.target.TargetPlatform;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.toolchain.ExecutableArtifact;
import minic.compiler.toolchain.Toolchain;
import minic.compiler.toolchain.ToolchainResult;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCompilerTest {
    @TempDir
    Path tempDir;

    @Test
    void compilesSourceThroughAssemblyAndKeepsIntermediateResults() {
        SourceFile sourceFile = new SourceFile("main.mc", """
                int add(int a, int b) {
                    return a + b;
                }

                int main() {
                    int x = 1;
                    x = add(x, 2);
                    return x;
                }
                """);

        CompileResult result = new MiniCompiler().compile(sourceFile);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.preprocessResult().sourceFile()).isSameAs(sourceFile);
        assertThat(result.preprocessResult().includes()).isEmpty();
        assertThat(result.preprocessResult().macros()).isEmpty();
        assertThat(result.lexResultOptional()).hasValueSatisfying(lexResult ->
                assertThat(lexResult.tokens()).isNotEmpty());
        assertThat(result.parseResultOptional()).isPresent();
        assertThat(result.programOptional()).isPresent();
        assertThat(result.semanticResultOptional()).isPresent();
        assertThat(result.irModuleOptional()).isPresent();
        assertThat(result.assemblySourceOptional()).hasValueSatisfying(assemblySource -> {
            assertThat(assemblySource.targetPlatform()).isEqualTo(TargetPlatform.WINDOWS_X86_64);
            assertThat(assemblySource.text()).contains("minic$add PROC", "main PROC", "    call minic$add");
        });
        assertThat(result.toolchainResult().assemblyPathOptional()).isEmpty();
        assertThat(result.toolchainResult().objectPathOptional()).isEmpty();
        assertThat(result.toolchainResult().executableArtifactOptional()).isEmpty();
    }

    @Test
    void compilesIncrementAndCompoundAssignmentSyntax() {
        SourceFile sourceFile = new SourceFile("loop.mc", """
                extern int printf(char *format, int value);

                int main() {
                    int a = 0;
                    for (int i = 0; i < 100; i++) {
                        a += i;
                    }
                    printf("value = %d\\n", a);
                    return 42;
                }
                """);

        CompileResult result = new MiniCompiler().compile(sourceFile);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.assemblySourceOptional()).hasValueSatisfying(assemblySource ->
                assertThat(assemblySource.text()).contains("call printf", "main PROC"));
    }

    @Test
    void stopsBeforeIrAndAssemblyWhenSemanticDiagnosticsExist() {
        SourceFile sourceFile = new SourceFile("bad.mc", "int main() { return missing; }");

        CompileResult result = new MiniCompiler().compile(sourceFile);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("未解析变量：missing");
        assertThat(result.parseResultOptional()).isPresent();
        assertThat(result.semanticResultOptional()).isPresent();
        assertThat(result.irModuleOptional()).isEmpty();
        assertThat(result.assemblySourceOptional()).isEmpty();
    }

    @Test
    void stopsBeforeParserWhenLexDiagnosticsExist() {
        SourceFile sourceFile = new SourceFile("bad.mc", "int main() { return @; }");

        CompileResult result = new MiniCompiler().compile(sourceFile);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("LEX001");
        assertThat(result.lexResultOptional()).isPresent();
        assertThat(result.parseResultOptional()).isEmpty();
        assertThat(result.semanticResultOptional()).isEmpty();
        assertThat(result.irModuleOptional()).isEmpty();
        assertThat(result.assemblySourceOptional()).isEmpty();
    }

    @Test
    void stopsBeforeLexerWhenPreprocessDiagnosticsExist() {
        SourceFile sourceFile = new SourceFile("bad.mc", "#include \"bad.h\"\nint main() { return 0; }");
        SourceRange range = new SourceRange(sourceFile, 0, 16);
        Diagnostic diagnostic = new Diagnostic(
                "PRE001",
                DiagnosticSeverity.ERROR,
                "预编译失败",
                range
        );
        MiniCompiler compiler = new MiniCompiler(
                new minic.compiler.codegen.windows.WindowsX64AssemblyEmitter(),
                source -> new minic.compiler.preprocess.PreprocessResult(source, List.of(diagnostic), List.of(), List.of())
        );

        CompileResult result = compiler.compile(sourceFile);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.diagnostics()).containsExactly(diagnostic);
        assertThat(result.lexResultOptional()).isEmpty();
        assertThat(result.parseResultOptional()).isEmpty();
        assertThat(result.semanticResultOptional()).isEmpty();
        assertThat(result.irModuleOptional()).isEmpty();
        assertThat(result.assemblySourceOptional()).isEmpty();
    }

    @Test
    void reportsUntrustedInputDiagnosticsWithoutReachingIr() {
        SourceFile sourceFile = new SourceFile(
                "untrusted.mc",
                "int main() { return 2147483648; }"
        );

        CompileResult result = new MiniCompiler().compile(sourceFile);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("整数字面量超出范围");
        assertThat(result.parseResultOptional()).isEmpty();
        assertThat(result.semanticResultOptional()).isEmpty();
        assertThat(result.irModuleOptional()).isEmpty();
        assertThat(result.assemblySourceOptional()).isEmpty();
    }

    @Test
    void stopsBeforeIrWhenInputExceedsCurrentLoweringLimits() {
        SourceFile sourceFile = new SourceFile(
                "lowering-limit.mc",
                """
                        int addressParameter(int value) {
                            int *pointer = &value;
                            return value;
                        }

                        int main() {
                            return addressParameter(1);
                        }
                        """
        );

        CompileResult result = new MiniCompiler().compile(sourceFile);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.message())
                .containsExactly("暂不支持对参数取址：value");
        assertThat(result.semanticResultOptional()).isPresent();
        assertThat(result.irModuleOptional()).isEmpty();
        assertThat(result.assemblySourceOptional()).isEmpty();
    }

    @Test
    void canRunConfiguredToolchainAndKeepExecutableArtifact() {
        SourceFile sourceFile = new SourceFile("main.mc", "int main() { return 1; }");
        Toolchain toolchain = (source, assembly, outputDirectory, artifactName) -> new ToolchainResult(
                outputDirectory.resolve(artifactName + ".asm"),
                outputDirectory.resolve(artifactName + ".obj"),
                new ExecutableArtifact(outputDirectory.resolve(artifactName + ".exe")),
                List.of()
        );

        CompileResult result = new MiniCompiler().compile(
                sourceFile,
                new CompileOptions(tempDir, "main", true, toolchain)
        );

        assertThat(result.succeeded()).isTrue();
        assertThat(result.assemblySourceOptional()).map(AssemblySource::entrySymbol).contains("minic$entry");
        assertThat(result.toolchainResult().assemblyPathOptional()).contains(tempDir.resolve("main.asm"));
        assertThat(result.toolchainResult().objectPathOptional()).contains(tempDir.resolve("main.obj"));
        assertThat(result.toolchainResult().executableArtifactOptional())
                .map(ExecutableArtifact::path)
                .contains(tempDir.resolve("main.exe"));
    }

    @Test
    void canCompileAndRunExecutableArtifact() throws Exception {
        SourceFile sourceFile = new SourceFile("main.mc", "int main() { return 7; }");
        Path executablePath = tempDir.resolve("main.cmd");
        Toolchain toolchain = (source, assembly, outputDirectory, artifactName) -> {
            try {
                java.nio.file.Files.writeString(executablePath, """
                        @echo off
                        echo hello
                        echo warning 1>&2
                        exit /b 7
                        """);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
            return new ToolchainResult(
                    outputDirectory.resolve(artifactName + ".asm"),
                    outputDirectory.resolve(artifactName + ".obj"),
                    new ExecutableArtifact(executablePath),
                    List.of()
            );
        };

        CompileResult result = new MiniCompiler().compile(
                sourceFile,
                new CompileOptions(tempDir, "main", true, true, toolchain)
        );

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.executionResult().stdout()).contains("hello");
        assertThat(result.executionResult().stderr()).contains("warning");
        assertThat(result.executionResult().exitCodeOptional()).hasValue(7);
    }
}
