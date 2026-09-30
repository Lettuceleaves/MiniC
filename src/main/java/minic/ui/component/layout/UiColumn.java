package minic.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import minic.ui.component.UiComponent;

/** 垂直排列子组件的容器。 */
public final class UiColumn extends VBox implements UiComponent {

    public UiColumn(Node... children) {
        super(children);
        setMinSize(0, 0);
        getStyleClass().add("ui-column");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
