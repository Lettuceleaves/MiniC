package minic.color;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ThemeCssGenerator {
    private static final String TEMPLATE_PATH = "/minic/ui/workbench.css";
    private static String template;

    private ThemeCssGenerator() {}

    public static String generate() {
        String css = loadTemplate();
        Map<String, String> snapshot = ThemeRegistry.snapshot();
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            css = css.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return css + MiniCTextStyleCssGenerator.generate();
    }

    private static String loadTemplate() {
        if (template == null) {
            try (InputStream is = ThemeCssGenerator.class.getResourceAsStream(TEMPLATE_PATH)) {
                if (is == null) {
                    throw new IllegalStateException("CSS template not found: " + TEMPLATE_PATH);
                }
                template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load CSS template", e);
            }
        }
        return template;
    }
}
