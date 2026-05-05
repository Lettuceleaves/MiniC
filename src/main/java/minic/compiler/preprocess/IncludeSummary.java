package minic.compiler.preprocess;

import minic.source.SourceRange;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * 预编译阶段记录的一条 include 摘要。
 *
 * @param requestedPath 源码中请求的 include 路径
 * @param resolvedPath 实际解析到的路径；尚未解析时为空
 * @param sourceRange include 指令在来源文件中的范围
 * @param expanded 是否已展开
 */
public record IncludeSummary(
        String requestedPath,
        Path resolvedPath,
        SourceRange sourceRange,
        boolean expanded
) {
    /**
     * 创建 include 摘要。
     *
     * @param requestedPath 源码中请求的 include 路径
     * @param resolvedPath 实际解析到的路径；尚未解析时为空
     * @param sourceRange include 指令在来源文件中的范围
     * @param expanded 是否已展开
     */
    public IncludeSummary {
        Objects.requireNonNull(requestedPath, "requestedPath");
        Objects.requireNonNull(sourceRange, "sourceRange");
        if (requestedPath.isBlank()) {
            throw new IllegalArgumentException("requestedPath must not be blank");
        }
    }

    /**
     * 返回解析后的 include 路径。
     *
     * @return 路径；不存在时为空
     */
    public Optional<Path> resolvedPathOptional() {
        return Optional.ofNullable(resolvedPath);
    }
}
