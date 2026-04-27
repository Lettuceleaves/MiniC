package minic.compiler.ir;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR return 指令。
 *
 * @param value 返回值
 * @param range 指令对应的源码范围
 */
public record IrReturnInstruction(IrValue value, SourceRange range) implements IrInstruction {
    /**
     * 创建 IR return 指令。
     *
     * @param value 返回值
     * @param range 指令对应的源码范围
     */
    public IrReturnInstruction {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }
}
