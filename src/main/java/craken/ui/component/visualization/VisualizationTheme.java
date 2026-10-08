package craken.ui.component.visualization;

import java.util.Locale;

/** The visualization color protocol; no CSS, alpha, text, or border overrides are accepted. */
public final class VisualizationTheme {
    public sealed interface NodeColor permits Preset, CustomFill {}

    public enum Preset implements NodeColor { BLUE, TEAL, VIOLET, AMBER, RED, NEUTRAL, BLACK }

    public record CustomFill(String srgbHex) implements NodeColor {}

    /** Opaque, quantized sRGB. It is a value, not permission to use it as a node fill. */
    public record Srgb(int red, int green, int blue) {
        public Srgb {
            if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
                throw new IllegalArgumentException("sRGB channels must be within 0..255");
            }
        }

        public String hex() { return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue); }
        public double opacity() { return 1.0; }
    }

    public static final Srgb CANVAS = new Srgb(13, 17, 23);
    public static final Srgb GROUP_BACKGROUND = new Srgb(22, 27, 34);
    public static final Srgb GROUP_BORDER = new Srgb(48, 54, 61);
    public static final Srgb NODE_BORDER = new Srgb(90, 105, 126);
    /** Thin outline of the approved IDE card surface. */
    public static final Srgb IDE_BORDER = new Srgb(48, 54, 61);
    /** Light sectioned-card outline of the bucket design: cards, bucket frame and shared dividers. */
    public static final Srgb DESIGN_BORDER = new Srgb(139, 148, 158);
    /** Active blue outline of a selected IDE card. */
    public static final Srgb IDE_SELECTION = new Srgb(88, 166, 255);
    /** Opaque bucket-cell tint under the active blue outline; keeps the Inside stroke contract. */
    public static final Srgb IDE_SELECTION_TINT = new Srgb(31, 59, 92);
    /** Muted secondary labels on IDE cards; titles and field values stay white. */
    public static final Srgb IDE_MUTED_TEXT = new Srgb(139, 148, 158);
    public static final Srgb INNER_DIVIDER = GROUP_BORDER;
    public static final Srgb TEXT = new Srgb(255, 255, 255);
    public static final double NODE_RADIUS = 6.0;
    public static final double GROUP_RADIUS = 0.0;
    public static final double BORDER_WIDTH = 1.0;
    public static final double SELECTED_BORDER_WIDTH = 2.0;

    private final NodeColor color;
    private final ThemeValidator.ValidatedFill fill;

    private VisualizationTheme(NodeColor color, ThemeValidator.ValidatedFill fill) {
        this.color = color;
        this.fill = fill;
    }

    public static VisualizationTheme of(NodeColor color) {
        return new VisualizationTheme(color, ThemeValidator.validate(color));
    }

    /** Model colors use exactly the same validator as explicit UI theme requests. */
    public static VisualizationTheme of(craken.visualization.style.ColorSpec color) {
        if (color instanceof craken.visualization.style.ColorSpec.Preset preset) {
            return of(Preset.valueOf(preset.name()));
        }
        if (color instanceof craken.visualization.style.ColorSpec.CustomFill custom) {
            return of(new CustomFill(custom.srgbHex()));
        }
        throw new IllegalArgumentException("An inherited color needs the caller's default theme");
    }

    public NodeColor nodeColor() { return color; }
    /** NEUTRAL is the approved IDE card surface: thin outline, blue selection and muted field names. */
    public boolean ideCard() { return color == Preset.NEUTRAL; }
    public Srgb bodyFill() { return fill.body(); }
    public Srgb headerFill() { return fill.header(); }
}
