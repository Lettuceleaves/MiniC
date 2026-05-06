package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * @visual 注释声明。
 *
 * @param directive 指令名
 * @param structureType 结构类型
 * @param name 逻辑结构名
 * @param line 源码行
 * @param attributes 属性
 */
public record VisualAnnotation(
        String directive,
        String structureType,
        String name,
        int line,
        Map<String, String> attributes
) {
    public VisualAnnotation {
        Objects.requireNonNull(directive, "directive");
        Objects.requireNonNull(structureType, "structureType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(attributes, "attributes");
        if (directive.isBlank() || structureType.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("directive, structureType and name must not be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
        attributes = Map.copyOf(attributes);
    }
}
