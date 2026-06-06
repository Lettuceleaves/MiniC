package minic.uilocal;

import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiStageVisualDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 AST visual DTO 生成树形行模型。
 */
public final class MiniCAstTreeModelFactory {
    /**
     * 创建 AST 树行模型。
     *
     * @param visual 当前阶段 visual DTO
     * @return 树行
     */
    public List<MiniCAstTreeLine> create(UiStageVisualDto visual) {
        if (visual.astRoot() == null) {
            return List.of();
        }
        ArrayList<MiniCAstTreeLine> lines = new ArrayList<>();
        append(visual.astRoot(), 0, lines);
        return List.copyOf(lines);
    }

    private void append(UiAstNodeVisualDto node, int depth, ArrayList<MiniCAstTreeLine> lines) {
        lines.add(new MiniCAstTreeLine(node.label(), depth, node.active(), node.range()));
        node.children().forEach(child -> append(child, depth + 1, lines));
    }
}
