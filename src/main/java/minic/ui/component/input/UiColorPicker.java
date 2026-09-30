package minic.ui.component.input;

import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 颜色选择器。 */
public final class UiColorPicker extends ColorPicker implements UiComponent {

    public UiColorPicker() {
        this(Color.WHITE);
    }

    public UiColorPicker(Color color) {
        super(color);
        setMinSize(0, 0);
        getStyleClass().add("ui-color-picker");
        setPrefSize(Region.USE_COMPUTED_SIZE, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
