package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * 数据结构 visual card/tab DTO。
 */
public record UiDebugVisualStructureDto(
        String id,
        String name,
        String type,
        String kind,
        String summary,
        List<UiDebugVisualElementDto> elements
) {
    public UiDebugVisualStructureDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(elements, "elements");
        elements = List.copyOf(elements);
    }
}
