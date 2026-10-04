package craken.compiler.preprocess;

import craken.compiler.Stage;
import craken.compiler.SourceFile;
import craken.SourceRange;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一次预编译阶段的结果。
 *
 * @param sourceFile 预编译后的源码
 * @param includes include 摘要
 * @param macros 宏摘要
 * @param sourceMap 预编译产物 offset 到原始源码 offset 的映射；与 {@code sourceFile.content()} 等长
 */
public record PreprocessResult(
        SourceFile sourceFile,
        List<IncludeSummary> includes,
        List<MacroSummary> macros,
        int[] sourceMap
) implements Stage.Context {
    /**
     * 创建预编译结果。
     *
     * @param sourceFile 预编译后的源码
     * @param includes include 摘要
     * @param macros 宏摘要
     * @param sourceMap 预编译产物 offset 到原始源码 offset 的映射
     */
    public PreprocessResult {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(includes, "includes");
        Objects.requireNonNull(macros, "macros");
        Objects.requireNonNull(sourceMap, "sourceMap");
        if (sourceMap.length != sourceFile.content().length()) {
            throw new IllegalArgumentException("sourceMap length must match preprocessed source length");
        }
        includes = List.copyOf(includes);
        macros = List.copyOf(macros);
        sourceMap = sourceMap.clone();
    }

    @Override
    public int[] sourceMap() {
        return sourceMap.clone();
    }

    /**
     * 一条 include 指令的处理摘要。
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
        public IncludeSummary {
            Objects.requireNonNull(requestedPath, "requestedPath");
            Objects.requireNonNull(sourceRange, "sourceRange");
            if (requestedPath.isBlank()) {
                throw new IllegalArgumentException("requestedPath must not be blank");
            }
        }

        public Optional<Path> resolvedPathOptional() {
            return Optional.ofNullable(resolvedPath);
        }
    }

    /**
     * 一条对象宏定义状态变化摘要。
     *
     * @param name 宏名称
     * @param replacement 宏替换文本
     * @param sourceRange 宏定义来源范围
     * @param defined 是否处于已定义状态
     */
    public record MacroSummary(
            String name,
            String replacement,
            SourceRange sourceRange,
            boolean defined
    ) {
        public MacroSummary {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(replacement, "replacement");
            Objects.requireNonNull(sourceRange, "sourceRange");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }
}
