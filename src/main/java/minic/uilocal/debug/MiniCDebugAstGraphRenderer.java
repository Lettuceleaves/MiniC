package minic.uilocal;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import minic.color.ThemeRegistry;
import minic.uiapi.UiAstNodeVisualDto;
import minic.uiapi.UiDebugAstViewDto;
import minic.uiapi.UiSourceSpanDto;

final class MiniCDebugAstGraphRenderer {
    private final MiniCAstGraphModelFactory astGraphModelFactory = new MiniCAstGraphModelFactory();
    private final Slider astZoom;
    private final MiniCDebugViewportController viewportController;

    MiniCDebugAstGraphRenderer(Slider astZoom, MiniCDebugViewportController viewportController) {
        this.astZoom = astZoom;
        this.viewportController = viewportController;
    }

    Node debugAstGraph(UiDebugAstViewDto view) {
        if (view == null || view.root() == null) {
            return emptyAstPane("AST 尚未就绪");
        }
        MiniCAstGraphModel graph = astGraphModelFactory.create(view.root());
        Pane pane = new Pane();
        pane.getStyleClass().add("ast-graph");
        pane.setMinSize(graph.width(), graph.height());
        pane.setPrefSize(graph.width(), graph.height());
        graph.edges().forEach(edge -> {
            Line line = new Line(edge.fromX(), edge.fromY(), edge.toX(), edge.toY());
            line.getStyleClass().add("ast-edge");
            if (edge.hot()) {
                line.getStyleClass().add("hot");
            }
            pane.getChildren().add(line);
        });
        graph.nodes().forEach(node -> {
            Circle circle = new Circle(node.x(), node.y(), node.root() ? 30 : node.leaf() ? 22 : 26);
            circle.getStyleClass().add("ast-graph-node");
            if (node.root()) {
                circle.getStyleClass().add("root");
            }
            if (node.active()) {
                circle.getStyleClass().add("active");
            }
            if (node.leaf()) {
                circle.getStyleClass().add("leaf");
            }
            Text text = new Text(shortLabel(node.label()));
            text.getStyleClass().add("ast-graph-label");
            text.setX(node.x() - 32);
            text.setY(node.y() + 4);
            text.setWrappingWidth(64);
            text.setFill(ThemeRegistry.getColor("graph.label"));
            UiAstNodeVisualDto astNode = astNodeById(view.root(), node.id());
            String tooltip = astNode == null
                    ? node.label()
                    : astNode.kind() + " " + astNode.label() + "\n" + rangeText(astNode.range());
            circle.setAccessibleText(tooltip);
            text.setAccessibleText(tooltip);
            pane.getChildren().addAll(circle, text);
        });
        Group group = new Group(pane);
        pane.scaleXProperty().bind(astZoom.valueProperty());
        pane.scaleYProperty().bind(astZoom.valueProperty());
        pane.setManaged(false);
        group.setManaged(false);
        Pane graphViewport = new Pane(group);
        graphViewport.getStyleClass().add("ast-graph-viewport");
        viewportController.configureAstGraphViewport(graphViewport, pane, graph.width(), graph.height());

        VBox box = new VBox(6);
        box.getStyleClass().add("ast-zoom-box");
        HBox zoomControls = new HBox(8);
        zoomControls.getStyleClass().add("ast-zoom-controls");
        Label zoomLabel = new Label("缩放");
        zoomLabel.getStyleClass().add("ast-zoom-label");
        Label zoomValue = new Label();
        zoomValue.getStyleClass().add("ast-zoom-value");
        zoomValue.textProperty().bind(astZoom.valueProperty().multiply(100).asString("%.0f%%"));
        zoomControls.getChildren().addAll(zoomLabel, astZoom, zoomValue);
        box.getChildren().addAll(zoomControls, graphViewport);
        return box;
    }

    private Pane emptyAstPane(String message) {
        Pane pane = new Pane(label(message, "body-text"));
        pane.getStyleClass().add("ast-graph");
        pane.setMinSize(360, 180);
        pane.setPrefSize(360, 180);
        return pane;
    }

    private UiAstNodeVisualDto astNodeById(UiAstNodeVisualDto node, String id) {
        if (node == null) {
            return null;
        }
        if (node.id().equals(id)) {
            return node;
        }
        for (UiAstNodeVisualDto child : node.children()) {
            UiAstNodeVisualDto found = astNodeById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String shortLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        String compact = label.replace('\n', ' ').strip();
        return compact.length() <= 10 ? compact : compact.substring(0, 9) + "...";
    }

    private String rangeText(UiSourceSpanDto range) {
        if (range == null) {
            return "-";
        }
        return "line " + range.startLine() + ":" + range.startColumn()
                + ".." + range.endLine() + ":" + range.endColumn();
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
