package minic.uilocal;

import java.util.Objects;

/**
 * AST 图中的连线。
 *
 * @param fromId 父节点 ID
 * @param toId 子节点 ID
 * @param fromX 起点横坐标
 * @param fromY 起点纵坐标
 * @param toX 终点横坐标
 * @param toY 终点纵坐标
 * @param hot 是否连接当前路径
 */
public record MiniCAstGraphEdge(
        String fromId,
        String toId,
        double fromX,
        double fromY,
        double toX,
        double toY,
        boolean hot
) {
    public MiniCAstGraphEdge {
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
    }
}
