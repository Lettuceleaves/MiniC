package craken.ui.component.navigation;

import javafx.scene.control.Pagination;
import javafx.scene.layout.Region;
import craken.ui.component.UiComponent;

/** 分页内容导航。 */
public final class UiPagination extends Pagination implements UiComponent {

    public UiPagination() {
        this(INDETERMINATE, 0);
    }

    public UiPagination(int pageCount, int pageIndex) {
        super(pageCount, pageIndex);
        setMinSize(0, 0);
        getStyleClass().add("ui-pagination");
        setPrefSize(Region.USE_COMPUTED_SIZE, 36);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
