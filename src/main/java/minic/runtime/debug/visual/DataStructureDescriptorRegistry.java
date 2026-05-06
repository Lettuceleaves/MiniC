package minic.runtime.debug.visual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 首批数据结构 descriptor 注册表。
 */
public final class DataStructureDescriptorRegistry {
    private final Map<String, DataStructureDescriptor> descriptors;

    private DataStructureDescriptorRegistry(Map<String, DataStructureDescriptor> descriptors) {
        this.descriptors = Map.copyOf(descriptors);
    }

    /**
     * 创建默认 descriptor 注册表。
     *
     * @return 注册表
     */
    public static DataStructureDescriptorRegistry defaults() {
        LinkedHashMap<String, DataStructureDescriptor> descriptors = new LinkedHashMap<>();
        add(descriptors, "array", VisualStructureType.ARRAY, "linear", "数组映射到连续空间基元");
        add(descriptors, "matrix", VisualStructureType.ARRAY, "matrix", "矩阵映射到二维表格基元");
        add(descriptors, "list", VisualStructureType.GRAPH, "linear", "链表映射到有向图基元");
        add(descriptors, "doubly_linked_list", VisualStructureType.GRAPH, "linear", "双向链表映射到带双向边的图基元");
        add(descriptors, "tree", VisualStructureType.GRAPH, "hierarchical", "树映射到图基元和层次布局");
        add(descriptors, "binary_tree", VisualStructureType.GRAPH, "hierarchical", "二叉树映射到图基元和左右子节点装饰");
        add(descriptors, "bst", VisualStructureType.GRAPH, "hierarchical", "二叉搜索树映射到图基元并保留有序性校验扩展点");
        add(descriptors, "heap", VisualStructureType.COMPOSITE, "array_tree", "堆映射到数组和树双投影");
        add(descriptors, "graph", VisualStructureType.GRAPH, "force", "普通图映射到图基元");
        add(descriptors, "hash_table", VisualStructureType.COMPOSITE, "bucket_graph", "哈希表映射到 bucket array 和链式图组合");
        add(descriptors, "union_find", VisualStructureType.GRAPH, "forest", "并查集映射到森林图基元");
        return new DataStructureDescriptorRegistry(descriptors);
    }

    /**
     * 查找 descriptor。
     *
     * @param kind 结构类别
     * @return descriptor
     */
    public Optional<DataStructureDescriptor> find(String kind) {
        return Optional.ofNullable(descriptors.get(kind));
    }

    /**
     * 返回全部 descriptor。
     *
     * @return descriptor 列表
     */
    public List<DataStructureDescriptor> all() {
        return List.copyOf(descriptors.values());
    }

    private static void add(
            LinkedHashMap<String, DataStructureDescriptor> descriptors,
            String kind,
            VisualStructureType primitiveType,
            String defaultLayout,
            String explanation
    ) {
        descriptors.put(kind, new DataStructureDescriptor(
                "descriptor-" + kind,
                kind,
                primitiveType,
                defaultLayout,
                List.of(new VisualDecorator(
                        "decorator-layout-" + kind,
                        "layout",
                        "structure",
                        Map.of("layout", defaultLayout)
                )),
                List.of(new VisualValidator(
                        "validator-" + kind,
                        kind,
                        explanation,
                        List.of()
                )),
                explanation
        ));
    }
}
