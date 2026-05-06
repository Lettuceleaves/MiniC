package minic.runtime.debug;

import java.util.Objects;

/**
 * Debug 数组元素。
 *
 * @param index 元素下标
 * @param value 元素值
 */
public record DebugValueElement(long index, DebugValue value) {
    /**
     * 创建数组元素。
     */
    public DebugValueElement {
        Objects.requireNonNull(value, "value");
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
    }
}
