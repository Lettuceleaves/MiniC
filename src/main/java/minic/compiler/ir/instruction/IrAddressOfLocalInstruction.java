package minic.compiler.ir.instruction;

import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.value.IrTemporary;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 取局部变量地址指令。
 *
 * @param result 保存地址的临时值
 * @param local 被取址的局部变量
 * @param range 指令对应的源码范围
 */
public record IrAddressOfLocalInstruction(IrTemporary result, IrLocal local, SourceRange range) implements IrInstruction {
    /**
     * 创建取局部变量地址指令。
     *
     * @param result 保存地址的临时值
     * @param local 被取址的局部变量
     * @param range 指令对应的源码范围
     */
    public IrAddressOfLocalInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(range, "range");
    }
}
