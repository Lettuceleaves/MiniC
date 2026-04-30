package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 从指针地址读取 int 值。
 *
 * @param result 保存读取结果的临时值
 * @param address 指针地址值
 * @param range 指令对应的源码范围
 */
public record IrLoadPointerInstruction(IrTemporary result, IrValue address, SourceRange range) implements IrInstruction {
    /**
     * 创建从指针读取指令。
     *
     * @param result 保存读取结果的临时值
     * @param address 指针地址值
     * @param range 指令对应的源码范围
     */
    public IrLoadPointerInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(range, "range");
    }
}
