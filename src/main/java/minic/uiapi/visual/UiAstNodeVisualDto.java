package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * AST 树节点图形数据。
 *
 * @param id UI 内稳定节点 ID
 * @param label 节点展示文本
 * @param kind 节点类型
 * @param range 源码范围；尚未细化时可为 {@code null}
 * @param active 是否为当前节点
 * @param children 子节点
 */
public record UiAstNodeVisualDto(
        String id,
        String label,
        String kind,
        UiSourceSpanDto range,
        boolean active,
        List<UiAstNodeVisualDto> children
) {
    public UiAstNodeVisualDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
