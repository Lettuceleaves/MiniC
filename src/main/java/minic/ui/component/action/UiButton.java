package minic.ui.component.action;

import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 触发单次操作的按钮。 */
public final class UiButton extends Button implements UiComponent {

    public UiButton() {
        this("");
    }

    public UiButton(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-button");
        setPrefSize(Region.USE_COMPUTED_SIZE, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
