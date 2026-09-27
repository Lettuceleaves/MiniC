package minic.compiler.toolchain;

import minic.compiler.pipeline.MiniCompiler;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsNativeToolchainRegressionTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsAssemblyCoffAndPeWithoutExternalCommands() throws Exception {
        SourceFile source = new SourceFile("main.mc", "int main() { return 7; }");
        var assembly = new MiniCompiler().compile(source).assemblySourceOptional().orElseThrow();

        ToolchainResult result = new WindowsNativeToolchain().buildExecutable(source, assembly, tempDir, "main");

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.assemblyPathOptional()).contains(tempDir.resolve("main.asm"));
        assertThat(result.objectPathOptional()).contains(tempDir.resolve("main.obj"));
        assertThat(result.executableArtifactOptional()).isPresent();
        byte[] object = Files.readAllBytes(tempDir.resolve("main.obj"));
        byte[] executable = Files.readAllBytes(tempDir.resolve("main.exe"));
        assertThat(Short.toUnsignedInt(ByteBuffer.wrap(object).order(ByteOrder.LITTLE_ENDIAN).getShort())).isEqualTo(0x8664);
        assertThat(executable).startsWith((byte) 'M', (byte) 'Z');
        assertThat(new String(executable, java.nio.charset.StandardCharsets.US_ASCII))
                .contains("ExitProcess", "KERNEL32.dll");
    }
}
