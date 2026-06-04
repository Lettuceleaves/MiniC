package minic.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MiniCSettings {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");
    private static final String DEFAULT_THEME = "dark";
    private static final long DEFAULT_FRAME_INTERVAL = 1000;
    private static final long MIN_FRAME_INTERVAL = 1;
    private static final long MAX_FRAME_INTERVAL = 1000;
    private static final double DEFAULT_GRAPH_ZOOM_STEP = 0.025;
    private static final double MIN_GRAPH_ZOOM_STEP = 0.001;
    private static final double MAX_GRAPH_ZOOM_STEP = 0.25;
    private static final String DEFAULT_GRAPH_ZOOM_ANCHOR = "mouse";
    private static final Map<String, String> DEFAULT_VALUES = defaultValues();
    private static final Map<String, String> values = new LinkedHashMap<>();
    private static Runnable frameIntervalChangeListener;

    private MiniCSettings() {}

    public static void load() {
        values.clear();
        values.putAll(DEFAULT_VALUES);
        if (!Files.exists(SETTINGS_FILE)) {
            save();
            return;
        }
        try {
            String json = Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8);
            Map<String, String> loaded = new LinkedHashMap<>();
            parseInto(json, loaded);
            values.putAll(loaded);
            if (!loaded.keySet().containsAll(DEFAULT_VALUES.keySet())) {
                save();
            }
        } catch (IOException ignored) {
        }
    }

    public static String theme() {
        return values.getOrDefault("theme", DEFAULT_THEME);
    }

    public static void setTheme(String name) {
        values.put("theme", name);
        save();
    }

    public static long frameIntervalMillis() {
        String raw = values.get("frameInterval");
        if (raw == null) {
            return DEFAULT_FRAME_INTERVAL;
        }
        try {
            long val = Long.parseLong(raw);
            return Math.max(MIN_FRAME_INTERVAL, Math.min(MAX_FRAME_INTERVAL, val));
        } catch (NumberFormatException e) {
            return DEFAULT_FRAME_INTERVAL;
        }
    }

    public static void setFrameIntervalMillis(long millis) {
        long clamped = Math.max(MIN_FRAME_INTERVAL, Math.min(MAX_FRAME_INTERVAL, millis));
        values.put("frameInterval", String.valueOf(clamped));
        save();
        if (frameIntervalChangeListener != null) {
            frameIntervalChangeListener.run();
        }
    }

    public static void setFrameIntervalChangeListener(Runnable listener) {
        frameIntervalChangeListener = listener;
    }

    public static long minFrameInterval() {
        return MIN_FRAME_INTERVAL;
    }

    public static long maxFrameInterval() {
        return MAX_FRAME_INTERVAL;
    }

    public static double graphZoomStep() {
        String raw = values.get("graphZoomStep");
        if (raw == null) {
            return DEFAULT_GRAPH_ZOOM_STEP;
        }
        try {
            double value = Double.parseDouble(raw);
            return Math.max(MIN_GRAPH_ZOOM_STEP, Math.min(MAX_GRAPH_ZOOM_STEP, value));
        } catch (NumberFormatException exception) {
            return DEFAULT_GRAPH_ZOOM_STEP;
        }
    }

    public static String graphZoomAnchor() {
        return values.getOrDefault("graphZoomAnchor", DEFAULT_GRAPH_ZOOM_ANCHOR);
    }

    public static boolean graphZoomAnchoredAtMouse() {
        return "mouse".equalsIgnoreCase(graphZoomAnchor());
    }

    private static void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
        } catch (IOException ignored) {
        }
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            sb.append("  \"").append(entry.getKey()).append("\": ");
            if (isNumeric(entry.getValue())) {
                sb.append(entry.getValue());
            } else {
                sb.append('"').append(entry.getValue()).append('"');
            }
            if (++i < values.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("}\n");
        try {
            Files.writeString(SETTINGS_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Map<String, String> defaultValues() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("theme", DEFAULT_THEME);
        defaults.put("frameInterval", String.valueOf(DEFAULT_FRAME_INTERVAL));
        defaults.put("graphZoomStep", String.valueOf(DEFAULT_GRAPH_ZOOM_STEP));
        defaults.put("graphZoomAnchor", DEFAULT_GRAPH_ZOOM_ANCHOR);
        return defaults;
    }

    private static void parseInto(String json, Map<String, String> target) {
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
            target.put(key, value);
        }
    }
}
