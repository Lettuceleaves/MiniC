package minic.ui;

import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiStageVisualDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据 AST visual DTO 生成 Graph View 风格布局模型。
 */
public final class MiniCAstGraphModelFactory {
    private static final double NODE_RADIUS = 28;
    private static final double X_GAP = 128;
    private static final double Y_GAP = 96;
    private static final double LEFT_PAD = 64;
    private static final double TOP_PAD = 58;

    /**
     * 创建 AST 图模型。
     *
     * @param visual 当前阶段 visual DTO
     * @return 图模型
     */
    public MiniCAstGraphModel create(UiStageVisualDto visual) {
        if (visual.astRoot() == null) {
            return new MiniCAstGraphModel(List.of(), List.of(), 360, 240);
        }
        ArrayList<PositionedNode> positioned = new ArrayList<>();
        int leafCount = assignPositions(visual.astRoot(), 0, new int[]{0}, positioned);
        Map<String, PositionedNode> positionedById = new HashMap<>();
        for (PositionedNode positionedNode : positioned) {
            positionedById.put(positionedNode.node.id(), positionedNode);
        }

        ArrayList<MiniCAstGraphNode> nodes = new ArrayList<>();
        ArrayList<MiniCAstGraphEdge> edges = new ArrayList<>();
        for (PositionedNode positionedNode : positioned) {
            UiAstNodeVisualDto node = positionedNode.node;
            nodes.add(new MiniCAstGraphNode(
                    node.id(),
                    node.label(),
                    positionedNode.depth,
                    positionedNode.x,
                    positionedNode.y,
                    node.active(),
                    node.children().isEmpty(),
                    node.id().equals("ast-root")
            ));
            for (UiAstNodeVisualDto child : node.children()) {
                PositionedNode childPosition = positionedById.get(child.id());
                if (childPosition == null) {
                    throw new IllegalStateException("missing AST graph position for node " + child.id());
                }
                edges.add(new MiniCAstGraphEdge(
                        node.id(),
                        child.id(),
                        positionedNode.x,
                        positionedNode.y + NODE_RADIUS,
                        childPosition.x,
                        childPosition.y - NODE_RADIUS,
                        false
                ));
            }
        }

        int maxDepth = positioned.stream().mapToInt(node -> node.depth).max().orElse(0);
        double width = Math.max(520, LEFT_PAD * 2 + Math.max(leafCount, 1) * X_GAP);
        double height = Math.max(300, TOP_PAD * 2 + (maxDepth + 1) * Y_GAP);
        return new MiniCAstGraphModel(nodes, edges, width, height);
    }

    private int assignPositions(
            UiAstNodeVisualDto node,
            int depth,
            int[] nextLeaf,
            ArrayList<PositionedNode> positioned
    ) {
        if (node.children().isEmpty()) {
            double x = LEFT_PAD + nextLeaf[0] * X_GAP;
            double y = TOP_PAD + depth * Y_GAP;
            nextLeaf[0]++;
            positioned.add(new PositionedNode(node, depth, x, y));
            return 1;
        }

        int before = nextLeaf[0];
        int leaves = 0;
        for (UiAstNodeVisualDto child : node.children()) {
            leaves += assignPositions(child, depth + 1, nextLeaf, positioned);
        }
        double left = LEFT_PAD + before * X_GAP;
        double right = LEFT_PAD + (nextLeaf[0] - 1) * X_GAP;
        double x = (left + right) / 2.0;
        double y = TOP_PAD + depth * Y_GAP;
        positioned.add(new PositionedNode(node, depth, x, y));
        return leaves;
    }

    private record PositionedNode(UiAstNodeVisualDto node, int depth, double x, double y) {
    }
}
