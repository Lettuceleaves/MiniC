package craken.ui.component.display;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 简短的状态或数量标记。 */
public final class UiBadge extends Label implements UiComponent {

    public UiBadge() {
        this("");
    }

    public UiBadge(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-badge");
        setPrefSize(Region.USE_COMPUTED_SIZE, 22);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
