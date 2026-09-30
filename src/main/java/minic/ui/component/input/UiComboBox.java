package minic.ui.component.input;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import minic.ui.component.UiComponent;

/** 支持选择和可选文本编辑的下拉框。 */
public final class UiComboBox<T> extends ComboBox<T> implements UiComponent {

    public UiComboBox() {
        setMinSize(0, 0);
        getStyleClass().add("ui-combo-box");
        setPrefSize(200, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public UiComboBox(ObservableList<T> items) {
        super(items);
        setMinSize(0, 0);
        getStyleClass().add("ui-combo-box");
        setPrefSize(200, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
