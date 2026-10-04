package craken.ui.component.feedback;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 短暂显示的非阻塞操作反馈内容。显示时机由调用者控制。 */
public final class UiToast extends HBox implements UiComponent {

    public UiToast(Node... content) {
        super(content);
        setMinSize(0, 0);
        getStyleClass().add("ui-toast");
        setPrefSize(Region.USE_COMPUTED_SIZE, 40);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
