package minic.uiapi;

import java.util.Map;
import java.util.Objects;

/**
 * 数据结构视图元素 DTO。
 */
public record UiDebugVisualElementDto(
        String id,
        String kind,
        String label,
        Map<String, String> metadata
) {
    public UiDebugVisualElementDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(metadata, "metadata");
        metadata = Map.copyOf(metadata);
    }
}
