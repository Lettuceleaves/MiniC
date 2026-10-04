package craken.ui.display;

import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/** 右侧展示区，承载信息展示和后续的可视化内容。 */
public final class DisplayArea extends StackPane {
    public DisplayArea() {
        setMinSize(0, 0);
        setPrefWidth(280);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setViewOrder(-1);
        getStyleClass().add("display-area");

        Label placeholder = new Label("右侧信息栏");
        placeholder.getStyleClass().add("placeholder-title");
        getChildren().add(placeholder);
    }

    /** 在展示台内切换功能内容；区域宽度仍由 AppFrame 管理。 */
    public void show(Node content) {
        getChildren().setAll(java.util.Objects.requireNonNull(content));
    }
}
