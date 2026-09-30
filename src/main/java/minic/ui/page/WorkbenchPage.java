package minic.ui.page;

import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import minic.ui.component.data.UiList;
import minic.ui.component.editor.UiCodeEditor;

/**
 * MiniC 主工作台的纯布局框架。
 *
 * <p>外层 BorderPane 隔离顶栏、状态栏和两侧活动栏。中央区域先由水平
 * SplitPane 分出高层级的右侧信息面板，其左侧再用垂直 SplitPane 管理
 * 编辑器与底部面板。分隔内容允许缩小到零，Tab 栏始终保留。</p>
 */
public final class WorkbenchPage extends BorderPane {
    private static final double TOP_BAR_HEIGHT = 36;
    private static final double ACTIVITY_BAR_WIDTH = 48;
    private static final double TAB_BAR_HEIGHT = 36;
    private static final double STATUS_BAR_HEIGHT = 22;
    private static final double TOP_ITEM_WIDTH = 84;
    private static final double TAB_ITEM_WIDTH = 160;
    private static final double ACTIVITY_ITEM_HEIGHT = 48;
    private static final double MIN_EDITOR_VISIBLE_WIDTH = 44;
    private static final double MIN_EDITOR_VISIBLE_HEIGHT = 44;
    private static final double INITIAL_BOTTOM_DIVIDER_POSITION = 0.75;
    private static final double INITIAL_INFO_DIVIDER_POSITION = 0.76;

    private static final String INITIAL_SOURCE = """
            #include <stdio.h>

            int main() {
                printf("Hello, MiniC!\\n");
                return 0;
            }
            """;

    private final UiCodeEditor editor = new UiCodeEditor(INITIAL_SOURCE);

    public WorkbenchPage() {
        getStyleClass().add("workbench-page");
        setMinSize(0, 0);

        setTop(createTopBar());
        setCenter(createOuterWorkspace());
        setBottom(createStatusBar());
        editor.widthProperty().addListener((observable, previous, current) ->
                updateEditorVisibility());
        editor.heightProperty().addListener((observable, previous, current) ->
                updateEditorVisibility());
    }

    public UiCodeEditor editor() {
        return editor;
    }

    private void updateEditorVisibility() {
        editor.setVisible(
                editor.getWidth() >= MIN_EDITOR_VISIBLE_WIDTH
                        && editor.getHeight() >= MIN_EDITOR_VISIBLE_HEIGHT
        );
    }

    private Node createTopBar() {
        UiList bar = new UiList(Orientation.HORIZONTAL, TOP_ITEM_WIDTH, TOP_BAR_HEIGHT);
        bar.getStyleClass().add("workbench-top-bar");
        bar.getItems().addAll(
                topItem("MiniC", true),
                topItem("文件", false),
                topItem("编辑", false),
                topItem("选择", false),
                topItem("查看", false),
                topItem("转到", false),
                topItem("运行", false),
                topItem("终端", false),
                topItem("帮助", false)
        );
        return bar;
    }

    private Node createOuterWorkspace() {
        BorderPane workspace = new BorderPane();
        workspace.setMinSize(0, 0);
        workspace.getStyleClass().add("workbench-body");
        workspace.setLeft(createActivityBar(
                "activity-bar-left", "▤", "⌕", "⑂", "▷", "⊞", "○", "⚙"));
        workspace.setRight(createActivityBar(
                "activity-bar-right", "☷", "ƒ", "✓", "◷", "⚙"));
        workspace.setCenter(createMainContent());
        return workspace;
    }

    private Node createActivityBar(String sideClass, String... icons) {
        UiList bar = new UiList(Orientation.VERTICAL, ACTIVITY_ITEM_HEIGHT, ACTIVITY_BAR_WIDTH);
        bar.getStyleClass().addAll("activity-bar", sideClass);
        for (String iconText : icons) {
            StackPane item = new StackPane(label(iconText, "activity-bar-icon"));
            item.getStyleClass().add("activity-list-item");
            bar.getItems().add(item);
        }
        return bar;
    }

    private Node createCenterWorkspace() {
        SplitPane split = new SplitPane(createDocumentArea(), createBottomPanel());
        split.setOrientation(Orientation.VERTICAL);
        split.setMinSize(0, 0);
        split.getStyleClass().addAll(
                "workbench-center", "workbench-split-pane", "workbench-vertical-split");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(split.widthProperty());
        clip.heightProperty().bind(split.heightProperty());
        split.setClip(clip);
        split.setDividerPositions(INITIAL_BOTTOM_DIVIDER_POSITION);
        return split;
    }

    private Node createBottomPanel() {
        StackPane panel = placeholder("底部面板（输出 / 问题 / 终端）", "bottom-panel");
        panel.setPrefHeight(180);
        panel.setMaxHeight(Double.MAX_VALUE);
        return panel;
    }

    private Node createMainContent() {
        SplitPane split = new SplitPane(createCenterWorkspace(), createInfoPanel());
        split.setOrientation(Orientation.HORIZONTAL);
        split.setMinWidth(0);
        split.setMinHeight(0);
        split.getStyleClass().addAll(
                "main-content", "workbench-split-pane", "workbench-horizontal-split");
        split.setDividerPositions(INITIAL_INFO_DIVIDER_POSITION);
        return split;
    }

    private Node createInfoPanel() {
        StackPane panel = placeholder("右侧信息栏", "info-panel");
        panel.setPrefWidth(280);
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setViewOrder(-1);
        return panel;
    }

    private Node createDocumentArea() {
        BorderPane document = new BorderPane();
        document.setMinWidth(0);
        document.setMinHeight(TAB_BAR_HEIGHT);
        document.getStyleClass().add("document-area");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(document.widthProperty());
        clip.heightProperty().bind(document.heightProperty());
        document.setClip(clip);
        document.setTop(createTabBar());
        document.setCenter(editor);
        return document;
    }

    private Node createStatusBar() {
        Label appName = label("MiniC", "status-app-name");
        Label font = label("等宽字体 14px", "status-item");
        Label encoding = label("UTF-8", "status-item");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(18, appName, spacer, font, encoding);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("workbench-status-bar");
        constrainHeight(bar, 20, STATUS_BAR_HEIGHT, 24);
        return bar;
    }

    private Node createTabBar() {
        UiList bar = new UiList(Orientation.HORIZONTAL, TAB_ITEM_WIDTH, TAB_BAR_HEIGHT);
        bar.getStyleClass().add("editor-tab-bar");
        bar.getItems().addAll(
                editorTab("main.c", true),
                editorTab("lexer.c", false),
                editorTab("parser.c", false),
                editorTab("stdio.h", false),
                editorTab("compiler.c", false),
                editorTab("README.md", false),
                editorTab("settings.json", false)
        );
        return bar;
    }

    private static StackPane topItem(String text, boolean title) {
        Label label = label(text, title ? "top-bar-title" : "top-bar-item-label");
        StackPane item = new StackPane(label);
        item.getStyleClass().add("top-list-item");
        return item;
    }

    private static HBox editorTab(String title, boolean selected) {
        Label tabTitle = label(title, "editor-tab-title");
        Label close = label("×", "editor-tab-close");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox tab = new HBox(10, tabTitle, spacer, close);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.getStyleClass().addAll("editor-tab", selected ? "editor-tab-selected" : "editor-tab-idle");
        return tab;
    }

    private static StackPane placeholder(String text, String styleClass) {
        StackPane pane = new StackPane(label(text, "placeholder-title"));
        pane.setMinSize(0, 0);
        pane.getStyleClass().add(styleClass);
        return pane;
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static void constrainHeight(Region region, double min, double pref, double max) {
        region.setMinHeight(min);
        region.setPrefHeight(pref);
        region.setMaxHeight(max);
    }
}
