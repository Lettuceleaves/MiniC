package minic.runtime.debug.dataflow;

import minic.source.SourceRange;

import java.util.Objects;

/**
 * 调试执行中的关键数据流事件。
 *
 * @param snapshotId 关联快照 ID
 * @param instructionId 关联 IR 指令 ID
 * @param sourceRange 关联源码范围；没有时为 {@code null}
 * @param type 事件类型
 * @param cExpression 对应 C 源码表达式或可读 fallback
 * @param lvaluePath 被写入或读取的位置路径
 * @param oldValue 变化前值
 * @param newValue 变化后值
 * @param address 关联地址；没有时为空字符串
 * @param pointerTarget 指针目标地址；非指针事件为空字符串
 * @param pointerFieldWrite 指针字段写入结构化信息；非指针字段写入事件为 {@code null}
 */
public record DataFlowEvent(
        long snapshotId,
        String instructionId,
        SourceRange sourceRange,
        DataFlowEventType type,
        String cExpression,
        String lvaluePath,
        String oldValue,
        String newValue,
        String address,
        String pointerTarget,
        PointerFieldWrite pointerFieldWrite
) {
    /**
     * 创建不携带结构化指针字段写入信息的数据流事件。
     */
    public DataFlowEvent(
            long snapshotId,
            String instructionId,
            SourceRange sourceRange,
            DataFlowEventType type,
            String cExpression,
            String lvaluePath,
            String oldValue,
            String newValue,
            String address,
            String pointerTarget
    ) {
        this(
                snapshotId,
                instructionId,
                sourceRange,
                type,
                cExpression,
                lvaluePath,
                oldValue,
                newValue,
                address,
                pointerTarget,
                null
        );
    }

    /**
     * 创建数据流事件。
     */
    public DataFlowEvent {
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(cExpression, "cExpression");
        Objects.requireNonNull(lvaluePath, "lvaluePath");
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "newValue");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(pointerTarget, "pointerTarget");
        if (snapshotId < 0) {
            throw new IllegalArgumentException("snapshotId must not be negative");
        }
        if (instructionId.isBlank()) {
            throw new IllegalArgumentException("instructionId must not be blank");
        }
        if (cExpression.isBlank()) {
            throw new IllegalArgumentException("cExpression must not be blank");
        }
        if (lvaluePath.isBlank()) {
            throw new IllegalArgumentException("lvaluePath must not be blank");
        }
    }
}
