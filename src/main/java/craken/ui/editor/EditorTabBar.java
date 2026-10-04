package craken.ui.editor;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import craken.ui.component.data.UiList;
import craken.ui.component.action.UiHoverMenuButton;
import craken.ui.component.display.UiIcon;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.navigation.UiContextMenu;

/** 标签只映射 EditorFile，不单独保存文件或编辑器状态。 */
final class EditorTabBar extends BorderPane {
    private final EditorArea area;
    private final UiList strip = new UiList(Orientation.HORIZONTAL, 160, 36);

    EditorTabBar(EditorArea area) {
        this.area = area;
        setMinSize(0, 36);
        setPrefHeight(36);
        setMaxHeight(36);
        getStyleClass().add("editor-tab-bar");
        UiHoverMenuButton more = new UiHoverMenuButton();
        more.setId("editor-tab-more");
        more.setGraphic(new UiIcon(UiIcon.Kind.MORE));
        more.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        more.setAlignment(Pos.CENTER);
        more.setAccessibleText("编辑器布局操作");
        more.setTooltip(new UiTooltip("编辑器布局", "向右或向下拆分当前文件，或在分屏中打开其他文件。", ""));
        more.setFocusTraversable(true);
        more.getStyleClass().add("editor-tab-more");
        more.setMinSize(36, 36);
        more.setPrefSize(36, 36);
        more.setMaxSize(36, 36);
        MenuItem right = menu("向右拆分当前文件", () -> area.splitFile(area.activeFile(), Orientation.HORIZONTAL));
        MenuItem bottom = menu("向下拆分当前文件", () -> area.splitFile(area.activeFile(), Orientation.VERTICAL));
        MenuItem other = menu("打开其他文件到右侧…", () -> area.chooseBeside(area.activeFile(), Orientation.HORIZONTAL));
        MenuItem otherBelow = menu("打开其他文件到下方…", () -> area.chooseBeside(area.activeFile(), Orientation.VERTICAL));
        right.disableProperty().bind(area.activeFileProperty().isNull());
        bottom.disableProperty().bind(area.activeFileProperty().isNull());
        other.disableProperty().bind(area.activeFileProperty().isNull());
        otherBelow.disableProperty().bind(area.activeFileProperty().isNull());
        more.getItems().addAll(right, bottom, other, otherBelow);
        setCenter(strip);
        setRight(more);
        area.files().addListener((ListChangeListener<EditorFile>) ignored -> rebuild());
        area.activeFileProperty().addListener(ignored -> updateSelection());
    }

    private void rebuild() {
        strip.getItems().setAll(area.files().stream().map(this::tab).toList());
        updateSelection();
    }

    private Node tab(EditorFile file) {
        Label title = new Label(file.path().getFileName().toString());
        title.getStyleClass().add("editor-tab-title");
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        Button close = EditorArea.action("×", "关闭 " + file.path().getFileName(), () -> area.closeFile(file));
        close.getStyleClass().add("editor-tab-close");
        close.setMinSize(22, 22);
        close.setPrefSize(22, 22);
        close.setMaxSize(22, 22);
        close.setPadding(Insets.EMPTY);
        close.textProperty().bind(Bindings.when(file.dirtyProperty().and(close.hoverProperty().not()))
                .then("●").otherwise("×"));
        HBox tab = new HBox(8, title, close);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.getStyleClass().add("editor-tab");
        tab.setUserData(file);
        tab.setAccessibleText(file.path().toString());
        UiTooltip.attach(tab, file.path().getFileName().toString(), file.path().toString(), "");
        tab.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.MIDDLE) {
                area.closeFile(file);
                event.consume();
            } else if (event.getButton() == MouseButton.PRIMARY && !inside(event.getTarget(), close)) {
                area.select(file);
            }
        });
        UiContextMenu context = new UiContextMenu(
                menu("向下拆分", () -> area.splitFile(file, Orientation.VERTICAL)),
                menu("向右拆分", () -> area.splitFile(file, Orientation.HORIZONTAL)),
                new SeparatorMenuItem(),
                menu("保存", () -> area.save(file)),
                menu("打开其他文件到右侧…", () -> area.chooseBeside(file, Orientation.HORIZONTAL)),
                menu("打开其他文件到下方…", () -> area.chooseBeside(file, Orientation.VERTICAL)),
                new SeparatorMenuItem(),
                menu("关闭", () -> area.closeFile(file)));
        context.setMenuStyle(UiContextMenu.Style.CONTEXT);
        context.setOnAction(event -> context.hide());
        // 拆分/关闭会重建标签，移除旧标签时一并收起其弹层，避免留下失效命令。
        tab.sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) context.hide();
        });
        tab.setOnContextMenuRequested(event -> {
            context.show(tab, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return tab;
    }

    private void updateSelection() {
        for (Node tab : strip.getItems()) {
            boolean selected = tab.getUserData() == area.activeFile();
            tab.getStyleClass().removeAll("editor-tab-selected", "editor-tab-idle");
            tab.getStyleClass().add(selected ? "editor-tab-selected" : "editor-tab-idle");
        }
        Platform.runLater(() -> strip.revealItem(area.files().indexOf(area.activeFile())));
    }

    private static boolean inside(Object target, Node parent) {
        for (Node node = target instanceof Node value ? value : null; node != null; node = node.getParent()) {
            if (node == parent) return true;
        }
        return false;
    }

    private static MenuItem menu(String title, Runnable action) {
        MenuItem item = new MenuItem(title);
        item.setOnAction(event -> action.run());
        return item;
    }
}
