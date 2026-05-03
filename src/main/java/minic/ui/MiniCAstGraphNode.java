package minic.ui;

import java.util.Objects;

/**
 * AST 图中的节点。
 *
 * @param id 节点 ID
 * @param label 展示标签
 * @param depth 深度
 * @param x 横坐标
 * @param y 纵坐标
 * @param active 是否当前节点
 * @param leaf 是否叶子节点
 * @param root 是否根节点
 */
public record MiniCAstGraphNode(
        String id,
        String label,
        int depth,
        double x,
        double y,
        boolean active,
        boolean leaf,
        boolean root
) {
    public MiniCAstGraphNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
    }
}
