package craken.ui.component.action;

import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 打开操作菜单的按钮。 */
public class UiMenuButton extends MenuButton implements UiComponent {

    public UiMenuButton() {
        this("");
    }

    public UiMenuButton(String text, MenuItem... items) {
        super(text, null, items);
        setMinSize(0, 0);
        getStyleClass().add("ui-menu-button");
        setPrefSize(Region.USE_COMPUTED_SIZE, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
