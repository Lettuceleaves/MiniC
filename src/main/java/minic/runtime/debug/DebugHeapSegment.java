package minic.runtime.debug;

import java.util.List;
import java.util.Objects;

/**
 * 虚拟进程 heap 段。
 *
 * @param blocks 堆块列表
 */
public record DebugHeapSegment(List<DebugHeapBlock> blocks) {
    /**
     * 创建 heap 段。
     */
    public DebugHeapSegment {
        Objects.requireNonNull(blocks, "blocks");
        blocks = List.copyOf(blocks);
    }

    /**
     * 创建空 heap 段。
     *
     * @return 空 heap 段
     */
    public static DebugHeapSegment empty() {
        return new DebugHeapSegment(List.of());
    }
}
