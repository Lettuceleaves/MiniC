package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * 图节点。
 *
 * @param id 节点 ID
 * @param label 展示标签
 * @param valueRef 关联运行时值引用
 * @param componentId 所属 component
 * @param metadata 点击元数据
 */
public record GraphNode(
        String id,
        String label,
        String valueRef,
        String componentId,
        Map<String, String> metadata
) {
    public GraphNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(valueRef, "valueRef");
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(metadata, "metadata");
        if (id.isBlank() || label.isBlank() || componentId.isBlank()) {
            throw new IllegalArgumentException("node id, label and componentId must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
