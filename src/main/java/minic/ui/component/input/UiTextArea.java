package minic.ui.component.input;

import javafx.scene.control.TextArea;
import minic.ui.component.UiComponent;

/** 多行文本输入。 */
public final class UiTextArea extends TextArea implements UiComponent {

    public UiTextArea() {
        this("");
    }

    public UiTextArea(String text) {
        super(text);
        setMinSize(0, 0);
        getStyleClass().add("ui-text-area");
        setPrefSize(320, 120);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
