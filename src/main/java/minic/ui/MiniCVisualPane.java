package minic.ui;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import minic.uiapi.UiStageVisualDto;

import java.util.List;
import java.util.Objects;

/**
 * 当前阶段结构化可视化区域。
 */
public final class MiniCVisualPane extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCVisualModelFactory modelFactory = new MiniCVisualModelFactory();
    private final MiniCLexerOverlayModelFactory lexerOverlayModelFactory = new MiniCLexerOverlayModelFactory();
    private final MiniCAstTreeModelFactory astTreeModelFactory = new MiniCAstTreeModelFactory();
    private final MiniCAstGraphModelFactory astGraphModelFactory = new MiniCAstGraphModelFactory();
    private final MiniCSemanticScopeTreeModelFactory semanticScopeTreeModelFactory = new MiniCSemanticScopeTreeModelFactory();
    private final MiniCAssemblyTextModelFactory assemblyTextModelFactory = new MiniCAssemblyTextModelFactory();
    private final Label header = new Label("Graph View");
    private final VBox canvas = new VBox(6);
    private final ScrollPane scroller = new ScrollPane(canvas);

    /**
     * 创建 Visual Pane。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCVisualPane(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        getStyleClass().add("pane");
        header.getStyleClass().add("pane-head");
        canvas.getStyleClass().add("visual-canvas");
        scroller.getStyleClass().add("visual-scroll");
        scroller.setFitToWidth(true);
        getChildren().addAll(header, scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        refresh();
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStageVisualDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    /**
     * 刷新可视化内容。
     */
    public void refresh() {
        String stage = viewModel.currentStageDataProperty().get() == null
                ? "pending"
                : viewModel.currentStageDataProperty().get().stage();
        header.setText("Graph View · " + stage);
        UiStageVisualDto visual = viewModel.currentStageVisualDataProperty().get();
        if (visual != null && "lexer".equals(visual.visualType())) {
            canvas.getChildren().setAll(lexerRows(visual));
            return;
        }
        if (visual != null && "ast".equals(visual.visualType())) {
            canvas.getChildren().setAll(astGraph(visual));
            return;
        }
        if (visual != null && "semantic-scope".equals(visual.visualType())) {
            canvas.getChildren().setAll(semanticRows(visual));
            return;
        }
        if (visual != null && "assembly".equals(visual.visualType())) {
            canvas.getChildren().setAll(assemblyRows(visual));
            return;
        }
        List<MiniCVisualItem> items = modelFactory.create(
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        canvas.getChildren().setAll(items.stream().map(this::node).toList());
    }

    private Label node(MiniCVisualItem item) {
        Label label = new Label(item.label());
        label.getStyleClass().add("visual-node");
        if (item.hot()) {
            label.getStyleClass().add("hot");
        }
        return label;
    }

    private List<HBox> astRows(UiStageVisualDto visual) {
        return astTreeModelFactory.create(visual).stream()
                .map(this::astRow)
                .toList();
    }

    private HBox astRow(MiniCAstTreeLine line) {
        HBox row = new HBox();
        row.getStyleClass().add("ast-row");
        Label label = new Label("  ".repeat(line.depth()) + line.label());
        label.getStyleClass().add("ast-node-line");
        if (line.active()) {
            label.getStyleClass().add("active");
        }
        row.getChildren().add(label);
        return row;
    }

    private Pane astGraph(UiStageVisualDto visual) {
        MiniCAstGraphModel graph = astGraphModelFactory.create(visual);
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
                circle.getStyleClass().add("hot");
            }
            if (node.leaf()) {
                circle.getStyleClass().add("leaf");
            }
            Text text = new Text(shortLabel(node.label()));
            text.getStyleClass().add("ast-graph-label");
            text.setX(node.x() - 32);
            text.setY(node.y() + 4);
            text.setWrappingWidth(64);
            text.setFill(Color.web("#d4d4d4"));
            pane.getChildren().addAll(circle, text);
        });
        return pane;
    }

    private String shortLabel(String label) {
        String compact = label
                .replace("FunctionDecl", "Fn")
                .replace("BlockStmt", "Block")
                .replace("ReturnStmt", "Return")
                .replace("IfStmt", "If")
                .replace("BinaryExpr", "Bin")
                .replace("IntegerLiteralExpr", "Int")
                .replace("NameExpr", "Name");
        return compact.length() <= 12 ? compact : compact.substring(0, 12);
    }

    private List<HBox> assemblyRows(UiStageVisualDto visual) {
        return assemblyTextModelFactory.create(visual).stream()
                .map(this::assemblyRow)
                .toList();
    }

    private HBox assemblyRow(MiniCAssemblyTextLine line) {
        HBox row = new HBox();
        row.getStyleClass().add("assembly-row");
        Label number = new Label(Integer.toString(line.lineNumber()));
        number.getStyleClass().add("assembly-line-number");
        Label text = new Label(line.text());
        text.getStyleClass().add("assembly-text");
        if (line.active()) {
            number.getStyleClass().add("active");
            text.getStyleClass().add("active");
        }
        row.getChildren().addAll(number, text);
        return row;
    }

    private List<HBox> semanticRows(UiStageVisualDto visual) {
        return semanticScopeTreeModelFactory.create(visual).stream()
                .map(this::semanticRow)
                .toList();
    }

    private HBox semanticRow(MiniCSemanticScopeTreeLine line) {
        HBox row = new HBox();
        row.getStyleClass().add("semantic-row");
        Label label = new Label("  ".repeat(line.depth()) + "↑ " + line.label() + "  " + String.join(", ", line.symbols()));
        label.getStyleClass().add("semantic-scope-line");
        if (line.active()) {
            label.getStyleClass().add("active");
        }
        if (line.onActivePath()) {
            label.getStyleClass().add("path");
        }
        row.getChildren().add(label);
        return row;
    }

    private List<HBox> lexerRows(UiStageVisualDto visual) {
        return lexerOverlayModelFactory.create(viewModel.sourceTextProperty().get(), visual).stream()
                .map(this::lexerRow)
                .toList();
    }

    private HBox lexerRow(MiniCLexerOverlayLine line) {
        HBox row = new HBox();
        row.getStyleClass().add("lexer-row");
        Label number = new Label(Integer.toString(line.lineNumber()));
        number.getStyleClass().add("lexer-line-number");
        row.getChildren().add(number);
        row.getChildren().addAll(line.segments().stream()
                .map(segment -> segment(segment.text(), segment.active()))
                .toList());
        return row;
    }

    private Label segment(String text, boolean active) {
        Label label = new Label(text.isEmpty() ? " " : text);
        label.getStyleClass().add("lexer-segment");
        if (active) {
            label.getStyleClass().add("active");
        }
        return label;
    }
}
