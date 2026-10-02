package minic.ui.component.navigation;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.collections.ListChangeListener;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.util.Objects;

/** 编辑器之外使用的命令菜单，保留 JavaFX 的键盘、分组和子菜单行为。 */
public final class UiContextMenu extends ContextMenu {
    public enum Style {
        MENUBAR("ui-menubar-context-menu"),
        TOOLBAR("ui-toolbar-context-menu"),
        CONTEXT("ui-context-context-menu");

        private final String styleClass;

        Style(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    private Style menuStyle = Style.CONTEXT;
    private final ListChangeListener<Window> childWindowsListener = change -> {
        while (change.next()) {
            for (Window window : change.getAddedSubList()) {
                styleChild(window);
            }
        }
    };

    public UiContextMenu(MenuItem... items) {
        super(items);
        setAutoHide(true);
        setAutoFix(true);
        setHideOnEscape(true);
        applyMenuStyle(this, menuStyle);
        showingProperty().addListener((observable, previous, showing) -> {
            if (showing) {
                Window.getWindows().addListener(childWindowsListener);
            } else {
                Window.getWindows().removeListener(childWindowsListener);
            }
        });
    }

    public Style getMenuStyle() {
        return menuStyle;
    }

    public void setMenuStyle(Style style) {
        menuStyle = Objects.requireNonNull(style, "style");
        applyMenuStyle(this, style);
        for (Window window : Window.getWindows()) {
            styleChild(window);
        }
    }

    private void styleChild(Window window) {
        if (!(window instanceof ContextMenu menu) || window == this) {
            return;
        }
        Window ancestor = menu.getOwnerWindow();
        while (ancestor instanceof PopupWindow popup) {
            if (ancestor == this) {
                applyMenuStyle(menu, menuStyle);
                return;
            }
            ancestor = popup.getOwnerWindow();
        }
    }

    /** 让原生 MenuButton 及其子菜单使用同一组视觉样式。 */
    public static void applyMenuStyle(ContextMenu menu, Style style) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(style, "style");
        if (!menu.getStyleClass().contains("ui-context-menu")) {
            menu.getStyleClass().add("ui-context-menu");
        }
        for (Style candidate : Style.values()) {
            menu.getStyleClass().remove(candidate.styleClass);
        }
        menu.getStyleClass().add(style.styleClass);
    }
}
