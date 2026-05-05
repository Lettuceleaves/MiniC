package minic.compiler.ir.lowering;

import java.util.Objects;

/**
 * IR lowering 单步动作。
 *
 * @param kind 动作类型
 * @param subject 动作对象摘要
 * @param astNode 当前动作对应 AST 节点；没有时为 {@code null}
 */
public record IrLoweringAction(IrLoweringActionKind kind, String subject, Object astNode) {
    /**
     * 创建 IR lowering 动作。
     *
     * @param kind 动作类型
     * @param subject 动作对象摘要
     * @param astNode 当前动作对应 AST 节点；没有时为 {@code null}
     */
    public IrLoweringAction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
    }

    public IrLoweringAction(IrLoweringActionKind kind, String subject) {
        this(kind, subject, null);
    }
}
