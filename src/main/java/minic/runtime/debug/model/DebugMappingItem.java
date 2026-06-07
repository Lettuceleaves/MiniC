package minic.runtime.debug;

import minic.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * Debug Source/AST/IR/ASM 映射项。
 *
 * @param id 稳定 debug id
 * @param kind 项类型
 * @param label 展示标签
 * @param sourceRange 源码范围；没有时为 {@code null}
 * @param detail 说明
 */
public record DebugMappingItem(
        String id,
        String kind,
        String label,
        SourceRange sourceRange,
        String detail
) {
    /**
     * 创建映射项。
     */
    public DebugMappingItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(detail, "detail");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }

    /**
     * 返回源码范围。
     *
     * @return 源码范围 Optional
     */
    public Optional<SourceRange> sourceRangeOptional() {
        return Optional.ofNullable(sourceRange);
    }
}
