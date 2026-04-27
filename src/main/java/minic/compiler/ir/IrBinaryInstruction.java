package minic.compiler.ir;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 二元算术指令。
 *
 * @param result 保存结果的临时值
 * @param operator 二元操作符
 * @param left 左操作数
 * @param right 右操作数
 * @param range 指令对应的源码范围
 */
public record IrBinaryInstruction(
        IrTemporary result,
        IrBinaryOperator operator,
        IrValue left,
        IrValue right,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建 IR 二元算术指令。
     *
     * @param result 保存结果的临时值
     * @param operator 二元操作符
     * @param left 左操作数
     * @param right 右操作数
     * @param range 指令对应的源码范围
     */
    public IrBinaryInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(range, "range");
    }
}
