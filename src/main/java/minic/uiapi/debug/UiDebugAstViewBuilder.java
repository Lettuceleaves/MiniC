package minic.uiapi;

import minic.compiler.ast.decl.Program;
import minic.compiler.ir.model.IrModule;
import minic.runtime.debug.DebugMappingIndex;
import minic.runtime.debug.DebugMappingItem;
import minic.runtime.debug.DebugMappingQueryResult;
import minic.source.SourceRange;

import java.util.List;

/**
 * AST Debug 视图构建器。
 */
public final class UiDebugAstViewBuilder {
    /**
     * 构建 AST Debug 视图。
     *
     * @param program AST 根节点
     * @param module IR 模块
     * @param activeRange 当前 Debug 源码范围
     * @return AST Debug 视图
     */
    public UiDebugAstViewDto build(Program program, IrModule module, SourceRange activeRange) {
        DebugMappingIndex index = DebugMappingIndex.build(program, module);
        DebugMappingQueryResult mappings = activeRange == null
                ? new DebugMappingQueryResult("none", List.of(), List.of(), List.of())
                : index.findBySourceRange(activeRange);
        UiAstNodeVisualDto root = new UiAstVisualBuilder().buildProgram(program, null);
        UiDebugAstNodeDetailDto activeNode = mappings.astItems().stream()
                .filter(item -> item.sourceRangeOptional().isPresent())
                .min((left, right) -> Integer.compare(spanLength(left), spanLength(right)))
                .map(this::detail)
                .orElse(null);
        return new UiDebugAstViewDto(
                markActive(root, activeNode == null ? null : activeNode.sourceRange()),
                activeNode,
                mappings.irItems().stream().map(DebugMappingItem::id).toList(),
                mappings.asmItems().stream().map(DebugMappingItem::id).toList()
        );
    }

    private UiDebugAstNodeDetailDto detail(DebugMappingItem item) {
        return new UiDebugAstNodeDetailDto(
                item.id(),
                item.kind(),
                item.label(),
                item.sourceRangeOptional().map(UiSourceSpanDto::from).orElse(null),
                "当前 Debug 源码范围关联到该 AST 节点。点击节点可查看源码范围和关联 IR/ASM 映射。"
        );
    }

    private UiAstNodeVisualDto markActive(UiAstNodeVisualDto node, UiSourceSpanDto activeRange) {
        boolean active = activeRange != null && sameRange(node.range(), activeRange);
        return new UiAstNodeVisualDto(
                node.id(),
                node.label(),
                node.kind(),
                node.range(),
                active,
                node.children().stream().map(child -> markActive(child, activeRange)).toList()
        );
    }

    private boolean sameRange(UiSourceSpanDto left, UiSourceSpanDto right) {
        return left != null
                && right != null
                && left.sourceName().equals(right.sourceName())
                && left.startOffset() < right.endOffset()
                && right.startOffset() < left.endOffset();
    }

    private int spanLength(DebugMappingItem item) {
        return item.sourceRangeOptional()
                .map(range -> range.endOffset() - range.startOffset())
                .orElse(Integer.MAX_VALUE);
    }
}
