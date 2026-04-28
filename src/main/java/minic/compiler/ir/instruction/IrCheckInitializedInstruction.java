package minic.compiler.ir.instruction;

import minic.compiler.ir.model.IrLocal;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 未初始化读取运行时检查指令。
 *
 * @param local 被读取前需要检查的局部变量槽位
 * @param range 指令对应的源码范围
 */
public record IrCheckInitializedInstruction(IrLocal local, SourceRange range) implements IrInstruction {
    /**
     * 创建 IR 未初始化读取运行时检查指令。
     *
     * @param local 被读取前需要检查的局部变量槽位
     * @param range 指令对应的源码范围
     */
    public IrCheckInitializedInstruction {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(range, "range");
    }
}
