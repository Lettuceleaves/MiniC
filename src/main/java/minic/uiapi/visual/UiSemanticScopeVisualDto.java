package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * Semantic 阶段作用域树节点图形数据。
 *
 * @param id UI 内稳定作用域 ID
 * @param label 作用域展示文本
 * @param symbols 符号摘要
 * @param range 源码范围；尚未细化时可为 {@code null}
 * @param active 是否为当前作用域
 * @param children 子作用域
 */
public record UiSemanticScopeVisualDto(
        String id,
        String label,
        List<String> symbols,
        UiSourceSpanDto range,
        boolean active,
        List<UiSemanticScopeVisualDto> children
) {
    public UiSemanticScopeVisualDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(children, "children");
        symbols = List.copyOf(symbols);
        children = List.copyOf(children);
    }
}
