package minic.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 快捷键配置。
 */
public final class MiniCKeyBindingConfig {
    private static final Path USER_BINDINGS_FILE = Path.of("config", "keybindings.json");
    private static final Pattern BINDING_PATTERN = Pattern.compile(
            "\\{\\s*\"action\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"keys\"\\s*:\\s*\\[(.*?)]\\s*}",
            Pattern.DOTALL
    );
    private static final Pattern KEY_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final Map<String, String> ACTION_LABELS = actionLabels();
    private static volatile List<KeyBinding> activeBindings = loadBindings();

    /**
     * 从资源和用户覆盖文件加载快捷键配置。
     *
     * @return 快捷键配置
     */
    public static MiniCKeyBindingConfig loadDefault() {
        activeBindings = loadBindings();
        return new MiniCKeyBindingConfig();
    }

    /**
     * 判断键盘事件是否匹配某个动作。
     *
     * @param action 动作名
     * @param event 键盘事件
     * @return 是否匹配
     */
    public boolean matches(String action, KeyEvent event) {
        return activeBindings.stream()
                .filter(binding -> binding.action().equals(action))
                .anyMatch(binding -> binding.matches(event));
    }

    /**
     * 判断鼠标事件是否匹配某个动作。
     *
     * @param action 动作名
     * @param event 鼠标事件
     * @return 是否匹配
     */
    public boolean matches(String action, MouseEvent event) {
        return activeBindings.stream()
                .filter(binding -> binding.action().equals(action))
                .anyMatch(binding -> binding.matches(event));
    }

    public List<String> actions() {
        return activeBindings.stream()
                .map(KeyBinding::action)
                .distinct()
                .toList();
    }

    public List<String> keysFor(String action) {
        return activeBindings.stream()
                .filter(binding -> binding.action().equals(action))
                .map(KeyBinding::key)
                .toList();
    }

    public String labelFor(String action) {
        return ACTION_LABELS.getOrDefault(action, action);
    }

    public static void setKeys(String action, List<String> keys) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(keys, "keys");
        List<String> normalized = keys.stream()
                .map(MiniCKeyBindingConfig::normalizeCombo)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        LinkedHashMap<String, List<String>> map = activeBindingsByAction();
        map.put(action, normalized);
        activeBindings = bindingsFrom(map);
        save(map);
    }

    public static Optional<String> conflictingAction(String action, String key) {
        String normalized = normalizeCombo(key);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return activeBindings.stream()
                .filter(binding -> !binding.action().equals(action))
                .filter(binding -> normalizeCombo(binding.key()).equals(normalized))
                .map(KeyBinding::action)
                .findFirst();
    }

    public static boolean isReserved(String key) {
        ParsedInput parsed = ParsedInput.parse(key);
        return parsed.keyCode() == KeyCode.ENTER;
    }

    public static String comboFrom(KeyEvent event) {
        return combo(event.isControlDown(), event.isAltDown(), event.isShiftDown(), event.isMetaDown(), event.getCode(), null);
    }

    public static String comboFrom(MouseEvent event) {
        return combo(event.isControlDown(), event.isAltDown(), event.isShiftDown(), event.isMetaDown(), null, event.getButton());
    }

    public static String normalizeCombo(String key) {
        ParsedInput parsed = ParsedInput.parse(key);
        if (parsed.keyCode() == KeyCode.UNDEFINED && parsed.mouseButton() == null) {
            return "";
        }
        return combo(parsed.control(), parsed.alt(), parsed.shift(), parsed.meta(), parsed.keyCode(), parsed.mouseButton());
    }

    private static List<KeyBinding> loadBindings() {
        LinkedHashMap<String, List<String>> map = bindingsByAction(defaultBindings());
        if (Files.exists(USER_BINDINGS_FILE)) {
            try {
                bindingsByAction(parse(Files.readString(USER_BINDINGS_FILE, StandardCharsets.UTF_8)))
                        .forEach(map::put);
            } catch (IOException ignored) {
            }
        }
        return bindingsFrom(map);
    }

    private static List<KeyBinding> defaultBindings() {
        try (InputStream stream = MiniCKeyBindingConfig.class.getResourceAsStream("/minic/ui/keybindings.json")) {
            if (stream == null) {
                return fallbackBindings();
            }
            List<KeyBinding> parsed = parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return parsed.isEmpty() ? fallbackBindings() : parsed;
        } catch (IOException exception) {
            return fallbackBindings();
        }
    }

    private static List<KeyBinding> parse(String json) {
        ArrayList<KeyBinding> bindings = new ArrayList<>();
        Matcher bindingMatcher = BINDING_PATTERN.matcher(json);
        while (bindingMatcher.find()) {
            String action = bindingMatcher.group(1);
            Matcher keyMatcher = KEY_PATTERN.matcher(bindingMatcher.group(2));
            while (keyMatcher.find()) {
                String key = normalizeCombo(keyMatcher.group(1));
                if (!key.isBlank()) {
                    bindings.add(new KeyBinding(action, key));
                }
            }
        }
        return bindings;
    }

    private static List<KeyBinding> fallbackBindings() {
        return List.of(
                new KeyBinding("ast.zoom.in", "Ctrl+="),
                new KeyBinding("ast.zoom.in", "Ctrl++"),
                new KeyBinding("ast.zoom.out", "Ctrl+-")
        );
    }

    private static LinkedHashMap<String, List<String>> activeBindingsByAction() {
        return bindingsByAction(activeBindings);
    }

    private static LinkedHashMap<String, List<String>> bindingsByAction(List<KeyBinding> bindings) {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        for (KeyBinding binding : bindings) {
            map.computeIfAbsent(binding.action(), ignored -> new ArrayList<>()).add(binding.key());
        }
        map.replaceAll((action, keys) -> keys.stream().distinct().toList());
        return map;
    }

    private static List<KeyBinding> bindingsFrom(Map<String, List<String>> map) {
        ArrayList<KeyBinding> bindings = new ArrayList<>();
        map.forEach((action, keys) -> keys.forEach(key -> bindings.add(new KeyBinding(action, key))));
        return List.copyOf(bindings);
    }

    private static void save(Map<String, List<String>> map) {
        try {
            Files.createDirectories(USER_BINDINGS_FILE.getParent());
            Files.writeString(USER_BINDINGS_FILE, json(map), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String json(Map<String, List<String>> map) {
        StringBuilder builder = new StringBuilder("{\n  \"bindings\": [\n");
        int actionIndex = 0;
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            builder.append("    {\n");
            builder.append("      \"action\": \"").append(escape(entry.getKey())).append("\",\n");
            builder.append("      \"keys\": [");
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append('"').append(escape(entry.getValue().get(i))).append('"');
            }
            builder.append("]\n    }");
            if (++actionIndex < map.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n}\n");
        return builder.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String combo(
            boolean control,
            boolean alt,
            boolean shift,
            boolean meta,
            KeyCode keyCode,
            MouseButton mouseButton
    ) {
        ArrayList<String> parts = new ArrayList<>();
        if (control) {
            parts.add("Ctrl");
        }
        if (alt) {
            parts.add("Alt");
        }
        if (shift) {
            parts.add("Shift");
        }
        if (meta) {
            parts.add("Meta");
        }
        if (mouseButton != null && mouseButton != MouseButton.NONE) {
            parts.add(mouseName(mouseButton));
        } else if (keyCode != null && keyCode != KeyCode.UNDEFINED && !isModifier(keyCode)) {
            parts.add(keyName(keyCode));
        }
        return String.join("+", parts);
    }

    private static boolean isModifier(KeyCode code) {
        return code == KeyCode.CONTROL
                || code == KeyCode.ALT
                || code == KeyCode.SHIFT
                || code == KeyCode.META;
    }

    private static String keyName(KeyCode code) {
        return switch (code) {
            case PLUS -> "+";
            case EQUALS -> "=";
            case MINUS -> "-";
            case ENTER -> "Enter";
            default -> code.getName().isBlank() ? code.name() : code.getName();
        };
    }

    private static String mouseName(MouseButton button) {
        return switch (button) {
            case PRIMARY -> "MouseLeft";
            case MIDDLE -> "MouseMiddle";
            case SECONDARY -> "MouseRight";
            case BACK -> "MouseBack";
            case FORWARD -> "MouseForward";
            default -> "";
        };
    }

    private static Map<String, String> actionLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("ast.zoom.in", "AST 放大");
        labels.put("ast.zoom.out", "AST 缩小");
        return labels;
    }

    private record KeyBinding(String action, String key) {
        private KeyBinding {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(key, "key");
        }

        private boolean matches(KeyEvent event) {
            ParsedInput parsed = ParsedInput.parse(key);
            return parsed.mouseButton() == null
                    && event.isControlDown() == parsed.control()
                    && event.isAltDown() == parsed.alt()
                    && event.isShiftDown() == parsed.shift()
                    && event.isMetaDown() == parsed.meta()
                    && event.getCode() == parsed.keyCode();
        }

        private boolean matches(MouseEvent event) {
            ParsedInput parsed = ParsedInput.parse(key);
            return parsed.mouseButton() != null
                    && event.isControlDown() == parsed.control()
                    && event.isAltDown() == parsed.alt()
                    && event.isShiftDown() == parsed.shift()
                    && event.isMetaDown() == parsed.meta()
                    && event.getButton() == parsed.mouseButton();
        }
    }

    private record ParsedInput(
            boolean control,
            boolean alt,
            boolean shift,
            boolean meta,
            KeyCode keyCode,
            MouseButton mouseButton
    ) {
        private static ParsedInput parse(String key) {
            boolean control = false;
            boolean alt = false;
            boolean shift = false;
            boolean meta = false;
            KeyCode code = KeyCode.UNDEFINED;
            MouseButton mouse = null;
            String[] parts = key.split("\\+", -1);
            for (int i = 0; i < parts.length; i++) {
                String normalized = parts[i].trim();
                if (normalized.isEmpty() && i == parts.length - 1 && key.endsWith("+")) {
                    code = KeyCode.PLUS;
                } else if (normalized.equalsIgnoreCase("Ctrl") || normalized.equalsIgnoreCase("Control")) {
                    control = true;
                } else if (normalized.equalsIgnoreCase("Alt")) {
                    alt = true;
                } else if (normalized.equalsIgnoreCase("Shift")) {
                    shift = true;
                } else if (normalized.equalsIgnoreCase("Meta") || normalized.equalsIgnoreCase("Command")) {
                    meta = true;
                } else if (!normalized.isBlank()) {
                    MouseButton parsedMouse = mouseButton(normalized);
                    if (parsedMouse != null) {
                        mouse = parsedMouse;
                    } else {
                        code = keyCode(normalized);
                    }
                }
            }
            return new ParsedInput(control, alt, shift, meta, code, mouse);
        }

        private static MouseButton mouseButton(String text) {
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "mouseleft", "leftclick", "primaryclick" -> MouseButton.PRIMARY;
                case "mousemiddle", "middleclick" -> MouseButton.MIDDLE;
                case "mouseright", "rightclick", "secondaryclick" -> MouseButton.SECONDARY;
                case "mouseback", "backclick" -> MouseButton.BACK;
                case "mouseforward", "forwardclick" -> MouseButton.FORWARD;
                default -> null;
            };
        }

        private static KeyCode keyCode(String text) {
            return switch (text) {
                case "+" -> KeyCode.PLUS;
                case "=" -> KeyCode.EQUALS;
                case "-" -> KeyCode.MINUS;
                default -> {
                    KeyCode code = KeyCode.getKeyCode(text);
                    if (code == null) {
                        code = KeyCode.getKeyCode(text.toUpperCase(Locale.ROOT));
                    }
                    yield code == null ? KeyCode.UNDEFINED : code;
                }
            };
        }
    }
}
