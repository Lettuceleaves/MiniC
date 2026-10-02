package minic.ui.component.action;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;
import minic.ui.component.navigation.UiContextMenu;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 支持悬停展开的工作台菜单按钮。
 *
 * <p>继承 MenuButton 的点击、键盘导航、Esc 和屏幕边缘定位行为；悬停只负责
 * 开关时机。退出按钮后保留短暂宽限期，鼠标可以安全进入弹层和多级子菜单。</p>
 */
public final class UiHoverMenuButton extends UiMenuButton {
    private static WeakReference<UiHoverMenuButton> activeMenu = new WeakReference<>(null);
    private final PauseTransition openDelay = new PauseTransition(Duration.millis(180));
    private final PauseTransition closeDelay = new PauseTransition(Duration.millis(280));
    private final Map<Window, PopupWatch> popupWatches = new IdentityHashMap<>();
    private final ListChangeListener<Window> windowsListener = change -> {
        while (change.next()) {
            for (Window removed : change.getRemoved()) {
                unwatch(removed);
            }
            for (Window added : change.getAddedSubList()) {
                watch(added);
            }
        }
    };
    private final ChangeListener<Boolean> windowShowingListener = (observable, previous, showing) -> {
        if (!showing) {
            cancelAndHide();
        }
    };
    private UiContextMenu.Style menuStyle = UiContextMenu.Style.TOOLBAR;
    private Window ownerWindow;
    private boolean pointerInButton;
    private boolean observingWindows;

    public UiHoverMenuButton() {
        this("");
    }

    public UiHoverMenuButton(String text, MenuItem... items) {
        super(text, items);
        getStyleClass().add("ui-hover-menu-button");
        openDelay.setOnFinished(event -> {
            if (pointerInButton && canShow()) {
                show();
            }
        });
        closeDelay.setOnFinished(event -> {
            if (!pointerInButton && popupWatches.values().stream().noneMatch(PopupWatch::isHovered)) {
                hide();
            }
        });
        addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            pointerInButton = true;
            closeDelay.stop();
            if (!isShowing() && canShow()) {
                UiHoverMenuButton active = activeMenu.get();
                if (menuStyle == UiContextMenu.Style.MENUBAR && active != null
                        && active != this && active.isShowing()
                        && active.menuStyle == UiContextMenu.Style.MENUBAR) {
                    show();
                } else {
                    openDelay.playFromStart();
                }
            }
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            pointerInButton = false;
            openDelay.stop();
            scheduleClose();
        });
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            openDelay.stop();
            closeDelay.stop();
        });
        showingProperty().addListener((observable, previous, showing) -> {
            openDelay.stop();
            closeDelay.stop();
            if (showing) {
                startWatching();
            } else {
                stopWatching();
            }
        });
        disabledProperty().addListener((observable, previous, disabled) -> {
            if (disabled) {
                cancelAndHide();
            }
        });
        visibleProperty().addListener((observable, previous, visible) -> {
            if (!visible) {
                cancelAndHide();
            }
        });
        sceneProperty().addListener((observable, previous, scene) -> {
            if (ownerWindow != null) {
                ownerWindow.showingProperty().removeListener(windowShowingListener);
                ownerWindow = null;
            }
            if (previous != null) {
                previous.windowProperty().removeListener(ownerWindowListener);
            }
            cancelAndHide();
            if (scene != null) {
                scene.windowProperty().addListener(ownerWindowListener);
                observeOwnerWindow(scene.getWindow());
            }
        });
    }

    private final ChangeListener<Window> ownerWindowListener = (observable, previous, current) ->
            observeOwnerWindow(current);

    public UiContextMenu.Style getMenuStyle() {
        return menuStyle;
    }

    public void setMenuStyle(UiContextMenu.Style style) {
        menuStyle = Objects.requireNonNull(style, "style");
        ContextMenu popup = getPopup();
        if (popup != null) {
            UiContextMenu.applyMenuStyle(popup, style);
        }
        popupWatches.keySet().stream()
                .filter(ContextMenu.class::isInstance)
                .map(ContextMenu.class::cast)
                .forEach(menu -> UiContextMenu.applyMenuStyle(menu, style));
    }

    /** 原生弹层在 skin 初始化后可用；空菜单尚无弹层时返回 null。 */
    public ContextMenu getPopup() {
        for (MenuItem item : getItems()) {
            ContextMenu popup = item.getParentPopup();
            if (popup != null) {
                return popup;
            }
        }
        return null;
    }

    public Duration getHoverOpenDelay() {
        return openDelay.getDuration();
    }

    public void setHoverOpenDelay(Duration delay) {
        openDelay.setDuration(requireDelay(delay));
    }

    public Duration getHoverCloseDelay() {
        return closeDelay.getDuration();
    }

    public void setHoverCloseDelay(Duration delay) {
        closeDelay.setDuration(requireDelay(delay));
    }

    private static Duration requireDelay(Duration delay) {
        Objects.requireNonNull(delay, "delay");
        if (delay.isUnknown() || delay.isIndefinite() || delay.lessThan(Duration.ZERO)) {
            throw new IllegalArgumentException("Hover delay must be finite and nonnegative");
        }
        return delay;
    }

    private boolean canShow() {
        return !isDisabled() && isVisible() && !getItems().isEmpty()
                && getScene() != null && getScene().getWindow() != null
                && getScene().getWindow().isShowing();
    }

    private void observeOwnerWindow(Window window) {
        if (ownerWindow != window) {
            cancelAndHide();
        }
        if (ownerWindow != null) {
            ownerWindow.showingProperty().removeListener(windowShowingListener);
        }
        ownerWindow = window;
        if (ownerWindow != null) {
            ownerWindow.showingProperty().addListener(windowShowingListener);
        }
    }

    private void cancelAndHide() {
        pointerInButton = false;
        openDelay.stop();
        closeDelay.stop();
        hide();
        stopWatching();
    }

    private void scheduleClose() {
        if (isShowing()) {
            closeDelay.playFromStart();
        }
    }

    private void startWatching() {
        UiHoverMenuButton previous = activeMenu.get();
        if (previous != null && previous != this) {
            previous.hide();
        }
        activeMenu = new WeakReference<>(this);
        ContextMenu popup = getPopup();
        if (popup != null) {
            UiContextMenu.applyMenuStyle(popup, menuStyle);
        }
        if (!observingWindows) {
            observingWindows = true;
            Window.getWindows().addListener(windowsListener);
        }
        for (Window window : Window.getWindows()) {
            watch(window);
        }
    }

    private void stopWatching() {
        if (activeMenu.get() == this) {
            activeMenu.clear();
        }
        if (observingWindows) {
            Window.getWindows().removeListener(windowsListener);
            observingWindows = false;
        }
        for (PopupWatch watch : popupWatches.values()) {
            watch.remove();
        }
        popupWatches.clear();
    }

    private boolean belongsToButton(Window window) {
        ContextMenu rootPopup = getPopup();
        while (window instanceof PopupWindow popup) {
            if (window == rootPopup || popup.getOwnerNode() == this) {
                return true;
            }
            window = popup.getOwnerWindow();
        }
        return false;
    }

    private void watch(Window window) {
        if (window instanceof ContextMenu menu && !popupWatches.containsKey(window)
                && belongsToButton(window) && window.getScene() != null) {
            UiContextMenu.applyMenuStyle(menu, menuStyle);
            popupWatches.put(window, new PopupWatch(window.getScene().getRoot()));
        }
    }

    private void unwatch(Window window) {
        PopupWatch watch = popupWatches.remove(window);
        if (watch != null) {
            watch.remove();
        }
    }

    private final class PopupWatch {
        private final Parent root;
        private boolean hovered;
        private final EventHandler<MouseEvent> entered = event -> {
            hovered = true;
            closeDelay.stop();
        };
        private final EventHandler<MouseEvent> exited = event -> {
            hovered = false;
            scheduleClose();
        };
        private final EventHandler<KeyEvent> pressed = event -> closeDelay.stop();

        private PopupWatch(Parent root) {
            this.root = root;
            hovered = root.isHover();
            root.addEventHandler(MouseEvent.MOUSE_ENTERED, entered);
            root.addEventHandler(MouseEvent.MOUSE_EXITED, exited);
            root.addEventFilter(KeyEvent.KEY_PRESSED, pressed);
        }

        private boolean isHovered() {
            return hovered;
        }

        private void remove() {
            root.removeEventHandler(MouseEvent.MOUSE_ENTERED, entered);
            root.removeEventHandler(MouseEvent.MOUSE_EXITED, exited);
            root.removeEventFilter(KeyEvent.KEY_PRESSED, pressed);
        }
    }
}
