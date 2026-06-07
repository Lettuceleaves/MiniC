package minic.settings;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MiniCSettings {
    private static final Path SETTINGS_FILE = Path.of("config", "settings.json");
    private static final String DEFAULT_THEME = "dark";
    private static final long DEFAULT_FRAME_INTERVAL = 1000;
    private static final long MIN_FRAME_INTERVAL = 1;
    private static final long MAX_FRAME_INTERVAL = 1000;
    private static final double DEFAULT_UI_SCALE = 1.0;
    private static final double MIN_UI_SCALE = 0.75;
    private static final double MAX_UI_SCALE = 1.5;
    private static final double DEFAULT_EDITOR_DISPLAY_SCALE = 1.0;
    private static final double MIN_EDITOR_DISPLAY_SCALE = 10.0 / 12.0;
    private static final double MAX_EDITOR_DISPLAY_SCALE = 2.0;
    private static final double DEFAULT_GRAPH_ZOOM_STEP = 0.025;
    private static final double MIN_GRAPH_ZOOM_STEP = 0.001;
    private static final double MAX_GRAPH_ZOOM_STEP = 0.25;
    private static final String DEFAULT_GRAPH_ZOOM_ANCHOR = "mouse";
    private static final String LAST_FILE_DIALOG_DIRECTORY_KEY = "lastFileDialogDirectory";
    private static final String OPEN_FILES_KEY = "openFiles";
    private static final MathContext TAB_ORDER_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal TAB_ORDER_STEP = BigDecimal.ONE;
    private static final Map<String, String> DEFAULT_VALUES = defaultValues();
    private static final Map<String, String> values = new LinkedHashMap<>();
    private static final List<OpenFileState> openFiles = new ArrayList<>();
    private static Runnable frameIntervalChangeListener;
    private static final List<Runnable> uiScaleChangeListeners = new ArrayList<>();

    private MiniCSettings() {}

    public static void load() {
        values.clear();
        values.putAll(DEFAULT_VALUES);
        openFiles.clear();
        if (!Files.exists(SETTINGS_FILE)) {
            save();
            return;
        }
        try {
            String json = Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8);
            ParsedSettings loaded = parseSettings(json);
            values.putAll(loaded.values());
            openFiles.addAll(loaded.openFiles());
            if (!loaded.values().keySet().containsAll(DEFAULT_VALUES.keySet()) || !loaded.hasOpenFiles()) {
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

    public static double uiScale() {
        String raw = values.get("uiScale");
        if (raw == null) {
            return DEFAULT_UI_SCALE;
        }
        try {
            double value = Double.parseDouble(raw);
            return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, value));
        } catch (NumberFormatException exception) {
            return DEFAULT_UI_SCALE;
        }
    }

    public static void setUiScale(double scale) {
        double clamped = Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, scale));
        values.put("uiScale", String.valueOf(clamped));
        save();
        for (Runnable listener : List.copyOf(uiScaleChangeListeners)) {
            listener.run();
        }
    }

    public static void setUiScaleChangeListener(Runnable listener) {
        uiScaleChangeListeners.clear();
        addUiScaleChangeListener(listener);
    }

    public static void addUiScaleChangeListener(Runnable listener) {
        if (listener != null && !uiScaleChangeListeners.contains(listener)) {
            uiScaleChangeListeners.add(listener);
        }
    }

    public static void removeUiScaleChangeListener(Runnable listener) {
        uiScaleChangeListeners.remove(listener);
    }

    public static double minUiScale() {
        return MIN_UI_SCALE;
    }

    public static double maxUiScale() {
        return MAX_UI_SCALE;
    }

    public static double editorDisplayScale() {
        String raw = values.get("editorDisplayScale");
        if (raw == null) {
            return DEFAULT_EDITOR_DISPLAY_SCALE;
        }
        try {
            double value = Double.parseDouble(raw);
            return Math.max(MIN_EDITOR_DISPLAY_SCALE, Math.min(MAX_EDITOR_DISPLAY_SCALE, value));
        } catch (NumberFormatException exception) {
            return DEFAULT_EDITOR_DISPLAY_SCALE;
        }
    }

    public static void setEditorDisplayScale(double scale) {
        double clamped = Math.max(MIN_EDITOR_DISPLAY_SCALE, Math.min(MAX_EDITOR_DISPLAY_SCALE, scale));
        values.put("editorDisplayScale", String.valueOf(clamped));
        save();
    }

    public static double minEditorDisplayScale() {
        return MIN_EDITOR_DISPLAY_SCALE;
    }

    public static double maxEditorDisplayScale() {
        return MAX_EDITOR_DISPLAY_SCALE;
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

    public static void setGraphZoomStep(double step) {
        double clamped = Math.max(MIN_GRAPH_ZOOM_STEP, Math.min(MAX_GRAPH_ZOOM_STEP, step));
        values.put("graphZoomStep", String.valueOf(clamped));
        save();
    }

    public static double minGraphZoomStep() {
        return MIN_GRAPH_ZOOM_STEP;
    }

    public static double maxGraphZoomStep() {
        return MAX_GRAPH_ZOOM_STEP;
    }

    public static String graphZoomAnchor() {
        return values.getOrDefault("graphZoomAnchor", DEFAULT_GRAPH_ZOOM_ANCHOR);
    }

    public static void setGraphZoomAnchor(String anchor) {
        String normalized = "center".equalsIgnoreCase(anchor) ? "center" : DEFAULT_GRAPH_ZOOM_ANCHOR;
        values.put("graphZoomAnchor", normalized);
        save();
    }

    public static boolean graphZoomAnchoredAtMouse() {
        return "mouse".equalsIgnoreCase(graphZoomAnchor());
    }

    public static Optional<Path> lastFileDialogDirectory() {
        String raw = values.get(LAST_FILE_DIALOG_DIRECTORY_KEY);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(normalizePath(Path.of(raw)));
    }

    public static void setLastFileDialogDirectory(Path directory) {
        if (directory == null) {
            values.put(LAST_FILE_DIALOG_DIRECTORY_KEY, "");
            save();
            return;
        }
        values.put(LAST_FILE_DIALOG_DIRECTORY_KEY, normalizePath(directory).toString());
        save();
    }

    public static void rememberFileDialogLocation(Path selectedPath) {
        if (selectedPath == null) {
            return;
        }
        Path path = normalizePath(selectedPath);
        Path directory = Files.isDirectory(path) ? path : path.getParent();
        if (directory != null) {
            setLastFileDialogDirectory(directory);
        }
    }

    public static List<OpenFileState> openFiles() {
        return openFiles.stream()
                .sorted(Comparator.comparing(OpenFileState::order))
                .toList();
    }

    public static void setOpenFiles(List<OpenFileState> files) {
        openFiles.clear();
        if (files != null) {
            for (OpenFileState file : files) {
                upsertOpenFile(file);
            }
        }
        save();
    }

    public static void rememberOpenFile(Path path, BigDecimal order) {
        if (path == null) {
            return;
        }
        upsertOpenFile(new OpenFileState(path, order));
        save();
    }

    public static void forgetOpenFile(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = normalizePath(path);
        openFiles.removeIf(state -> state.path().equals(normalized));
        save();
    }

    public static void updateOpenFileOrder(Path path, BigDecimal order) {
        if (path == null) {
            return;
        }
        Path normalized = normalizePath(path);
        for (int i = 0; i < openFiles.size(); i++) {
            OpenFileState state = openFiles.get(i);
            if (state.path().equals(normalized)) {
                openFiles.set(i, new OpenFileState(normalized, order));
                save();
                return;
            }
        }
        rememberOpenFile(normalized, order);
    }

    public static BigDecimal tabOrderBetween(BigDecimal previous, BigDecimal next) {
        if (previous == null && next == null) {
            return BigDecimal.ZERO;
        }
        if (previous == null) {
            return tabOrderBefore(next);
        }
        if (next == null) {
            return tabOrderAfter(previous);
        }
        return normalizeOrder(previous.add(next, TAB_ORDER_CONTEXT).divide(BigDecimal.valueOf(2), TAB_ORDER_CONTEXT));
    }

    public static BigDecimal tabOrderBefore(BigDecimal next) {
        return normalizeOrder(next.subtract(TAB_ORDER_STEP, TAB_ORDER_CONTEXT));
    }

    public static BigDecimal tabOrderAfter(BigDecimal previous) {
        return normalizeOrder(previous.add(TAB_ORDER_STEP, TAB_ORDER_CONTEXT));
    }

    private static void upsertOpenFile(OpenFileState file) {
        if (file == null) {
            return;
        }
        openFiles.removeIf(state -> state.path().equals(file.path()));
        openFiles.add(file);
    }

    private static void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
        } catch (IOException ignored) {
        }
        StringBuilder sb = new StringBuilder("{\n");
        int remaining = values.size() + 1;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            appendScalarSetting(sb, entry);
            if (--remaining > 0) {
                sb.append(',');
            }
            sb.append('\n');
        }
        appendOpenFiles(sb);
        sb.append("\n}\n");
        try {
            Files.writeString(SETTINGS_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void appendScalarSetting(StringBuilder sb, Map.Entry<String, String> entry) {
        sb.append("  \"").append(entry.getKey()).append("\": ");
        if (isNumeric(entry.getValue())) {
            sb.append(entry.getValue());
        } else {
            appendJsonString(sb, entry.getValue());
        }
    }

    private static void appendOpenFiles(StringBuilder sb) {
        List<OpenFileState> sorted = openFiles();
        if (sorted.isEmpty()) {
            sb.append("  \"").append(OPEN_FILES_KEY).append("\": []");
            return;
        }
        sb.append("  \"").append(OPEN_FILES_KEY).append("\": [\n");
        for (int i = 0; i < sorted.size(); i++) {
            OpenFileState file = sorted.get(i);
            sb.append("    { \"path\": ");
            appendJsonString(sb, file.path().toString());
            sb.append(", \"order\": ");
            appendJsonString(sb, file.order().toPlainString());
            sb.append(" }");
            if (i + 1 < sorted.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]");
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
        defaults.put("uiScale", String.valueOf(DEFAULT_UI_SCALE));
        defaults.put("editorDisplayScale", String.valueOf(DEFAULT_EDITOR_DISPLAY_SCALE));
        defaults.put("graphZoomStep", String.valueOf(DEFAULT_GRAPH_ZOOM_STEP));
        defaults.put("graphZoomAnchor", DEFAULT_GRAPH_ZOOM_ANCHOR);
        defaults.put(LAST_FILE_DIALOG_DIRECTORY_KEY, "");
        return defaults;
    }

    private static ParsedSettings parseSettings(String json) {
        Object parsed = new JsonReader(json).parse();
        Map<String, String> loadedValues = new LinkedHashMap<>();
        List<OpenFileState> loadedOpenFiles = new ArrayList<>();
        boolean hasOpenFiles = false;
        if (!(parsed instanceof Map<?, ?> root)) {
            return new ParsedSettings(loadedValues, loadedOpenFiles, false);
        }
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            Object value = entry.getValue();
            if (OPEN_FILES_KEY.equals(key)) {
                hasOpenFiles = true;
                loadedOpenFiles.addAll(parseOpenFiles(value));
            } else if (value instanceof String string) {
                loadedValues.put(key, string);
            } else if (value instanceof Number || value instanceof Boolean) {
                loadedValues.put(key, String.valueOf(value));
            }
        }
        return new ParsedSettings(loadedValues, loadedOpenFiles, hasOpenFiles);
    }

    private static List<OpenFileState> parseOpenFiles(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<OpenFileState> files = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object pathValue = map.get("path");
            Object orderValue = map.get("order");
            if (!(pathValue instanceof String path) || orderValue == null) {
                continue;
            }
            try {
                BigDecimal order = normalizeOrder(new BigDecimal(String.valueOf(orderValue)));
                files.add(new OpenFileState(Path.of(path), order));
            } catch (NumberFormatException ignored) {
            }
        }
        return files;
    }

    private static Path normalizePath(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static BigDecimal normalizeOrder(BigDecimal order) {
        return Objects.requireNonNull(order, "order").plus(TAB_ORDER_CONTEXT).stripTrailingZeros();
    }

    private static void appendJsonString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
    }

    public record OpenFileState(Path path, BigDecimal order) {
        public OpenFileState {
            path = normalizePath(path);
            order = normalizeOrder(order);
        }
    }

    private record ParsedSettings(Map<String, String> values, List<OpenFileState> openFiles, boolean hasOpenFiles) {
    }

    private static final class JsonReader {
        private final String source;
        private int index;

        private JsonReader(String source) {
            this.source = source == null ? "" : source;
        }

        private Object parse() {
            skipWhitespace();
            if (index >= source.length()) {
                return Map.of();
            }
            return parseValue();
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= source.length()) {
                return "";
            }
            char c = source.charAt(index);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            return parsePrimitive();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++;
            while (index < source.length()) {
                skipWhitespace();
                if (consume('}')) {
                    break;
                }
                if (index >= source.length() || source.charAt(index) != '"') {
                    break;
                }
                String key = parseString();
                skipWhitespace();
                if (!consume(':')) {
                    break;
                }
                map.put(key, parseValue());
                skipWhitespace();
                if (consume(',')) {
                    continue;
                }
                consume('}');
                break;
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            index++;
            while (index < source.length()) {
                skipWhitespace();
                if (consume(']')) {
                    break;
                }
                list.add(parseValue());
                skipWhitespace();
                if (consume(',')) {
                    continue;
                }
                consume(']');
                break;
            }
            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            index++;
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') {
                    break;
                }
                if (c != '\\' || index >= source.length()) {
                    sb.append(c);
                    continue;
                }
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> sb.append(parseUnicodeEscape());
                    default -> sb.append(escaped);
                }
            }
            return sb.toString();
        }

        private char parseUnicodeEscape() {
            if (index + 4 > source.length()) {
                return 'u';
            }
            String hex = source.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                return 'u';
            }
        }

        private Object parsePrimitive() {
            int start = index;
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c == ',' || c == ']' || c == '}' || Character.isWhitespace(c)) {
                    break;
                }
                index++;
            }
            String raw = source.substring(start, index);
            if ("true".equals(raw)) {
                return Boolean.TRUE;
            }
            if ("false".equals(raw)) {
                return Boolean.FALSE;
            }
            if ("null".equals(raw)) {
                return "";
            }
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException exception) {
                return raw;
            }
        }

        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }
    }
}
