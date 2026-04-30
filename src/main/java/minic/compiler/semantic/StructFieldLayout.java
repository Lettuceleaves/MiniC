package minic.compiler.semantic;

import minic.compiler.type.MiniType;

import java.util.Objects;

/**
 * 结构体字段布局。
 *
 * @param name 字段名
 * @param type 字段类型
 * @param offset 字段相对结构体起始地址的字节偏移
 * @param size 字段占用字节数
 * @param alignment 字段对齐字节数
 */
public record StructFieldLayout(String name, MiniType type, int offset, int size, int alignment) {
    /**
     * 创建结构体字段布局。
     *
     * @param name 字段名
     * @param type 字段类型
     * @param offset 字段相对结构体起始地址的字节偏移
     * @param size 字段占用字节数
     * @param alignment 字段对齐字节数
     */
    public StructFieldLayout {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (alignment <= 0) {
            throw new IllegalArgumentException("alignment must be positive");
        }
    }
}
