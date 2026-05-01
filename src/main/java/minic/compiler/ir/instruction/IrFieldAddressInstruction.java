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
 * @param fieldName 字段名
 * @param offset 字段相对结构体起始地址的字节偏移
 * @param range 源码范围
 */
public record IrFieldAddressInstruction(
        IrTemporary result,
        IrValue baseAddress,
        String fieldName,
        int offset,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建字段地址计算指令。
     *
     * @param result 字段地址结果
     * @param baseAddress 结构体基地址
     * @param fieldName 字段名
     * @param offset 字段相对结构体起始地址的字节偏移
     * @param range 源码范围
     */
    public IrFieldAddressInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(baseAddress, "baseAddress");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(range, "range");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
