package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * 数组/表格 cell。
 *
 * @param id cell ID
 * @param row 行下标
 * @param column 列下标
 * @param linearIndex 线性下标
 * @param label 展示标签
 * @param valueRef 运行时值引用
 * @param metadata 点击元数据
 */
public record ArrayCell(
        String id,
        int row,
        int column,
        int linearIndex,
        String label,
        String valueRef,
        Map<String, String> metadata
) {
    public ArrayCell {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(valueRef, "valueRef");
        Objects.requireNonNull(metadata, "metadata");
        if (id.isBlank()) {
            throw new IllegalArgumentException("cell id must not be blank");
        }
        if (row < 0 || column < 0 || linearIndex < 0) {
            throw new IllegalArgumentException("cell indexes must not be negative");
        }
        metadata = Map.copyOf(metadata);
    }
}
