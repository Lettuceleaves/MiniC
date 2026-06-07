package minic.runtime.debug;

import java.util.List;
import java.util.Objects;

/**
 * 虚拟进程 stack 段。
 *
 * @param frames 调用帧，调用栈底在前，栈顶在后
 */
public record DebugStackSegment(List<DebugStackFrame> frames) {
    /**
     * 创建 stack 段。
     */
    public DebugStackSegment {
        Objects.requireNonNull(frames, "frames");
        frames = List.copyOf(frames);
    }

    /**
     * 创建空 stack 段。
     *
     * @return 空 stack 段
     */
    public static DebugStackSegment empty() {
        return new DebugStackSegment(List.of());
    }

    /**
     * 入栈并返回新 stack 段。
     *
     * @param frame 栈帧
     * @return 新 stack 段
     */
    public DebugStackSegment push(DebugStackFrame frame) {
        java.util.ArrayList<DebugStackFrame> next = new java.util.ArrayList<>(frames);
        next.add(Objects.requireNonNull(frame, "frame"));
        return new DebugStackSegment(next);
    }

    /**
     * 出栈并返回新 stack 段。
     *
     * @return 新 stack 段
     */
    public DebugStackSegment pop() {
        if (frames.isEmpty()) {
            throw new IllegalStateException("stack is empty");
        }
        return new DebugStackSegment(frames.subList(0, frames.size() - 1));
    }
}
