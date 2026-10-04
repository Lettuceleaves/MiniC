package craken.compiler.obj.machine;

import java.util.List;
import java.util.Objects;

/**
 * 机器模块中的逻辑 section。
 *
 * @param name section 名称
 * @param kind section 类型
 * @param alignment section 对齐
 * @param items section 内容
 */
public record MachineSection(
        String name,
        MachineSectionKind kind,
        int alignment,
        List<MachineItem> items
) {
    public MachineSection {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(items, "items");
        if (name.isBlank()) {
            throw new IllegalArgumentException("section name must not be blank");
        }
        if (alignment <= 0 || Integer.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("alignment must be a positive power of two");
        }
        items = List.copyOf(items);
    }
}
