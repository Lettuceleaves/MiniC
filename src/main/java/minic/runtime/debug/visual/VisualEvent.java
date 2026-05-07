package minic.runtime.debug.visual;

import java.util.Map;
import java.util.Objects;

/**
 * Debugger 运行时数据结构可视化事件。
 *
 * @param snapshotId 事件对应快照 ID
 * @param type 事件类型
 * @param graphName 图名称
 * @param nodeId 节点 ID；事件不关联单节点时为空字符串
 * @param fromId 起点 ID；非边事件为空字符串
 * @param toId 终点 ID；非边事件为空字符串
 * @param key 字段或元数据键；没有时为空字符串
 * @param value 字段或元数据值；没有时为空字符串
 * @param label 展示标签；没有时为空字符串
 * @param metadata 附加元数据
 */
public record VisualEvent(
        long snapshotId,
        VisualEventType type,
        String graphName,
        String nodeId,
        String fromId,
        String toId,
        String key,
        String value,
        String label,
        Map<String, String> metadata
) {
    /**
     * 创建运行时可视化事件。
     */
    public VisualEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(graphName, "graphName");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(metadata, "metadata");
        if (snapshotId < 0) {
            throw new IllegalArgumentException("snapshotId must not be negative");
        }
        if (graphName.isBlank()) {
            throw new IllegalArgumentException("graphName must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }

    /**
     * 创建节点创建事件。
     *
     * @param snapshotId 快照 ID
     * @param graphName 图名称
     * @param nodeId 节点 ID
     * @param label 展示标签
     * @return 节点创建事件
     */
    public static VisualEvent nodeCreated(long snapshotId, String graphName, String nodeId, String label) {
        return new VisualEvent(
                snapshotId,
                VisualEventType.NODE_CREATED,
                graphName,
                nodeId,
                "",
                "",
                "",
                "",
                label,
                Map.of()
        );
    }

    /**
     * 创建边设置事件。
     *
     * @param snapshotId 快照 ID
     * @param graphName 图名称
     * @param key 边角色
     * @param fromId 起点 ID
     * @param toId 终点 ID
     * @return 边设置事件
     */
    public static VisualEvent edgeSet(long snapshotId, String graphName, String key, String fromId, String toId) {
        return new VisualEvent(
                snapshotId,
                VisualEventType.EDGE_SET,
                graphName,
                "",
                fromId,
                toId,
                key,
                "",
                key,
                Map.of()
        );
    }
}
