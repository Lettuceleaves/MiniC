package minic.settings;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import minic.color.ThemeManager;
import minic.ui.control.MiniCWorkbenchControlHub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MiniCSettingsPane extends VBox {
    private static final long FRAME_INTERVAL_STEP = 50;
    private final ObservableList<String> themeNames = FXCollections.observableArrayList();
    private final ComboBox<String> themeCombo = new ComboBox<>(themeNames);
    private final MiniCWorkbenchControlHub controlHub = new MiniCWorkbenchControlHub();

    public MiniCSettingsPane() {
        registerSettingsCommands();
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
                controlHub.setTheme(selected);
            }
        });

        Button uploadBtn = new Button("导入主题...");
        uploadBtn.getStyleClass().add("control-secondary");
        uploadBtn.setOnAction(e -> importTheme());

        HBox themeRow = new HBox(10, themeCombo, uploadBtn);

        Label intervalLabel = new Label("帧间隔");
        intervalLabel.getStyleClass().add("activity-placeholder-text");

        long current = MiniCSettings.frameIntervalMillis();
        Slider intervalSlider = new Slider(
                MiniCSettings.minFrameInterval(),
                MiniCSettings.maxFrameInterval(),
                current);
        intervalSlider.setBlockIncrement(FRAME_INTERVAL_STEP);
        intervalSlider.getStyleClass().add("control-secondary");

        Label intervalValue = new Label(current + " ms");
        intervalValue.getStyleClass().add("body-text");
        intervalValue.setMinWidth(60);

        intervalSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            long millis = Math.round(newVal.doubleValue());
            controlHub.setFrameIntervalMillis(millis);
            intervalValue.setText(MiniCSettings.frameIntervalMillis() + " ms");
        });

        HBox intervalRow = new HBox(10, intervalSlider, intervalValue);

        getChildren().addAll(title, themeLabel, themeRow, intervalLabel, intervalRow);
    }

    private void registerSettingsCommands() {
        controlHub.registerSettingsCommands(new MiniCWorkbenchControlHub.SettingsCommands(
                ThemeManager::setTheme,
                MiniCSettings::setFrameIntervalMillis,
                MiniCSettings::frameIntervalMillis,
                MiniCSettings::minFrameInterval,
                MiniCSettings::maxFrameInterval,
                FRAME_INTERVAL_STEP
        ));
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
        controlHub.setTheme(name);
    }
}
