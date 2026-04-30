package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 条件跳转 IR 指令。
 *
 * @param condition 条件值，非 0 为真
 * @param thenLabel 条件为真时跳转的基本块标签
 * @param elseLabel 条件为假时跳转的基本块标签
 * @param range 源码范围
 */
public record IrBranchInstruction(IrValue condition, String thenLabel, String elseLabel, SourceRange range)
        implements IrInstruction {
    /**
     * 创建条件跳转指令。
     *
     * @param condition 条件值
     * @param thenLabel then 目标标签
     * @param elseLabel else 目标标签
     * @param range 源码范围
     */
    public IrBranchInstruction {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(thenLabel, "thenLabel");
        Objects.requireNonNull(elseLabel, "elseLabel");
        Objects.requireNonNull(range, "range");
        if (thenLabel.isBlank() || elseLabel.isBlank()) {
            throw new IllegalArgumentException("branch labels must not be blank");
        }
    }
}
