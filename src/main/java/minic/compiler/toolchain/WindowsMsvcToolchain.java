package minic.compiler.toolchain;

import minic.compiler.codegen.AssemblySource;
import minic.diagnostics.Diagnostic;
import minic.diagnostics.DiagnosticSeverity;
import minic.source.SourceFile;
import minic.source.SourceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 MSVC {@code ml64.exe} 和 {@code link.exe} 的 Windows x64 工具链。
 */
public final class WindowsMsvcToolchain implements Toolchain {
    private final String assemblerCommand;
    private final String linkerCommand;

    /**
     * 使用 PATH 中的 {@code ml64} 和 {@code link} 创建工具链。
     */
    public WindowsMsvcToolchain() {
        this("ml64", "link");
    }

    /**
     * 使用指定命令创建工具链。
     *
     * @param assemblerCommand 汇编器命令或路径
     * @param linkerCommand 链接器命令或路径
     */
    public WindowsMsvcToolchain(String assemblerCommand, String linkerCommand) {
        this.assemblerCommand = requireCommand(assemblerCommand, "assemblerCommand");
        this.linkerCommand = requireCommand(linkerCommand, "linkerCommand");
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

        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        Path assemblyPath = outputDirectory.resolve(artifactName + ".asm");
        Path objectPath = outputDirectory.resolve(artifactName + ".obj");
        Path executablePath = outputDirectory.resolve(artifactName + ".exe");
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(assemblyPath, assemblySource.text(), StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            diagnostics.add(toolDiagnostic(sourceFile, "TOOL001", "写出汇编文件失败：" + exception.getMessage()));
            return new ToolchainResult(assemblyPath, null, null, diagnostics);
        }

        if (!runCommand(
                sourceFile,
                diagnostics,
                List.of(assemblerCommand, "/c", "/Fo:" + objectPath, assemblyPath.toString()),
                outputDirectory,
                "汇编失败"
        )) {
            return new ToolchainResult(assemblyPath, objectPath, null, diagnostics);
        }

        if (!runCommand(
                sourceFile,
                diagnostics,
                List.of(
                        linkerCommand,
                        "/ENTRY:" + assemblySource.entrySymbol(),
                        "/SUBSYSTEM:CONSOLE",
                        "/OUT:" + executablePath,
                        objectPath.toString()
                ),
                outputDirectory,
                "链接失败"
        )) {
            return new ToolchainResult(assemblyPath, objectPath, null, diagnostics);
        }

        return new ToolchainResult(assemblyPath, objectPath, new ExecutableArtifact(executablePath), diagnostics);
    }

    private boolean runCommand(
            SourceFile sourceFile,
            List<Diagnostic> diagnostics,
            List<String> command,
            Path workingDirectory,
            String failurePrefix
    ) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                diagnostics.add(toolDiagnostic(sourceFile, "TOOL001", failurePrefix + "：" + command.get(0)
                        + " exit " + exitCode + formatOutput(output)));
                return false;
            }
            return true;
        } catch (IOException exception) {
            diagnostics.add(toolDiagnostic(sourceFile, "TOOL001", failurePrefix + "：" + command.get(0)
                    + " 不可用：" + exception.getMessage()));
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            diagnostics.add(toolDiagnostic(sourceFile, "TOOL001", failurePrefix + "：" + command.get(0) + " 被中断"));
            return false;
        }
    }

    private String formatOutput(String output) {
        if (output.isBlank()) {
            return "";
        }
        return "，输出：" + output;
    }

    private Diagnostic toolDiagnostic(SourceFile sourceFile, String code, String message) {
        return new Diagnostic(
                code,
                DiagnosticSeverity.ERROR,
                message,
                new SourceRange(sourceFile, 0, 0)
        );
    }

    private String requireCommand(String command, String name) {
        Objects.requireNonNull(command, name);
        if (command.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return command;
    }
}
