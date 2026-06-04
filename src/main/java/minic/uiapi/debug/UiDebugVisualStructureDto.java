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
        String layoutHint,
        String summary,
        String explanation,
        List<UiDebugVisualElementDto> elements
) {
    public UiDebugVisualStructureDto(
            String id,
            String name,
            String type,
            String kind,
            String summary,
            List<UiDebugVisualElementDto> elements
    ) {
        this(id, name, type, kind, "", summary, "", elements);
    }

    public UiDebugVisualStructureDto(
            String id,
            String name,
            String type,
            String kind,
            String summary,
            String explanation,
            List<UiDebugVisualElementDto> elements
    ) {
        this(id, name, type, kind, "", summary, explanation, elements);
    }

    public UiDebugVisualStructureDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(layoutHint, "layoutHint");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(elements, "elements");
        elements = List.copyOf(elements);
    }
}
