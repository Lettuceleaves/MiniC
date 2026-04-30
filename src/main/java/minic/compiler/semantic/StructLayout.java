package minic.compiler.semantic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 结构体布局。
 *
 * @param name 结构体名
 * @param size 结构体占用字节数，包含尾部补齐
 * @param alignment 结构体对齐字节数
 * @param fields 字段布局列表
 */
public record StructLayout(String name, int size, int alignment, List<StructFieldLayout> fields) {
    /**
     * 创建结构体布局，并防御性复制字段布局。
     *
     * @param name 结构体名
     * @param size 结构体占用字节数，包含尾部补齐
     * @param alignment 结构体对齐字节数
     * @param fields 字段布局列表
     */
    public StructLayout {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fields, "fields");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (alignment <= 0) {
            throw new IllegalArgumentException("alignment must be positive");
        }
        fields = List.copyOf(fields);
    }

    /**
     * 按字段名查询字段布局。
     *
     * @param fieldName 字段名
     * @return 字段布局；不存在时为空
     */
    public Optional<StructFieldLayout> field(String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        return fields.stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst();
    }
}
