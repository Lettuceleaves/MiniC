package craken.ui.component.feedback;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import craken.ui.component.UiComponent;

/** 由 {@link UiOverlay} 承载的应用内部对话框。 */
public final class UiDialog extends BorderPane implements UiComponent {
    private final Label title = new Label();
    private final HBox actions = new HBox();

    public UiDialog(String titleText, Node content) {
        setMinSize(0, 0);
        getStyleClass().add("ui-dialog");
        title.getStyleClass().add("ui-dialog-title");
        actions.getStyleClass().add("ui-dialog-actions");
        title.setText(titleText);
        setTop(title);
        setCenter(content);
        setBottom(actions);
        setPrefSize(480, 320);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }

    public String title() {
        return title.getText();
    }

    public void title(String value) {
        title.setText(value);
    }

    public HBox actions() {
        return actions;
    }
}
