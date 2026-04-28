package minic.compiler.ir.instruction;

import minic.compiler.ir.model.IrLocal;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 局部变量声明指令。
 *
 * @param local 声明的局部变量槽位
 * @param range 指令对应的源码范围
 */
public record IrDeclareLocalInstruction(IrLocal local, SourceRange range) implements IrInstruction {
    /**
     * 创建 IR 局部变量声明指令。
     *
     * @param local 声明的局部变量槽位
     * @param range 指令对应的源码范围
     */
    public IrDeclareLocalInstruction {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(range, "range");
    }
}
