package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 标量类型转换指令。
 *
 * @param result 保存转换结果
 * @param value 输入值
 * @param range 指令对应的源码范围
 */
public record IrCastInstruction(IrTemporary result, IrValue value, SourceRange range) implements IrInstruction {
    public IrCastInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }
}
