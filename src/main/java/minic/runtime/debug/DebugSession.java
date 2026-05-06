package minic.runtime.debug;

import minic.source.SourceFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Debugger 会话基础模型。
 */
public final class DebugSession {
    private final SourceFile sourceFile;
    private final ArrayList<DebugSnapshot> snapshots = new ArrayList<>();
    private final ArrayList<DebugEvent> events = new ArrayList<>();
    private DebugExecutionState state = DebugExecutionState.PAUSED;

    private DebugSession(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        appendSnapshot(DebugSnapshot.initial());
    }

    /**
     * 从源码创建 Debug 会话。
     *
     * @param sourceFile 源码文件
     * @return Debug 会话
     */
    public static DebugSession fromSource(SourceFile sourceFile) {
        return new DebugSession(sourceFile);
    }

    /**
     * 返回源码文件。
     *
     * @return 源码文件
     */
    public SourceFile sourceFile() {
        return sourceFile;
    }

    /**
     * 返回执行状态。
     *
     * @return 执行状态
     */
    public DebugExecutionState state() {
        return state;
    }

    /**
     * 设置执行状态。
     *
     * @param state 执行状态
     */
    public void setState(DebugExecutionState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    /**
     * 返回当前快照。
     *
     * @return 当前快照
     */
    public DebugSnapshot currentSnapshot() {
        return snapshots.getLast();
    }

    /**
     * 追加快照。
     *
     * @param snapshot 快照
     */
    public void appendSnapshot(DebugSnapshot snapshot) {
        snapshots.add(Objects.requireNonNull(snapshot, "snapshot"));
    }

    /**
     * 追加事件。
     *
     * @param event 事件
     */
    public void appendEvent(DebugEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
    }

    /**
     * 返回快照历史。
     *
     * @return 快照历史
     */
    public List<DebugSnapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    /**
     * 返回事件日志。
     *
     * @return 事件日志
     */
    public List<DebugEvent> events() {
        return List.copyOf(events);
    }
}
