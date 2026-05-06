package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * 可视化校验器插槽。
 *
 * @param id 校验器 ID
 * @param kind 校验器类别
 * @param explanation 校验说明
 * @param warnings 当前警告
 */
public record VisualValidator(
        String id,
        String kind,
        String explanation,
        List<String> warnings
) {
    public VisualValidator {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(warnings, "warnings");
        if (id.isBlank() || kind.isBlank()) {
            throw new IllegalArgumentException("validator id and kind must not be blank");
        }
        warnings = List.copyOf(warnings);
    }
}
