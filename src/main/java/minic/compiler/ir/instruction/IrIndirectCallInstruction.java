package minic.compiler.ir.instruction;

import minic.compiler.ir.value.IrTemporary;
import minic.compiler.ir.value.IrValue;
import minic.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * IR 间接函数调用指令。
 *
 * @param result 保存调用返回值的临时值
 * @param calleeAddress 被调用函数地址
 * @param arguments 实参值列表
 * @param range 指令对应的源码范围
 */
public record IrIndirectCallInstruction(
        IrTemporary result,
        IrValue calleeAddress,
        List<IrValue> arguments,
        SourceRange range
) implements IrInstruction {
    /**
     * 创建 IR 间接函数调用指令，并防御性复制实参列表。
     *
     * @param result 保存调用返回值的临时值
     * @param calleeAddress 被调用函数地址
     * @param arguments 实参值列表
     * @param range 指令对应的源码范围
     */
    public IrIndirectCallInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(calleeAddress, "calleeAddress");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(range, "range");
        arguments = List.copyOf(arguments);
    }
}
