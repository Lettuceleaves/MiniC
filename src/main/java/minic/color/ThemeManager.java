package minic.color;

import javafx.application.Platform;
import javafx.scene.Scene;
import minic.settings.MiniCSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ThemeManager {
    private static final Path THEMES_DIR = Path.of("config", "themes");
    private static final String DEFAULT_THEME = "dark";
    private static String currentThemeName;
    private static Scene scene;
    private static Path cssFile;

    private ThemeManager() {}

    public static void bind(Scene target) {
        scene = target;
        ThemeRegistry.setRefreshCallback(ThemeManager::applyStylesheet);
        MiniCSettings.load();
        MiniCSettings.setUiScaleChangeListener(ThemeManager::applyStylesheet);
        currentThemeName = MiniCSettings.theme();
        refresh();
    }

    public static void refresh() {
        Path themePath = THEMES_DIR.resolve(currentThemeName + ".json");
        if (!Files.exists(themePath)) {
            themePath = THEMES_DIR.resolve(DEFAULT_THEME + ".json");
        }
        try {
            ThemeRegistry.load(themePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load theme: " + themePath, e);
        }
        applyStylesheet();
    }

    public static void setTheme(String themeName) {
        currentThemeName = themeName;
        MiniCSettings.setTheme(themeName);
        refresh();
    }

    public static String currentTheme() {
        return currentThemeName;
    }

    public static List<String> availableThemes() {
        if (!Files.isDirectory(THEMES_DIR)) {
            return List.of();
        }
        try (var stream = Files.list(THEMES_DIR)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Path themesDirectory() {
        return THEMES_DIR;
    }

    private static void applyStylesheet() {
        if (scene == null) return;
        Runnable task = () -> {
            try {
                String css = ThemeCssGenerator.generate();
                Path file = getCssFile();
                Files.writeString(file, css, StandardCharsets.UTF_8);
                String url = file.toUri().toString();
                scene.getStylesheets().setAll(url);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write theme CSS", e);
            }
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    private static Path getCssFile() throws IOException {
        if (cssFile == null) {
            cssFile = Files.createTempFile("minic-theme-", ".css");
            cssFile.toFile().deleteOnExit();
        }
        return cssFile;
    }
}
