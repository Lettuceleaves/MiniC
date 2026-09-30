package minic.ui.component.layout;

import javafx.scene.control.Accordion;
import javafx.scene.control.TitledPane;
import minic.ui.component.UiComponent;

/** 一次展开一个区段的折叠容器。 */
public final class UiAccordion extends Accordion implements UiComponent {

    public UiAccordion(TitledPane... panes) {
        super(panes);
        setMinSize(0, 0);
        getStyleClass().add("ui-accordion");
        setPrefSize(320, 240);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
    }
}
