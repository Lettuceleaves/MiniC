package minic.runtime.debug.visual.layout;

import java.util.List;
import java.util.Objects;

/**
 * Input consumed by a visual layout strategy.
 *
 * @param roots ordered root memory references
 * @param mirror runtime memory mirror
 * @param style style preset id
 */
public record LayoutInput(List<String> roots, VisualMemoryMirror mirror, String style) {
    public LayoutInput {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(style, "style");
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("roots must not be empty");
        }
        for (String root : roots) {
            if (root == null || root.isBlank()) {
                throw new IllegalArgumentException("roots must not contain blank values");
            }
        }
        if (style.isBlank()) {
            throw new IllegalArgumentException("style must not be blank");
        }
        roots = List.copyOf(roots);
    }
}
