package minic.runtime.debug.visual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisualStructureTest {
    @Test
    void createsGraphArrayAndCompositeStructures() {
        VisualDecorator decorator = new VisualDecorator("decorator-color", "color", "node:*", Map.of("color", "red"));
        VisualValidator validator = new VisualValidator("validator-tree", "tree", "检查是否保持树形结构", List.of());

        GraphStructure graph = new GraphStructure(
                "graph-1",
                "tree",
                "binary_tree",
                "hierarchical",
                List.of(new GraphNode("n1", "root", "stack:root", "component-1", Map.of("value", "1"))),
                List.of(),
                List.of(new GraphComponent("component-1", "tree", List.of("n1"))),
                List.of(decorator),
                List.of(validator)
        );
        ArrayStructure array = new ArrayStructure(
                "array-1",
                "table",
                "matrix",
                "matrix",
                2,
                ArrayShape.twoDimensional(1, 1),
                List.of(new ArrayCell("cell-0", 0, 0, 0, "1", "stack:a[0]", Map.of())),
                List.of(),
                List.of()
        );
        CompositeStructure composite = new CompositeStructure(
                "composite-1",
                "hash",
                "hash_table",
                "array-1",
                List.of(
                        new CompositePart("part-array", array.id(), "buckets", Map.of()),
                        new CompositePart("part-graph", graph.id(), "chains", Map.of())
                ),
                List.of(new CompositeLink(
                        "link-buckets-chains",
                        "part-array",
                        "part-graph",
                        "bucket_to_chain",
                        "bucket 指向对应链表 component",
                        Map.of()
                )),
                List.of(),
                List.of()
        );

        assertThat(graph.type()).isEqualTo(VisualStructureType.GRAPH);
        assertThat(array.type()).isEqualTo(VisualStructureType.ARRAY);
        assertThat(composite.type()).isEqualTo(VisualStructureType.COMPOSITE);
        assertThat(graph.summary()).contains("graph tree", "nodes=1", "hierarchical");
        assertThat(array.summary()).contains("dimensions=2", "cells=1");
        assertThat(composite.summary()).contains("parts=2", "links=1");
    }

    @Test
    void descriptorKeepsPrimitiveSlots() {
        VisualDecorator decorator = new VisualDecorator("decorator-layout", "layout", "structure", Map.of("hint", "grid"));
        VisualValidator validator = new VisualValidator("validator-size", "size", "检查长度和容量关系", List.of("容量未知"));

        DataStructureDescriptor descriptor = new DataStructureDescriptor(
                "descriptor-array",
                "array",
                VisualStructureType.ARRAY,
                "linear",
                List.of(decorator),
                List.of(validator),
                "数组映射到连续空间基元"
        );

        assertThat(descriptor.primitiveType()).isEqualTo(VisualStructureType.ARRAY);
        assertThat(descriptor.decorators()).singleElement().isEqualTo(decorator);
        assertThat(descriptor.validators()).singleElement().isEqualTo(validator);
        assertThat(descriptor.explanation()).contains("连续空间");
    }

    @Test
    void graphStructureKeepsNodesEdgesComponentsAndMetadata() {
        GraphNode head = new GraphNode("node-head", "head", "stack:head", "component-list", Map.of("address", "stack@1"));
        GraphNode tail = new GraphNode("node-tail", "tail", "heap:tail", "component-list", Map.of("address", "heap@2"));
        GraphEdge next = new GraphEdge("edge-next", head.id(), tail.id(), "next", true, Map.of("field", "next"));
        GraphComponent component = new GraphComponent("component-list", "linked list", List.of(head.id(), tail.id()));
        VisualDecorator decorator = new VisualDecorator("decorator-edge", "edge-style", "edge:edge-next", Map.of("stroke", "solid"));

        GraphStructure list = new GraphStructure(
                "graph-list",
                "list",
                "linked_list",
                "linear",
                List.of(head, tail),
                List.of(next),
                List.of(component),
                List.of(decorator),
                List.of()
        );

        assertThat(list.nodes()).containsExactly(head, tail);
        assertThat(list.edges()).singleElement().satisfies(edge -> {
            assertThat(edge.directed()).isTrue();
            assertThat(edge.metadata()).containsEntry("field", "next");
        });
        assertThat(list.components()).singleElement().isEqualTo(component);
        assertThat(list.decorators()).singleElement().isEqualTo(decorator);
        assertThat(list.summary()).contains("edges=1", "components=1");
    }

    @Test
    void graphStructureSupportsTreeLayoutAndDisconnectedComponents() {
        GraphNode root = new GraphNode("node-root", "root", "heap:1", "component-tree", Map.of());
        GraphNode left = new GraphNode("node-left", "left", "heap:2", "component-tree", Map.of());
        GraphNode isolated = new GraphNode("node-isolated", "isolated", "heap:3", "component-extra", Map.of());
        GraphStructure graph = new GraphStructure(
                "graph-tree",
                "forest",
                "tree",
                "hierarchical",
                List.of(root, left, isolated),
                List.of(new GraphEdge("edge-left", root.id(), left.id(), "left", true, Map.of())),
                List.of(
                        new GraphComponent("component-tree", "tree", List.of(root.id(), left.id())),
                        new GraphComponent("component-extra", "extra", List.of(isolated.id()))
                ),
                List.of(),
                List.of()
        );

        assertThat(graph.layoutHint()).isEqualTo("hierarchical");
        assertThat(graph.components()).hasSize(2);
        assertThat(graph.summary()).contains("nodes=3", "components=2");
    }

    @Test
    void arrayStructureKeepsCellsShapeAndLayoutMetadata() {
        ArrayCell first = new ArrayCell("cell-0", 0, 0, 0, "1", "stack:a[0]", Map.of("index", "0"));
        ArrayCell second = new ArrayCell("cell-1", 0, 1, 1, "2", "stack:a[1]", Map.of("index", "1"));
        VisualDecorator decorator = new VisualDecorator("decorator-cell", "cell-color", "cell:cell-1", Map.of("color", "blue"));

        ArrayStructure array = new ArrayStructure(
                "array-linear",
                "arr",
                "array",
                "linear",
                1,
                ArrayShape.oneDimensional(2),
                List.of(first, second),
                List.of(decorator),
                List.of()
        );

        assertThat(array.shape()).isEqualTo(ArrayShape.oneDimensional(2));
        assertThat(array.cells()).containsExactly(first, second);
        assertThat(array.decorators()).singleElement().isEqualTo(decorator);
        assertThat(array.summary()).contains("shape=1x2", "layout=linear");
    }

    @Test
    void arrayStructureSupportsMatrixGridRingAndBucketHints() {
        ArrayStructure matrix = new ArrayStructure(
                "array-matrix",
                "dp",
                "matrix",
                "matrix",
                2,
                ArrayShape.twoDimensional(2, 3),
                List.of(new ArrayCell("cell-0-0", 0, 0, 0, "0", "stack:dp[0][0]", Map.of("role", "origin"))),
                List.of(),
                List.of()
        );
        ArrayStructure ring = new ArrayStructure(
                "array-ring",
                "queue",
                "queue",
                "ring",
                1,
                new ArrayShape(1, 4, 4, 2),
                List.of(),
                List.of(),
                List.of()
        );
        ArrayStructure buckets = new ArrayStructure(
                "array-buckets",
                "hashBuckets",
                "hash_bucket_array",
                "bucket",
                1,
                ArrayShape.oneDimensional(8),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(matrix.layoutHint()).isEqualTo("matrix");
        assertThat(matrix.summary()).contains("shape=2x3");
        assertThat(ring.layoutHint()).isEqualTo("ring");
        assertThat(ring.shape().logicalLength()).isEqualTo(2);
        assertThat(buckets.layoutHint()).isEqualTo("bucket");
    }

    @Test
    void compositeStructureKeepsPartsLinksAndPrimaryPart() {
        CompositePart buckets = new CompositePart("part-buckets", "array-buckets", "hash buckets", Map.of("range", "static"));
        CompositePart chains = new CompositePart("part-chains", "graph-chains", "bucket chains", Map.of("layout", "linear"));
        CompositeLink link = new CompositeLink(
                "link-bucket-chain",
                buckets.id(),
                chains.id(),
                "bucket_to_linked_graph",
                "每个 bucket cell 指向一个链式图 component",
                Map.of("field", "next")
        );

        CompositeStructure hashTable = new CompositeStructure(
                "composite-hash",
                "hashTable",
                "hash_table",
                buckets.id(),
                List.of(buckets, chains),
                List.of(link),
                List.of(),
                List.of()
        );

        assertThat(hashTable.primaryPartId()).isEqualTo(buckets.id());
        assertThat(hashTable.parts()).containsExactly(buckets, chains);
        assertThat(hashTable.links()).singleElement().satisfies(value -> {
            assertThat(value.relation()).isEqualTo("bucket_to_linked_graph");
            assertThat(value.explanation()).contains("bucket cell");
        });
        assertThat(hashTable.summary()).contains("primary=part-buckets", "links=1");
    }

    @Test
    void compositeStructureModelsHeapArrayAndTreeProjection() {
        CompositePart heapArray = new CompositePart("part-array", "array-heap", "heap array", Map.of("layout", "linear"));
        CompositePart heapTree = new CompositePart("part-tree", "graph-heap", "tree projection", Map.of("layout", "hierarchical"));
        CompositeLink projection = new CompositeLink(
                "link-array-tree",
                heapArray.id(),
                heapTree.id(),
                "projection",
                "同一堆数据同时以数组和树两种视角展示",
                Map.of()
        );

        CompositeStructure heap = new CompositeStructure(
                "composite-heap",
                "heap",
                "heap",
                heapArray.id(),
                List.of(heapArray, heapTree),
                List.of(projection),
                List.of(),
                List.of()
        );

        assertThat(heap.parts()).extracting(CompositePart::role).containsExactly("heap array", "tree projection");
        assertThat(heap.links()).extracting(CompositeLink::relation).containsExactly("projection");
        assertThat(heap.links().getFirst().explanation()).contains("数组和树");
    }
}
