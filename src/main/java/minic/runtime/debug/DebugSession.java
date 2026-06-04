package minic.runtime.debug;

import minic.source.SourceFile;
import minic.source.SourceRange;
import minic.runtime.debug.dataflow.DataFlowEvent;
import minic.runtime.debug.visual.VisualEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Debugger 会话基础模型。
 */
public final class DebugSession {
    private final SourceFile sourceFile;
    private final ArrayList<DebugSnapshot> snapshots = new ArrayList<>();
    private final ArrayList<DebugEvent> events = new ArrayList<>();
    private final ArrayList<DataFlowEvent> dataFlowEvents = new ArrayList<>();
    private final ArrayList<VisualEvent> visualEvents = new ArrayList<>();
    private final Map<Integer, DebugBreakpoint> breakpoints = new LinkedHashMap<>();
    private DebugExecutionState state = DebugExecutionState.PAUSED;
    private int currentSnapshotIndex;
    private boolean pauseRequested;

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
        return snapshots.get(currentSnapshotIndex);
    }

    /**
     * 追加快照。
     *
     * @param snapshot 快照
     */
    public void appendSnapshot(DebugSnapshot snapshot) {
        snapshots.add(Objects.requireNonNull(snapshot, "snapshot"));
        currentSnapshotIndex = snapshots.size() - 1;
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
     * 追加数据流事件。
     *
     * @param event 数据流事件
     */
    public void appendDataFlowEvent(DataFlowEvent event) {
        dataFlowEvents.add(Objects.requireNonNull(event, "event"));
    }

    /**
     * 追加数据结构可视化事件。
     *
     * @param event 可视化事件
     */
    public void appendVisualEvent(VisualEvent event) {
        visualEvents.add(Objects.requireNonNull(event, "event"));
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

    /**
     * 返回数据流事件日志。
     *
     * @return 数据流事件日志
     */
    public List<DataFlowEvent> dataFlowEvents() {
        return List.copyOf(dataFlowEvents);
    }

    /**
     * 返回数据结构可视化事件日志。
     *
     * @return 可视化事件日志
     */
    public List<VisualEvent> visualEvents() {
        return List.copyOf(visualEvents);
    }

    /**
     * 返回断点列表。
     *
     * @return 断点列表
     */
    public List<DebugBreakpoint> breakpoints() {
        return List.copyOf(breakpoints.values());
    }

    /**
     * 设置源码行断点。
     *
     * @param line 一基源码行号
     * @return 设置结果
     */
    public DebugBreakpointResult setBreakpoint(int line) {
        if (!isBreakableLine(line)) {
            return DebugBreakpointResult.rejected(line);
        }
        DebugBreakpoint breakpoint = DebugBreakpoint.enabled(line);
        breakpoints.put(line, breakpoint);
        return DebugBreakpointResult.accepted(breakpoint);
    }

    /**
     * 取消源码行断点。
     *
     * @param line 一基源码行号
     * @return 是否移除
     */
    public boolean clearBreakpoint(int line) {
        return breakpoints.remove(line) != null;
    }

    /**
     * 执行正向控制命令。
     *
     * @param command 控制命令
     * @return 控制结果
     */
    public DebugControlResult control(DebugCommand command) {
        Objects.requireNonNull(command, "command");
        return switch (command) {
            case FAST_FORWARD -> fastForward(command);
            case RUN_TO_BREAKPOINT -> runToBreakpoint(command);
            case STEP_OVER -> moveToSnapshot(command, stepOverIndex(), "已单步执行当前源码级语句");
            case STEP_INTO -> moveToSnapshot(command, nextExecutableIndex(), "已步入下一个可见调试步");
            case STEP_OUT -> moveToSnapshot(command, stepOutIndex(), "已运行到当前函数返回");
            case PAUSE -> requestPause();
            case CLOSE -> close();
            case RESTART -> restart();
            case STEP_BACK -> stepBack();
            case BACK_TO_BREAKPOINT -> backToBreakpoint();
            case BACK_TO_CALL_SITE -> backToCallSite();
        };
    }

    /**
     * 把当前快照移动到指定下标，供解释器标记断点命中后使用。
     *
     * @param snapshotIndex 快照下标
     */
    void selectSnapshot(int snapshotIndex) {
        if (snapshotIndex < 0 || snapshotIndex >= snapshots.size()) {
            throw new IllegalArgumentException("snapshotIndex out of bounds: " + snapshotIndex);
        }
        currentSnapshotIndex = snapshotIndex;
        state = stateForCurrentSnapshot();
    }

    /**
     * 标记当前快照命中断点。
     */
    void markCurrentBreakpointHit() {
        DebugSnapshot current = currentSnapshot();
        snapshots.set(currentSnapshotIndex, new DebugSnapshot(
                current.snapshotId(),
                current.visibleStepIndex(),
                current.cursor(),
                current.callStackSummary(),
                current.processSpace(),
                true,
                DebugStopReason.BREAKPOINT
        ));
        state = DebugExecutionState.PAUSED;
    }

    private DebugControlResult fastForward(DebugCommand command) {
        state = DebugExecutionState.RUNNING;
        for (int i = currentSnapshotIndex + 1; i < snapshots.size(); i++) {
            currentSnapshotIndex = i;
            if (pauseRequested && currentSnapshot().visibleStepIndex() > 0) {
                pauseRequested = false;
                replaceCurrentStop(DebugStopReason.PAUSE_REQUESTED, false);
                state = DebugExecutionState.PAUSED;
                return result(command, "已按暂停请求停在下一条可见源码行之前");
            }
            if (isBreakpointSnapshot(currentSnapshot())) {
                replaceCurrentStop(DebugStopReason.BREAKPOINT, true);
                state = DebugExecutionState.PAUSED;
                return result(command, "命中第 " + currentLine().orElse(-1) + " 行断点");
            }
            if (isTerminalSnapshot(currentSnapshot())) {
                state = stateForCurrentSnapshot();
                return result(command, "连续运行已停止");
            }
        }
        state = stateForCurrentSnapshot();
        return result(command, "连续运行已到达末尾");
    }

    private DebugControlResult runToBreakpoint(DebugCommand command) {
        state = DebugExecutionState.RUNNING;
        for (int i = currentSnapshotIndex + 1; i < snapshots.size(); i++) {
            currentSnapshotIndex = i;
            if (pauseRequested && currentSnapshot().visibleStepIndex() > 0) {
                pauseRequested = false;
                replaceCurrentStop(DebugStopReason.PAUSE_REQUESTED, false);
                state = DebugExecutionState.PAUSED;
                return result(command, "已按暂停请求停在下一条可见源码行之前");
            }
            if (isBreakpointSnapshot(currentSnapshot())) {
                replaceCurrentStop(DebugStopReason.BREAKPOINT, true);
                state = DebugExecutionState.PAUSED;
                return result(command, "命中第 " + currentLine().orElse(-1) + " 行断点");
            }
            if (isTerminalSnapshot(currentSnapshot())) {
                state = stateForCurrentSnapshot();
                return result(command, "没有后续断点，已运行到结束或错误");
            }
        }
        state = stateForCurrentSnapshot();
        return result(command, "没有后续断点");
    }

    private DebugControlResult moveToSnapshot(DebugCommand command, int targetIndex, String message) {
        currentSnapshotIndex = targetIndex;
        state = stateForCurrentSnapshot();
        return result(command, message);
    }

    private DebugControlResult requestPause() {
        pauseRequested = true;
        return result(DebugCommand.PAUSE, "已请求暂停，连续运行会在下一条可见源码行前停住");
    }

    private DebugControlResult close() {
        state = DebugExecutionState.CLOSED;
        replaceCurrentStop(DebugStopReason.CLOSED, false);
        return result(DebugCommand.CLOSE, "Debug 会话已关闭");
    }

    private DebugControlResult restart() {
        currentSnapshotIndex = 0;
        pauseRequested = false;
        state = DebugExecutionState.PAUSED;
        return result(DebugCommand.RESTART, "Debug 会话已重启，断点已保留");
    }

    private DebugControlResult stepBack() {
        pauseRequested = false;
        currentSnapshotIndex = previousExecutableIndex();
        state = stateForCurrentSnapshot();
        return result(DebugCommand.STEP_BACK, "已回退到上一个可见调试步");
    }

    private DebugControlResult backToBreakpoint() {
        pauseRequested = false;
        for (int i = currentSnapshotIndex - 1; i >= 0; i--) {
            if (snapshots.get(i).breakpointHit()) {
                currentSnapshotIndex = i;
                state = DebugExecutionState.PAUSED;
                return result(DebugCommand.BACK_TO_BREAKPOINT, "已回退到上一个断点命中状态");
            }
        }
        currentSnapshotIndex = 0;
        state = DebugExecutionState.PAUSED;
        return result(DebugCommand.BACK_TO_BREAKPOINT, "没有更早的断点命中状态，已回到初始状态");
    }

    private DebugControlResult backToCallSite() {
        pauseRequested = false;
        int currentDepth = currentSnapshot().callStackSummary().size();
        for (int i = currentSnapshotIndex - 1; i >= 0; i--) {
            if (snapshots.get(i).callStackSummary().size() < currentDepth) {
                currentSnapshotIndex = i;
                state = DebugExecutionState.PAUSED;
                return result(DebugCommand.BACK_TO_CALL_SITE, "已返回进入当前函数调用之前的调用处");
            }
        }
        currentSnapshotIndex = 0;
        state = DebugExecutionState.PAUSED;
        return result(DebugCommand.BACK_TO_CALL_SITE, "当前不在更深调用中，已回到初始状态");
    }

    private int nextExecutableIndex() {
        long currentVisibleStep = currentSnapshot().visibleStepIndex();
        for (int i = currentSnapshotIndex + 1; i < snapshots.size(); i++) {
            if (snapshots.get(i).visibleStepIndex() > currentVisibleStep || isTerminalSnapshot(snapshots.get(i))) {
                return endOfVisibleStep(i);
            }
        }
        return snapshots.size() - 1;
    }

    private int stepOverIndex() {
        int currentDepth = currentSnapshot().callStackSummary().size();
        int next = currentSnapshotIndex + 1;
        while (next < snapshots.size()) {
            DebugSnapshot snapshot = snapshots.get(next);
            if (isTerminalSnapshot(snapshot)) {
                return next;
            }
            if (snapshot.visibleStepIndex() > currentSnapshot().visibleStepIndex()
                    && snapshot.callStackSummary().size() <= currentDepth) {
                return endOfVisibleStep(next);
            }
            next++;
        }
        return snapshots.size() - 1;
    }

    private int stepOutIndex() {
        int currentDepth = currentSnapshot().callStackSummary().size();
        if (currentDepth <= 1) {
            return stepOverIndex();
        }
        for (int i = currentSnapshotIndex + 1; i < snapshots.size(); i++) {
            DebugSnapshot snapshot = snapshots.get(i);
            if (isTerminalSnapshot(snapshot) || snapshot.callStackSummary().size() < currentDepth) {
                return endOfVisibleStep(i);
            }
        }
        return snapshots.size() - 1;
    }

    private int previousExecutableIndex() {
        long currentVisibleStep = currentSnapshot().visibleStepIndex();
        for (int i = currentSnapshotIndex - 1; i >= 0; i--) {
            if (snapshots.get(i).visibleStepIndex() < currentVisibleStep) {
                return endOfVisibleStep(i);
            }
        }
        return 0;
    }

    private int endOfVisibleStep(int snapshotIndex) {
        long visibleStep = snapshots.get(snapshotIndex).visibleStepIndex();
        int last = snapshotIndex;
        for (int i = snapshotIndex + 1; i < snapshots.size(); i++) {
            if (snapshots.get(i).visibleStepIndex() != visibleStep) {
                break;
            }
            last = i;
        }
        return last;
    }

    private boolean isBreakableLine(int line) {
        if (line < 1) {
            return false;
        }
        return snapshots.stream()
                .map(DebugSnapshot::cursor)
                .map(DebugCursor::sourceRangeOptional)
                .flatMap(Optional::stream)
                .map(range -> range.startPosition().line())
                .anyMatch(snapshotLine -> snapshotLine == line);
    }

    private boolean isBreakpointSnapshot(DebugSnapshot snapshot) {
        return line(snapshot.cursor().sourceRange()).stream()
                .anyMatch(snapshotLine -> {
                    DebugBreakpoint breakpoint = breakpoints.get(snapshotLine);
                    return breakpoint != null && breakpoint.enabled();
                });
    }

    private Optional<Integer> currentLine() {
        return line(currentSnapshot().cursor().sourceRange());
    }

    private Optional<Integer> line(SourceRange range) {
        return Optional.ofNullable(range).map(value -> value.startPosition().line());
    }

    private boolean isTerminalSnapshot(DebugSnapshot snapshot) {
        return snapshot.stopReason() == DebugStopReason.COMPLETED
                || snapshot.stopReason() == DebugStopReason.ERROR
                || snapshot.stopReason() == DebugStopReason.CLOSED;
    }

    private DebugExecutionState stateForCurrentSnapshot() {
        return switch (currentSnapshot().stopReason()) {
            case COMPLETED -> DebugExecutionState.COMPLETED;
            case ERROR -> DebugExecutionState.FAILED;
            case CLOSED -> DebugExecutionState.CLOSED;
            case START, STEP, BREAKPOINT, PAUSE_REQUESTED, RETURN -> DebugExecutionState.PAUSED;
        };
    }

    private void replaceCurrentStop(DebugStopReason stopReason, boolean breakpointHit) {
        DebugSnapshot current = currentSnapshot();
        snapshots.set(currentSnapshotIndex, new DebugSnapshot(
                current.snapshotId(),
                current.visibleStepIndex(),
                current.cursor(),
                current.callStackSummary(),
                current.processSpace(),
                breakpointHit,
                stopReason
        ));
    }

    private DebugControlResult result(DebugCommand command, String message) {
        return new DebugControlResult(command, state, currentSnapshot(), message);
    }
}
