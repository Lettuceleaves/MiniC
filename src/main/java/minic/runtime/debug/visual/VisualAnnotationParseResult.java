package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * @visual 注释解析结果。
 *
 * @param annotations 合法声明
 * @param specs 规范化的简化 @visual DSL 声明
 * @param warnings 解析警告
 */
public record VisualAnnotationParseResult(
        List<VisualAnnotation> annotations,
        List<VisualSpec> specs,
        List<String> warnings
) {
    public VisualAnnotationParseResult {
        Objects.requireNonNull(annotations, "annotations");
        Objects.requireNonNull(specs, "specs");
        Objects.requireNonNull(warnings, "warnings");
        annotations = List.copyOf(annotations);
        specs = List.copyOf(specs);
        warnings = List.copyOf(warnings);
    }

    /**
     * 创建兼容旧调用点的解析结果。
     *
     * @param annotations 合法声明
     * @param warnings 解析警告
     */
    public VisualAnnotationParseResult(List<VisualAnnotation> annotations, List<String> warnings) {
        this(annotations, List.of(), warnings);
    }
}
