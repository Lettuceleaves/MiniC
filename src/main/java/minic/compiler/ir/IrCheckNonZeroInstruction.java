package minic.compiler.ir;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * IR 除零运行时检查指令。
 *
 * @param value 除法右操作数
 * @param range 指令对应的源码范围
 */
public record IrCheckNonZeroInstruction(IrValue value, SourceRange range) implements IrInstruction {
    /**
     * 创建 IR 除零运行时检查指令。
     *
     * @param value 除法右操作数
     * @param range 指令对应的源码范围
     */
    public IrCheckNonZeroInstruction {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(range, "range");
    }
}
