package minic.color;

import javafx.application.Platform;
import javafx.scene.Scene;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ThemeManager {
    private static final Path THEMES_DIR = Path.of("config", "themes");
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");
    private static final String DEFAULT_THEME = "dark";
    private static String currentThemeName;
    private static Scene scene;
    private static Path cssFile;

    private ThemeManager() {}

    public static void bind(Scene target) {
        scene = target;
        ThemeRegistry.setRefreshCallback(ThemeManager::applyStylesheet);
        currentThemeName = loadSavedTheme();
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
        saveThemePreference(themeName);
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

    private static String loadSavedTheme() {
        if (!Files.exists(SETTINGS_FILE)) {
            return DEFAULT_THEME;
        }
        try {
            String json = Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8);
            String value = extractJsonValue(json, "theme");
            return value != null && !value.isBlank() ? value : DEFAULT_THEME;
        } catch (IOException e) {
            return DEFAULT_THEME;
        }
    }

    private static void saveThemePreference(String themeName) {
        String json = "{\n  \"theme\": \"" + themeName + "\"\n}\n";
        try {
            Files.writeString(SETTINGS_FILE, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int index = json.indexOf(pattern);
        if (index < 0) {
            return null;
        }
        int colon = json.indexOf(':', index + pattern.length());
        if (colon < 0) {
            return null;
        }
        int quote1 = json.indexOf('"', colon + 1);
        if (quote1 < 0) {
            return null;
        }
        int quote2 = json.indexOf('"', quote1 + 1);
        if (quote2 < 0) {
            return null;
        }
        return json.substring(quote1 + 1, quote2);
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
