package minic.color;

import minic.settings.MiniCSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThemeCssGenerator {
    private static final String[] TEMPLATE_PATHS = {
            "/minic/uilocal/workbench.css",
            "/minic/uilocal/workbench-components.css"
    };
    private static final Pattern PIXEL_VALUE = Pattern.compile("(?<![-\\w.])(-?\\d+(?:\\.\\d+)?)px");
    private static String template;

    private ThemeCssGenerator() {}

    public static String generate() {
        String css = loadTemplate();
        Map<String, String> snapshot = ThemeRegistry.snapshot();
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            css = css.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return scalePixelValues(css + MiniCTextStyleCssGenerator.generate(), MiniCSettings.uiScale());
    }

    private static String scalePixelValues(String css, double scale) {
        if (Math.abs(scale - 1.0) < 0.0001) {
            return css;
        }
        Matcher matcher = PIXEL_VALUE.matcher(css);
        StringBuilder scaled = new StringBuilder();
        while (matcher.find()) {
            double pixels = Double.parseDouble(matcher.group(1));
            matcher.appendReplacement(scaled, Matcher.quoteReplacement(formatPixels(pixels * scale) + "px"));
        }
        matcher.appendTail(scaled);
        return scaled.toString();
    }

    private static String formatPixels(double value) {
        if (Math.abs(value) < 0.0001) {
            return "0";
        }
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        formatted = formatted.replaceAll("0+$", "");
        formatted = formatted.replaceAll("\\.$", "");
        return formatted;
    }

    private static String loadTemplate() {
        if (template == null) {
            StringBuilder css = new StringBuilder();
            for (String path : TEMPLATE_PATHS) {
                css.append(loadTemplatePart(path)).append('\n');
            }
            template = css.toString();
        }
        return template;
    }

    private static String loadTemplatePart(String path) {
        try (InputStream is = ThemeCssGenerator.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("CSS template not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load CSS template: " + path, e);
        }
    }
}
