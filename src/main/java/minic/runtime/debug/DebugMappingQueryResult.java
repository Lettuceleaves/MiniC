package minic.runtime.debug;

import java.util.List;
import java.util.Objects;

/**
 * Debug 映射查询结果。
 *
 * @param sourceKey 查询源码 key
 * @param astItems AST 相关项
 * @param irItems IR 相关项
 * @param asmItems ASM 相关项
 */
public record DebugMappingQueryResult(
        String sourceKey,
        List<DebugMappingItem> astItems,
        List<DebugMappingItem> irItems,
        List<DebugMappingItem> asmItems
) {
    /**
     * 创建查询结果。
     */
    public DebugMappingQueryResult {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(astItems, "astItems");
        Objects.requireNonNull(irItems, "irItems");
        Objects.requireNonNull(asmItems, "asmItems");
        astItems = List.copyOf(astItems);
        irItems = List.copyOf(irItems);
        asmItems = List.copyOf(asmItems);
    }
}
