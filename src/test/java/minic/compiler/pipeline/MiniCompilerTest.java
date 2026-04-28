package minic.compiler.pipeline;

import minic.compiler.codegen.target.TargetPlatform;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCompilerTest {
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
        assertThat(result.lexResult().tokens()).isNotEmpty();
        assertThat(result.parseResultOptional()).isPresent();
        assertThat(result.programOptional()).isPresent();
        assertThat(result.semanticResultOptional()).isPresent();
        assertThat(result.irModuleOptional()).isPresent();
        assertThat(result.assemblySourceOptional()).hasValueSatisfying(assemblySource -> {
            assertThat(assemblySource.targetPlatform()).isEqualTo(TargetPlatform.WINDOWS_X86_64);
            assertThat(assemblySource.text()).contains("add PROC", "main PROC", "    call add");
        });
        assertThat(result.toolchainResult().assemblyPathOptional()).isEmpty();
        assertThat(result.toolchainResult().objectPathOptional()).isEmpty();
        assertThat(result.toolchainResult().executableArtifactOptional()).isEmpty();
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
        assertThat(result.parseResultOptional()).isEmpty();
        assertThat(result.semanticResultOptional()).isEmpty();
        assertThat(result.irModuleOptional()).isEmpty();
        assertThat(result.assemblySourceOptional()).isEmpty();
    }
}
