package minic.runtime.debug;

import java.util.Objects;

/**
 * Debugger 虚拟进程空间。
 *
 * @param code code 段
 * @param staticData static/data 段
 * @param stack stack 段
 * @param heap heap 段
 * @param io IO 段
 */
public record DebugProcessSpace(
        DebugCodeSegment code,
        DebugStaticSegment staticData,
        DebugStackSegment stack,
        DebugHeapSegment heap,
        DebugIoSegment io
) {
    /**
     * 创建虚拟进程空间。
     */
    public DebugProcessSpace {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(staticData, "staticData");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(heap, "heap");
        Objects.requireNonNull(io, "io");
    }

    /**
     * 创建空虚拟进程空间。
     *
     * @return 空虚拟进程空间
     */
    public static DebugProcessSpace empty() {
        return new DebugProcessSpace(
                DebugCodeSegment.empty(),
                DebugStaticSegment.empty(),
                DebugStackSegment.empty(),
                DebugHeapSegment.empty(),
                DebugIoSegment.empty()
        );
    }
}
