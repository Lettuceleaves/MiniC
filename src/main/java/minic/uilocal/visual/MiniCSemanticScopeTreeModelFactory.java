package minic.uilocal;

import minic.uiapi.UiSemanticScopeVisualDto;
import minic.uiapi.UiStageVisualDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 Semantic scope visual DTO 生成树形行模型。
 */
public final class MiniCSemanticScopeTreeModelFactory {
    /**
     * 创建作用域树行。
     *
     * @param visual 当前阶段 visual DTO
     * @return 树行
     */
    public List<MiniCSemanticScopeTreeLine> create(UiStageVisualDto visual) {
        if (visual.semanticRoot() == null) {
            return List.of();
        }
        ArrayList<MiniCSemanticScopeTreeLine> lines = new ArrayList<>();
        append(visual.semanticRoot(), 0, visual.semanticEdgesPointChildToParent(), lines);
        return List.copyOf(lines);
    }

    private boolean append(
            UiSemanticScopeVisualDto node,
            int depth,
            boolean childToParent,
            ArrayList<MiniCSemanticScopeTreeLine> lines
    ) {
        int currentIndex = lines.size();
        lines.add(new MiniCSemanticScopeTreeLine(
                node.label(),
                depth,
                node.symbols(),
                node.active(),
                node.active(),
                childToParent ? "child-to-parent" : "parent-to-child"
        ));
        boolean path = node.active();
        for (UiSemanticScopeVisualDto child : node.children()) {
            path |= append(child, depth + 1, childToParent, lines);
        }
        if (path && !lines.get(currentIndex).onActivePath()) {
            MiniCSemanticScopeTreeLine line = lines.get(currentIndex);
            lines.set(currentIndex, new MiniCSemanticScopeTreeLine(
                    line.label(),
                    line.depth(),
                    line.symbols(),
                    line.active(),
                    true,
                    line.arrowDirection()
            ));
        }
        return path;
    }
}
