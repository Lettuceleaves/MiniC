package minic.compiler.pipeline;

import minic.compiler.toolchain.NoOpToolchain;
import minic.compiler.toolchain.Toolchain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 编译选项。
 *
 * @param outputDirectory 输出目录
 * @param artifactName 产物基础名称
 * @param runToolchain 是否运行工具链
 * @param runExecutable 是否在工具链成功后运行可执行文件
 * @param toolchain 工具链实现
 * @param includeRoots 显式 include 根目录
 */
public record CompileOptions(
        Path outputDirectory,
        String artifactName,
        boolean runToolchain,
        boolean runExecutable,
        Toolchain toolchain,
        List<Path> includeRoots
) {
    /**
     * 创建编译选项。
     *
     * @param outputDirectory 输出目录
     * @param artifactName 产物基础名称
     * @param runToolchain 是否运行工具链
     * @param runExecutable 是否在工具链成功后运行可执行文件
     * @param toolchain 工具链实现
     * @param includeRoots 显式 include 根目录
     */
    public CompileOptions {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(artifactName, "artifactName");
        Objects.requireNonNull(toolchain, "toolchain");
        Objects.requireNonNull(includeRoots, "includeRoots");
        includeRoots = List.copyOf(includeRoots);
        if (artifactName.isBlank()) {
            throw new IllegalArgumentException("artifactName must not be blank");
        }
    }

    /**
     * 创建只生成内存汇编、不运行工具链的默认选项。
     *
     * @return 默认编译选项
     */
    public static CompileOptions assemblyOnly() {
        return new CompileOptions(Path.of("build", "minic"), "a", false, false, new NoOpToolchain(), List.of());
    }

    /**
     * 创建不运行可执行文件的编译选项。
     *
     * @param outputDirectory 输出目录
     * @param artifactName 产物基础名称
     * @param runToolchain 是否运行工具链
     * @param toolchain 工具链实现
     */
    public CompileOptions(Path outputDirectory, String artifactName, boolean runToolchain, Toolchain toolchain) {
        this(outputDirectory, artifactName, runToolchain, false, toolchain, List.of());
    }

    /**
     * 创建不运行可执行文件的编译选项。
     *
     * @param outputDirectory 输出目录
     * @param artifactName 产物基础名称
     * @param runToolchain 是否运行工具链
     * @param toolchain 工具链实现
     * @param includeRoots 显式 include 根目录
     */
    public CompileOptions(
            Path outputDirectory,
            String artifactName,
            boolean runToolchain,
            Toolchain toolchain,
            List<Path> includeRoots
    ) {
        this(outputDirectory, artifactName, runToolchain, false, toolchain, includeRoots);
    }

    /**
     * 创建无显式 include 根目录的编译选项。
     *
     * @param outputDirectory 输出目录
     * @param artifactName 产物基础名称
     * @param runToolchain 是否运行工具链
     * @param runExecutable 是否在工具链成功后运行可执行文件
     * @param toolchain 工具链实现
     */
    public CompileOptions(
            Path outputDirectory,
            String artifactName,
            boolean runToolchain,
            boolean runExecutable,
            Toolchain toolchain
    ) {
        this(outputDirectory, artifactName, runToolchain, runExecutable, toolchain, List.of());
    }
}
