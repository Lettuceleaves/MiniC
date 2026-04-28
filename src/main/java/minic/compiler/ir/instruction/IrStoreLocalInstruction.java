package minic.compiler.ir.instruction;

import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 局部变量写入指令。
 *
 * @param local 被写入的局部变量槽位
 * @param value 写入值
 * @param range 指令对应的源码范围
 */
public record IrStoreLocalInstruction(IrLocal local, IrValue value, SourceRange range) implements IrInstruction {
    /**
     * 创建 IR 局部变量写入指令。
     *
     * @param local 被写入的局部变量槽位
     * @param value 写入值
     * @param range 指令对应的源码范围
     */
    public IrStoreLocalInstruction {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }
}
