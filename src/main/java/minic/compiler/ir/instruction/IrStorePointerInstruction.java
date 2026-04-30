package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 向指针地址写入 int 值。
 *
 * @param address 指针地址值
 * @param value 要写入的值
 * @param range 指令对应的源码范围
 */
public record IrStorePointerInstruction(IrValue address, IrValue value, SourceRange range) implements IrInstruction {
    /**
     * 创建向指针写入指令。
     *
     * @param address 指针地址值
     * @param value 要写入的值
     * @param range 指令对应的源码范围
     */
    public IrStorePointerInstruction {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }
}
