package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * @visual 注释解析结果。
 *
 * @param annotations 合法声明
 * @param warnings 解析警告
 */
public record VisualAnnotationParseResult(
        List<VisualAnnotation> annotations,
        List<String> warnings
) {
    public VisualAnnotationParseResult {
        Objects.requireNonNull(annotations, "annotations");
        Objects.requireNonNull(warnings, "warnings");
        annotations = List.copyOf(annotations);
        warnings = List.copyOf(warnings);
    }
}
