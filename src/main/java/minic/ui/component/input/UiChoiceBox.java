package minic.ui.component.input;

import javafx.collections.ObservableList;
import javafx.scene.control.ChoiceBox;
import minic.ui.component.UiComponent;

/** 从较少的预定义选项中选择一项。 */
public final class UiChoiceBox<T> extends ChoiceBox<T> implements UiComponent {

    public UiChoiceBox() {
        setMinSize(0, 0);
        getStyleClass().add("ui-choice-box");
        setPrefSize(200, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public UiChoiceBox(ObservableList<T> items) {
        super(items);
        setMinSize(0, 0);
        getStyleClass().add("ui-choice-box");
        setPrefSize(200, 32);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
