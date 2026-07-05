package minic.uilocal;

import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import minic.uiapi.UiDebugVisualElementDto;
import minic.uiapi.UiDebugVisualStructureDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCDebugVisualDiagramRendererTest {
    @Test
    void rendersMixedGridArrayCellsGraphNodesAndEdgesTogether() {
        MiniCDebugVisualDiagramRenderer renderer = new MiniCDebugVisualDiagramRenderer((node, element) -> {
        });
        UiDebugVisualStructureDto visual = new UiDebugVisualStructureDto(
                "hash",
                "hash",
                "GRAPH",
                "natural",
                "grid",
                "hash",
                "",
                List.of(
                        new UiDebugVisualElementDto(
                                "hash-cell-0",
                                "GRAPH_NODE",
                                "[0]",
                                Map.of(
                                        "id", "cell-0",
                                        "visual-shape", "SQUARE",
                                        "gridX", "-2",
                                        "gridY", "0",
                                        "gridWidth", "2",
                                        "gridHeight", "2"
                                )
                        ),
                        new UiDebugVisualElementDto(
                                "hash-node-entry-0",
                                "GRAPH_NODE",
                                "5",
                                Map.of(
                                        "id", "entry-0",
                                        "visual-shape", "RECT",
                                        "gridX", "-2",
                                        "gridY", "6",
                                        "gridWidth", "4",
                                        "gridHeight", "2"
                                )
                        ),
                        new UiDebugVisualElementDto(
                                "hash-edge-cell-entry",
                                "GRAPH_EDGE",
                                "bucket",
                                Map.of(
                                        "from", "cell-0",
                                        "to", "entry-0",
                                        "gridStartX", "0",
                                        "gridStartY", "1",
                                        "gridEndX", "0",
                                        "gridEndY", "6"
                                )
                        )
                )
        );

        Pane pane = (Pane) renderer.visualDiagram(visual);

        assertThat(pane.getChildren().stream()
                .filter(Rectangle.class::isInstance)
                .map(node -> node.getStyleClass().toString())
                .filter(classes -> classes.contains("debug-grid-square")))
                .hasSize(1);
        assertThat(pane.getChildren().stream()
                .filter(Rectangle.class::isInstance)
                .map(node -> node.getStyleClass().toString())
                .filter(classes -> classes.contains("debug-grid-node")))
                .hasSize(1);
        assertThat(pane.getChildren().stream().filter(Line.class::isInstance)).hasSize(1);
    }

    @Test
    void rendersStructRectanglesAsVerticalValueTables() {
        MiniCDebugVisualDiagramRenderer renderer = new MiniCDebugVisualDiagramRenderer((node, element) -> {
        });
        UiDebugVisualStructureDto visual = new UiDebugVisualStructureDto(
                "hash",
                "hash",
                "GRAPH",
                "natural",
                "grid",
                "hash",
                "",
                List.of(new UiDebugVisualElementDto(
                        "hash-node-entry-0",
                        "GRAPH_NODE",
                        "e2",
                        Map.ofEntries(
                                Map.entry("id", "entry-0"),
                                Map.entry("visual-shape", "RECT"),
                                Map.entry("visual-content", "STRUCT_TABLE"),
                                Map.entry("visual-row-count", "3"),
                                Map.entry("visual-row.0", "5"),
                                Map.entry("visual-row.1", "50"),
                                Map.entry("visual-row.2", "s@1234"),
                                Map.entry("gridX", "0"),
                                Map.entry("gridY", "0"),
                                Map.entry("gridWidth", "4"),
                                Map.entry("gridHeight", "6")
                        )
                ))
        );

        Pane pane = (Pane) renderer.visualDiagram(visual);

        assertThat(pane.getChildren().stream()
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .map(Text::getText))
                .containsExactly("5", "50", "s@1234");
        assertThat(pane.getChildren().stream()
                .filter(Line.class::isInstance)
                .map(node -> node.getStyleClass().toString())
                .filter(classes -> classes.contains("debug-grid-table-line")))
                .hasSize(2);
    }

    @Test
    void appliesGenericVisualStyleMetadataToGridNodes() {
        MiniCDebugVisualDiagramRenderer renderer = new MiniCDebugVisualDiagramRenderer((node, element) -> {
        });
        UiDebugVisualStructureDto visual = new UiDebugVisualStructureDto(
                "rb",
                "rb",
                "GRAPH",
                "natural",
                "grid",
                "rb",
                "",
                List.of(new UiDebugVisualElementDto(
                        "rb-node-root",
                        "GRAPH_NODE",
                        "root",
                        Map.ofEntries(
                                Map.entry("id", "root"),
                                Map.entry("visual-shape", "RECT"),
                                Map.entry("visual-style-class", "debug-tree-node-black"),
                                Map.entry("visual-fill", "#1f2329"),
                                Map.entry("visual-stroke", "#aeb7c5"),
                                Map.entry("visual-text-fill", "#f8fafc"),
                                Map.entry("gridX", "0"),
                                Map.entry("gridY", "0"),
                                Map.entry("gridWidth", "4"),
                                Map.entry("gridHeight", "2")
                        )
                ))
        );

        Pane pane = (Pane) renderer.visualDiagram(visual);

        Rectangle rectangle = pane.getChildren().stream()
                .filter(Rectangle.class::isInstance)
                .map(Rectangle.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(rectangle.getStyleClass()).contains("debug-tree-node-black");
        assertThat(rectangle.getStyle()).contains("-fx-fill: #1f2329").contains("-fx-stroke: #aeb7c5");
        assertThat(pane.getChildren().stream()
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .map(text -> text.getFill().toString()))
                .contains("0xf8fafcff");
    }

    @Test
    void usesDefaultGridNodeClassWhenVisualStyleMetadataIsAbsent() {
        MiniCDebugVisualDiagramRenderer renderer = new MiniCDebugVisualDiagramRenderer((node, element) -> {
        });
        UiDebugVisualStructureDto visual = new UiDebugVisualStructureDto(
                "rb",
                "rb",
                "GRAPH",
                "natural",
                "grid",
                "rb",
                "",
                List.of(new UiDebugVisualElementDto(
                        "rb-node-root",
                        "GRAPH_NODE",
                        "root",
                        Map.of(
                                "id", "root",
                                "visual-shape", "RECT",
                                "gridX", "0",
                                "gridY", "0",
                                "gridWidth", "4",
                                "gridHeight", "2"
                        )
                ))
        );

        Pane pane = (Pane) renderer.visualDiagram(visual);

        Rectangle rectangle = pane.getChildren().stream()
                .filter(Rectangle.class::isInstance)
                .map(Rectangle.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(rectangle.getStyleClass()).contains("debug-grid-node");
        assertThat(rectangle.getStyleClass()).doesNotContain("debug-tree-node-black", "debug-tree-node-red");
        assertThat(rectangle.getStyle()).isBlank();
    }

    @Test
    void nonGridRendererDoesNotApplyLegacyBucketLayoutFromKind() {
        MiniCDebugVisualDiagramRenderer renderer = new MiniCDebugVisualDiagramRenderer((node, element) -> {
        });
        UiDebugVisualStructureDto visual = new UiDebugVisualStructureDto(
                "hash",
                "hash",
                "GRAPH",
                "hash-chain-table",
                "bucketed",
                "hash",
                "",
                List.of(
                        new UiDebugVisualElementDto(
                                "hash-node-bucket-0",
                                "GRAPH_NODE",
                                "[0]",
                                Map.of(
                                        "id", "bucket-0",
                                        "visual-role", "bucket",
                                        "bucketIndex", "0"
                                )
                        ),
                        new UiDebugVisualElementDto(
                                "hash-node-chain-0",
                                "GRAPH_NODE",
                                "entry",
                                Map.of(
                                        "id", "entry-0",
                                        "visual-role", "chain-node",
                                        "bucketIndex", "0",
                                        "chainDepth", "0"
                                )
                        )
                )
        );

        Pane pane = (Pane) renderer.visualDiagram(visual);

        assertThat(pane.getChildren().stream()
                .filter(javafx.scene.shape.Circle.class::isInstance)
                .map(javafx.scene.shape.Circle.class::cast)
                .map(javafx.scene.shape.Circle::getCenterY))
                .containsExactly(46.0, 46.0);
    }
}
