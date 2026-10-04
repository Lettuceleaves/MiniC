package craken.ui.component.input;

import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 表达立即生效的开关状态。 */
public final class UiSwitch extends CheckBox implements UiComponent {

    public UiSwitch() {
        this("");
    }

    public UiSwitch(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-switch");
        setPrefSize(Region.USE_COMPUTED_SIZE, 28);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
