package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 根据结构体基地址和字段偏移计算字段地址。
 *
 * @param result 字段地址结果
 * @param baseAddress 结构体基地址
 * @param ownerStructName 字段所属结构体名
 * @param fieldName 字段名
 * @param declaredFieldIndex 字段在结构体声明中的序号
 * @param pointerFieldIndex 字段在指针字段列表中的序号；非指针字段为 -1
 * @param offset 字段相对结构体起始地址的字节偏移
 * @param fieldType 字段类型的可读名称
 * @param range 源码范围
 */
public record IrFieldAddressInstruction(
        IrTemporary result,
        IrValue baseAddress,
        String ownerStructName,
        String fieldName,
        int declaredFieldIndex,
        int pointerFieldIndex,
        int offset,
        String fieldType,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建不携带结构体元数据的字段地址计算指令。
     *
     * @param result 字段地址结果
     * @param baseAddress 结构体基地址
     * @param fieldName 字段名
     * @param offset 字段相对结构体起始地址的字节偏移
     * @param range 源码范围
     */
    public IrFieldAddressInstruction(
            IrTemporary result,
            IrValue baseAddress,
            String fieldName,
            int offset,
            SourceRange range
    ) {
        this(result, baseAddress, "", fieldName, -1, -1, offset, "", range);
    }

    /**
     * 创建字段地址计算指令。
     *
     * @param result 字段地址结果
     * @param baseAddress 结构体基地址
     * @param ownerStructName 字段所属结构体名
     * @param fieldName 字段名
     * @param declaredFieldIndex 字段在结构体声明中的序号
     * @param pointerFieldIndex 字段在指针字段列表中的序号；非指针字段为 -1
     * @param offset 字段相对结构体起始地址的字节偏移
     * @param fieldType 字段类型的可读名称
     * @param range 源码范围
     */
    public IrFieldAddressInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(baseAddress, "baseAddress");
        Objects.requireNonNull(ownerStructName, "ownerStructName");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(fieldType, "fieldType");
        Objects.requireNonNull(range, "range");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        if (declaredFieldIndex < -1) {
            throw new IllegalArgumentException("declaredFieldIndex must not be less than -1");
        }
        if (pointerFieldIndex < -1) {
            throw new IllegalArgumentException("pointerFieldIndex must not be less than -1");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
