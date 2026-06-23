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
 */
public record VisualSpec(
        String name,
        String root,
        VisualKind kind,
        Map<String, String> attributes,
        List<String> fields,
        int line
) implements minic.runtime.debug.visual.typed.VisualSpec {
    public VisualSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(fields, "fields");
        if (name.isBlank() || root.isBlank()) {
            throw new IllegalArgumentException("name and root must not be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
        attributes = Map.copyOf(attributes);
        fields = List.copyOf(fields);
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
}
