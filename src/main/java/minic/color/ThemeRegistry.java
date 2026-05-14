package minic.color;

import javafx.scene.paint.Color;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemeRegistry {
    private static final Path DEFAULT_CONFIG = Path.of("config", "theme.json");
    private static final Map<String, String> colors = new LinkedHashMap<>();
    private static boolean loaded;
    private static Runnable refreshCallback;

    private ThemeRegistry() {}

    public static void load(Path configPath) throws IOException {
        colors.clear();
        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        colors.putAll(parseJson(json));
        loaded = true;
    }

    public static String get(String key) {
        ensureLoaded();
        return colors.get(key);
    }

    public static Color getColor(String key) {
        ensureLoaded();
        String value = colors.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Unknown theme color: " + key);
        }
        return Color.web(value);
    }

    public static Map<String, String> snapshot() {
        ensureLoaded();
        return Collections.unmodifiableMap(new LinkedHashMap<>(colors));
    }

    private static void ensureLoaded() {
        if (!loaded) {
            try {
                load(DEFAULT_CONFIG);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load theme config: " + DEFAULT_CONFIG, e);
            }
        }
    }

    public static void setRefreshCallback(Runnable callback) {
        refreshCallback = callback;
    }

    static void requestRefresh() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        json = json.strip();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        for (String line : json.split("\n")) {
            line = line.strip();
            if (line.isEmpty() || line.equals(",")) continue;
            if (line.endsWith(",")) line = line.substring(0, line.length() - 1);
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (key.startsWith("\"") && key.endsWith("\"")) key = key.substring(1, key.length() - 1);
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            result.put(key, value);
        }
        return result;
    }
}
