package minic.runtime.debug.visual;

import java.util.List;
import java.util.Objects;

/**
 * 图离散 component。
 *
 * @param id component ID
 * @param label 展示标签
 * @param nodeIds 节点 ID 列表
 */
public record GraphComponent(String id, String label, List<String> nodeIds) {
    public GraphComponent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(nodeIds, "nodeIds");
        if (id.isBlank() || label.isBlank()) {
            throw new IllegalArgumentException("component id and label must not be blank");
        }
        nodeIds = List.copyOf(nodeIds);
    }
}
