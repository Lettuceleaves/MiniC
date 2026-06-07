package minic.runtime.debug;

import java.util.Objects;

/**
 * Debug 控制命令执行结果。
 *
 * @param command 命令
 * @param state 执行后的会话状态
 * @param snapshot 当前快照
 * @param message 解释
 */
public record DebugControlResult(
        DebugCommand command,
        DebugExecutionState state,
        DebugSnapshot snapshot,
        String message
) {
    /**
     * 创建控制结果。
     */
    public DebugControlResult {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(message, "message");
    }
}
