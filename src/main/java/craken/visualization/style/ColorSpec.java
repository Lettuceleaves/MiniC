package craken.visualization.style;

import java.util.Locale;

/** Pure model color request. Validation belongs to the transaction, before publication. */
public sealed interface ColorSpec permits ColorSpec.Preset, ColorSpec.CustomFill {
    enum Preset implements ColorSpec { BLUE, TEAL, VIOLET, AMBER, RED, NEUTRAL, BLACK }
    record CustomFill(String srgbHex) implements ColorSpec {}

    /** Opaque, quantized sRGB output; it is not a validated node fill request. */
    record Srgb(int red, int green, int blue) {
        public Srgb {
            if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
                throw new IllegalArgumentException("sRGB channels must be within 0..255");
            }
        }
        public String hex() { return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue); }
        public double opacity() { return 1.0; }
    }
}
