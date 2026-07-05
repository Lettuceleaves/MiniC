package minic.runtime.debug.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized @visual DSL declaration.
 *
 * @param name logical visual name
 * @param root root variable to visualize
 * @param kind normalized visual kind
 * @param attributes raw key-value options from the annotation
 * @param fields optional struct field list from fields=x,y
 * @param line source line number
 * @param styleRules optional node style rules declared by following @style lines
 */
public record VisualSpec(
        String name,
        String root,
        VisualKind kind,
        Map<String, String> attributes,
        List<String> fields,
        int line,
        List<VisualStyleRule> styleRules
) {
    public VisualSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(styleRules, "styleRules");
        if (name.isBlank() || root.isBlank()) {
            throw new IllegalArgumentException("name and root must not be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
        attributes = Map.copyOf(attributes);
        fields = List.copyOf(fields);
        styleRules = List.copyOf(styleRules);
    }

    public VisualSpec(
            String name,
            String root,
            VisualKind kind,
            Map<String, String> attributes,
            List<String> fields,
            int line
    ) {
        this(name, root, kind, attributes, fields, line, List.of());
    }

    public VisualSpec withStyleRule(VisualStyleRule rule) {
        ArrayList<VisualStyleRule> rules = new ArrayList<>(styleRules);
        rules.add(rule);
        return new VisualSpec(name, root, kind, attributes, fields, line, rules);
    }

    static List<String> parseFields(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        ArrayList<String> fields = new ArrayList<>();
        for (String field : value.split(",")) {
            String trimmed = field.trim();
            if (!trimmed.isEmpty()) {
                fields.add(trimmed);
            }
        }
        return fields;
    }

    public Map<String, String> options() {
        return attributes;
    }

    /**
     * Returns the ordered root variable names selected by the annotation.
     *
     * @return root variable names
     */
    public List<String> roots() {
        return parseRoots(attributes.get("roots"), root);
    }

    /**
     * Returns the selected layout strategy id.
     *
     * @return layout id
     */
    public String layout() {
        return attributes.getOrDefault("layout", "natural");
    }

    /**
     * Returns the selected style preset id.
     *
     * @return style id
     */
    public String style() {
        return attributes.getOrDefault("style", "default");
    }

    /**
     * Returns the additional captured type names selected by type=T or type=[A,B].
     *
     * @return additional type names
     */
    public List<String> captureTypes() {
        return parseList(attributes.get("type"));
    }

    private static List<String> parseRoots(String value, String fallbackRoot) {
        if (value == null || value.isBlank()) {
            return List.of(fallbackRoot);
        }
        List<String> parsed = parseList(value);
        return parsed.isEmpty() ? List.of(fallbackRoot) : parsed;
    }

    private static List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        ArrayList<String> result = new ArrayList<>();
        for (String item : normalized.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return List.copyOf(result);
    }
}
