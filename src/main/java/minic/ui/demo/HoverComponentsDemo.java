package minic.ui.demo;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import minic.ui.component.UiStyles;
import minic.ui.component.action.UiHoverMenuButton;
import minic.ui.component.feedback.UiTooltip;
import minic.ui.component.navigation.UiContextMenu;

/** 可从“帮助”打开的真实组件演示；每个入口均可鼠标和键盘操作。 */
public final class HoverComponentsDemo {
    private HoverComponentsDemo() { }

    public static void main(String[] args) {
        Application.launch(DemoApplication.class, args);
    }

    public static final class DemoApplication extends Application {
        @Override public void start(Stage stage) {
            open(stage);
        }
    }

    public static void show(Window owner) {
        Stage stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        open(stage);
    }

    private static void open(Stage stage) {
        stage.setTitle("MiniC · 菜单与悬停提示");
        Scene scene = new Scene(createContent(), 1000, 720);
        UiStyles.install(scene);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(660);
        stage.show();
    }

    public static Parent createContent() {
        Label feedback = text("就绪 · 将鼠标停留在下方控件上", "hover-demo-feedback");
        Label title = text("菜单与悬停提示", "hover-demo-title");
        Label eyebrow = text("MINIC  /  INTERACTION COMPONENTS", "hover-demo-eyebrow");
        Label subtitle = text("为不同位置提供不同密度的菜单，以及轻量、清晰的悬停说明。", "hover-demo-subtitle");
        VBox heading = new VBox(8, eyebrow, title, subtitle);

        UiHoverMenuButton file = menu("文件", "menu-demo-title", UiContextMenu.Style.MENUBAR,
                action("新建文件", "Shortcut+N", feedback),
                action("打开文件…", "Shortcut+O", feedback),
                new SeparatorMenuItem(),
                action("保存", "Shortcut+S", feedback));
        Menu recent = new Menu("打开最近的文件");
        recent.getItems().addAll(action("main.c", null, feedback), action("parser.c", null, feedback));
        file.getItems().add(2, recent);
        MenuItem disabled = action("关闭已保存的文件", null, feedback);
        disabled.setDisable(true);
        file.getItems().addAll(new SeparatorMenuItem(), disabled);
        file.getStyleClass().add("app-menu-button");
        HBox menubar = new HBox(file, text("编辑", "hover-demo-nav-label"), text("查看", "hover-demo-nav-label"));
        menubar.setAlignment(Pos.CENTER_LEFT);
        menubar.getStyleClass().add("hover-demo-menubar");
        VBox first = card("01", "菜单栏", "紧凑的命令列表 · 快捷键 · 级联子菜单", menubar);
        first.getChildren().add(codePreview());

        UiHoverMenuButton toolbar = menu("编辑器操作", "menu-demo-toolbar", UiContextMenu.Style.TOOLBAR,
                action("向右拆分", null, feedback), action("向下拆分", null, feedback),
                new SeparatorMenuItem(), action("打开其他文件到右侧…", null, feedback));
        CheckMenuItem minimap = new CheckMenuItem("显示行号");
        minimap.setSelected(true);
        minimap.setOnAction(event -> feedback.setText("演示：行号" + (minimap.isSelected() ? "已显示" : "已隐藏")));
        toolbar.getItems().addAll(new SeparatorMenuItem(), minimap);
        VBox second = card("02", "工具栏菜单", "更宽松的操作项 · 分组分隔 · 勾选状态", toolbar);
        second.getChildren().add(text("悬停展开，再移入菜单选择操作。", "hover-demo-hint"));

        Button simple = new Button("＋");
        simple.setId("tooltip-demo-simple");
        simple.getStyleClass().addAll("hover-demo-icon-button", "editor-file-action");
        simple.setTooltip(new UiTooltip("新建文件"));
        simple.setOnAction(event -> feedback.setText("演示：新建文件"));
        Button rich = new Button("向右拆分");
        rich.setId("tooltip-demo-rich");
        rich.getStyleClass().add("hover-demo-button");
        rich.setTooltip(new UiTooltip("向右拆分编辑器", "在右侧打开当前文件的另一个视图，两个视图共享编辑内容。", "Ctrl + Alt + →"));
        rich.setOnAction(event -> feedback.setText("演示：向右拆分编辑器"));
        HBox tips = new HBox(12, simple, rich);
        tips.setAlignment(Pos.CENTER_LEFT);
        VBox third = card("03", "悬停提示浮窗", "短文本提示 / 标题、说明与快捷键", tips);
        third.getChildren().add(text("停留片刻显示 · 离开、点击或 Esc 收起", "hover-demo-hint"));

        Button target = new Button("main.c    ⋯");
        target.setId("context-demo-target");
        target.getStyleClass().add("hover-demo-button");
        UiContextMenu context = new UiContextMenu(action("向右拆分", null, feedback),
                action("复制文件路径", null, feedback), new SeparatorMenuItem(),
                action("关闭文件", "Shortcut+W", feedback));
        target.setContextMenu(context);
        target.setOnAction(event -> context.show(target, Side.BOTTOM, 0, 3));
        UiHoverMenuButton status = menu("UTF-8", "menu-demo-status", UiContextMenu.Style.MENUBAR,
                action("以编码重新打开", null, feedback), action("以编码保存", null, feedback),
                new SeparatorMenuItem(), action("UTF-8", null, feedback));
        status.setPopupSide(Side.TOP);
        status.getStyleClass().add("status-menu-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomControls = new HBox(target, spacer, status);
        bottomControls.setAlignment(Pos.CENTER_LEFT);
        VBox fourth = card("04", "右键菜单与上拉菜单", "文件操作靠近目标 · 底部入口向上展开", bottomControls);
        fourth.getChildren().add(text("右键 main.c，或悬停 UTF-8。", "hover-demo-hint"));

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(18);
        grid.add(first, 0, 0);
        grid.add(second, 1, 0);
        grid.add(third, 0, 1);
        grid.add(fourth, 1, 1);
        for (VBox card : new VBox[]{first, second, third, fourth}) {
            card.setPrefWidth(455);
            card.setMinWidth(0);
            card.setPrefHeight(228);
            card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setVgrow(card, Priority.ALWAYS);
        }
        VBox content = new VBox(26, heading, grid);
        content.setPadding(new Insets(30, 32, 22, 32));
        VBox.setVgrow(grid, Priority.ALWAYS);
        BorderPane root = new BorderPane(content);
        root.getStyleClass().add("hover-demo");
        HBox footer = new HBox(feedback);
        footer.setPadding(new Insets(10, 32, 12, 32));
        footer.getStyleClass().add("hover-demo-footer");
        root.setBottom(footer);
        return root;
    }

    private static UiHoverMenuButton menu(String label, String id, UiContextMenu.Style style, MenuItem... items) {
        UiHoverMenuButton button = new UiHoverMenuButton(label, items);
        button.setId(id);
        button.setMenuStyle(style);
        return button;
    }

    private static MenuItem action(String title, String shortcut, Label feedback) {
        MenuItem item = new MenuItem(title);
        if (shortcut != null) item.setAccelerator(KeyCombination.keyCombination(shortcut));
        item.setOnAction(event -> feedback.setText("演示：" + title));
        return item;
    }

    private static VBox card(String number, String title, String description, javafx.scene.Node controls) {
        Label index = text(number, "hover-demo-index");
        Label name = text(title, "hover-demo-card-title");
        HBox header = new HBox(10, index, name);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(14, header, text(description, "hover-demo-hint"), controls);
        card.setPadding(new Insets(20));
        card.getStyleClass().add("hover-demo-card");
        return card;
    }

    private static VBox codePreview() {
        VBox code = new VBox(7,
                text("1   int main() {", "hover-demo-code"),
                text("2       return 0;", "hover-demo-code-muted"),
                text("3   }", "hover-demo-code"));
        code.setPadding(new Insets(0, 6, 0, 6));
        return code;
    }

    private static Label text(String value, String style) {
        Label label = new Label(value);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }
}
