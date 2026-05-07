package minic.runtime.debug.visual;

import minic.source.SourceFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisualAnnotationParserTest {
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
                int main() { return 0; }
                """);

        VisualAnnotationParseResult result = new VisualAnnotationParser().parse(sourceFile);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.annotations()).hasSize(8);
        assertThat(result.annotations()).extracting(VisualAnnotation::structureType)
                .containsExactly("graph", "array", "composite", "graph", "graph", "node", "edge", "meta");
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
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("to 只允许简单变量名或字面值"));
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("value 只允许简单变量名或字面值"));
    }
}
