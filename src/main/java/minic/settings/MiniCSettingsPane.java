package minic.settings;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import minic.color.ThemeManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MiniCSettingsPane extends VBox {
    private final ObservableList<String> themeNames = FXCollections.observableArrayList();
    private final ComboBox<String> themeCombo = new ComboBox<>(themeNames);

    public MiniCSettingsPane() {
        setSpacing(16);
        getStyleClass().add("activity-placeholder");

        Label title = new Label("设置");
        title.getStyleClass().add("activity-placeholder-title");

        Label themeLabel = new Label("主题");
        themeLabel.getStyleClass().add("activity-placeholder-text");

        refreshThemeList();
        themeCombo.setValue(ThemeManager.currentTheme());
        themeCombo.getStyleClass().add("control-secondary");
        themeCombo.setOnAction(e -> {
            String selected = themeCombo.getValue();
            if (selected != null) {
                ThemeManager.setTheme(selected);
            }
        });

        Button uploadBtn = new Button("导入主题...");
        uploadBtn.getStyleClass().add("control-secondary");
        uploadBtn.setOnAction(e -> importTheme());

        HBox themeRow = new HBox(10, themeCombo, uploadBtn);

        getChildren().addAll(title, themeLabel, themeRow);
    }

    private void refreshThemeList() {
        themeNames.setAll(ThemeManager.availableThemes());
    }

    private void importTheme() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导入主题文件");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON 主题文件 (*.json)", "*.json"));
        java.io.File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        Path source = file.toPath();
        Path target = ThemeManager.themesDirectory().resolve(source.getFileName());
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("无法导入主题文件: " + source, ex);
        }
        String name = target.getFileName().toString().replace(".json", "");
        refreshThemeList();
        themeCombo.setValue(name);
        ThemeManager.setTheme(name);
    }
}
