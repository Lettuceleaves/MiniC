package minic.compiler.nativebuild;

import minic.diagnostics.Diagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 内置本机构建阶段结果。
 *
 * @param assemblyPath 汇编文件路径；尚未写出时为 {@code null}
 * @param objectPath 目标文件路径；尚未生成时为 {@code null}
 * @param executableArtifact 可执行文件产物；尚未生成时为 {@code null}
 * @param diagnostics 本机构建诊断列表
 */
public record NativeBuildResult(
        Path assemblyPath,
        Path objectPath,
        ExecutableArtifact executableArtifact,
        List<Diagnostic> diagnostics
) {
    /**
     * 创建本机构建阶段结果，并防御性复制诊断列表。
     *
     * @param assemblyPath 汇编文件路径；尚未写出时为 {@code null}
     * @param objectPath 目标文件路径；尚未生成时为 {@code null}
     * @param executableArtifact 可执行文件产物；尚未生成时为 {@code null}
     * @param diagnostics 本机构建诊断列表
     */
    public NativeBuildResult {
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 创建尚未执行本机构建的空结果。
     *
     * @return 空本机构建结果
     */
    public static NativeBuildResult notRun() {
        return new NativeBuildResult(null, null, null, List.of());
    }

    /**
     * 返回汇编文件路径。
     *
     * @return 汇编文件路径；不存在时为空
     */
    public Optional<Path> assemblyPathOptional() {
        return Optional.ofNullable(assemblyPath);
    }

    /**
     * 返回目标文件路径。
     *
     * @return 目标文件路径；不存在时为空
     */
    public Optional<Path> objectPathOptional() {
        return Optional.ofNullable(objectPath);
    }

    /**
     * 返回可执行文件产物。
     *
     * @return 可执行文件产物；不存在时为空
     */
    public Optional<ExecutableArtifact> executableArtifactOptional() {
        return Optional.ofNullable(executableArtifact);
    }
}
