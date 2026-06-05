package minic.ui;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

final class MiniCInfoView extends ScrollPane {
    MiniCInfoView() {
        VBox markdown = new MiniCMarkdownRenderer().render(MiniCGuideDocument.loadDefault());
        getStyleClass().add("info-scroll");
        setContent(markdown);
        setFitToWidth(true);
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
    }
}
