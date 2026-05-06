package minic.runtime.debug.visual;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataStructureDescriptorRegistryTest {
    @Test
    void containsFirstBatchDescriptorsWithoutRedBlackTree() {
        DataStructureDescriptorRegistry registry = DataStructureDescriptorRegistry.defaults();

        assertThat(registry.all()).extracting(DataStructureDescriptor::kind)
                .containsExactlyInAnyOrder(
                        "array",
                        "matrix",
                        "list",
                        "doubly_linked_list",
                        "tree",
                        "binary_tree",
                        "bst",
                        "heap",
                        "graph",
                        "hash_table",
                        "union_find"
                )
                .doesNotContain("red_black_tree", "rb_tree");
    }

    @Test
    void descriptorsMapAdvancedKindsToPrimitiveStructures() {
        DataStructureDescriptorRegistry registry = DataStructureDescriptorRegistry.defaults();

        assertThat(registry.find("tree")).hasValueSatisfying(descriptor -> {
            assertThat(descriptor.primitiveType()).isEqualTo(VisualStructureType.GRAPH);
            assertThat(descriptor.defaultLayout()).isEqualTo("hierarchical");
        });
        assertThat(registry.find("heap")).hasValueSatisfying(descriptor -> {
            assertThat(descriptor.primitiveType()).isEqualTo(VisualStructureType.COMPOSITE);
            assertThat(descriptor.explanation()).contains("数组和树双投影");
        });
        assertThat(registry.find("hash_table")).hasValueSatisfying(descriptor -> {
            assertThat(descriptor.primitiveType()).isEqualTo(VisualStructureType.COMPOSITE);
            assertThat(descriptor.decorators()).singleElement().satisfies(decorator ->
                    assertThat(decorator.attributes()).containsEntry("layout", "bucket_graph"));
            assertThat(descriptor.validators()).singleElement().satisfies(validator ->
                    assertThat(validator.explanation()).contains("bucket array"));
        });
    }
}
