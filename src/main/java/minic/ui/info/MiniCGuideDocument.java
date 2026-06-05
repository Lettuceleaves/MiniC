package minic.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class MiniCGuideDocument {
    private static final Path DEFAULT_GUIDE = Path.of("docs", "GUIDE.md");
    private static final String DEFAULT_VERSION = "1.0.0";

    private MiniCGuideDocument() {}

    static Path defaultGuidePath() {
        return DEFAULT_GUIDE;
    }

    static String loadDefault() {
        return load(DEFAULT_GUIDE);
    }

    static String load(Path guidePath) {
        String markdown = readGuide(guidePath);
        for (Map.Entry<String, String> entry : runtimeVariables().entrySet()) {
            markdown = markdown.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return markdown;
    }

    private static String readGuide(Path guidePath) {
        try {
            return Files.readString(guidePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "# MiniC 使用指南\n\nGUIDE.md 未找到: `" + guidePath + "`\n";
        }
    }

    private static Map<String, String> runtimeVariables() {
        LinkedHashMap<String, String> vars = new LinkedHashMap<>();
        vars.put("app.version", appVersion());
        vars.put("java.version", property("java.version"));
        vars.put("java.vendor", property("java.vendor"));
        vars.put("system.os.name", property("os.name"));
        vars.put("system.os.version", property("os.version"));
        vars.put("system.os.arch", property("os.arch"));
        vars.put("system.processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        return vars;
    }

    private static String appVersion() {
        String configured = System.getProperty("minic.version");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        Package pkg = MiniCGuideDocument.class.getPackage();
        String implementation = pkg == null ? null : pkg.getImplementationVersion();
        return implementation == null || implementation.isBlank() ? DEFAULT_VERSION : implementation;
    }

    private static String property(String key) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
