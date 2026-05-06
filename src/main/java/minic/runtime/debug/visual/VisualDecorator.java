package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * 可视化装饰器插槽。
 *
 * @param id 装饰器 ID
 * @param kind 装饰器类别
 * @param targetSelector 目标选择器
 * @param attributes 展示属性
 */
public record VisualDecorator(
        String id,
        String kind,
        String targetSelector,
        Map<String, String> attributes
) {
    public VisualDecorator {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetSelector, "targetSelector");
        Objects.requireNonNull(attributes, "attributes");
        if (id.isBlank() || kind.isBlank() || targetSelector.isBlank()) {
            throw new IllegalArgumentException("decorator id, kind and targetSelector must not be blank");
        }
        attributes = Map.copyOf(attributes);
    }
}
