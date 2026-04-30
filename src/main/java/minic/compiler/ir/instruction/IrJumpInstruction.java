package minic.compiler.ir.instruction;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 无条件跳转 IR 指令。
 *
 * @param targetLabel 目标基本块标签
 * @param range 源码范围
 */
public record IrJumpInstruction(String targetLabel, SourceRange range) implements IrInstruction {
    /**
     * 创建无条件跳转指令。
     *
     * @param targetLabel 目标基本块标签
     * @param range 源码范围
     */
    public IrJumpInstruction {
        Objects.requireNonNull(targetLabel, "targetLabel");
        Objects.requireNonNull(range, "range");
        if (targetLabel.isBlank()) {
            throw new IllegalArgumentException("targetLabel must not be blank");
        }
    }
}
