package minic.uiapi;

import java.util.List;
import java.util.Objects;

/**
 * AST Debug 视图 DTO。
 */
public record UiDebugAstViewDto(
        UiAstNodeVisualDto root,
        UiDebugAstNodeDetailDto activeNode,
        List<String> relatedIrIds,
        List<String> relatedAsmIds
) {
    public UiDebugAstViewDto {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(relatedIrIds, "relatedIrIds");
        Objects.requireNonNull(relatedAsmIds, "relatedAsmIds");
        relatedIrIds = List.copyOf(relatedIrIds);
        relatedAsmIds = List.copyOf(relatedAsmIds);
    }
}
