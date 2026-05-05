package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 条件选择指令。
 *
 * @param result 结果临时值
 * @param condition 条件值
 * @param thenValue 条件为真时的值
 * @param elseValue 条件为假时的值
 * @param range 源码范围
 */
public record IrSelectInstruction(
        IrTemporary result,
        IrValue condition,
        IrValue thenValue,
        IrValue elseValue,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建 IR 条件选择指令。
     *
     * @param result 结果临时值
     * @param condition 条件值
     * @param thenValue 条件为真时的值
     * @param elseValue 条件为假时的值
     * @param range 源码范围
     */
    public IrSelectInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(thenValue, "thenValue");
        Objects.requireNonNull(elseValue, "elseValue");
        Objects.requireNonNull(range, "range");
    }
}
