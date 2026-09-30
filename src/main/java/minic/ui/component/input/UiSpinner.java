package minic.ui.component.input;

import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import minic.ui.component.UiComponent;

/** 通过输入或增减按钮选择有序值。 */
public final class UiSpinner<T> extends Spinner<T> implements UiComponent {

    public UiSpinner() {
        setMinSize(0, 0);
        getStyleClass().add("ui-spinner");
        setPrefSize(160, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public UiSpinner(SpinnerValueFactory<T> valueFactory) {
        this();
        setValueFactory(valueFactory);
    }
}
