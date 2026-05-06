package minic.runtime.debug;

import java.util.List;
import java.util.Objects;

/**
 * 虚拟堆块。
 *
 * @param address 虚拟地址
 * @param typeName 类型名
 * @param size 字节大小或教学模型大小
 * @param entries 字段或数组元素摘要
 * @param status 状态
 */
public record DebugHeapBlock(
        DebugVirtualAddress address,
        String typeName,
        long size,
        List<DebugMemoryEntry> entries,
        String status
) {
    /**
     * 创建虚拟堆块。
     */
    public DebugHeapBlock {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(status, "status");
        if (typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        if (status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        entries = List.copyOf(entries);
    }
}
