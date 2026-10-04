package craken.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import craken.ui.component.UiComponent;

/** 将子组件叠放在同一布局区域。 */
public final class UiStack extends StackPane implements UiComponent {

    public UiStack(Node... children) {
        super(children);
        setMinSize(0, 0);
        getStyleClass().add("ui-stack");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
