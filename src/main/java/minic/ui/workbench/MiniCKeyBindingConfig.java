package minic.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import minic.ui.control.MiniCWorkbenchControlHub;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 快捷键配置。
 */
public final class MiniCKeyBindingConfig {
    private static final Path USER_BINDINGS_FILE = Path.of("config", "keybindings.json");
    private static final String LEGACY_AST_ZOOM_IN = "ast.zoom.in";
    private static final String LEGACY_AST_ZOOM_OUT = "ast.zoom.out";
    private static final Pattern BINDING_PATTERN = Pattern.compile(
            "\\{\\s*\"action\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"keys\"\\s*:\\s*\\[(.*?)]\\s*}",
            Pattern.DOTALL
    );
    private static final Pattern KEY_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final LinkedHashMap<String, String> ACTION_LABELS = actionLabels();
    private static final List<String> ACTION_ORDER = List.copyOf(ACTION_LABELS.keySet());
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
        return matches(action, event, Set.of());
    }

    public boolean matches(String action, KeyEvent event, Set<KeyCode> heldKeys) {
        String normalizedAction = normalizeAction(action);
        return activeBindings.stream()
                .filter(binding -> binding.action().equals(normalizedAction))
                .anyMatch(binding -> binding.matches(event, heldKeys));
    }

    /**
     * 判断鼠标事件是否匹配某个动作。
     *
     * @param action 动作名
     * @param event 鼠标事件
     * @return 是否匹配
     */
    public boolean matches(String action, MouseEvent event) {
        return matches(action, event, Set.of());
    }

    public boolean matches(String action, MouseEvent event, Set<KeyCode> heldKeys) {
        String normalizedAction = normalizeAction(action);
        return activeBindings.stream()
                .filter(binding -> binding.action().equals(normalizedAction))
                .anyMatch(binding -> binding.matches(event, heldKeys));
    }

    public boolean matches(String action, ScrollEvent event) {
        return matches(action, event, Set.of());
    }

    public boolean matches(String action, ScrollEvent event, Set<KeyCode> heldKeys) {
        String normalizedAction = normalizeAction(action);
        return activeBindings.stream()
                .filter(binding -> binding.action().equals(normalizedAction))
                .anyMatch(binding -> binding.matches(event, heldKeys));
    }

    public List<String> actions() {
        ArrayList<String> actions = new ArrayList<>(ACTION_ORDER);
        activeBindings.stream()
                .map(KeyBinding::action)
                .distinct()
                .filter(action -> !actions.contains(action))
                .forEach(actions::add);
        return List.copyOf(actions);
    }

    public List<String> keysFor(String action) {
        String normalizedAction = normalizeAction(action);
        return activeBindings.stream()
                .filter(binding -> binding.action().equals(normalizedAction))
                .map(KeyBinding::key)
                .toList();
    }

    public String labelFor(String action) {
        String normalizedAction = normalizeAction(action);
        return ACTION_LABELS.getOrDefault(normalizedAction, normalizedAction);
    }

    public static void setKeys(String action, List<String> keys) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(keys, "keys");
        String normalizedAction = normalizeAction(action);
        List<String> normalized = keys.stream()
                .map(MiniCKeyBindingConfig::normalizeCombo)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        LinkedHashMap<String, List<String>> map = activeBindingsByAction();
        map.put(normalizedAction, normalized);
        activeBindings = bindingsFrom(map);
        save(map);
    }

    public static Optional<String> conflictingAction(String action, String key) {
        String normalizedAction = normalizeAction(action);
        String normalized = normalizeCombo(key);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return activeBindings.stream()
                .filter(binding -> !binding.action().equals(normalizedAction))
                .filter(binding -> normalizeCombo(binding.key()).equals(normalized))
                .map(KeyBinding::action)
                .findFirst();
    }

    public static boolean isReserved(String key) {
        ParsedInput parsed = ParsedInput.parse(key);
        return parsed.keys().contains(KeyCode.ENTER) || parsed.keys().contains(KeyCode.ESCAPE);
    }

    public static String comboFrom(KeyEvent event) {
        return combo(
                event.isControlDown(),
                event.isAltDown(),
                event.isShiftDown(),
                event.isMetaDown(),
                List.of(event.getCode()),
                null,
                null
        );
    }

    public static String comboFrom(MouseEvent event) {
        return comboFrom(event, Set.of());
    }

    public static String comboFrom(MouseEvent event, Set<KeyCode> heldKeys) {
        return combo(
                event.isControlDown(),
                event.isAltDown(),
                event.isShiftDown(),
                event.isMetaDown(),
                heldKeys,
                event.getButton(),
                null
        );
    }

    public static String comboFrom(ScrollEvent event) {
        return comboFrom(event, Set.of());
    }

    public static String comboFrom(ScrollEvent event, Set<KeyCode> heldKeys) {
        return combo(
                event.isControlDown(),
                event.isAltDown(),
                event.isShiftDown(),
                event.isMetaDown(),
                heldKeys,
                null,
                wheelDirection(event)
        );
    }

    public static String normalizeCombo(String key) {
        ParsedInput parsed = ParsedInput.parse(key);
        if (parsed.keys().isEmpty() && parsed.mouseButton() == null && parsed.wheelDirection() == null) {
            return "";
        }
        return combo(
                parsed.control(),
                parsed.alt(),
                parsed.shift(),
                parsed.meta(),
                parsed.keys(),
                parsed.mouseButton(),
                parsed.wheelDirection()
        );
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
            String action = normalizeAction(bindingMatcher.group(1));
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
                new KeyBinding(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, "Ctrl+="),
                new KeyBinding(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, "Ctrl++"),
                new KeyBinding(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, "Ctrl+-")
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

    private static String normalizeAction(String action) {
        return switch (action) {
            case LEGACY_AST_ZOOM_IN -> MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN;
            case LEGACY_AST_ZOOM_OUT -> MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT;
            default -> action;
        };
    }

    private static String combo(
            boolean control,
            boolean alt,
            boolean shift,
            boolean meta,
            Collection<KeyCode> keyCodes,
            MouseButton mouseButton,
            WheelDirection wheelDirection
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
        orderedKeys(keyCodes).forEach(code -> parts.add(keyName(code)));
        if (mouseButton != null && mouseButton != MouseButton.NONE) {
            parts.add(mouseName(mouseButton));
        } else if (wheelDirection != null) {
            parts.add(wheelName(wheelDirection));
        }
        return String.join("+", parts);
    }

    private static List<KeyCode> orderedKeys(Collection<KeyCode> keyCodes) {
        if (keyCodes == null) {
            return List.of();
        }
        return keyCodes.stream()
                .filter(code -> code != null && code != KeyCode.UNDEFINED && !isModifier(code))
                .distinct()
                .sorted(Comparator.comparing(MiniCKeyBindingConfig::keyName))
                .toList();
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
            case PERIOD -> "Period";
            case OPEN_BRACKET -> "[";
            case CLOSE_BRACKET -> "]";
            case ENTER -> "Enter";
            case ESCAPE -> "Esc";
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

    private static String wheelName(WheelDirection direction) {
        return switch (direction) {
            case UP -> "WheelUp";
            case DOWN -> "WheelDown";
            case LEFT -> "WheelLeft";
            case RIGHT -> "WheelRight";
        };
    }

    private static WheelDirection wheelDirection(ScrollEvent event) {
        double deltaX = event.getDeltaX();
        double deltaY = event.getDeltaY();
        if (Math.abs(deltaY) >= Math.abs(deltaX) && deltaY != 0) {
            return deltaY > 0 ? WheelDirection.UP : WheelDirection.DOWN;
        }
        if (deltaX != 0) {
            return deltaX > 0 ? WheelDirection.RIGHT : WheelDirection.LEFT;
        }
        return null;
    }

    private static LinkedHashMap<String, String> actionLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, "当前视口 · 放大");
        labels.put(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, "当前视口 · 缩小");
        labels.put(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP, "当前视口 · 向上滚动");
        labels.put(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, "当前视口 · 向下滚动");
        labels.put(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT, "当前视口 · 向左滚动");
        labels.put(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT, "当前视口 · 向右滚动");
        labels.put(MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE, "当前视口 · 居中高亮");
        labels.put(MiniCWorkbenchControlHub.DEBUG_START, "调试 · 从头开始");
        labels.put(MiniCWorkbenchControlHub.DEBUG_RUN_TO_END, "调试 · 运行到结束");
        labels.put(MiniCWorkbenchControlHub.DEBUG_RUN_TO_BREAKPOINT, "调试 · 下个断点");
        labels.put(MiniCWorkbenchControlHub.DEBUG_STEP_OVER, "调试 · 本层下一句");
        labels.put(MiniCWorkbenchControlHub.DEBUG_STEP_INTO, "调试 · 下一句");
        labels.put(MiniCWorkbenchControlHub.DEBUG_BACK_TO_BREAKPOINT, "调试 · 上个断点");
        labels.put(MiniCWorkbenchControlHub.DEBUG_STEP_BACK_OVER, "调试 · 本层上一句");
        labels.put(MiniCWorkbenchControlHub.DEBUG_STEP_BACK, "调试 · 上一句");
        labels.put(MiniCWorkbenchControlHub.COMPILER_NEXT, "编译器 · 下一步");
        labels.put(MiniCWorkbenchControlHub.COMPILER_NEXT_STAGE, "编译器 · 下一阶段");
        labels.put(MiniCWorkbenchControlHub.COMPILER_RUN_TO_EXECUTION, "编译器 · 到执行");
        labels.put(MiniCWorkbenchControlHub.COMPILER_PLAY, "编译器 · 播放");
        labels.put(MiniCWorkbenchControlHub.COMPILER_PLAY_FAST, "编译器 · 2x");
        labels.put(MiniCWorkbenchControlHub.COMPILER_PAUSE, "编译器 · 暂停");
        labels.put(MiniCWorkbenchControlHub.SETTINGS_THEME_NEXT, "设置 · 下一个主题");
        labels.put(MiniCWorkbenchControlHub.SETTINGS_THEME_PREVIOUS, "设置 · 上一个主题");
        labels.put(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_INCREASE, "设置 · 增加帧间隔");
        labels.put(MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_DECREASE, "设置 · 减少帧间隔");
        return labels;
    }

    private record KeyBinding(String action, String key) {
        private KeyBinding {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(key, "key");
        }

        private boolean matches(KeyEvent event, Set<KeyCode> heldKeys) {
            ParsedInput parsed = ParsedInput.parse(key);
            return parsed.mouseButton() == null
                    && parsed.wheelDirection() == null
                    && modifiersMatch(parsed, event.isControlDown(), event.isAltDown(), event.isShiftDown(), event.isMetaDown())
                    && keysMatch(parsed.keys(), heldKeys, event.getCode());
        }

        private boolean matches(MouseEvent event, Set<KeyCode> heldKeys) {
            ParsedInput parsed = ParsedInput.parse(key);
            return parsed.mouseButton() != null
                    && parsed.wheelDirection() == null
                    && modifiersMatch(parsed, event.isControlDown(), event.isAltDown(), event.isShiftDown(), event.isMetaDown())
                    && keysMatch(parsed.keys(), heldKeys)
                    && event.getButton() == parsed.mouseButton();
        }

        private boolean matches(ScrollEvent event, Set<KeyCode> heldKeys) {
            ParsedInput parsed = ParsedInput.parse(key);
            return parsed.mouseButton() == null
                    && parsed.wheelDirection() != null
                    && modifiersMatch(parsed, event.isControlDown(), event.isAltDown(), event.isShiftDown(), event.isMetaDown())
                    && keysMatch(parsed.keys(), heldKeys)
                    && wheelDirection(event) == parsed.wheelDirection();
        }
    }

    private static boolean modifiersMatch(
            ParsedInput parsed,
            boolean control,
            boolean alt,
            boolean shift,
            boolean meta
    ) {
        return control == parsed.control()
                && alt == parsed.alt()
                && shift == parsed.shift()
                && meta == parsed.meta();
    }

    private static boolean keysMatch(List<KeyCode> expected, Set<KeyCode> heldKeys, KeyCode eventCode) {
        ArrayList<KeyCode> actual = new ArrayList<>(heldKeys == null ? Set.of() : heldKeys);
        if (eventCode != null && eventCode != KeyCode.UNDEFINED && !isModifier(eventCode)) {
            actual.add(eventCode);
        }
        return orderedKeys(expected).equals(orderedKeys(actual));
    }

    private static boolean keysMatch(List<KeyCode> expected, Set<KeyCode> heldKeys) {
        return orderedKeys(expected).equals(orderedKeys(heldKeys == null ? Set.of() : heldKeys));
    }

    private record ParsedInput(
            boolean control,
            boolean alt,
            boolean shift,
            boolean meta,
            List<KeyCode> keys,
            MouseButton mouseButton,
            WheelDirection wheelDirection
    ) {
        private static ParsedInput parse(String key) {
            boolean control = false;
            boolean alt = false;
            boolean shift = false;
            boolean meta = false;
            ArrayList<KeyCode> keys = new ArrayList<>();
            MouseButton mouse = null;
            WheelDirection wheel = null;
            String[] parts = key.split("\\+", -1);
            for (int i = 0; i < parts.length; i++) {
                String normalized = parts[i].trim();
                if (normalized.isEmpty() && i == parts.length - 1 && key.endsWith("+")) {
                    addKey(keys, KeyCode.PLUS);
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
                        WheelDirection parsedWheel = wheelDirection(normalized);
                        if (parsedWheel != null) {
                            wheel = parsedWheel;
                        } else {
                            addKey(keys, keyCode(normalized));
                        }
                    }
                }
            }
            return new ParsedInput(control, alt, shift, meta, List.copyOf(keys), mouse, wheel);
        }

        private static void addKey(List<KeyCode> keys, KeyCode code) {
            if (code != null && code != KeyCode.UNDEFINED && !isModifier(code) && !keys.contains(code)) {
                keys.add(code);
            }
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

        private static WheelDirection wheelDirection(String text) {
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "wheelup", "scrollup" -> WheelDirection.UP;
                case "wheeldown", "scrolldown" -> WheelDirection.DOWN;
                case "wheelleft", "scrollleft" -> WheelDirection.LEFT;
                case "wheelright", "scrollright" -> WheelDirection.RIGHT;
                default -> null;
            };
        }

        private static KeyCode keyCode(String text) {
            return switch (text) {
                case "+" -> KeyCode.PLUS;
                case "=" -> KeyCode.EQUALS;
                case "-" -> KeyCode.MINUS;
                case ".", "Period" -> KeyCode.PERIOD;
                case "[" -> KeyCode.OPEN_BRACKET;
                case "]" -> KeyCode.CLOSE_BRACKET;
                case "Esc", "Escape" -> KeyCode.ESCAPE;
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

    private enum WheelDirection {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }
}
