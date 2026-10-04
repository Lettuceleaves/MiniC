package craken.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 水平排列子组件的容器。 */
public final class UiRow extends HBox implements UiComponent {

    public UiRow(Node... children) {
        super(children);
        setMinSize(0, 0);
        getStyleClass().add("ui-row");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
