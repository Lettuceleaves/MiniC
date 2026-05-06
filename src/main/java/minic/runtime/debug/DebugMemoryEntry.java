package minic.runtime.debug;

import java.util.Objects;
import java.util.Optional;

/**
 * 虚拟内存条目。
 *
 * @param name 名称
 * @param address 虚拟地址；没有时为 {@code null}
 * @param typeName 类型名
 * @param valueSummary 值摘要；E140 会引入正式 DebugValue
 */
public record DebugMemoryEntry(
        String name,
        DebugVirtualAddress address,
        String typeName,
        String valueSummary
) {
    /**
     * 创建虚拟内存条目。
     */
    public DebugMemoryEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(valueSummary, "valueSummary");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be blank");
        }
    }

    /**
     * 返回虚拟地址。
     *
     * @return 虚拟地址 Optional
     */
    public Optional<DebugVirtualAddress> addressOptional() {
        return Optional.ofNullable(address);
    }
}
