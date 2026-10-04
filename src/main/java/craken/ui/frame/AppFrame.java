package craken.ui.frame;

import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import craken.ui.component.data.UiList;
import craken.ui.component.display.UiIcon;
import craken.ui.component.layout.UiRow;
import craken.ui.component.navigation.UiAppTitleBar;
import craken.ui.component.action.UiHoverMenuButton;
import craken.ui.component.navigation.UiContextMenu;
import craken.ui.component.feedback.UiTooltip;

import java.util.Objects;
import java.net.URL;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Craken 整体框架，管理标题菜单栏、两侧活动栏、文件标签栏和状态栏。
 *
 * <p>编辑区、展示区和交互区由调用者创建后传入。框架只安排三个区域的
 * 位置、裁剪和分隔比例，不创建区域内容，也不持有具体编辑器。
 * 展示区在右侧贯穿内容高度，交互区位于编辑区下方。它们和文件标签栏一起
 * 属于编辑工作区；左侧导航切换整个工作区，保留各页已有内容和布局。</p>
 */
public final class AppFrame extends BorderPane {
    public enum WorkspaceTab {
        EDITOR("editor", "编辑", UiIcon.Kind.CODE),
        EXTENSIONS("extensions", "扩展", UiIcon.Kind.EXTENSIONS),
        SETTINGS("settings", "设置", UiIcon.Kind.SETTINGS),
        PROFILE("profile", "个人主页", UiIcon.Kind.PROFILE);

        private final String id;
        private final String title;
        private final UiIcon.Kind icon;

        WorkspaceTab(String id, String title, UiIcon.Kind icon) {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }
    }

    public static final double DEFAULT_DISPLAY_WIDTH = 280;
    private static final double TOP_BAR_HEIGHT = 36;
    private static final double ACTIVITY_BAR_WIDTH = 48;
    private static final double TAB_BAR_HEIGHT = 36;
    private static final double STATUS_BAR_HEIGHT = 22;
    private static final double TOP_BAR_ICON_SIZE = 20;
    private static final double TOP_ITEM_HORIZONTAL_PADDING = 12;
    private static final double ACTIVITY_ITEM_HEIGHT = 48;
    private static final double INITIAL_INTERACTION_DIVIDER_POSITION = 0.75;
    private final DisplayPaneWidthController displayWidthController;
    private final Map<String, UiHoverMenuButton> menus = new LinkedHashMap<>();
    private final StackPane workspaceHost = new StackPane();
    private final Map<WorkspaceTab, Node> workspacePages = new EnumMap<>(WorkspaceTab.class);
    private final Map<WorkspaceTab, ToggleButton> workspaceButtons = new EnumMap<>(WorkspaceTab.class);
    private WorkspaceTab selectedWorkspaceTab = WorkspaceTab.EDITOR;
    private Button runButton;
    private Runnable onRun;
    private boolean runDisabled;
    private ToggleButton pipelineButton;
    private Runnable onPipeline;
    private boolean pipelineDisabled;
    private boolean pipelineDisplayActive;
    private boolean pipelineDisplayOpen;

    public AppFrame(Node editorArea, Node displayArea, Node interactionArea, Node tabBar) {
        Objects.requireNonNull(editorArea, "editorArea");
        Objects.requireNonNull(displayArea, "displayArea");
        Objects.requireNonNull(interactionArea, "interactionArea");
        Objects.requireNonNull(tabBar, "tabBar");
        getStyleClass().add("app-frame");
        setMinSize(0, 0);

        SplitPane content = createContent(editorArea, displayArea, interactionArea, tabBar);
        displayWidthController = new DisplayPaneWidthController(content, DEFAULT_DISPLAY_WIDTH);
        workspacePages.put(WorkspaceTab.EDITOR, content);
        workspaceHost.setId("app-workspace-host");
        workspaceHost.getStyleClass().add("app-workspace-host");
        workspaceHost.setMinSize(0, 0);
        setTop(createTopBar());
        setCenter(createBody(workspaceHost));
        setBottom(createStatusBar());
        showEditorWorkspace();
    }

    public WorkspaceTab selectedWorkspaceTab() {
        return selectedWorkspaceTab;
    }

    /** 页面只在首次访问时创建；切换不会关闭文件、终端或编译会话。 */
    public void selectWorkspaceTab(WorkspaceTab tab) {
        Objects.requireNonNull(tab, "tab");
        Node page = workspacePages.computeIfAbsent(tab, this::createWorkspacePlaceholder);
        selectedWorkspaceTab = tab;
        workspaceButtons.forEach((item, button) -> button.setSelected(item == tab));
        if (workspaceHost.getChildren().isEmpty() || workspaceHost.getChildren().getFirst() != page) {
            workspaceHost.getChildren().setAll(page);
        }
        updatePipelineSelection();
    }

    public void showEditorWorkspace() {
        selectWorkspaceTab(WorkspaceTab.EDITOR);
    }

    /** 将右侧信息栏展开到左侧极限。 */
    public void expandDisplayArea() {
        showEditorWorkspace();
        pipelineDisplayOpen = true;
        updatePipelineSelection();
        displayWidthController.expand();
    }

    /** 将右侧信息栏收起到右侧极限。 */
    public void collapseDisplayArea() {
        showEditorWorkspace();
        pipelineDisplayOpen = false;
        updatePipelineSelection();
        displayWidthController.collapse();
    }

    /** 将右侧信息栏恢复为固定默认宽度。 */
    public void restoreDefaultDisplayAreaWidth() {
        showEditorWorkspace();
        pipelineDisplayOpen = true;
        updatePipelineSelection();
        displayWidthController.restoreDefault();
    }

    /** 显式打开 Pipeline；重复调用保持展开，只有图标点击负责切换开关。 */
    public void showPipelineDisplayArea() {
        pipelineDisplayActive = true;
        expandDisplayArea();
    }

    /** 运行图标仅转发命令，具体取码、编译与执行由调用者负责。 */
    public void setOnRun(Runnable action) {
        onRun = action;
        updateRunButton();
    }

    public void setRunDisabled(boolean disabled) {
        runDisabled = disabled;
        updateRunButton();
    }

    private void updateRunButton() {
        if (runButton != null) runButton.setDisable(runDisabled || onRun == null);
    }

    /** 打开时转发命令；再次点击已选中图标只收起信息栏，保留编译会话。 */
    public void setOnPipeline(Runnable action) {
        onPipeline = action;
        updatePipelineButton();
    }

    public void setPipelineDisabled(boolean disabled) {
        pipelineDisabled = disabled;
        updatePipelineButton();
    }

    private void updatePipelineButton() {
        if (onPipeline == null) {
            pipelineDisplayActive = false;
        }
        updatePipelineSelection();
        if (pipelineButton != null) pipelineButton.setDisable(pipelineDisabled || onPipeline == null);
    }

    private void updatePipelineSelection() {
        if (pipelineButton != null) pipelineButton.setSelected(selectedWorkspaceTab == WorkspaceTab.EDITOR
                && pipelineDisplayActive && pipelineDisplayOpen);
    }

    /** 各功能区注入自己的菜单命令，框架仍只负责排布。 */
    public void setMenuItems(String name, MenuItem... items) {
        UiHoverMenuButton menu = menus.get(name);
        if (menu == null) throw new IllegalArgumentException("Unknown menu: " + name);
        menu.getItems().setAll(items);
    }

    private Node createTopBar() {
        UiRow bar = new UiRow();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefHeight(TOP_BAR_HEIGHT);
        bar.setMinWidth(Region.USE_PREF_SIZE);
        bar.setMaxWidth(Region.USE_PREF_SIZE);
        bar.getStyleClass().add("app-menu-strip");
        bar.getChildren().addAll(
                topItemWithIcon("Craken"),
                menuButton("文件"),
                topItem("编辑", false),
                topItem("选择", false),
                menuButton("查看"),
                topItem("转到", false),
                topItem("运行", false),
                menuButton("终端"),
                menuButton("帮助")
        );
        setMenuItems("查看",
                command("展开右侧信息栏", this::expandDisplayArea),
                command("收起右侧信息栏", this::collapseDisplayArea),
                new SeparatorMenuItem(),
                command("恢复默认布局宽度", this::restoreDefaultDisplayAreaWidth));
        return new UiAppTitleBar(bar);
    }

    private UiHoverMenuButton menuButton(String title) {
        UiHoverMenuButton button = new UiHoverMenuButton(title);
        button.setMenuStyle(UiContextMenu.Style.MENUBAR);
        button.getStyleClass().add("app-menu-button");
        button.setPrefHeight(TOP_BAR_HEIGHT);
        button.setMinHeight(TOP_BAR_HEIGHT);
        button.setMaxHeight(TOP_BAR_HEIGHT);
        button.setId("app-menu-" + switch (title) {
            case "文件" -> "file";
            case "查看" -> "view";
            case "终端" -> "terminal";
            default -> "help";
        });
        menus.put(title, button);
        return button;
    }

    private static MenuItem command(String title, Runnable action) {
        MenuItem item = new MenuItem(title);
        item.setOnAction(event -> action.run());
        return item;
    }

    private Node createBody(Node content) {
        BorderPane body = new BorderPane();
        body.setMinSize(0, 0);
        body.getStyleClass().add("app-body");
        body.setLeft(createWorkspaceActivityBar());
        body.setRight(createDisplayActivityBar());
        body.setCenter(content);
        return body;
    }

    private Node createWorkspaceActivityBar() {
        UiList bar = new UiList(Orientation.VERTICAL, ACTIVITY_ITEM_HEIGHT, ACTIVITY_BAR_WIDTH);
        bar.getStyleClass().addAll("activity-bar", "activity-bar-left");
        for (WorkspaceTab tab : WorkspaceTab.values()) {
            UiIcon icon = new UiIcon(tab.icon);
            icon.getStyleClass().add("activity-bar-icon");
            ToggleButton button = new ToggleButton();
            button.setGraphic(icon);
            button.setId("app-" + tab.id + "-button");
            button.getStyleClass().addAll("activity-list-item", "activity-workspace-button");
            button.setAccessibleText(tab.title);
            button.setTooltip(new UiTooltip(tab.title));
            button.setMinSize(0, 0);
            button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            button.setOnAction(event -> selectWorkspaceTab(tab));
            workspaceButtons.put(tab, button);
            bar.getItems().add(button);
        }
        return bar;
    }

    private Node createWorkspacePlaceholder(WorkspaceTab tab) {
        UiIcon icon = new UiIcon(tab.icon, 32);
        Label title = label(tab.title, "workspace-placeholder-title");
        Label hint = label(switch (tab) {
            case EXTENSIONS -> "扩展功能尚未开放";
            case SETTINGS -> "设置功能尚未开放";
            case PROFILE -> "个人主页尚未开放";
            case EDITOR -> throw new IllegalArgumentException("The editor workspace is supplied by the caller");
        }, "workspace-placeholder-hint");
        VBox page = new VBox(14, icon, title, hint);
        page.setId("app-" + tab.id + "-page");
        page.getStyleClass().add("app-workspace-placeholder");
        page.setAlignment(Pos.CENTER);
        page.setPadding(new Insets(24));
        page.setMinSize(0, 0);
        return page;
    }

    private Node createActivityBar(String sideClass, UiIcon.Kind... icons) {
        UiList bar = new UiList(Orientation.VERTICAL, ACTIVITY_ITEM_HEIGHT, ACTIVITY_BAR_WIDTH);
        bar.getStyleClass().addAll("activity-bar", sideClass);
        for (UiIcon.Kind kind : icons) {
            UiIcon icon = new UiIcon(kind);
            icon.getStyleClass().add("activity-bar-icon");
            if (kind == UiIcon.Kind.RUN) {
                runButton = new Button();
                runButton.setGraphic(icon);
                runButton.setId("app-run-button");
                runButton.getStyleClass().addAll("activity-list-item", "activity-run-button");
                runButton.setAccessibleText("编译并运行当前标签");
                runButton.setTooltip(new UiTooltip("运行当前标签", "编译当前编辑内容，包含尚未保存的修改。", ""));
                runButton.setMinSize(0, 0);
                runButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                runButton.setOnAction(event -> {
                    if (onRun != null && !runButton.isDisabled()) {
                        showEditorWorkspace();
                        onRun.run();
                    }
                });
                updateRunButton();
                bar.getItems().add(runButton);
                continue;
            }
            if (kind == UiIcon.Kind.PIPELINE) {
                pipelineButton = new ToggleButton();
                pipelineButton.setGraphic(icon);
                pipelineButton.setId("app-pipeline-button");
                pipelineButton.getStyleClass().addAll("activity-list-item", "activity-pipeline-button");
                pipelineButton.setAccessibleText("打开编译流水线");
                pipelineButton.setTooltip(new UiTooltip("编译流水线", "展开编译展示台；再次点击收起，保留编译进度。", ""));
                pipelineButton.setMinSize(0, 0);
                pipelineButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                pipelineButton.setOnAction(event -> {
                    if (onPipeline == null || pipelineButton.isDisabled()) return;
                    if (pipelineButton.isSelected()) {
                        pipelineDisplayActive = true;
                        pipelineDisplayOpen = true;
                        showEditorWorkspace();
                        onPipeline.run();
                    } else {
                        collapseDisplayArea();
                    }
                });
                updatePipelineButton();
                bar.getItems().add(pipelineButton);
                continue;
            }
            StackPane item = new StackPane(icon);
            item.getStyleClass().add("activity-list-item");
            item.setAccessibleText(kind.label());
            UiTooltip.attach(item, kind.label());
            bar.getItems().add(item);
        }
        return bar;
    }

    private Node createDisplayActivityBar() {
        Node activities = createActivityBar("activity-bar-right",
                UiIcon.Kind.RUN, UiIcon.Kind.PIPELINE, UiIcon.Kind.DEBUGGER);
        VBox.setVgrow(activities, Priority.ALWAYS);
        VBox bar = new VBox(activities);
        bar.setMinWidth(ACTIVITY_BAR_WIDTH);
        bar.setPrefWidth(ACTIVITY_BAR_WIDTH);
        bar.setMaxWidth(ACTIVITY_BAR_WIDTH);
        bar.getStyleClass().add("display-activity-bar");
        return bar;
    }

    private Node createCenterSplit(Node editorArea, Node interactionArea, Node tabBar) {
        SplitPane split = new SplitPane(createEditorSlot(editorArea, tabBar), interactionArea);
        split.setOrientation(Orientation.VERTICAL);
        split.setMinSize(0, 0);
        split.getStyleClass().addAll(
                "app-center", "app-split-pane", "app-vertical-split");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(split.widthProperty());
        clip.heightProperty().bind(split.heightProperty());
        split.setClip(clip);
        split.setDividerPositions(INITIAL_INTERACTION_DIVIDER_POSITION);
        return split;
    }

    private SplitPane createContent(Node editorArea, Node displayArea, Node interactionArea, Node tabBar) {
        StackPane displaySlot = new StackPane(displayArea);
        displaySlot.setMinSize(0, 0);
        displaySlot.setPrefWidth(DEFAULT_DISPLAY_WIDTH);
        displaySlot.getStyleClass().add("app-display-slot");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(displaySlot.widthProperty());
        clip.heightProperty().bind(displaySlot.heightProperty());
        displaySlot.setClip(clip);
        SplitPane split = new SplitPane(createCenterSplit(editorArea, interactionArea, tabBar), displaySlot);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setMinWidth(0);
        split.setMinHeight(0);
        split.getStyleClass().addAll(
                "app-content", "app-split-pane", "app-horizontal-split");
        return split;
    }

    private Node createEditorSlot(Node editorArea, Node tabBar) {
        BorderPane document = new BorderPane();
        document.setMinWidth(0);
        document.setMinHeight(TAB_BAR_HEIGHT);
        document.getStyleClass().add("app-editor-slot");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(document.widthProperty());
        clip.heightProperty().bind(document.heightProperty());
        document.setClip(clip);
        document.setTop(tabBar);
        document.setCenter(editorArea);
        return document;
    }

    private Node createStatusBar() {
        Label appName = label("Craken", "status-app-name");
        Label font = label("等宽字体 14px", "status-item");
        Label encoding = label("UTF-8", "status-item");
        UiTooltip.attach(font, "编辑器字体", "按住 Ctrl 并滚动鼠标滚轮可缩放编辑器文字。", "Ctrl + 滚轮");
        UiTooltip.attach(encoding, "文件编码", "源文件以 UTF-8 编码读取和保存。", "");
        UiHoverMenuButton layout = new UiHoverMenuButton("布局",
                command("展开右侧信息栏", this::expandDisplayArea),
                command("收起右侧信息栏", this::collapseDisplayArea),
                new SeparatorMenuItem(),
                command("恢复默认宽度", this::restoreDefaultDisplayAreaWidth));
        layout.setPopupSide(Side.TOP);
        layout.setMenuStyle(UiContextMenu.Style.MENUBAR);
        layout.getStyleClass().add("status-menu-button");
        layout.setId("status-layout-menu");
        layout.setPrefHeight(STATUS_BAR_HEIGHT);
        layout.setMinHeight(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(18, appName, spacer, layout, font, encoding);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-status-bar");
        constrainHeight(bar, 20, STATUS_BAR_HEIGHT, 24);
        return bar;
    }

    private static StackPane topItem(String text, boolean title) {
        Label label = label(text, title ? "top-bar-title" : "top-bar-item-label");
        StackPane item = new StackPane(label);
        item.setPadding(new Insets(0, TOP_ITEM_HORIZONTAL_PADDING, 0, TOP_ITEM_HORIZONTAL_PADDING));
        item.setMinWidth(Region.USE_PREF_SIZE);
        item.setMaxWidth(Region.USE_PREF_SIZE);
        item.getStyleClass().add("top-list-item");
        return item;
    }

    private static StackPane topItemWithIcon(String title) {
        URL iconResource = AppFrame.class.getResource("/craken/ui/icons/app-icon.png");
        if (iconResource == null) {
            throw new IllegalStateException("Missing app icon resource: /craken/ui/icons/app-icon.png");
        }
        Image icon = new Image(
                iconResource.toExternalForm(),
                TOP_BAR_ICON_SIZE, TOP_BAR_ICON_SIZE, true, true, false);
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(TOP_BAR_ICON_SIZE);
        iconView.setFitHeight(TOP_BAR_ICON_SIZE);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);
        iconView.getStyleClass().addAll("top-bar-icon", "window-title-no-drag");
        Label label = label(title, "top-bar-title");
        HBox content = new HBox(7, iconView, label);
        content.setAlignment(Pos.CENTER_LEFT);
        StackPane item = new StackPane(content);
        item.setPadding(new Insets(0, TOP_ITEM_HORIZONTAL_PADDING, 0, TOP_ITEM_HORIZONTAL_PADDING));
        item.setMinWidth(Region.USE_PREF_SIZE);
        item.setMaxWidth(Region.USE_PREF_SIZE);
        item.getStyleClass().add("top-list-item");
        return item;
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
