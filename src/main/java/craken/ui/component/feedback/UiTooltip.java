package craken.ui.component.feedback;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Objects;

/**
 * 工作台悬停提示：短文本或标题、说明和快捷键；不占用布局或键盘焦点。
 * 使用原生 Tooltip 的延迟和屏幕边缘定位，颜色由工作台主题提供。
 */
public final class UiTooltip extends Tooltip {
    private static final PseudoClass RICH = PseudoClass.getPseudoClass("rich");
    private final StringProperty title = new SimpleStringProperty(this, "title", "");
    private final StringProperty shortcut = new SimpleStringProperty(this, "shortcut", "");
    private final VBox content = new VBox(6);
    private Scene ownerScene;
    private Window observedWindow;
    private Node observedNode;
    private MenuButton observedMenu;
    private final EventHandler<KeyEvent> dismissKey = event -> {
        if (event.getCode() == KeyCode.ESCAPE) {
            hide();
            event.consume();
        }
    };
    private final EventHandler<MouseEvent> dismissClick = event -> hide();
    private final ChangeListener<Boolean> ownerFocus = (observable, previous, current) -> {
        if (!current) hide();
    };
    private final ChangeListener<Boolean> ownerVisible = (observable, previous, current) -> {
        if (!current) hide();
    };
    private final ChangeListener<Boolean> ownerDisabled = (observable, previous, current) -> {
        if (current) hide();
    };
    private final ChangeListener<Boolean> menuShowing = (observable, previous, current) -> {
        if (current) hide();
    };
    private final ChangeListener<Scene> ownerSceneChanged = (observable, previous, current) -> hide();

    public UiTooltip(String text) {
        this("", text, "");
    }

    public UiTooltip(String title, String description, String shortcut) {
        super(description == null ? "" : description);
        getStyleClass().add("ui-tooltip");
        setShowDelay(Duration.millis(500));
        setHideDelay(Duration.millis(80));
        setShowDuration(Duration.INDEFINITE);
        setHideOnEscape(true);
        // 悬停提示沿用原生 Tooltip 的收起逻辑，不像菜单一样抓取宿主鼠标焦点。
        setAutoHide(false);
        // 原生鼠标偏移很小；按内容定位会让 CSS 阴影向左上扩展并盖住鼠标，
        // 反复触发 tab 的 exit/enter。以含阴影的整个浮窗为锚点，保持鼠标间隙。
        setAnchorLocation(PopupWindow.AnchorLocation.WINDOW_TOP_LEFT);
        setConsumeAutoHidingEvents(false);
        setWrapText(true);
        setStyle("-fx-padding: 9 11 9 11; -fx-background-radius: 3; -fx-border-radius: 3; -fx-border-width: 1;");
        setMaxWidth(402);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        Label heading = new Label();
        heading.getStyleClass().add("ui-tooltip-title");
        heading.textProperty().bind(this.title);
        heading.setWrapText(true);
        heading.setMinWidth(0);
        heading.setMaxWidth(300);
        heading.visibleProperty().bind(Bindings.isNotEmpty(this.title));
        heading.managedProperty().bind(heading.visibleProperty());
        HBox.setHgrow(heading, Priority.ALWAYS);

        Label key = new Label();
        key.getStyleClass().add("ui-tooltip-shortcut");
        key.textProperty().bind(this.shortcut);
        key.setMinWidth(Region.USE_PREF_SIZE);
        key.setPadding(new Insets(1, 5, 1, 5));
        key.setStyle("-fx-background-radius: 2; -fx-border-radius: 2; -fx-border-width: 1;");
        key.visibleProperty().bind(Bindings.isNotEmpty(this.shortcut));
        key.managedProperty().bind(key.visibleProperty());

        HBox header = new HBox(14, heading, key);
        header.getStyleClass().add("ui-tooltip-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.visibleProperty().bind(heading.visibleProperty().or(key.visibleProperty()));
        header.managedProperty().bind(header.visibleProperty());

        Label body = new Label();
        body.getStyleClass().add("ui-tooltip-description");
        body.textProperty().bind(textProperty());
        body.setWrapText(true);
        body.setMinWidth(0);
        body.setMaxWidth(380);
        body.visibleProperty().bind(Bindings.isNotEmpty(textProperty()));
        body.managedProperty().bind(body.visibleProperty());
        content.getStyleClass().add("ui-tooltip-content");
        content.setMaxWidth(380);
        content.getChildren().addAll(header, body);
        content.pseudoClassStateChanged(RICH, header.isVisible());
        header.visibleProperty().addListener((observable, previous, current) ->
                content.pseudoClassStateChanged(RICH, current));
        setGraphic(content);
        setTitle(title);
        setShortcut(shortcut);
        showingProperty().addListener((observable, previous, current) -> {
            if (current) observeOwner();
            else releaseOwner();
        });
    }

    public String getTitle() { return title.get(); }
    public void setTitle(String value) { title.set(value == null ? "" : value); }
    public StringProperty titleProperty() { return title; }
    public String getShortcut() { return shortcut.get(); }
    public void setShortcut(String value) { shortcut.set(value == null ? "" : value); }
    public StringProperty shortcutProperty() { return shortcut; }

    /** 给非 Control 节点安装短提示；Control 也可直接使用 setTooltip。 */
    public static UiTooltip attach(Node node, String text) {
        return attach(node, "", text, "");
    }

    /** 标题、正文、快捷键均可留空；正文通过继承的 textProperty 更新。 */
    public static UiTooltip attach(Node node, String title, String description, String shortcut) {
        UiTooltip tooltip = new UiTooltip(title, description, shortcut);
        Tooltip.install(Objects.requireNonNull(node, "node"), tooltip);
        return tooltip;
    }

    @Override
    public void show(Node ownerNode, double anchorX, double anchorY) {
        MenuButton menu = menuOwner(ownerNode);
        if (menu == null || !menu.isShowing()) super.show(ownerNode, anchorX, anchorY);
    }

    @Override
    public void show(Window ownerWindow, double anchorX, double anchorY) {
        MenuButton menu = menuOwner(hoverOwner());
        if (menu == null || !menu.isShowing()) super.show(ownerWindow, anchorX, anchorY);
    }

    @Override
    public void show(Window ownerWindow) {
        MenuButton menu = menuOwner(hoverOwner());
        if (menu == null || !menu.isShowing()) super.show(ownerWindow);
    }

    private void observeOwner() {
        releaseOwner();
        observedWindow = getOwnerWindow();
        observedNode = getOwnerNode() == null ? hoverOwner() : getOwnerNode();
        ownerScene = observedWindow == null ? null : observedWindow.getScene();
        if (ownerScene != null) {
            ownerScene.addEventFilter(KeyEvent.KEY_PRESSED, dismissKey);
            ownerScene.addEventFilter(MouseEvent.MOUSE_PRESSED, dismissClick);
        }
        if (observedWindow != null) observedWindow.focusedProperty().addListener(ownerFocus);
        if (observedNode != null) {
            observedNode.sceneProperty().addListener(ownerSceneChanged);
            observedNode.visibleProperty().addListener(ownerVisible);
            observedNode.disabledProperty().addListener(ownerDisabled);
        }
        observedMenu = menuOwner(observedNode);
        if (observedMenu != null) observedMenu.showingProperty().addListener(menuShowing);
    }

    private void releaseOwner() {
        if (ownerScene != null) {
            ownerScene.removeEventFilter(KeyEvent.KEY_PRESSED, dismissKey);
            ownerScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, dismissClick);
        }
        if (observedWindow != null) observedWindow.focusedProperty().removeListener(ownerFocus);
        if (observedNode != null) {
            observedNode.sceneProperty().removeListener(ownerSceneChanged);
            observedNode.visibleProperty().removeListener(ownerVisible);
            observedNode.disabledProperty().removeListener(ownerDisabled);
        }
        if (observedMenu != null) observedMenu.showingProperty().removeListener(menuShowing);
        ownerScene = null;
        observedWindow = null;
        observedNode = null;
        observedMenu = null;
    }

    private static MenuButton menuOwner(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof MenuButton menu) return menu;
        }
        return null;
    }

    // JavaFX 的原生悬停调度使用 Window 重载；其公开的 styleable parent 是悬停节点。
    private Node hoverOwner() {
        return getStyleableParent() instanceof Node node ? node : null;
    }
}
