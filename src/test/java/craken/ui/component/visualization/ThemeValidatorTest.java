package craken.ui.component.visualization;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static craken.ui.component.visualization.VisualizationTheme.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-model")
final class ThemeValidatorTest {
    @Test
    void builtinPaletteMatchesTheApprovedQuantizedColors() {
        String[][] palette = {
                {"#28547D", "#254C70"}, {"#285C53", "#25534B"}, {"#57436E", "#4E3C62"},
                {"#65502F", "#5B482B"}, {"#70434A", "#643D43"}, {"#161B22", "#21262D"},
                {"#101317", "#20242B"}};
        for (Preset preset : Preset.values()) {
            VisualizationTheme theme = VisualizationTheme.of(preset);
            assertEquals(palette[preset.ordinal()][0], theme.bodyFill().hex());
            assertEquals(palette[preset.ordinal()][1], theme.headerFill().hex());
            assertTrue(contrast(theme.bodyFill()) >= 7.0);
            assertTrue(contrast(theme.headerFill()) >= 7.0);
            assertEquals(1.0, theme.bodyFill().opacity());
            assertEquals(1.0, theme.headerFill().opacity());
        }
    }

    @Test
    void customFillAutomaticallyDerivesItsHeaderAndPreservesBodyBytes() {
        VisualizationTheme theme = VisualizationTheme.of(new CustomFill("#445577"));
        assertEquals("#445577", theme.bodyFill().hex());
        assertEquals("#3E4D6B", theme.headerFill().hex());
        assertTrue(contrast(theme.headerFill()) >= 7.0);
        assertEquals("#4D4D4D", VisualizationTheme.of(new CustomFill("#555555")).headerFill().hex());
    }

    @Test
    void brightAndOverChromaticColorsFailEvenWhenWhiteContrastIsAdequate() {
        rejected("#D6A64F", ThemeValidator.ErrorCode.BODY_LIGHTNESS);
        rejected("#A43D46", ThemeValidator.ErrorCode.BODY_LIGHTNESS);
        rejected("#161B22", ThemeValidator.ErrorCode.BODY_LIGHTNESS);
        rejected("#101317", ThemeValidator.ErrorCode.BODY_LIGHTNESS);
        assertTrue(contrast(new Srgb(0, 0, 96)) >= 7);
        assertThrows(ThemeValidator.ValidationException.class,
                () -> VisualizationTheme.of(new CustomFill("#000060")));
        // L=0.4001406889 and contrast 9.31 are legal, but C=0.1207712573 is not.
        rejected("#144687", ThemeValidator.ErrorCode.BODY_CHROMA);
    }

    @Test
    void aBodyInsideOklchBoundsStillNeedsUnroundedFinalByteContrast() {
        // This color has L=0.4580527191, C=0.0823893346 but white contrast 6.9798186.
        rejected("#006276", ThemeValidator.ErrorCode.WHITE_CONTRAST);
        // The displayed contrast rounds to 7.00, while the actual ratio is 6.9998912577.
        rejected("#17626C", ThemeValidator.ErrorCode.WHITE_CONTRAST);
    }

    @Test
    void nearLightnessLimitsAreJudgedWithoutRoundingTheirOklchValues() {
        assertDoesNotThrow(() -> VisualizationTheme.of(new CustomFill("#484848")));
        assertDoesNotThrow(() -> VisualizationTheme.of(new CustomFill("#575757")));
        rejected("#474747", ThemeValidator.ErrorCode.BODY_LIGHTNESS);
        rejected("#585858", ThemeValidator.ErrorCode.BODY_LIGHTNESS);
    }

    @Test
    void onlyOpaqueSixDigitHexIsAcceptedAndNoCssCanEnterTheProtocol() {
        for (String input : new String[]{"red", "neutral", "#FFF", "#44557780", "rgba(1,2,3,1)",
                "linear-gradient(red, blue)", " #445577", "#445577; -fx-opacity: 0.5", null}) {
            ThemeValidator.ValidationException error = assertThrows(ThemeValidator.ValidationException.class,
                    () -> VisualizationTheme.of(new CustomFill(input)));
            assertEquals(ThemeValidator.ErrorCode.INVALID_COLOR, error.code());
            assertEquals(String.valueOf(input), error.input());
        }
        assertEquals("#445577", VisualizationTheme.of(new CustomFill("#445577")).bodyFill().hex());
        assertEquals("#28547D", VisualizationTheme.of(new CustomFill("#28547d")).bodyFill().hex());
    }

    @Test
    void aFailedBatchLeavesThePreviouslyPublishedThemeIntact() {
        Map<String, NodeColor> initial = new LinkedHashMap<>();
        initial.put("one", Preset.BLUE);
        initial.put("two", Preset.NEUTRAL);
        Map<String, VisualizationTheme> published = ThemeValidator.validateAll(initial);
        Map<String, NodeColor> requested = new LinkedHashMap<>(initial);
        requested.put("one", Preset.RED);
        requested.put("two", new CustomFill("#D6A64F"));
        assertThrows(ThemeValidator.ValidationException.class, () -> ThemeValidator.validateAll(requested));
        assertEquals("#28547D", published.get("one").bodyFill().hex());
        assertEquals("#161B22", published.get("two").bodyFill().hex());
        assertEquals(Preset.BLUE, initial.get("one"));
        assertThrows(UnsupportedOperationException.class, () -> published.clear());
    }

    @Test
    void fixedColorsAndGeometryCannotBeOverriddenByChangingNodeFill() {
        for (Preset preset : Preset.values()) {
            VisualizationTheme.of(preset);
            assertEquals("#0D1117", CANVAS.hex());
            assertEquals("#161B22", GROUP_BACKGROUND.hex());
            assertEquals("#30363D", GROUP_BORDER.hex());
            assertEquals("#5A697E", NODE_BORDER.hex());
            assertEquals("#30363D", INNER_DIVIDER.hex());
            assertEquals("#FFFFFF", TEXT.hex());
            assertEquals(1.0, TEXT.opacity());
            assertEquals(6.0, NODE_RADIUS);
            assertEquals(0.0, GROUP_RADIUS);
            assertEquals(1.0, BORDER_WIDTH);
            assertEquals(2.0, SELECTED_BORDER_WIDTH);
        }
    }

    @Test
    void exactOklchBoundsAreClosedAndNeighboringFloatingValuesFail() {
        assertDoesNotThrow(() -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(0.40, 0, 0), "minimum"));
        assertDoesNotThrow(() -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(0.46, 0.085, 359), "maximum"));
        assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(Math.nextDown(0.40), 0, 0), "too dark"));
        assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(Math.nextUp(0.46), 0, 0), "too light"));
        assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(0.43, Math.nextUp(0.085), 0), "too chromatic"));
        assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(0.43, -Double.MIN_VALUE, 0), "negative chroma"));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(ThemeValidator.ValidationException.class,
                    () -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(value, 0, 0), "non-finite L"));
            assertThrows(ThemeValidator.ValidationException.class,
                    () -> ThemeValidator.requireOrdinaryBody(new ThemeValidator.Oklch(0.43, value, 0), "non-finite C"));
        }
    }

    @Test
    void oklchRangeDoesNotPermitClippingColorsOutsideTheSrgbGamut() {
        ThemeValidator.Oklch outside = new ThemeValidator.Oklch(0.40, 0.085, 180);
        assertDoesNotThrow(() -> ThemeValidator.requireOrdinaryBody(outside, "outside"));
        ThemeValidator.ValidationException failure = assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.quantize(outside, "outside"));
        assertEquals(ThemeValidator.ErrorCode.OUT_OF_GAMUT, failure.code());
        assertEquals("outside", failure.input());
        assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.quantize(new ThemeValidator.Oklch(Double.NaN, 0, 0), "non-finite"));
    }

    @Test
    void theFiveOrdinaryPresetHeadersAlsoMatchTheCustomDerivation() {
        for (Preset preset : new Preset[]{Preset.BLUE, Preset.TEAL, Preset.VIOLET, Preset.AMBER, Preset.RED}) {
            VisualizationTheme builtin = VisualizationTheme.of(preset);
            VisualizationTheme custom = VisualizationTheme.of(new CustomFill(builtin.bodyFill().hex()));
            assertEquals(builtin.headerFill(), custom.headerFill());
        }
        assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.requireWhiteContrast(new Srgb(255, 255, 255), "header"));
    }

    @Test
    void knownOklabReferenceAndHueNormalizationRemainStable() {
        ThemeValidator.Oklch red = ThemeValidator.toOklch(new Srgb(255, 0, 0));
        assertEquals(0.62795536, red.lightness(), 1e-7);
        assertEquals(0.25768331, red.chroma(), 1e-7);
        assertEquals(29.233885, red.hue(), 1e-5);
        assertEquals(ThemeValidator.quantize(new ThemeValidator.Oklch(0.43, 0.05, 30), "hue"),
                ThemeValidator.quantize(new ThemeValidator.Oklch(0.43, 0.05, 390), "hue"));
        assertEquals(ThemeValidator.quantize(new ThemeValidator.Oklch(0.43, 0.05, 330), "hue"),
                ThemeValidator.quantize(new ThemeValidator.Oklch(0.43, 0.05, -30), "hue"));
    }

    @Test
    void successfulBatchesAreImmutableSnapshotsIndependentOfTheInputMap() {
        Map<String, NodeColor> requests = new LinkedHashMap<>();
        requests.put("one", Preset.BLUE);
        requests.put("two", new CustomFill("#445577"));
        Map<String, VisualizationTheme> result = ThemeValidator.validateAll(requests);
        requests.clear();
        assertEquals(2, result.size());
        assertEquals("#3E4D6B", result.get("two").headerFill().hex());
        assertThrows(UnsupportedOperationException.class, () -> result.put("three", VisualizationTheme.of(Preset.RED)));
    }

    @Test
    void theFinalQuantizedHeaderHasItsOwnChromaAndContrastGate() {
        ThemeValidator.ValidationException chroma = assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.requireHeader(new Srgb(20, 70, 135), "header"));
        assertEquals(ThemeValidator.ErrorCode.HEADER_CHROMA, chroma.code());
        assertThrows(ThemeValidator.ValidationException.class,
                () -> ThemeValidator.requireHeader(new Srgb(255, 255, 255), "header"));
        // The two fixed exception headers also pass their independent checks.
        assertDoesNotThrow(() -> ThemeValidator.requireHeader(new Srgb(33, 38, 45), "neutral header"));
        assertDoesNotThrow(() -> ThemeValidator.requireHeader(new Srgb(32, 36, 43), "black header"));
    }

    private static void rejected(String fill, ThemeValidator.ErrorCode code) {
        ThemeValidator.ValidationException error = assertThrows(ThemeValidator.ValidationException.class,
                () -> VisualizationTheme.of(new CustomFill(fill)));
        assertEquals(code, error.code());
        assertEquals(fill, error.input());
    }

    private static double contrast(Srgb rgb) {
        return 1.05 / (0.2126 * linear(rgb.red()) + 0.7152 * linear(rgb.green())
                + 0.0722 * linear(rgb.blue()) + 0.05);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
