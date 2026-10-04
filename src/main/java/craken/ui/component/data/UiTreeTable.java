package craken.ui.component.data;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import craken.ui.component.UiComponent;

/** 以表格列展示分层数据。 */
public final class UiTreeTable<T> extends TreeTableView<T> implements UiComponent {

    public UiTreeTable() {
        this(null);
    }

    public UiTreeTable(TreeItem<T> root) {
        super(root);
        setMinSize(0, 0);
        getStyleClass().add("ui-tree-table");
        setPrefSize(480, 280);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
