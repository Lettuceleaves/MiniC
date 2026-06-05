package minic.settings;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import minic.color.ThemeManager;
import minic.ui.MiniCKeyBindingConfig;
import minic.ui.control.MiniCWorkbenchControlHub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class MiniCSettingsPane extends VBox {
    private static final long FRAME_INTERVAL_STEP = 50;
    private static final double GRAPH_ZOOM_STEP_BLOCK = 0.005;
    private static final double UI_SCALE_STEP_BLOCK = 0.05;
    private final ObservableList<String> themeNames = FXCollections.observableArrayList();
    private final ComboBox<String> themeCombo = new ComboBox<>(themeNames);
    private final MiniCWorkbenchControlHub controlHub = new MiniCWorkbenchControlHub();
    private final MiniCKeyBindingConfig keyBindingConfig = MiniCKeyBindingConfig.loadDefault();
    private final Label keyBindingWarning = new Label("");
    private String captureAction = "";
    private Button captureButton;
    private String pendingCombo = "";
    private final LinkedHashSet<KeyCode> captureKeys = new LinkedHashSet<>();

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

        Label uiScaleLabel = new Label("全局缩放");
        uiScaleLabel.getStyleClass().add("activity-placeholder-text");

        Slider uiScaleSlider = new Slider(
                MiniCSettings.minUiScale(),
                MiniCSettings.maxUiScale(),
                MiniCSettings.uiScale());
        uiScaleSlider.setBlockIncrement(UI_SCALE_STEP_BLOCK);
        uiScaleSlider.getStyleClass().add("control-secondary");
        uiScaleSlider.setAccessibleText("setting:uiScale");

        Label uiScaleValue = new Label(formatPercent(MiniCSettings.uiScale()));
        uiScaleValue.getStyleClass().add("body-text");
        uiScaleValue.setMinWidth(80);

        uiScaleSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            MiniCSettings.setUiScale(roundUiScale(newVal.doubleValue()));
            uiScaleValue.setText(formatPercent(MiniCSettings.uiScale()));
        });

        HBox uiScaleRow = new HBox(10, uiScaleSlider, uiScaleValue);

        Label zoomLabel = new Label("图形缩放灵敏度");
        zoomLabel.getStyleClass().add("activity-placeholder-text");

        Slider zoomSlider = new Slider(
                MiniCSettings.minGraphZoomStep(),
                MiniCSettings.maxGraphZoomStep(),
                MiniCSettings.graphZoomStep());
        zoomSlider.setBlockIncrement(GRAPH_ZOOM_STEP_BLOCK);
        zoomSlider.getStyleClass().add("control-secondary");

        Label zoomValue = new Label(formatZoomStep(MiniCSettings.graphZoomStep()));
        zoomValue.getStyleClass().add("body-text");
        zoomValue.setMinWidth(80);

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            MiniCSettings.setGraphZoomStep(newVal.doubleValue());
            zoomValue.setText(formatZoomStep(MiniCSettings.graphZoomStep()));
        });

        HBox zoomRow = new HBox(10, zoomSlider, zoomValue);

        Label zoomAnchorLabel = new Label("图形缩放中心");
        zoomAnchorLabel.getStyleClass().add("activity-placeholder-text");

        ComboBox<String> zoomAnchorCombo = new ComboBox<>(FXCollections.observableArrayList("mouse", "center"));
        zoomAnchorCombo.getStyleClass().add("control-secondary");
        zoomAnchorCombo.setValue(MiniCSettings.graphZoomAnchor());
        zoomAnchorCombo.setOnAction(event -> {
            String selected = zoomAnchorCombo.getValue();
            if (selected != null) {
                MiniCSettings.setGraphZoomAnchor(selected);
            }
        });

        Label keyBindingLabel = new Label("键位绑定");
        keyBindingLabel.getStyleClass().add("activity-placeholder-text");
        keyBindingWarning.getStyleClass().addAll("body-text", "key-binding-warning");

        VBox keyBindingRows = new VBox(8);
        keyBindingRows.getStyleClass().add("key-binding-list");
        keyBindingConfig.actions().forEach(action -> keyBindingRows.getChildren().add(keyBindingRow(action)));

        getChildren().addAll(
                title,
                themeLabel, themeRow,
                intervalLabel, intervalRow,
                uiScaleLabel, uiScaleRow,
                zoomLabel, zoomRow,
                zoomAnchorLabel, zoomAnchorCombo,
                keyBindingLabel, keyBindingRows, keyBindingWarning
        );
    }

    private void registerSettingsCommands() {
        controlHub.registerSettingsCommands(new MiniCWorkbenchControlHub.SettingsCommands(
                ThemeManager::setTheme,
                () -> shiftTheme(1),
                () -> shiftTheme(-1),
                MiniCSettings::setFrameIntervalMillis,
                MiniCSettings::frameIntervalMillis,
                MiniCSettings::minFrameInterval,
                MiniCSettings::maxFrameInterval,
                FRAME_INTERVAL_STEP
        ));
    }

    private void shiftTheme(int delta) {
        if (themeNames.isEmpty()) {
            refreshThemeList();
        }
        if (themeNames.isEmpty()) {
            return;
        }
        String current = themeCombo.getValue();
        int index = current == null ? -1 : themeNames.indexOf(current);
        int nextIndex = Math.floorMod(index + delta, themeNames.size());
        String next = themeNames.get(nextIndex);
        themeCombo.setValue(next);
        controlHub.setTheme(next);
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

    private HBox keyBindingRow(String action) {
        Label actionLabel = new Label(keyBindingConfig.labelFor(action));
        actionLabel.getStyleClass().add("body-text");
        actionLabel.setMinWidth(110);

        Button binding = new Button(bindingText(action));
        binding.getStyleClass().addAll("control-secondary", "key-binding-button");
        binding.setAccessibleText("keybinding:" + action);
        binding.setFocusTraversable(true);
        binding.setOnAction(event -> beginCapture(action, binding));
        binding.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleCaptureKey(action, binding, event));
        binding.addEventFilter(KeyEvent.KEY_RELEASED, event -> handleCaptureKeyRelease(action, binding, event));
        binding.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> handleCaptureMouse(action, binding, event));
        binding.addEventFilter(ScrollEvent.SCROLL, event -> handleCaptureScroll(action, binding, event));

        return new HBox(10, actionLabel, binding);
    }

    private String bindingText(String action) {
        List<String> keys = keyBindingConfig.keysFor(action);
        return keys.isEmpty() ? "(未绑定)" : String.join(" / ", keys);
    }

    private void beginCapture(String action, Button button) {
        clearCaptureStyle();
        captureAction = action;
        captureButton = button;
        pendingCombo = "";
        captureKeys.clear();
        button.getStyleClass().add("key-binding-capturing");
        button.setText("输入组合后按 Enter");
        keyBindingWarning.setText("");
        button.requestFocus();
    }

    private void handleCaptureKey(String action, Button button, KeyEvent event) {
        if (!isCapturing(action, button)) {
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            cancelCapture();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.ENTER) {
            confirmCapture();
            event.consume();
            return;
        }
        if (!isModifier(event.getCode())) {
            captureKeys.add(event.getCode());
        }
        String combo = MiniCKeyBindingConfig.comboFrom(event);
        if (!combo.isBlank()) {
            pendingCombo = combo;
            button.setText(combo + "  Enter 确认");
            clearConflict(button);
        }
        event.consume();
    }

    private void handleCaptureKeyRelease(String action, Button button, KeyEvent event) {
        if (!isCapturing(action, button) || isModifier(event.getCode())) {
            return;
        }
        captureKeys.remove(event.getCode());
        event.consume();
    }

    private void handleCaptureMouse(String action, Button button, MouseEvent event) {
        if (!isCapturing(action, button)) {
            return;
        }
        String combo = MiniCKeyBindingConfig.comboFrom(event, captureKeys);
        if (!combo.isBlank()) {
            pendingCombo = combo;
            button.setText(combo + "  Enter 确认");
            clearConflict(button);
            event.consume();
        }
    }

    private void handleCaptureScroll(String action, Button button, ScrollEvent event) {
        if (!isCapturing(action, button)) {
            return;
        }
        String combo = MiniCKeyBindingConfig.comboFrom(event, captureKeys);
        if (!combo.isBlank()) {
            pendingCombo = combo;
            button.setText(combo + "  Enter 确认");
            clearConflict(button);
            event.consume();
        }
    }

    private boolean isCapturing(String action, Button button) {
        return button == captureButton && action.equals(captureAction);
    }

    private void confirmCapture() {
        if (captureButton == null || captureAction.isBlank()) {
            return;
        }
        String normalizedCombo = MiniCKeyBindingConfig.normalizeCombo(pendingCombo);
        if (normalizedCombo.isBlank()) {
            showConflict("请输入包含普通按键或鼠标按键的组合。");
            return;
        }
        if (MiniCKeyBindingConfig.isReserved(normalizedCombo)) {
            showConflict("Enter/Esc 为保留键位，请重新输入组合。");
            return;
        }
        Optional<String> conflict = MiniCKeyBindingConfig.conflictingAction(captureAction, normalizedCombo);
        if (conflict.isPresent()) {
            showConflict("键位冲突：" + keyBindingConfig.labelFor(conflict.get()) + " 已使用 " + normalizedCombo);
            return;
        }
        MiniCKeyBindingConfig.setKeys(captureAction, List.of(normalizedCombo));
        captureButton.setText(bindingText(captureAction));
        clearCaptureStyle();
        captureAction = "";
        captureButton = null;
        pendingCombo = "";
        captureKeys.clear();
        keyBindingWarning.setText("");
    }

    private void cancelCapture() {
        clearCaptureStyle();
        captureAction = "";
        captureButton = null;
        pendingCombo = "";
        captureKeys.clear();
        keyBindingWarning.setText("");
    }

    private void showConflict(String message) {
        if (captureButton != null && !captureButton.getStyleClass().contains("key-binding-conflict")) {
            captureButton.getStyleClass().add("key-binding-conflict");
        }
        keyBindingWarning.setText(message);
    }

    private void clearConflict(Button button) {
        button.getStyleClass().remove("key-binding-conflict");
        keyBindingWarning.setText("");
    }

    private void clearCaptureStyle() {
        if (captureButton != null) {
            captureButton.getStyleClass().remove("key-binding-capturing");
            captureButton.getStyleClass().remove("key-binding-conflict");
            if (!captureAction.isBlank()) {
                captureButton.setText(bindingText(captureAction));
            }
        }
    }

    private String formatZoomStep(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static double roundUiScale(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", value * 100.0);
    }

    private static boolean isModifier(KeyCode code) {
        return code == KeyCode.CONTROL
                || code == KeyCode.ALT
                || code == KeyCode.SHIFT
                || code == KeyCode.META;
    }
}
