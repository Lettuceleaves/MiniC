package craken.visualization.style;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static craken.visualization.style.ColorSpec.*;

/** Pure validation. Callers publish returned values only after the whole batch succeeds. */
public final class ColorValidator {
    private ColorValidator() {}

    public enum ErrorCode { INVALID_COLOR, BODY_LIGHTNESS, BODY_CHROMA, HEADER_CHROMA, OUT_OF_GAMUT, WHITE_CONTRAST }

    public static final class ValidationException extends IllegalArgumentException {
        private final ErrorCode code;
        private final String input;
        private ValidationException(ErrorCode code, String input, String message) {
            super(message + ": " + input);
            this.code = code;
            this.input = input;
        }
        public ErrorCode code() { return code; }
        public String input() { return input; }
    }

    /** A fill pair can only be produced by this validator. */
    public static final class ValidatedFill {
        private final Srgb body;
        private final Srgb header;
        private ValidatedFill(Srgb body, Srgb header) { this.body = body; this.header = header; }
        public Srgb body() { return body; }
        public Srgb header() { return header; }
    }

    public static ValidatedFill validate(ColorSpec color) {
        if (color instanceof Preset preset) {
            String[] pair = switch (preset) {
                case BLUE -> new String[]{"#28547D", "#254C70"};
                case TEAL -> new String[]{"#285C53", "#25534B"};
                case VIOLET -> new String[]{"#57436E", "#4E3C62"};
                case AMBER -> new String[]{"#65502F", "#5B482B"};
                case RED -> new String[]{"#70434A", "#643D43"};
                case NEUTRAL -> new String[]{"#161B22", "#21262D"};
                case BLACK -> new String[]{"#101317", "#20242B"};
            };
            Srgb body = parse(pair[0]);
            Srgb header = parse(pair[1]);
            if (preset != Preset.NEUTRAL && preset != Preset.BLACK) {
                requireOrdinaryBody(toOklch(body), preset.name());
            }
            requireWhiteContrast(body, preset.name());
            requireHeader(header, preset.name());
            return new ValidatedFill(body, header);
        }
        if (color instanceof CustomFill custom) {
            Srgb body = parse(custom.srgbHex());
            Oklch coordinates = toOklch(body);
            requireOrdinaryBody(coordinates, custom.srgbHex());
            requireWhiteContrast(body, custom.srgbHex());
            Srgb header = quantize(new Oklch(coordinates.lightness() - 0.03,
                    coordinates.chroma() * 0.9, coordinates.hue()), custom.srgbHex());
            requireHeader(header, custom.srgbHex());
            return new ValidatedFill(body, header);
        }
        throw new ValidationException(ErrorCode.INVALID_COLOR, String.valueOf(color), "Color is required");
    }

    public static <K> Map<K, ValidatedFill> validateAll(Map<K, ? extends ColorSpec> colors) {
        Map<K, ValidatedFill> resolved = new LinkedHashMap<>();
        colors.forEach((key, color) -> resolved.put(key, validate(color)));
        return Collections.unmodifiableMap(resolved);
    }

    public static double whiteContrast(Srgb color) {
        return 1.05 / (0.2126 * linear(color.red() / 255.0) + 0.7152 * linear(color.green() / 255.0)
                + 0.0722 * linear(color.blue() / 255.0) + 0.05);
    }

    public static void requireWhiteContrast(Srgb color, String input) {
        if (whiteContrast(color) < 7.0) {
            throw new ValidationException(ErrorCode.WHITE_CONTRAST, input,
                    "Final opaque sRGB fill needs white-text contrast >= 7:1");
        }
    }

    public static void requireHeader(Srgb color, String input) {
        if (toOklch(color).chroma() > 0.085) {
            throw new ValidationException(ErrorCode.HEADER_CHROMA, input, "Final header OKLCH C must be <= 0.085");
        }
        requireWhiteContrast(color, input);
    }

    public record Oklch(double lightness, double chroma, double hue) {}

    public static void requireOrdinaryBody(Oklch color, String input) {
        if (!Double.isFinite(color.lightness()) || color.lightness() < 0.40 || color.lightness() > 0.46) {
            throw new ValidationException(ErrorCode.BODY_LIGHTNESS, input, "Body OKLCH L must be within [0.40, 0.46]");
        }
        if (!Double.isFinite(color.chroma()) || color.chroma() < 0 || color.chroma() > 0.085) {
            throw new ValidationException(ErrorCode.BODY_CHROMA, input, "Body OKLCH C must be within [0, 0.085]");
        }
        if (!Double.isFinite(color.hue())) {
            throw new ValidationException(ErrorCode.INVALID_COLOR, input, "Hue must be finite");
        }
    }

    // Conversion matrices and transfer functions: W3C CSS Color 4, section 19.
    // https://www.w3.org/TR/css-color-4/#color-conversion-code
    private static final double[][] SRGB_TO_XYZ = {
            {506752.0 / 1228815, 87881.0 / 245763, 12673.0 / 70218},
            {87098.0 / 409605, 175762.0 / 245763, 12673.0 / 175545},
            {7918.0 / 409605, 87881.0 / 737289, 1001167.0 / 1053270}};
    private static final double[][] XYZ_TO_SRGB = {
            {12831.0 / 3959, -329.0 / 214, -1974.0 / 3959},
            {-851781.0 / 878810, 1648619.0 / 878810, 36519.0 / 878810},
            {705.0 / 12673, -2585.0 / 12673, 705.0 / 667}};
    private static final double[][] XYZ_TO_LMS = {
            {0.8190224379967030, 0.3619062600528904, -0.1288737815209879},
            {0.0329836539323885, 0.9292868615863434, 0.0361446663506424},
            {0.0481771893596242, 0.2642395317527308, 0.6335478284694309}};
    private static final double[][] LMS_TO_XYZ = {
            {1.2268798758459243, -0.5578149944602171, 0.2813910456659647},
            {-0.0405757452148008, 1.1122868032803170, -0.0717110580655164},
            {-0.0763729366746601, -0.4214933324022432, 1.5869240198367816}};
    private static final double[][] LMS_TO_OKLAB = {
            {0.2104542683093140, 0.7936177747023054, -0.0040720430116193},
            {1.9779985324311684, -2.4285922420485799, 0.4505937096174110},
            {0.0259040424655478, 0.7827717124575296, -0.8086757549230774}};
    private static final double[][] OKLAB_TO_LMS = {
            {1.0, 0.3963377773761749, 0.2158037573099136},
            {1.0, -0.1055613458156586, -0.0638541728258133},
            {1.0, -0.0894841775298119, -1.2914855480194092}};

    public static Oklch toOklch(Srgb color) {
        double[] rgb = {linear(color.red() / 255.0), linear(color.green() / 255.0), linear(color.blue() / 255.0)};
        double[] lms = multiply(XYZ_TO_LMS, multiply(SRGB_TO_XYZ, rgb));
        for (int i = 0; i < lms.length; i++) lms[i] = Math.cbrt(lms[i]);
        double[] lab = multiply(LMS_TO_OKLAB, lms);
        double chroma = Math.hypot(lab[1], lab[2]);
        // Hue is immaterial for achromatic fills. Keep a finite value without changing chroma.
        double hue = chroma <= 0.000004 ? 0 : Math.toDegrees(Math.atan2(lab[2], lab[1]));
        if (hue < 0) hue += 360;
        return new Oklch(lab[0], chroma, hue);
    }

    public static Srgb quantize(Oklch color, String input) {
        if (!Double.isFinite(color.lightness()) || !Double.isFinite(color.chroma()) || !Double.isFinite(color.hue())) {
            throw new ValidationException(ErrorCode.OUT_OF_GAMUT, input, "Non-finite color coordinates");
        }
        double radians = Math.toRadians(color.hue() % 360);
        double[] lab = {color.lightness(), color.chroma() * Math.cos(radians), color.chroma() * Math.sin(radians)};
        double[] lms = multiply(OKLAB_TO_LMS, lab);
        for (int i = 0; i < lms.length; i++) lms[i] = lms[i] * lms[i] * lms[i];
        double[] rgb = multiply(XYZ_TO_SRGB, multiply(LMS_TO_XYZ, lms));
        int[] bytes = new int[3];
        for (int i = 0; i < rgb.length; i++) {
            // Reject before rounding: a tiny negative channel must not silently become zero.
            if (!Double.isFinite(rgb[i]) || rgb[i] < 0 || rgb[i] > 1) {
                throw new ValidationException(ErrorCode.OUT_OF_GAMUT, input, "Derived fill is outside sRGB; no clipping is allowed");
            }
            double encoded = rgb[i] <= 0.0031308 ? 12.92 * rgb[i] : 1.055 * Math.pow(rgb[i], 1.0 / 2.4) - 0.055;
            bytes[i] = (int) Math.round(encoded * 255.0);
        }
        return new Srgb(bytes[0], bytes[1], bytes[2]);
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] result = new double[3];
        for (int row = 0; row < result.length; row++) {
            for (int column = 0; column < vector.length; column++) result[row] += matrix[row][column] * vector[column];
        }
        return result;
    }

    public static Srgb parse(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            throw new ValidationException(ErrorCode.INVALID_COLOR, String.valueOf(hex), "Expected opaque #RRGGBB");
        }
        return new Srgb(Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16), Integer.parseInt(hex.substring(5, 7), 16));
    }
}
