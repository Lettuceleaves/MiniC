package minic.compiler.ir.instruction;

import minic.compiler.ir.model.IrLocal;
import minic.compiler.ir.value.IrTemporary;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 局部变量读取指令。
 *
 * @param result 保存读取值的临时值
 * @param local 被读取的局部变量槽位
 * @param range 指令对应的源码范围
 */
public record IrLoadLocalInstruction(IrTemporary result, IrLocal local, SourceRange range) implements IrInstruction {
    /**
     * 创建 IR 局部变量读取指令。
     *
     * @param result 保存读取值的临时值
     * @param local 被读取的局部变量槽位
     * @param range 指令对应的源码范围
     */
    public IrLoadLocalInstruction {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(range, "range");
    }
}
