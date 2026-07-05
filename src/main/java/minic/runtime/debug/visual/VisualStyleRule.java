package minic.runtime.debug.visual;

import java.util.Objects;

/**
 * Optional node style rule attached to the nearest preceding @visual spec.
 *
 * @param type target node type
 * @param template style template id
 * @param line source line number
 */
public record VisualStyleRule(
        String type,
        String template,
        int line
) {
    public VisualStyleRule {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(template, "template");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (template.isBlank()) {
            throw new IllegalArgumentException("template must not be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
    }
}
