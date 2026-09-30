package minic.ui.component.display;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 普通只读文本标签。 */
public class UiLabel extends Label implements UiComponent {

    public UiLabel() {
        this("");
    }

    public UiLabel(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-label");
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
