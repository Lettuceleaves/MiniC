package minic.runtime.debug.visual;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisualAnnotationParserTest {
    @Test
    void parsesMinimalVisualSpecsWithDefaultAndKebabCaseKinds() {
        SourceFile sourceFile = new SourceFile("visual-specs.mc", """
                // @visual root=arr
                // @visual root=matrix kind=matrix
                // @visual root=head kind=struct-list next=next label=value
                // @visual root=points kind=struct-array fields=x,y
                int main() { return 0; }
                """);

        VisualAnnotationParser parser = new VisualAnnotationParser();
        VisualAnnotationParseResult result = parser.parse(sourceFile);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).hasSize(4);
        assertThat(result.specs()).extracting(VisualSpec::root)
                .containsExactly("arr", "matrix", "head", "points");
        assertThat(result.specs()).extracting(VisualSpec::kind)
                .containsExactly(VisualKind.AUTO, VisualKind.MATRIX, VisualKind.STRUCT_LIST, VisualKind.STRUCT_ARRAY);
        assertThat(result.specs().get(0).name()).isEqualTo("arr");
        assertThat(result.specs().get(2).attributes())
                .containsEntry("next", "next")
                .containsEntry("label", "value");
        assertThat(result.specs().get(3).fields()).containsExactly("x", "y");
        assertThat(parser.specs(sourceFile)).isEqualTo(result.specs());
    }

    @Test
    void normalizesSnakeCaseAndUppercaseVisualKinds() {
        SourceFile sourceFile = new SourceFile("visual-kind-aliases.mc", """
                // @visual root=head kind=STRUCT_LIST next=next
                // @visual root=links kind=pointer_chain
                // @visual root=head2 kind=doubly_list
                // @visual root=tree kind=BINARY_TREE
                int main() { return 0; }
                """);

        VisualAnnotationParser parser = new VisualAnnotationParser();
        VisualAnnotationParseResult result = parser.parse(sourceFile);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).extracting(VisualSpec::kind)
                .containsExactly(
                        VisualKind.STRUCT_LIST,
                        VisualKind.POINTER_CHAIN,
                        VisualKind.DOUBLY_LIST,
                        VisualKind.BINARY_TREE
                );
    }

    @Test
    void warnsForUnknownKindButUsesDefaultNextForListTemplates() {
        SourceFile sourceFile = new SourceFile("visual-spec-warnings.mc", """
                // @visual root=data kind=alien-array
                // @visual root=head kind=struct-list label=value
                // @visual root=head2 kind=singly-list
                // @visual root=head3 kind=lru-list label=key
                int main() { return 0; }
                """);

        VisualAnnotationParser parser = new VisualAnnotationParser();
        VisualAnnotationParseResult result = parser.parse(sourceFile);

        assertThat(result.specs()).extracting(VisualSpec::kind)
                .containsExactly(VisualKind.AUTO, VisualKind.STRUCT_LIST, VisualKind.SINGLY_LIST, VisualKind.LRU_LIST);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("kind 不支持：alien-array"));
        assertThat(result.warnings()).noneSatisfy(warning -> assertThat(warning).contains("缺少 next"));
    }

    @Test
    void parsesUserTemplatesThatReduceManualConfiguration() {
        SourceFile sourceFile = new SourceFile("visual-user-templates.mc", """
                // @visual root=head kind=doubly-list label=value
                // @visual root=cache kind=lru-list label=key
                // @visual root=root kind=binary-tree label=value
                // @visual root=buckets kind=hash-chain-table label=key
                // @visual root=items kind=stack
                // @visual root=items kind=queue
                // @visual root=items kind=deque
                // @visual root=items kind=circular-queue
                // @visual root=heap kind=heap
                // @visual root=bit kind=fenwick-tree
                // @visual root=fa kind=dsu
                // @visual root=edges kind=adjacency-list
                // @visual root=trie kind=trie
                // @visual root=seg kind=segment-tree
                // @visual root=ver kind=persistent-tree
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(sourceFile);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.specs()).extracting(VisualSpec::kind)
                .containsExactly(
                        VisualKind.DOUBLY_LIST,
                        VisualKind.LRU_LIST,
                        VisualKind.BINARY_TREE,
                        VisualKind.HASH_CHAIN_TABLE,
                        VisualKind.STACK,
                        VisualKind.QUEUE,
                        VisualKind.DEQUE,
                        VisualKind.CIRCULAR_QUEUE,
                        VisualKind.HEAP,
                        VisualKind.FENWICK_TREE,
                        VisualKind.DSU,
                        VisualKind.ADJACENCY_LIST,
                        VisualKind.TRIE,
                        VisualKind.SEGMENT_TREE,
                        VisualKind.PERSISTENT_TREE
                );
    }

    @Test
    void parsesLegacyAnnotationsAndVisualSpecsInOneResult() {
        SourceFile sourceFile = new SourceFile("mixed-visual.mc", """
                // @visual graph name=legacy root=head
                // @visual root=points kind=struct-array fields=x,y
                // @visual-node graph=legacy id=1 label=head
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(sourceFile);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.annotations()).hasSize(2);
        assertThat(result.annotations()).extracting(VisualAnnotation::name)
                .containsExactly("legacy", "legacy");
        assertThat(result.specs()).singleElement().satisfies(spec -> {
            assertThat(spec.root()).isEqualTo("points");
            assertThat(spec.kind()).isEqualTo(VisualKind.STRUCT_ARRAY);
            assertThat(spec.fields()).containsExactly("x", "y");
        });
    }

    @Test
    void parsesGraphArrayCompositeNodeAndEdgeAnnotations() {
        SourceFile sourceFile = new SourceFile("visual.mc", """
                // @visual graph name=tree kind=tree root=root node=Node left=left right=right label=value
                // @visual array name=arr kind=array root=a length=n label=value
                // @visual composite name=cache kind=hash_table
                // @visual-node graph=network id=i label=name
                // @visual-edge graph=network from=u to=v label=w directed=true
                // @visual-map node graph=network id=index label=value
                // @visual-map edge graph=network key=left from=index to=left
                // @visual-map meta graph=network key=height node=index value=height
                // @visual-map node graph=tree id=node label=node->value
                // @visual-map edge graph=tree key=left from=node to=node->left
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(sourceFile);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.annotations()).hasSize(10);
        assertThat(result.annotations()).extracting(VisualAnnotation::structureType)
                .containsExactly("graph", "array", "composite", "graph", "graph", "node", "edge", "meta", "node", "edge");
        assertThat(result.annotations()).extracting(VisualAnnotation::name)
                .contains("tree", "arr", "cache", "network");
    }

    @Test
    void keepsSameGraphNameForDisconnectedComponents() {
        SourceFile sourceFile = new SourceFile("visual-components.mc", """
                // @visual-node graph=network id=1 label=a
                // @visual-node graph=network id=2 label=b
                // @visual-node graph=network id=3 label=c
                // @visual-edge graph=network from=1 to=2 directed=true
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(sourceFile);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.annotations()).extracting(VisualAnnotation::name)
                .containsOnly("network");
    }

    @Test
    void rejectsComplexRootAndComplexNodeOrEdgeValues() {
        SourceFile sourceFile = new SourceFile("visual-invalid.mc", """
                // @visual graph name=bad root=a[0]
                // @visual-node graph=g id=i+1
                // @visual-edge graph=g from=u to=nodes[v]
                // @visual-map edge graph=g from=u to=left[index]
                // @visual-map meta graph=g key=height node=index value=height+1
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(sourceFile);

        assertThat(result.annotations()).isEmpty();
        assertThat(result.warnings()).hasSize(5);
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("root 只允许变量名"));
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("id 只允许简单变量名或字面值"));
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("to 只允许变量名、字面值或 -> 字段路径"));
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("value 只允许变量名、字面值或 -> 字段路径"));
    }
}
