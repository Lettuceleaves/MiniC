package minic.uiapi;

import java.util.Objects;

/**
 * AST Debug 节点详情。
 */
public record UiDebugAstNodeDetailDto(
        String nodeId,
        String kind,
        String label,
        UiSourceSpanDto sourceRange,
        String explanation
) {
    public UiDebugAstNodeDetailDto {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(explanation, "explanation");
    }
}
