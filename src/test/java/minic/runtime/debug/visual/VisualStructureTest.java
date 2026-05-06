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

        GraphStructure graph = new GraphStructure("graph-1", "tree", "binary_tree", "hierarchical", List.of(decorator), List.of(validator));
        ArrayStructure array = new ArrayStructure("array-1", "table", "matrix", "matrix", 2, List.of(), List.of());
        CompositeStructure composite = new CompositeStructure(
                "composite-1",
                "hash",
                "hash_table",
                "array-1",
                List.of(array.id(), graph.id()),
                List.of(),
                List.of()
        );

        assertThat(graph.type()).isEqualTo(VisualStructureType.GRAPH);
        assertThat(array.type()).isEqualTo(VisualStructureType.ARRAY);
        assertThat(composite.type()).isEqualTo(VisualStructureType.COMPOSITE);
        assertThat(graph.summary()).contains("graph tree", "hierarchical");
        assertThat(array.summary()).contains("dimensions=2");
        assertThat(composite.summary()).contains("parts=2");
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
}
