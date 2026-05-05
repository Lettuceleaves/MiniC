package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 将一个 IR 值复制到临时值槽位，供多分支表达式在汇合块读取结果。
 *
 * @param result 目标临时值
 * @param value 源值
 * @param range 源码范围
 */
public record IrMoveInstruction(IrTemporary result, IrValue value, SourceRange range) implements IrInstruction {
    /**
     * 创建复制指令。
     *
     * @param result 目标临时值
     * @param value 源值
     * @param range 源码范围
     */
    public IrMoveInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }
}
