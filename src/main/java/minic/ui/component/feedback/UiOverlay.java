package minic.ui.component.feedback;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import minic.ui.component.UiComponent;

/** 在现有内容之上承载对话框、菜单或加载状态。 */
public final class UiOverlay extends StackPane implements UiComponent {

    public UiOverlay(Node... content) {
        super(content);
        setMinSize(0, 0);
        getStyleClass().add("ui-overlay");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
