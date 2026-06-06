package minic.uilocal;

import java.util.List;

/**
 * AST 图布局模型。
 *
 * @param nodes 节点
 * @param edges 连线
 * @param width 建议宽度
 * @param height 建议高度
 */
public record MiniCAstGraphModel(
        List<MiniCAstGraphNode> nodes,
        List<MiniCAstGraphEdge> edges,
        double width,
        double height
) {
    public MiniCAstGraphModel {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
