package minic.compiler.pipeline;

import minic.compiler.toolchain.NoOpToolchain;
import minic.compiler.toolchain.Toolchain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 编译选项。
 *
 * @param outputDirectory 输出目录
 * @param artifactName 产物基础名称
 * @param runToolchain 是否运行工具链
 * @param toolchain 工具链实现
 */
public record CompileOptions(
        Path outputDirectory,
        String artifactName,
        boolean runToolchain,
        Toolchain toolchain
) {
    /**
     * 创建编译选项。
     *
     * @param outputDirectory 输出目录
     * @param artifactName 产物基础名称
     * @param runToolchain 是否运行工具链
     * @param toolchain 工具链实现
     */
    public CompileOptions {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(artifactName, "artifactName");
        Objects.requireNonNull(toolchain, "toolchain");
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
        return new CompileOptions(Path.of("build", "minic"), "a", false, new NoOpToolchain());
    }
}
