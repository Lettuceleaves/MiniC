package craken.ui.component.input;

import javafx.scene.control.PasswordField;
import craken.ui.component.UiComponent;

/** 掩码单行文本输入。 */
public final class UiPasswordField extends PasswordField implements UiComponent {

    public UiPasswordField() {
        setMinSize(0, 0);
        getStyleClass().add("ui-password-field");
        setPrefSize(240, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
