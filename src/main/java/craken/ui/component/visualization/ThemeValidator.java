package craken.ui.component.visualization;

import craken.visualization.style.ColorSpec;
import craken.visualization.style.ColorValidator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import static craken.ui.component.visualization.VisualizationTheme.*;

/** UI compatibility facade over the core's only color validation implementation. */
public final class ThemeValidator {
    private ThemeValidator() {}
    public enum ErrorCode { INVALID_COLOR, BODY_LIGHTNESS, BODY_CHROMA, HEADER_CHROMA, OUT_OF_GAMUT, WHITE_CONTRAST }
    public static final class ValidationException extends IllegalArgumentException {
        private final ErrorCode code;
        private final String input;
        private ValidationException(ColorValidator.ValidationException failure) {
            super(failure.getMessage());
            code = ErrorCode.valueOf(failure.code().name());
            input = failure.input();
        }
        public ErrorCode code() { return code; }
        public String input() { return input; }
    }
    public static final class ValidatedFill {
        private final Srgb body;
        private final Srgb header;
        private ValidatedFill(ColorValidator.ValidatedFill core) { body = fromCore(core.body()); header = fromCore(core.header()); }
        public Srgb body() { return body; }
        public Srgb header() { return header; }
    }
    public static ValidatedFill validate(NodeColor color) {
        ColorSpec request = color instanceof Preset preset ? ColorSpec.Preset.valueOf(preset.name())
                : color instanceof CustomFill custom ? new ColorSpec.CustomFill(custom.srgbHex()) : null;
        return checked(() -> new ValidatedFill(ColorValidator.validate(request)));
    }
    public static <K> Map<K, VisualizationTheme> validateAll(Map<K, ? extends NodeColor> colors) {
        Map<K, VisualizationTheme> resolved = new LinkedHashMap<>();
        colors.forEach((key, color) -> resolved.put(key, VisualizationTheme.of(color)));
        return Collections.unmodifiableMap(resolved);
    }
    public static double whiteContrast(Srgb color) { return ColorValidator.whiteContrast(toCore(color)); }
    record Oklch(double lightness, double chroma, double hue) {}
    static void requireWhiteContrast(Srgb color, String input) {
        checked(() -> { ColorValidator.requireWhiteContrast(toCore(color), input); return null; });
    }
    static void requireHeader(Srgb color, String input) {
        checked(() -> { ColorValidator.requireHeader(toCore(color), input); return null; });
    }
    static void requireOrdinaryBody(Oklch color, String input) {
        checked(() -> { ColorValidator.requireOrdinaryBody(toCore(color), input); return null; });
    }
    static Oklch toOklch(Srgb color) {
        var core = ColorValidator.toOklch(toCore(color));
        return new Oklch(core.lightness(), core.chroma(), core.hue());
    }
    static Srgb quantize(Oklch color, String input) { return checked(() -> fromCore(ColorValidator.quantize(toCore(color), input))); }
    static Srgb parse(String hex) { return checked(() -> fromCore(ColorValidator.parse(hex))); }
    private static ColorValidator.Oklch toCore(Oklch color) { return new ColorValidator.Oklch(color.lightness(), color.chroma(), color.hue()); }
    private static ColorSpec.Srgb toCore(Srgb color) { return new ColorSpec.Srgb(color.red(), color.green(), color.blue()); }
    private static Srgb fromCore(ColorSpec.Srgb color) { return new Srgb(color.red(), color.green(), color.blue()); }
    private static <T> T checked(Supplier<T> operation) {
        try { return operation.get(); } catch (ColorValidator.ValidationException failure) { throw new ValidationException(failure); }
    }
}
