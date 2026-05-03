package minic.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * MiniC Visual Workbench 的 VS Code 风格外壳。
 */
public final class MiniCWorkbenchShell {
    private final MiniCWorkbenchViewModel viewModel;

    /**
     * 创建工作台外壳。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCWorkbenchShell(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    /**
     * 创建 JavaFX 根节点。
     *
     * @return 工作台根节点
     */
    public Parent createRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("workbench-root");
        root.setTop(titlebar());
        root.setLeft(activityBar());
        root.setCenter(workbenchBody());
        root.setBottom(statusBar());
        return root;
    }

    private HBox titlebar() {
        HBox titlebar = new HBox();
        titlebar.getStyleClass().add("titlebar");
        HBox traffic = new HBox(8, trafficDot(), trafficDot(), trafficDot());
        traffic.getStyleClass().add("traffic");
        Label command = new Label("MiniC Visual Workbench · JavaFX Shell");
        command.getStyleClass().add("command");
        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);
        titlebar.getChildren().addAll(traffic, leftSpacer, command, rightSpacer);
        return titlebar;
    }

    private Region trafficDot() {
        Region dot = new Region();
        dot.getStyleClass().add("traffic-dot");
        return dot;
    }

    private VBox activityBar() {
        VBox activityBar = new VBox(6);
        activityBar.getStyleClass().add("activity-bar");
        activityBar.getChildren().addAll(
                activityItem("▣", true),
                activityItem("⌕", false),
                activityItem("⑂", false),
                activityItem("▷", false),
                activityItem("⚙", false)
        );
        return activityBar;
    }

    private Label activityItem(String text, boolean active) {
        Label label = new Label(text);
        label.getStyleClass().add("activity-item");
        if (active) {
            label.getStyleClass().add("active");
        }
        return label;
    }

    private HBox workbenchBody() {
        HBox body = new HBox();
        body.getStyleClass().add("workbench-body");
        VBox sidebar = sidebar();
        VBox editor = editorArea();
        VBox inspector = inspector();
        HBox.setHgrow(editor, Priority.ALWAYS);
        body.getChildren().addAll(sidebar, editor, inspector);
        return body;
    }

    private VBox sidebar() {
        return new MiniCSidebarView(viewModel);
    }

    private VBox editorArea() {
        VBox editor = new VBox();
        editor.getStyleClass().add("editor-area");
        HBox.setHgrow(editor, Priority.ALWAYS);

        HBox tabs = new HBox();
        tabs.getStyleClass().add("tabs");
        Label sourceTab = new Label("C  main.mc");
        sourceTab.getStyleClass().addAll("tab", "active");
        Label visualTab = new Label("workbench.visual");
        visualTab.getStyleClass().add("tab");
        tabs.getChildren().addAll(sourceTab, visualTab);

        HBox split = new HBox();
        split.getStyleClass().add("split");
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox codePane = new MiniCSourceView(viewModel);
        VBox visualPane = new MiniCVisualPane(viewModel);
        HBox.setHgrow(codePane, Priority.ALWAYS);
        HBox.setHgrow(visualPane, Priority.ALWAYS);
        split.getChildren().addAll(codePane, visualPane);

        editor.getChildren().addAll(tabs, split, new MiniCBottomPanel(viewModel));
        return editor;
    }

    private VBox pane(String title, String content) {
        VBox pane = new VBox();
        pane.getStyleClass().add("pane");
        Label head = new Label(title);
        head.getStyleClass().add("pane-head");
        Label body = new Label(content);
        body.getStyleClass().add("mono-body");
        body.setPadding(new Insets(12));
        VBox.setVgrow(body, Priority.ALWAYS);
        pane.getChildren().addAll(head, body);
        return pane;
    }

    private VBox inspector() {
        VBox inspector = new VBox();
        inspector.getStyleClass().add("inspector");
        inspector.getChildren().addAll(
                panelTitle("MiniC Observation"),
                controls(),
                sectionLabel("CURRENT STATE"),
                bodyText("stage: pending\nglobalStep: 0\nstageStep: 0\nframeInterval: 0ms"),
                sectionLabel("CURRENT ITEM"),
                bodyText("C020 ViewModel is ready. Later tasks will bind live DTO fields."),
                sectionLabel("ACCUMULATED OUTPUT"),
                bodyText("tokens: 0\nast: 0\nsemantic: 0\nir: 0\nassembly: 0")
        );
        return inspector;
    }

    private HBox controls() {
        HBox controls = new HBox(6);
        controls.getStyleClass().add("controls");
        controls.getChildren().addAll(control("Next", true), control("Play", false), control("2x", false), control("Pause", false));
        return controls;
    }

    private Label control(String text, boolean primary) {
        Label label = new Label(text);
        label.getStyleClass().add(primary ? "control-primary" : "control-secondary");
        return label;
    }

    private HBox statusBar() {
        HBox status = new HBox();
        status.getStyleClass().add("status-bar");
        Label left = new Label("MiniC Visual Workbench · VS Code style");
        Label right = new Label("C030 · Shell · " + viewModel.sourceNameProperty().get());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        status.getChildren().addAll(left, spacer, right);
        return status;
    }

    private Label panelTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-title");
        return label;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private Label bodyText(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("body-text");
        return label;
    }

}
