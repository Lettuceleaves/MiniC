package minic.compiler.toolchain;

import minic.compiler.coff.CoffObjectFile;
import minic.compiler.coff.CoffObjectWriter;
import minic.compiler.codegen.AssemblySource;
import minic.compiler.codegen.machine.MachineModule;
import minic.compiler.codegen.windows.WindowsX64MachineAssembler;
import minic.compiler.pe.PeImage;
import minic.compiler.pe.WindowsPeLinker;
import minic.compiler.runtime.MiniCWindowsRuntime;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * 完全由 MiniC Java 代码实现的 Windows x64 汇编、COFF 和 PE 工具链。
 */
public final class WindowsNativeToolchain implements Toolchain {
    private static final Map<String, String> SYSTEM_IMPORTS = Map.of(
            "ExitProcess", "KERNEL32.dll",
            "GetStdHandle", "KERNEL32.dll",
            "WriteFile", "KERNEL32.dll"
    );

    private final WindowsX64MachineAssembler assembler;
    private final CoffObjectWriter objectWriter;
    private final WindowsPeLinker linker;
    private final MiniCWindowsRuntime runtime;

    public WindowsNativeToolchain() {
        this(new WindowsX64MachineAssembler(), new CoffObjectWriter(), new WindowsPeLinker(), new MiniCWindowsRuntime());
    }

    WindowsNativeToolchain(
            WindowsX64MachineAssembler assembler,
            CoffObjectWriter objectWriter,
            WindowsPeLinker linker,
            MiniCWindowsRuntime runtime
    ) {
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.objectWriter = Objects.requireNonNull(objectWriter, "objectWriter");
        this.linker = Objects.requireNonNull(linker, "linker");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public ToolchainResult buildExecutable(
            SourceFile sourceFile,
            AssemblySource assemblySource,
            Path outputDirectory,
            String artifactName
    ) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(assemblySource, "assemblySource");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("artifactName must not be blank");
        }
        Path assemblyPath = outputDirectory.resolve(artifactName + ".asm");
        Path objectPath = outputDirectory.resolve(artifactName + ".obj");
        Path executablePath = outputDirectory.resolve(artifactName + ".exe");
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(assemblyPath, assemblySource.text(), StandardCharsets.US_ASCII);
            MachineModule module = runtime.attach(assembler.assemble(assemblySource));
            CoffObjectFile objectFile = objectWriter.write(module);
            Files.write(objectPath, objectFile.bytes());
            PeImage image = linker.link(objectFile, module.entrySymbol(), SYSTEM_IMPORTS);
            Files.write(executablePath, image.bytes());
            return new ToolchainResult(
                    assemblyPath,
                    objectPath,
                    new ExecutableArtifact(executablePath),
                    diagnostics
            );
        } catch (IOException exception) {
            diagnostics.add(diagnostic(sourceFile, "TOOL001", "写出本机二进制产物失败：" + exception.getMessage()));
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            diagnostics.add(diagnostic(sourceFile, "TOOL002", "内置 Windows x64 工具链失败：" + exception.getMessage()));
        }
        return new ToolchainResult(assemblyPath, objectPath, null, diagnostics);
    }

    private static Diagnostic diagnostic(SourceFile sourceFile, String code, String message) {
        return new Diagnostic(
                code,
                DiagnosticSeverity.ERROR,
                message,
                new SourceRange(sourceFile, 0, 0)
        );
    }
}
