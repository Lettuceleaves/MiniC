package minic.compiler.toolchain;

import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.target.TargetPlatform;
import minic.source.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsMsvcToolchainTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultConstructorCanBeCreatedOutsideDeveloperPrompt() {
        WindowsMsvcToolchain toolchain = new WindowsMsvcToolchain();

        assertThat(toolchain).isNotNull();
    }

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

    @Test
    void passesRuntimeLibrariesToLinker() throws Exception {
        SourceFile sourceFile = new SourceFile("main.mc", "int main() { return 1; }");
        AssemblySource assemblySource = new AssemblySource(
                TargetPlatform.WINDOWS_X86_64,
                "minic$entry",
                "PUBLIC minic$entry\n.code\nminic$entry PROC\n    ret\nminic$entry ENDP\nEND\n"
        );
        Path assemblerCommand = tempDir.resolve("fake-ml64.cmd");
        Path linkerCommand = tempDir.resolve("fake-link.cmd");
        Path linkArgsPath = tempDir.resolve("link-args.txt");
        Files.writeString(assemblerCommand, """
                @echo off
                exit /b 0
                """);
        Files.writeString(linkerCommand, """
                @echo off
                echo %%* > "%s"
                exit /b 0
                """.formatted(linkArgsPath.toString()));
        WindowsMsvcToolchain toolchain = new WindowsMsvcToolchain(
                assemblerCommand.toString(),
                linkerCommand.toString(),
                List.of("kernel32.lib", "ucrt.lib", "legacy_stdio_definitions.lib")
        );

        ToolchainResult result = toolchain.buildExecutable(sourceFile, assemblySource, tempDir, "main");

        assertThat(result.diagnostics()).isEmpty();
        assertThat(Files.readString(linkArgsPath)).contains(
                "/ENTRY:minic$entry",
                "/SUBSYSTEM:CONSOLE",
                "main.obj",
                "kernel32.lib",
                "ucrt.lib",
                "legacy_stdio_definitions.lib"
        );
    }

    @Test
    void usesConfiguredLibraryPathsWithExplicitToolCommands() throws Exception {
        SourceFile sourceFile = new SourceFile("main.mc", "int main() { return 1; }");
        AssemblySource assemblySource = new AssemblySource(
                TargetPlatform.WINDOWS_X86_64,
                "minic$entry",
                "PUBLIC minic$entry\n.code\nminic$entry PROC\n    ret\nminic$entry ENDP\nEND\n"
        );
        Path assemblerCommand = tempDir.resolve("fake-ml64.cmd");
        Path linkerCommand = tempDir.resolve("fake-link.cmd");
        Path linkArgsPath = tempDir.resolve("link-args-configured.txt");
        Path vcLib = tempDir.resolve("vc-lib");
        Path sdkLib = tempDir.resolve("sdk-lib");
        Files.writeString(assemblerCommand, """
                @echo off
                exit /b 0
                """);
        Files.writeString(linkerCommand, """
                @echo off
                echo %%* > "%s"
                exit /b 0
                """.formatted(linkArgsPath.toString()));
        String previous = System.getProperty("minic.msvc.lib.paths");
        try {
            System.setProperty("minic.msvc.lib.paths", vcLib + java.io.File.pathSeparator + sdkLib);
            WindowsMsvcToolchain toolchain = new WindowsMsvcToolchain(
                    assemblerCommand.toString(),
                    linkerCommand.toString()
            );

            ToolchainResult result = toolchain.buildExecutable(sourceFile, assemblySource, tempDir, "main");

            assertThat(result.diagnostics()).isEmpty();
            assertThat(Files.readString(linkArgsPath)).contains(
                    "/LIBPATH:" + vcLib.toAbsolutePath(),
                    "/LIBPATH:" + sdkLib.toAbsolutePath()
            );
        } finally {
            if (previous == null) {
                System.clearProperty("minic.msvc.lib.paths");
            } else {
                System.setProperty("minic.msvc.lib.paths", previous);
            }
        }
    }

    @Test
    void defaultDiscoveryUsesBundledToolchainRoots() throws Exception {
        Path msvcRoot = tempDir.resolve("toolchain").resolve("msvc");
        Path msvcBin = msvcRoot.resolve(Path.of("bin", "Hostx64", "x64"));
        Path vcLib = msvcRoot.resolve(Path.of("lib", "x64"));
        Path kitsRoot = tempDir.resolve("toolchain").resolve("windows-kits");
        Path sdkUmLib = kitsRoot.resolve(Path.of("Lib", "10.0.26100.0", "um", "x64"));
        Path sdkUcrtLib = kitsRoot.resolve(Path.of("Lib", "10.0.26100.0", "ucrt", "x64"));
        Files.createDirectories(msvcBin);
        Files.createDirectories(vcLib);
        Files.createDirectories(sdkUmLib);
        Files.createDirectories(sdkUcrtLib);
        Path ml64 = msvcBin.resolve("ml64.exe");
        Path link = msvcBin.resolve("link.exe");
        Files.writeString(ml64, "");
        Files.writeString(link, "");
        String previousMsvcRoot = System.getProperty("minic.msvc.toolchain.root");
        String previousKitsRoot = System.getProperty("minic.windows.kits.root");
        try {
            System.setProperty("minic.msvc.toolchain.root", msvcRoot.toString());
            System.setProperty("minic.windows.kits.root", kitsRoot.toString());

            WindowsMsvcToolchain toolchain = new WindowsMsvcToolchain();

            assertThat(field(toolchain, "assemblerCommand")).isEqualTo(ml64.toString());
            assertThat(field(toolchain, "linkerCommand")).isEqualTo(link.toString());
            assertThat((List<Path>) field(toolchain, "libraryPaths"))
                    .containsExactly(vcLib, sdkUmLib, sdkUcrtLib);
        } finally {
            restoreProperty("minic.msvc.toolchain.root", previousMsvcRoot);
            restoreProperty("minic.windows.kits.root", previousKitsRoot);
        }
    }

    private Object field(WindowsMsvcToolchain toolchain, String name) throws Exception {
        Field field = WindowsMsvcToolchain.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(toolchain);
    }

    private void restoreProperty(String name, String previous) {
        if (previous == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previous);
        }
    }
}
