package minic.compiler.ir.lowering;

import java.util.Objects;

/**
 * IR lowering 单步动作。
 *
 * @param kind 动作类型
 * @param subject 动作对象摘要
 */
public record IrLoweringAction(IrLoweringActionKind kind, String subject) {
    /**
     * 创建 IR lowering 动作。
     *
     * @param kind 动作类型
     * @param subject 动作对象摘要
     */
    public IrLoweringAction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
    }
}
