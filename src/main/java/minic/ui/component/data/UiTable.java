package minic.ui.component.data;

import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import minic.ui.component.UiComponent;

/** 按列展示和操作结构化数据。 */
public final class UiTable<T> extends TableView<T> implements UiComponent {

    public UiTable() {
        setMinSize(0, 0);
        getStyleClass().add("ui-table");
        setPrefSize(480, 280);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public UiTable(ObservableList<T> items) {
        super(items);
        setMinSize(0, 0);
        getStyleClass().add("ui-table");
        setPrefSize(480, 280);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
