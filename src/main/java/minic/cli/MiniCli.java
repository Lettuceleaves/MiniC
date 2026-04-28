package minic.cli;

import minic.compiler.pipeline.CompileOptions;
import minic.compiler.pipeline.CompileResult;
import minic.compiler.pipeline.MiniCompiler;
import minic.compiler.toolchain.WindowsMsvcToolchain;
import minic.diagnostics.Diagnostic;
import minic.source.SourceFile;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MiniC 命令行接口。
 */
public final class MiniCli {
    private final PrintStream out;
    private final PrintStream err;

    /**
     * 创建命令行接口。
     *
     * @param out 标准输出
     * @param err 标准错误
     */
    public MiniCli(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    /**
     * 执行命令行。
     *
     * @param args 命令行参数
     * @return 进程退出码
     */
    public int run(String[] args) {
        try {
            CliOptions options = CliOptions.parse(args);
            if (options.help()) {
                printUsage(out);
                return 0;
            }
            if (!"compile".equals(options.command())) {
                printUsage(err);
                return 2;
            }
            return compile(options);
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            printUsage(err);
            return 2;
        }
    }

    private int compile(CliOptions options) {
        SourceFile sourceFile;
        try {
            sourceFile = new SourceFile(options.sourcePath().toString(), Files.readString(options.sourcePath()));
        } catch (IOException exception) {
            err.println("读取源码失败：" + exception.getMessage());
            return 2;
        }

        String artifactName = artifactName(options.sourcePath());
        CompileOptions compileOptions = new CompileOptions(
                options.outputDirectory(),
                artifactName,
                true,
                new WindowsMsvcToolchain(options.assemblerCommand(), options.linkerCommand())
        );
        CompileResult result = new MiniCompiler().compile(sourceFile, compileOptions);
        printRequestedStages(result, options.showStages());
        if (options.emitAssembly() || result.toolchainResult().assemblyPathOptional().isPresent()) {
            result.toolchainResult().assemblyPathOptional()
                    .ifPresent(path -> out.println("assembly=" + path));
        }
        result.toolchainResult().objectPathOptional()
                .ifPresent(path -> out.println("object=" + path));
        result.toolchainResult().executableArtifactOptional()
                .ifPresent(artifact -> out.println("executable=" + artifact.path()));

        if (!result.diagnostics().isEmpty()) {
            printDiagnostics(result.diagnostics());
            return 1;
        }
        return 0;
    }

    private void printRequestedStages(CompileResult result, Set<String> showStages) {
        if (showStages.contains("tokens")) {
            out.println("tokens=" + result.lexResult().tokens());
        }
        if (showStages.contains("ast")) {
            result.programOptional().ifPresent(program -> out.println("ast=" + program));
        }
        if (showStages.contains("ir")) {
            result.irModuleOptional().ifPresent(irModule -> out.println("ir=" + irModule));
        }
        if (showStages.contains("assembly")) {
            result.assemblySourceOptional().ifPresent(assembly -> out.println(assembly.text()));
        }
        if (showStages.contains("diagnostics")) {
            printDiagnostics(result.diagnostics());
        }
    }

    private void printDiagnostics(List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            err.println(diagnostic.code() + " " + diagnostic.severity() + " " + diagnostic.message());
        }
    }

    private String artifactName(Path sourcePath) {
        String fileName = sourcePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private void printUsage(PrintStream stream) {
        stream.println("用法：minic compile <source.mc> [--out-dir <dir>] [--emit-asm] [--show tokens,ast,ir,assembly,diagnostics]");
    }

    private record CliOptions(
            String command,
            Path sourcePath,
            Path outputDirectory,
            boolean emitAssembly,
            boolean help,
            String assemblerCommand,
            String linkerCommand,
            Set<String> showStages
    ) {
        private static CliOptions parse(String[] args) {
            if (args.length == 0 || contains(args, "--help") || contains(args, "-h")) {
                return new CliOptions("help", null, Path.of("build", "minic"), false, true, "ml64", "link", Set.of());
            }
            String command = args[0];
            Path sourcePath = null;
            Path outputDirectory = Path.of("build", "minic");
            boolean emitAssembly = false;
            String assemblerCommand = "ml64";
            String linkerCommand = "link";
            LinkedHashSet<String> showStages = new LinkedHashSet<>();
            int index = 1;
            while (index < args.length) {
                String arg = args[index];
                switch (arg) {
                    case "--out-dir" -> {
                        index++;
                        if (index >= args.length) {
                            throw new IllegalArgumentException("--out-dir 需要目录参数");
                        }
                        outputDirectory = Path.of(args[index]);
                    }
                    case "--emit-asm" -> emitAssembly = true;
                    case "--ml64" -> {
                        index++;
                        if (index >= args.length) {
                            throw new IllegalArgumentException("--ml64 需要命令或路径参数");
                        }
                        assemblerCommand = args[index];
                    }
                    case "--link" -> {
                        index++;
                        if (index >= args.length) {
                            throw new IllegalArgumentException("--link 需要命令或路径参数");
                        }
                        linkerCommand = args[index];
                    }
                    case "--show" -> {
                        index++;
                        if (index >= args.length) {
                            throw new IllegalArgumentException("--show 需要阶段列表");
                        }
                        addStages(showStages, args[index]);
                    }
                    default -> {
                        if (sourcePath == null) {
                            sourcePath = Path.of(arg);
                        } else {
                            throw new IllegalArgumentException("未知参数：" + arg);
                        }
                    }
                }
                index++;
            }
            if ("compile".equals(command) && sourcePath == null) {
                throw new IllegalArgumentException("compile 需要源码文件路径");
            }
            return new CliOptions(
                    command,
                    sourcePath,
                    outputDirectory,
                    emitAssembly,
                    false,
                    assemblerCommand,
                    linkerCommand,
                    Set.copyOf(showStages)
            );
        }

        private static boolean contains(String[] args, String expected) {
            for (String arg : args) {
                if (expected.equals(arg)) {
                    return true;
                }
            }
            return false;
        }

        private static void addStages(Set<String> stages, String value) {
            for (String stage : value.split(",")) {
                String trimmed = stage.trim();
                if (!trimmed.isEmpty()) {
                    stages.add(trimmed);
                }
            }
        }
    }
}
