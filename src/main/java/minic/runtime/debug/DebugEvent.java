package minic.runtime.debug;

import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Debugger 事件日志项。
 *
 * @param eventId 事件 ID
 * @param snapshotId 关联快照 ID
 * @param type 事件类型
 * @param title 标题
 * @param description 解释文本
 * @param sourceRange 关联源码范围；没有时为 {@code null}
 * @param affectedValueRefs 受影响值引用
 */
public record DebugEvent(
        long eventId,
        long snapshotId,
        String type,
        String title,
        String description,
        SourceRange sourceRange,
        List<String> affectedValueRefs
) {
    /**
     * 创建 Debug 事件。
     */
    public DebugEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(affectedValueRefs, "affectedValueRefs");
        if (eventId < 0) {
            throw new IllegalArgumentException("eventId must not be negative");
        }
        if (snapshotId < 0) {
            throw new IllegalArgumentException("snapshotId must not be negative");
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        affectedValueRefs = List.copyOf(affectedValueRefs);
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
