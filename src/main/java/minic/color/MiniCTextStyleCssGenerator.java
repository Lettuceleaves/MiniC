package minic.color;

import minic.uilocal.text.MiniCTextStyleRole;
import minic.uilocal.text.MiniCTextStyleState;

import java.util.Map;

/**
 * Generates JavaFX CSS for text roles and composable text states.
 */
final class MiniCTextStyleCssGenerator {
    private static final String UI_FONT = "\"Segoe UI\", \"Microsoft YaHei\", Arial, sans-serif";
    private static final String MONO_FONT = "Consolas, \"Courier New\", monospace";

    private MiniCTextStyleCssGenerator() {}

    static String generate() {
        Map<String, String> theme = ThemeRegistry.snapshot();
        StringBuilder css = new StringBuilder();
        css.append("\n/* Generated reusable text styles. */\n");
        for (MiniCTextStyleRole role : MiniCTextStyleRole.values()) {
            appendRole(css, theme, role);
        }
        for (MiniCTextStyleState state : MiniCTextStyleState.values()) {
            appendState(css, theme, state);
        }
        return css.toString();
    }

    private static void appendRole(StringBuilder css, Map<String, String> theme, MiniCTextStyleRole role) {
        String color = roleValue(theme, role, "color", themeValue(theme, role.fallbackColorKey()));
        String fontFamily = fontFamily(roleValue(theme, role, "fontFamily", role.fallbackFontFamily()), theme);
        String fontWeight = roleValue(theme, role, "fontWeight", role.fallbackFontWeight());
        String fontStyle = roleValue(theme, role, "fontStyle", role.fallbackFontStyle());
        css.append('.').append(role.cssClass()).append(" {\n");
        appendTextColor(css, color);
        css.append("    -fx-font-family: ").append(fontFamily).append(";\n");
        css.append("    -fx-font-weight: ").append(fontWeight).append(";\n");
        css.append("    -fx-font-style: ").append(fontStyle).append(";\n");
        css.append("}\n\n");
    }

    private static void appendState(StringBuilder css, Map<String, String> theme, MiniCTextStyleState state) {
        String color = stateValue(theme, state, "color", nullableThemeValue(theme, state.fallbackColorKey()));
        String background = stateValue(theme, state, "background", nullableThemeValue(theme, state.fallbackBackgroundKey()));
        css.append('.').append(state.cssClass()).append(" {\n");
        if (color != null) {
            appendTextColor(css, color);
        }
        if (background != null) {
            css.append("    -rtfx-background-color: ").append(background).append(";\n");
            css.append("    -fx-background-color: ").append(background).append(";\n");
        }
        css.append("}\n\n");
    }

    private static void appendTextColor(StringBuilder css, String color) {
        css.append("    -fx-fill: ").append(color).append(";\n");
        css.append("    -fx-text-fill: ").append(color).append(";\n");
    }

    private static String roleValue(
            Map<String, String> theme,
            MiniCTextStyleRole role,
            String property,
            String fallback
    ) {
        return theme.getOrDefault("textStyle." + role.themeId() + "." + property, fallback);
    }

    private static String stateValue(
            Map<String, String> theme,
            MiniCTextStyleState state,
            String property,
            String fallback
    ) {
        return theme.getOrDefault("textStyleState." + state.themeId() + "." + property, fallback);
    }

    private static String themeValue(Map<String, String> theme, String key) {
        String value = nullableThemeValue(theme, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing theme key: " + key);
        }
        return value;
    }

    private static String nullableThemeValue(Map<String, String> theme, String key) {
        if (key == null) {
            return null;
        }
        return theme.get(key);
    }

    private static String fontFamily(String configured, Map<String, String> theme) {
        String value = theme.getOrDefault("fontFamily." + configured, configured);
        return switch (value) {
            case "ui" -> UI_FONT;
            case "mono" -> MONO_FONT;
            default -> value;
        };
    }
}
