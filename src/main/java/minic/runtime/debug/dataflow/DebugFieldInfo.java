package minic.runtime.debug.dataflow;

import java.util.Objects;

/**
 * 指针字段写入事件携带的结构体字段元数据。
 *
 * @param ownerStructName 字段所属结构体名称
 * @param fieldName 字段名
 * @param declaredFieldIndex 字段在结构体声明中的序号
 * @param pointerFieldIndex 字段在指针字段列表中的序号；非指针字段为 -1
 * @param fieldOffset 字段相对结构体起始地址的字节偏移
 * @param fieldType 字段类型的可读名称
 */
public record DebugFieldInfo(
        String ownerStructName,
        String fieldName,
        int declaredFieldIndex,
        int pointerFieldIndex,
        int fieldOffset,
        String fieldType
) {
    public DebugFieldInfo {
        Objects.requireNonNull(ownerStructName, "ownerStructName");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(fieldType, "fieldType");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        if (declaredFieldIndex < -1) {
            throw new IllegalArgumentException("declaredFieldIndex must not be less than -1");
        }
        if (pointerFieldIndex < -1) {
            throw new IllegalArgumentException("pointerFieldIndex must not be less than -1");
        }
        if (fieldOffset < 0) {
            throw new IllegalArgumentException("fieldOffset must not be negative");
        }
    }
}
