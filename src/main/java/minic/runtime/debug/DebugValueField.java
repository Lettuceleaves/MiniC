package minic.runtime.debug;

import java.util.Objects;

/**
 * Debug 结构体字段。
 *
 * @param name 字段名
 * @param value 字段值
 */
public record DebugValueField(String name, DebugValue value) {
    /**
     * 创建结构体字段。
     */
    public DebugValueField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
