package minic.runtime.debug.visual;

/**
 * 运行时可视化事件类型。
 */
public enum VisualEventType {
    /**
     * 创建或首次发现节点。
     */
    NODE_CREATED,

    /**
     * 更新节点展示数据。
     */
    NODE_UPDATED,

    /**
     * 设置或替换一条边。
     */
    EDGE_SET,

    /**
     * 移除一条边。
     */
    EDGE_REMOVED,

    /**
     * 设置节点元数据。
     */
    META_SET
}
