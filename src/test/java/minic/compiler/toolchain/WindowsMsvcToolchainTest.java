package minic.compiler.toolchain;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsMsvcToolchainTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAssemblyAndReportsDiagnosticWhenAssemblerIsUnavailable() throws Exception {
        SourceFile sourceFile = new SourceFile("main.mc", "int main() { return 1; }");
        AssemblySource assemblySource = new AssemblySource(
                TargetPlatform.WINDOWS_X86_64,
                "main",
                "PUBLIC main\n.code\nmain PROC\n    mov eax, 1\n    ret\nmain ENDP\nEND\n"
        );
        WindowsMsvcToolchain toolchain = new WindowsMsvcToolchain("missing-ml64-for-test", "missing-link-for-test");

        ToolchainResult result = toolchain.buildExecutable(sourceFile, assemblySource, tempDir, "main");

        Path assemblyPath = tempDir.resolve("main.asm");
        assertThat(result.assemblyPathOptional()).contains(assemblyPath);
        assertThat(Files.readString(assemblyPath)).contains("PUBLIC main", "mov eax, 1");
        assertThat(result.objectPathOptional()).contains(tempDir.resolve("main.obj"));
        assertThat(result.executableArtifactOptional()).isEmpty();
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .containsExactly("TOOL001");
    }
}
