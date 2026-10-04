package craken.ui.component.input;

import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 单行文本输入。 */
public class UiTextField extends TextField implements UiComponent {

    public UiTextField() {
        this("");
    }

    public UiTextField(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-text-field");
        setPrefSize(240, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
