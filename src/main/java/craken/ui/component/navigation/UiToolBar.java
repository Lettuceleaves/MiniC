package craken.ui.component.navigation;

import javafx.scene.Node;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 当前视图常用操作的工具栏。 */
public final class UiToolBar extends ToolBar implements UiComponent {

    public UiToolBar(Node... items) {
        super(items);
        setMinSize(0, 0);
        getStyleClass().add("ui-tool-bar");
        setPrefSize(Region.USE_COMPUTED_SIZE, 40);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
