package minic.uiapi;

import minic.compiler.SourceFile;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.ir.IrResult;
import minic.debug.DebugMappingIndex;
import minic.debug.DebugMappingItem;
import minic.debug.DebugMappingQueryResult;
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
     * @param irResult IR 结果
     * @param activeRange 当前 Debug 源码范围
     * @return AST Debug 视图
     */
    public UiDebugAstViewDto build(
            SourceFile sourceFile,
            Program program,
            IrResult irResult,
            SourceRange activeRange
    ) {
        DebugMappingIndex index = DebugMappingIndex.build(program, irResult);
        DebugMappingQueryResult mappings = activeRange == null
                ? new DebugMappingQueryResult("none", List.of(), List.of(), List.of())
                : index.findBySourceRange(activeRange);
        UiAstNodeVisualDto root = new UiAstVisualBuilder(sourceFile).buildProgram(program, null);
        UiDebugAstNodeDetailDto activeNode = mappings.astItems().stream()
                .filter(item -> item.sourceRangeOptional().isPresent())
                .min((left, right) -> Integer.compare(spanLength(left), spanLength(right)))
                .map(item -> detail(sourceFile, item))
                .orElse(null);
        return new UiDebugAstViewDto(
                markActive(root, activeNode == null ? null : activeNode.sourceRange()),
                activeNode,
                mappings.irItems().stream().map(DebugMappingItem::id).toList(),
                mappings.asmItems().stream().map(DebugMappingItem::id).toList()
        );
    }

    private UiDebugAstNodeDetailDto detail(SourceFile sourceFile, DebugMappingItem item) {
        return new UiDebugAstNodeDetailDto(
                item.id(),
                item.kind(),
                item.label(),
                item.sourceRangeOptional().map(range -> UiSourceSpanDto.from(sourceFile, range)).orElse(null),
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
                .map(range -> (range.endLine() - range.startLine()) * 1_000_000
                        + range.endByte() - range.startByte())
                .orElse(Integer.MAX_VALUE);
    }
}
