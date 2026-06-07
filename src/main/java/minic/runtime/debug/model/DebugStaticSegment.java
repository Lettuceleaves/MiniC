package minic.runtime.debug;

import java.util.List;
import java.util.Objects;

/**
 * 虚拟进程 static/data 段。
 *
 * @param globals 全局变量
 * @param stringLiterals 字符串字面量
 */
public record DebugStaticSegment(
        List<DebugMemoryEntry> globals,
        List<DebugMemoryEntry> stringLiterals
) {
    /**
     * 创建 static/data 段。
     */
    public DebugStaticSegment {
        Objects.requireNonNull(globals, "globals");
        Objects.requireNonNull(stringLiterals, "stringLiterals");
        globals = List.copyOf(globals);
        stringLiterals = List.copyOf(stringLiterals);
    }

    /**
     * 创建空 static/data 段。
     *
     * @return 空 static/data 段
     */
    public static DebugStaticSegment empty() {
        return new DebugStaticSegment(List.of(), List.of());
    }
}
