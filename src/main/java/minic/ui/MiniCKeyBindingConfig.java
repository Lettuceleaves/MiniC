package minic.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 快捷键配置。
 */
public final class MiniCKeyBindingConfig {
    private static final Pattern BINDING_PATTERN = Pattern.compile(
            "\\{\\s*\"action\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"keys\"\\s*:\\s*\\[(.*?)]\\s*}",
            Pattern.DOTALL
    );
    private static final Pattern KEY_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private final List<KeyBinding> bindings;

    private MiniCKeyBindingConfig(List<KeyBinding> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    /**
     * 从资源加载快捷键配置。
     *
     * @return 快捷键配置
     */
    public static MiniCKeyBindingConfig loadDefault() {
        try (InputStream stream = MiniCKeyBindingConfig.class.getResourceAsStream("/minic/ui/keybindings.json")) {
            if (stream == null) {
                return fallback();
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return fallback();
        }
    }

    /**
     * 判断事件是否匹配某个动作。
     *
     * @param action 动作名
     * @param event 键盘事件
     * @return 是否匹配
     */
    public boolean matches(String action, KeyEvent event) {
        return bindings.stream()
                .filter(binding -> binding.action().equals(action))
                .anyMatch(binding -> binding.matches(event));
    }

    private static MiniCKeyBindingConfig parse(String json) {
        ArrayList<KeyBinding> bindings = new ArrayList<>();
        Matcher bindingMatcher = BINDING_PATTERN.matcher(json);
        while (bindingMatcher.find()) {
            String action = bindingMatcher.group(1);
            Matcher keyMatcher = KEY_PATTERN.matcher(bindingMatcher.group(2));
            while (keyMatcher.find()) {
                bindings.add(new KeyBinding(action, keyMatcher.group(1)));
            }
        }
        return bindings.isEmpty() ? fallback() : new MiniCKeyBindingConfig(bindings);
    }

    private static MiniCKeyBindingConfig fallback() {
        return new MiniCKeyBindingConfig(List.of(
                new KeyBinding("ast.zoom.in", "Ctrl+="),
                new KeyBinding("ast.zoom.in", "Ctrl++"),
                new KeyBinding("ast.zoom.out", "Ctrl+-")
        ));
    }

    private record KeyBinding(String action, String key) {
        private KeyBinding {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(key, "key");
        }

        private boolean matches(KeyEvent event) {
            ParsedKey parsed = ParsedKey.parse(key);
            return event.isControlDown() == parsed.control()
                    && event.isAltDown() == parsed.alt()
                    && event.isShiftDown() == parsed.shift()
                    && event.getCode() == parsed.code();
        }
    }

    private record ParsedKey(boolean control, boolean alt, boolean shift, KeyCode code) {
        private static ParsedKey parse(String key) {
            boolean control = false;
            boolean alt = false;
            boolean shift = false;
            KeyCode code = null;
            for (String part : key.split("\\+")) {
                String normalized = part.trim();
                if (normalized.equalsIgnoreCase("Ctrl") || normalized.equalsIgnoreCase("Control")) {
                    control = true;
                } else if (normalized.equalsIgnoreCase("Alt")) {
                    alt = true;
                } else if (normalized.equalsIgnoreCase("Shift")) {
                    shift = true;
                } else {
                    code = keyCode(normalized);
                }
            }
            if (code == null && key.endsWith("+")) {
                code = KeyCode.PLUS;
            }
            return new ParsedKey(control, alt, shift, code == null ? KeyCode.UNDEFINED : code);
        }

        private static KeyCode keyCode(String text) {
            return switch (text) {
                case "+" -> KeyCode.PLUS;
                case "=" -> KeyCode.EQUALS;
                case "-" -> KeyCode.MINUS;
                default -> KeyCode.getKeyCode(text.toUpperCase(java.util.Locale.ROOT));
            };
        }
    }
}
