package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 一元运算指令。
 *
 * @param result 结果临时值
 * @param operator 一元操作符
 * @param operand 操作数
 * @param range 源码范围
 */
public record IrUnaryInstruction(
        IrTemporary result,
        IrUnaryOperator operator,
        IrValue operand,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建 IR 一元运算指令。
     *
     * @param result 结果临时值
     * @param operator 一元操作符
     * @param operand 操作数
     * @param range 源码范围
     */
    public IrUnaryInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
        Objects.requireNonNull(range, "range");
    }
}
