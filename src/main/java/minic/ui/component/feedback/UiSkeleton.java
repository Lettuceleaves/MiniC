package minic.ui.component.feedback;

import javafx.scene.layout.Region;
import minic.ui.component.UiComponent;

/** 内容加载期间使用的占位块。 */
public final class UiSkeleton extends Region implements UiComponent {

    public UiSkeleton() {
        setMinSize(0, 0);
        getStyleClass().add("ui-skeleton");
        setPrefSize(240, 20);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
