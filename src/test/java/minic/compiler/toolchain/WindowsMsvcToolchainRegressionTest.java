package minic.compiler.toolchain;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsMsvcToolchainRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsToolchainCommandsAndReportsUnavailableTools() {
        SourceFile source = new SourceFile("main.mc", "int main() { return 0; }");
        AssemblySource assembly = new AssemblySource(TargetPlatform.WINDOWS_X86_64, "minic$entry", "END\n");
        WindowsMsvcToolchain toolchain = new WindowsMsvcToolchain("definitely-missing-ml64", "definitely-missing-link", List.of("kernel32.lib"));

        ToolchainResult result = toolchain.buildExecutable(source, assembly, tempDir, "main");

        assertThat(result.assemblyPathOptional()).contains(tempDir.resolve("main.asm"));
        assertThat(result.objectPathOptional()).contains(tempDir.resolve("main.obj"));
        assertThat(result.executableArtifactOptional()).isEmpty();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("TOOL001");
    }
}
