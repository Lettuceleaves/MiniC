package minic.settings;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import minic.color.ThemeManager;

public final class MiniCSettingsPane extends VBox {
    public MiniCSettingsPane() {
        setSpacing(16);
        getStyleClass().add("activity-placeholder");

        Label title = new Label("设置");
        title.getStyleClass().add("activity-placeholder-title");

        Label hint = new Label("修改 config/theme.json 后点击下方按钮刷新主题。");
        hint.getStyleClass().add("activity-placeholder-text");

        Button refreshBtn = new Button("刷新主题");
        refreshBtn.getStyleClass().add("control-primary");
        refreshBtn.setOnAction(e -> ThemeManager.refresh());

        getChildren().addAll(title, hint, refreshBtn);
    }
}
