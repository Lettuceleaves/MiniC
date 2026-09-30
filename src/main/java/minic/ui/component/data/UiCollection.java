package minic.ui.component.data;

import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import minic.ui.component.UiComponent;

/** 可选择的线性数据集合。 */
public final class UiCollection<T> extends ListView<T> implements UiComponent {

    public UiCollection() {
        setMinSize(0, 0);
        getStyleClass().add("ui-collection");
        setPrefSize(280, 240);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public UiCollection(ObservableList<T> items) {
        super(items);
        setMinSize(0, 0);
        getStyleClass().add("ui-collection");
        setPrefSize(280, 240);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
