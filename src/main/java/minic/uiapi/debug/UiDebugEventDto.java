package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * UI Debug 事件 DTO。
 */
public record UiDebugEventDto(
        long eventId,
        long snapshotId,
        String type,
        String title,
        String description,
        UiSourceSpanDto sourceRange,
        List<String> affectedValueRefs
) {
    public UiDebugEventDto {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(affectedValueRefs, "affectedValueRefs");
        affectedValueRefs = List.copyOf(affectedValueRefs);
    }
}
