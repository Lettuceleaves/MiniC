package craken.ui.component.input;

import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 独立或多选布尔项。 */
public final class UiCheckBox extends CheckBox implements UiComponent {

    public UiCheckBox() {
        this("");
    }

    public UiCheckBox(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-check-box");
        setPrefSize(Region.USE_COMPUTED_SIZE, 28);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
