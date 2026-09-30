package minic.ui.component.input;

import javafx.scene.control.RadioButton;
import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 单选组中的一个选项。 */
public final class UiRadioButton extends RadioButton implements UiComponent {

    public UiRadioButton() {
        this("");
    }

    public UiRadioButton(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-radio-button");
        setPrefSize(Region.USE_COMPUTED_SIZE, 28);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
