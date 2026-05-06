package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * 数据结构可视化投影结果。
 *
 * @param structures 结构列表
 * @param warnings 投影警告
 */
public record VisualProjection(
        List<VisualStructure> structures,
        List<String> warnings
) {
    public VisualProjection {
        Objects.requireNonNull(structures, "structures");
        Objects.requireNonNull(warnings, "warnings");
        structures = List.copyOf(structures);
        warnings = List.copyOf(warnings);
    }
}
