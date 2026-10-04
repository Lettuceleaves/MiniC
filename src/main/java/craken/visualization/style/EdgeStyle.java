package craken.visualization.style;

import java.util.Locale;
import java.util.Objects;

/** Opaque sRGB strokes are independent of restricted node fills; labels always use fixed white text. */
public record EdgeStyle(String lineColor, String arrowColor, String label) {
    public static final EdgeStyle DEFAULT = new EdgeStyle("#5A697E", null, "");
    public EdgeStyle {
        lineColor = color(lineColor); if (arrowColor != null) arrowColor = color(arrowColor);
        label = Objects.requireNonNullElse(label, "");
    }
    public String effectiveArrowColor() { return arrowColor == null ? lineColor : arrowColor; }
    private static String color(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Edge colors require opaque #RRGGBB");
        return value.toUpperCase(Locale.ROOT);
    }
}
