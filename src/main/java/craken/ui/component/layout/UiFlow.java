package craken.ui.component.layout;

import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 根据可用空间自动换行的流式容器。 */
public final class UiFlow extends FlowPane implements UiComponent {

    public UiFlow(Node... children) {
        getChildren().addAll(children);
        setMinSize(0, 0);
        getStyleClass().add("ui-flow");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
