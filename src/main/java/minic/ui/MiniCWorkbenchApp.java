package minic.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * MiniC Visual Workbench 的 JavaFX 应用入口。
 */
public final class MiniCWorkbenchApp extends Application {
    /**
     * UI 窗口标题。
     */
    public static final String TITLE = "MiniC Visual Workbench";

    /**
     * 默认窗口宽度。
     */
    public static final double DEFAULT_WIDTH = 1280;

    /**
     * 默认窗口高度。
     */
    public static final double DEFAULT_HEIGHT = 760;

    /**
     * 启动 JavaFX UI。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * 创建并展示首版空工作台窗口。
     *
     * @param stage 主窗口
     */
    @Override
    public void start(Stage stage) {
        stage.setTitle(TITLE);
        stage.setScene(new Scene(createShell(), DEFAULT_WIDTH, DEFAULT_HEIGHT));
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.show();
    }

    private BorderPane createShell() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("workbench-root");
        root.setStyle("-fx-background-color: #1e1e1e; -fx-font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;");

        root.setTop(titlebar());
        root.setLeft(activityBar());
        root.setCenter(content());
        root.setRight(inspector());
        root.setBottom(statusBar());
        return root;
    }

    private HBox titlebar() {
        HBox titlebar = new HBox();
        titlebar.setPadding(new Insets(0, 10, 0, 10));
        titlebar.setMinHeight(30);
        titlebar.setPrefHeight(30);
        titlebar.setStyle("-fx-background-color: #3c3c3c; -fx-alignment: center;");

        Label title = new Label(TITLE + " · C010");
        title.setStyle("-fx-text-fill: #c8c8c8; -fx-font-size: 13px;");
        titlebar.getChildren().add(title);
        return titlebar;
    }

    private VBox activityBar() {
        VBox activityBar = new VBox(6);
        activityBar.setPadding(new Insets(9, 0, 0, 0));
        activityBar.setPrefWidth(48);
        activityBar.setStyle("-fx-background-color: #333333;");
        activityBar.getChildren().addAll(
                activityLabel("▣"),
                activityLabel("⌕"),
                activityLabel("⑂"),
                activityLabel("▷"),
                activityLabel("⚙")
        );
        return activityBar;
    }

    private Label activityLabel(String text) {
        Label label = new Label(text);
        label.setMinSize(48, 43);
        label.setStyle("-fx-text-fill: #bdbdbd; -fx-font-size: 20px; -fx-alignment: center;");
        return label;
    }

    private HBox content() {
        HBox content = new HBox();
        content.setStyle("-fx-background-color: #1e1e1e;");
        content.getChildren().addAll(sidebar(), editor());
        HBox.setHgrow(editor(), Priority.ALWAYS);
        return content;
    }

    private VBox sidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(260);
        sidebar.setStyle("-fx-background-color: #252526; -fx-border-color: #1b1b1b; -fx-border-width: 0 1 0 0;");
        sidebar.getChildren().addAll(
                panelTitle("Explorer"),
                sectionLabel("MINIC WORKSPACE"),
                bodyText("samples\nmain.mc\nprintf.mc"),
                sectionLabel("PIPELINE"),
                bodyText("Source\nLexer\nParser\nSemantic\nIR\nCodegen")
        );
        return sidebar;
    }

    private VBox editor() {
        VBox editor = new VBox();
        editor.setStyle("-fx-background-color: #1e1e1e;");
        HBox.setHgrow(editor, Priority.ALWAYS);

        Label tab = new Label("C  main.mc");
        tab.setMinHeight(35);
        tab.setPadding(new Insets(0, 12, 0, 12));
        tab.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #ffffff; -fx-border-color: #1b1b1b; -fx-border-width: 0 1 1 0;");

        Label placeholder = new Label("JavaFX UI shell is ready. C020 will bind MiniCObservationApi state.");
        placeholder.setPadding(new Insets(16));
        placeholder.setStyle("-fx-text-fill: #d4d4d4; -fx-font-family: Consolas, monospace;");
        VBox.setVgrow(placeholder, Priority.ALWAYS);

        editor.getChildren().addAll(tab, placeholder);
        return editor;
    }

    private VBox inspector() {
        VBox inspector = new VBox();
        inspector.setPrefWidth(360);
        inspector.setStyle("-fx-background-color: #252526; -fx-border-color: #1b1b1b; -fx-border-width: 0 0 0 1;");
        inspector.getChildren().addAll(
                panelTitle("MiniC Observation"),
                sectionLabel("CURRENT STATE"),
                bodyText("stage: pending\nglobalStep: 0\nframeInterval: 0ms"),
                sectionLabel("NEXT"),
                bodyText("C020 will introduce the JavaFX ViewModel.")
        );
        return inspector;
    }

    private HBox statusBar() {
        HBox statusBar = new HBox();
        statusBar.setMinHeight(22);
        statusBar.setPrefHeight(22);
        statusBar.setPadding(new Insets(0, 10, 0, 10));
        statusBar.setStyle("-fx-background-color: #007acc; -fx-alignment: center-left;");

        Label left = new Label("MiniC Visual Workbench · VS Code style");
        left.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12px;");
        Region spacer = new Region();
        Label right = new Label("C010 · JavaFX shell");
        right.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12px;");

        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusBar.getChildren().addAll(left, spacer, right);
        return statusBar;
    }

    private Label panelTitle(String text) {
        Label label = new Label(text);
        label.setMinHeight(36);
        label.setPadding(new Insets(0, 14, 0, 14));
        label.setStyle("-fx-text-fill: #bbbbbb; -fx-font-size: 11px;");
        return label;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setMinHeight(28);
        label.setPadding(new Insets(0, 14, 0, 14));
        label.setStyle("-fx-background-color: #2d2d2d; -fx-text-fill: #cccccc; -fx-font-size: 11px; -fx-font-weight: bold;");
        return label;
    }

    private Label bodyText(String text) {
        Label label = new Label(text);
        label.setPadding(new Insets(10, 14, 10, 14));
        label.setStyle("-fx-text-fill: #d4d4d4; -fx-font-family: Consolas, monospace; -fx-font-size: 12px;");
        return label;
    }
}
