package minic.runtime.debug;

import java.util.Locale;
import java.util.Objects;

/**
 * Debugger 教学型虚拟地址。
 *
 * @param segment 所属段
 * @param offset 偏移
 */
public record DebugVirtualAddress(String segment, long offset) {
    /**
     * 创建虚拟地址。
     */
    public DebugVirtualAddress {
        Objects.requireNonNull(segment, "segment");
        if (segment.isBlank()) {
            throw new IllegalArgumentException("segment must not be blank");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        segment = segment.toLowerCase(Locale.ROOT);
    }

    /**
     * 返回展示文本。
     *
     * @return 展示文本
     */
    public String display() {
        return segment + ":0x" + Long.toHexString(offset);
    }
}
