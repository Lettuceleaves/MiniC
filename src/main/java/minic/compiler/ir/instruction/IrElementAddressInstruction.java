package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 计算 int 元素地址。
 *
 * @param result 保存元素地址的临时值
 * @param baseAddress 数组或指针基地址
 * @param index 元素下标值
 * @param range 指令对应的源码范围
 */
public record IrElementAddressInstruction(
        IrTemporary result,
        IrValue baseAddress,
        IrValue index,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建元素地址计算指令。
     *
     * @param result 保存元素地址的临时值
     * @param baseAddress 数组或指针基地址
     * @param index 元素下标值
     * @param range 指令对应的源码范围
     */
    public IrElementAddressInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(baseAddress, "baseAddress");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(range, "range");
    }
}
