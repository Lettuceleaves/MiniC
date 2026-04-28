package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * IR 函数调用指令。
 *
 * @param result 保存调用返回值的临时值
 * @param calleeName 被调用函数名称
 * @param arguments 实参值列表
 * @param range 指令对应的源码范围
 */
public record IrCallInstruction(
        IrTemporary result,
        String calleeName,
        List<IrValue> arguments,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建 IR 函数调用指令，并防御性复制实参列表。
     *
     * @param result 保存调用返回值的临时值
     * @param calleeName 被调用函数名称
     * @param arguments 实参值列表
     * @param range 指令对应的源码范围
     */
    public IrCallInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(calleeName, "calleeName");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(range, "range");
        if (calleeName.isBlank()) {
            throw new IllegalArgumentException("calleeName must not be blank");
        }
        arguments = List.copyOf(arguments);
    }
}
