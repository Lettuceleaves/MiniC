package craken.ui.component.input;

import javafx.scene.control.Slider;
import craken.ui.component.UiComponent;

/** 连续或离散数值范围选择。 */
public final class UiSlider extends Slider implements UiComponent {

    public UiSlider() {
        this(0, 100, 0);
    }

    public UiSlider(double min, double max, double value) {
        super(min, max, value);
        setMinSize(0, 0);
        getStyleClass().add("ui-slider");
        setPrefSize(200, 28);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
