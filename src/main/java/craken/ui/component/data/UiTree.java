package craken.ui.component.data;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import craken.ui.component.UiComponent;

/** 展示分层数据的树。 */
public final class UiTree<T> extends TreeView<T> implements UiComponent {

    public UiTree() {
        this(null);
    }

    public UiTree(TreeItem<T> root) {
        super(root);
        setMinSize(0, 0);
        getStyleClass().add("ui-tree");
        setPrefSize(280, 280);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
