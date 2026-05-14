package minic.color;

import javafx.application.Platform;
import javafx.scene.Scene;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ThemeManager {
    private static final Path CONFIG_PATH = Path.of("config", "theme.json");
    private static Scene scene;
    private static Path cssFile;

    private ThemeManager() {}

    public static void bind(Scene target) {
        scene = target;
        ThemeRegistry.setRefreshCallback(ThemeManager::applyStylesheet);
        refresh();
    }

    public static void refresh() {
        try {
            ThemeRegistry.load(CONFIG_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load theme config: " + CONFIG_PATH, e);
        }
        applyStylesheet();
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
