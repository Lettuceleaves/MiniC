package minic.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MiniCSettings {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");
    private static final long DEFAULT_FRAME_INTERVAL = 1000;
    private static final long MIN_FRAME_INTERVAL = 1;
    private static final long MAX_FRAME_INTERVAL = 1000;
    private static final Map<String, String> values = new LinkedHashMap<>();
    private static Runnable frameIntervalChangeListener;

    private MiniCSettings() {}

    public static void load() {
        values.clear();
        if (!Files.exists(SETTINGS_FILE)) {
            return;
        }
        try {
            String json = Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8);
            parseInto(json, values);
        } catch (IOException ignored) {
        }
    }

    public static String theme() {
        return values.getOrDefault("theme", "dark");
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

    private static void save() {
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
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
