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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 MSVC {@code ml64.exe} 和 {@code link.exe} 的 Windows x64 工具链。
 */
public final class WindowsMsvcToolchain implements Toolchain {
    private static final List<String> DEFAULT_LIBRARIES = List.of(
            "kernel32.lib",
            "ucrt.lib",
            "legacy_stdio_definitions.lib"
    );
    private final String assemblerCommand;
    private final String linkerCommand;
    private final List<String> libraries;
    private final List<Path> libraryPaths;

    /**
     * 使用 PATH 中的 {@code ml64} 和 {@code link} 创建工具链。
     */
    public WindowsMsvcToolchain() {
        this(MsvcTools.discover());
    }

    /**
     * 使用指定命令创建工具链。
     *
     * @param assemblerCommand 汇编器命令或路径
     * @param linkerCommand 链接器命令或路径
     */
    public WindowsMsvcToolchain(String assemblerCommand, String linkerCommand) {
        this(assemblerCommand, linkerCommand, DEFAULT_LIBRARIES, configuredLibraryPaths());
    }

    /**
     * 使用指定命令和链接库创建工具链。
     *
     * @param assemblerCommand 汇编器命令或路径
     * @param linkerCommand 链接器命令或路径
     * @param libraries 额外链接库
     */
    public WindowsMsvcToolchain(String assemblerCommand, String linkerCommand, List<String> libraries) {
        this(assemblerCommand, linkerCommand, libraries, List.of());
    }

    /**
     * 使用指定命令、链接库和库搜索路径创建工具链。
     *
     * @param assemblerCommand 汇编器命令或路径
     * @param linkerCommand 链接器命令或路径
     * @param libraries 额外链接库
     * @param libraryPaths 额外库搜索路径
     */
    public WindowsMsvcToolchain(String assemblerCommand, String linkerCommand, List<String> libraries, List<Path> libraryPaths) {
        this.assemblerCommand = requireCommand(assemblerCommand, "assemblerCommand");
        this.linkerCommand = requireCommand(linkerCommand, "linkerCommand");
        Objects.requireNonNull(libraries, "libraries");
        Objects.requireNonNull(libraryPaths, "libraryPaths");
        for (String library : libraries) {
            requireCommand(library, "library");
        }
        this.libraries = List.copyOf(libraries);
        this.libraryPaths = List.copyOf(libraryPaths);
    }

    private WindowsMsvcToolchain(MsvcTools tools) {
        this(
                tools.assemblerCommand(),
                tools.linkerCommand(),
                DEFAULT_LIBRARIES,
                tools.libraryPaths()
        );
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
        } catch (IOException exception) {
            diagnostics.add(toolDiagnostic(sourceFile, "TOOL001", "创建输出目录失败：" + outputDirectory + "：" + exception.getMessage()));
            return new ToolchainResult(assemblyPath, null, null, diagnostics);
        }
        try {
            Files.writeString(assemblyPath, assemblySource.text(), StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            diagnostics.add(toolDiagnostic(sourceFile, "TOOL001", "写出汇编文件失败：" + assemblyPath + "：" + exception.getMessage()));
            return new ToolchainResult(assemblyPath, null, null, diagnostics);
        }

        if (!runCommand(
                sourceFile,
                diagnostics,
                List.of(
                        assemblerCommand,
                        "/c",
                        "/Fo",
                        objectPath.toAbsolutePath().toString(),
                        assemblyPath.toAbsolutePath().toString()
                ),
                outputDirectory,
                "汇编失败"
        )) {
            return new ToolchainResult(assemblyPath, objectPath, null, diagnostics);
        }

        ArrayList<String> linkCommand = new ArrayList<>(List.of(
                linkerCommand,
                "/ENTRY:" + assemblySource.entrySymbol(),
                "/SUBSYSTEM:CONSOLE",
                "/OUT:" + executablePath.toAbsolutePath(),
                objectPath.toAbsolutePath().toString()
        ));
        for (Path libraryPath : libraryPaths) {
            linkCommand.add("/LIBPATH:" + libraryPath.toAbsolutePath());
        }
        linkCommand.addAll(libraries);

        if (!runCommand(
                sourceFile,
                diagnostics,
                linkCommand,
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

    private static List<Path> configuredLibraryPaths() {
        ArrayList<Path> paths = new ArrayList<>();
        addConfiguredPaths(paths, System.getProperty("minic.msvc.lib.paths"));
        addConfiguredPaths(paths, System.getenv("MINIC_MSVC_LIB_PATHS"));
        return List.copyOf(paths);
    }

    private static void addConfiguredPaths(List<Path> paths, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String item : value.split(java.io.File.pathSeparator)) {
            if (!item.isBlank()) {
                paths.add(Path.of(item));
            }
        }
    }

    private record MsvcTools(String assemblerCommand, String linkerCommand, List<Path> libraryPaths) {
        private static MsvcTools discover() {
            Optional<Path> ml64 = firstExisting(msvcToolCandidates("ml64.exe"));
            Optional<Path> link = firstExisting(msvcToolCandidates("link.exe"));
            if (ml64.isEmpty() || link.isEmpty()) {
                return new MsvcTools("ml64", "link", List.of());
            }
            return new MsvcTools(
                    ml64.orElseThrow().toString(),
                    link.orElseThrow().toString(),
                    discoverLibraryPaths(ml64.orElseThrow())
            );
        }

        private static List<Path> msvcToolCandidates(String fileName) {
            ArrayList<Path> candidates = new ArrayList<>();
            addBundledMsvcToolCandidate(candidates, fileName);
            for (String edition : List.of("BuildTools", "Community", "Professional", "Enterprise")) {
                Path toolsRoot = Path.of(
                        "C:",
                        "Program Files (x86)",
                        "Microsoft Visual Studio",
                        "2022",
                        edition,
                        "VC",
                        "Tools",
                        "MSVC"
                );
                if (!Files.isDirectory(toolsRoot)) {
                    continue;
                }
                latestDirectory(toolsRoot)
                        .map(version -> version.resolve(Path.of("bin", "Hostx64", "x64", fileName)))
                        .ifPresent(candidates::add);
            }
            return candidates;
        }

        private static void addBundledMsvcToolCandidate(ArrayList<Path> candidates, String fileName) {
            String bundledRoot = configuredValue("minic.msvc.toolchain.root", "MINIC_MSVC_TOOLCHAIN_ROOT");
            if (bundledRoot == null || bundledRoot.isBlank()) {
                return;
            }
            candidates.add(Path.of(bundledRoot).resolve(Path.of("bin", "Hostx64", "x64", fileName)));
        }

        private static Optional<Path> firstExisting(List<Path> candidates) {
            return candidates.stream()
                    .filter(Files::isRegularFile)
                    .findFirst();
        }

        private static List<Path> discoverLibraryPaths(Path ml64Path) {
            ArrayList<Path> paths = new ArrayList<>();
            Path msvcRoot = ml64Path.getParent().getParent().getParent().getParent();
            Path vcLib = msvcRoot.resolve(Path.of("lib", "x64"));
            if (Files.isDirectory(vcLib)) {
                paths.add(vcLib);
            }
            Path windowsKitsLib = windowsKitsLibRoot();
            latestDirectory(windowsKitsLib).ifPresent(version -> {
                Path um = version.resolve(Path.of("um", "x64"));
                Path ucrt = version.resolve(Path.of("ucrt", "x64"));
                if (Files.isDirectory(um)) {
                    paths.add(um);
                }
                if (Files.isDirectory(ucrt)) {
                    paths.add(ucrt);
                }
            });
            return List.copyOf(paths);
        }

        private static Path windowsKitsLibRoot() {
            String bundledRoot = configuredValue("minic.windows.kits.root", "MINIC_WINDOWS_KITS_ROOT");
            if (bundledRoot != null && !bundledRoot.isBlank()) {
                return Path.of(bundledRoot).resolve("Lib");
            }
            return Path.of("C:", "Program Files (x86)", "Windows Kits", "10", "Lib");
        }

        private static String configuredValue(String propertyName, String environmentName) {
            String property = System.getProperty(propertyName);
            if (property != null && !property.isBlank()) {
                return property;
            }
            return System.getenv(environmentName);
        }

        private static Optional<Path> latestDirectory(Path root) {
            if (!Files.isDirectory(root)) {
                return Optional.empty();
            }
            try (var stream = Files.list(root)) {
                return stream
                        .filter(Files::isDirectory)
                        .max(Comparator.comparing(path -> path.getFileName().toString()));
            } catch (IOException exception) {
                return Optional.empty();
            }
        }
    }
}
