package minic.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExplanationTemplates {
    private static final Map<String, Map<String, String>> cache = new LinkedHashMap<>();

    private ExplanationTemplates() {}

    public static String get(String stage, String key) {
        Map<String, String> sections = load(stage);
        String value = sections.get(key);
        return value != null ? value : sections.getOrDefault("default", "");
    }

    public static String header(String stage) {
        return load(stage).getOrDefault("header", "");
    }

    public static String footer(String stage) {
        return load(stage).getOrDefault("footer", "");
    }

    private static Map<String, String> load(String stage) {
        return cache.computeIfAbsent(stage, ExplanationTemplates::parse);
    }

    private static Map<String, String> parse(String stage) {
        Map<String, String> sections = new LinkedHashMap<>();
        String path = "/minic/templates/" + stage + ".md";
        InputStream stream = ExplanationTemplates.class.getResourceAsStream(path);
        if (stream == null) {
            return sections;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String currentKey = null;
            StringBuilder currentBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("<!--") && line.endsWith("-->")) {
                    continue;
                }
                if (line.startsWith("## ")) {
                    if (currentKey != null) {
                        sections.put(currentKey, currentBody.toString().strip());
                    }
                    currentKey = line.substring(3).strip();
                    currentBody.setLength(0);
                } else if (currentKey != null) {
                    currentBody.append(line).append('\n');
                }
            }
            if (currentKey != null) {
                sections.put(currentKey, currentBody.toString().strip());
            }
        } catch (IOException ignored) {
        }
        return sections;
    }
}
