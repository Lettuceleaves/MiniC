package minic.ui.component.navigation;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 应用级菜单栏。 */
public final class UiMenuBar extends MenuBar implements UiComponent {

    public UiMenuBar(Menu... menus) {
        super(menus);
        setMinSize(0, 0);
        getStyleClass().add("ui-menu-bar");
        setPrefSize(Region.USE_COMPUTED_SIZE, 30);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
