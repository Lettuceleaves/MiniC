package minic.runtime.debug;

import java.util.List;
import java.util.Objects;

/**
 * Debugger 可恢复状态快照。
 *
 * @param snapshotId 快照 ID
 * @param visibleStepIndex 可见调试步下标
 * @param cursor 执行游标
 * @param callStackSummary 调用栈摘要
 * @param processSpace 虚拟进程空间
 * @param breakpointHit 是否命中断点
 * @param stopReason 停止原因
 */
public record DebugSnapshot(
        long snapshotId,
        long visibleStepIndex,
        DebugCursor cursor,
        List<String> callStackSummary,
        DebugProcessSpace processSpace,
        boolean breakpointHit,
        DebugStopReason stopReason
) {
    /**
     * 创建 Debug 快照。
     */
    public DebugSnapshot {
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(callStackSummary, "callStackSummary");
        Objects.requireNonNull(processSpace, "processSpace");
        Objects.requireNonNull(stopReason, "stopReason");
        if (snapshotId < 0) {
            throw new IllegalArgumentException("snapshotId must not be negative");
        }
        if (visibleStepIndex < 0) {
            throw new IllegalArgumentException("visibleStepIndex must not be negative");
        }
        callStackSummary = List.copyOf(callStackSummary);
    }

    /**
     * 创建初始快照。
     *
     * @return 初始快照
     */
    public static DebugSnapshot initial() {
        return new DebugSnapshot(
                0,
                0,
                DebugCursor.initial(),
                List.of("main"),
                DebugProcessSpace.empty(),
                false,
                DebugStopReason.START
        );
    }
}
