package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * 图边。
 *
 * @param id 边 ID
 * @param fromNodeId 起点
 * @param toNodeId 终点
 * @param label 展示标签
 * @param directed 是否有向
 * @param metadata 点击元数据
 */
public record GraphEdge(
        String id,
        String fromNodeId,
        String toNodeId,
        String label,
        boolean directed,
        Map<String, String> metadata
) {
    public GraphEdge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromNodeId, "fromNodeId");
        Objects.requireNonNull(toNodeId, "toNodeId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(metadata, "metadata");
        if (id.isBlank() || fromNodeId.isBlank() || toNodeId.isBlank()) {
            throw new IllegalArgumentException("edge id, fromNodeId and toNodeId must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
