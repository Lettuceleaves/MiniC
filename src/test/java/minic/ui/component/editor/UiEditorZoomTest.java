package minic.ui.component.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiEditorZoomTest {
    @Test
    void startsAtTheOriginalSize() {
        var zoom = new UiEditorZoom();

        assertEquals(0, zoom.level());
        assertEquals(1, zoom.factor());
        assertFalse(zoom.setLevel(0));
    }

    @Test
    void clampsAtBothLimitsAndReportsOnlyEffectiveChanges() {
        var zoom = new UiEditorZoom();

        assertTrue(zoom.setLevel(-100));
        assertEquals(-5, zoom.level());
        assertEquals(0.5, zoom.factor());
        assertFalse(zoom.setLevel(-6));
        assertFalse(zoom.setLevel(-5));

        assertTrue(zoom.setLevel(100));
        assertEquals(20, zoom.level());
        assertEquals(3, zoom.factor());
        assertFalse(zoom.setLevel(21));
        assertFalse(zoom.setLevel(20));
    }

    @Test
    void reversesImmediatelyAfterSaturatingAtEitherLimit() {
        var zoom = new UiEditorZoom();
        zoom.setLevel(100);

        assertTrue(zoom.setLevel(zoom.level() - 1));
        assertEquals(19, zoom.level());
        assertEquals(2.9, zoom.factor(), 1e-12);

        zoom.setLevel(-100);
        assertTrue(zoom.setLevel(zoom.level() + 1));
        assertEquals(-4, zoom.level());
        assertEquals(0.6, zoom.factor(), 1e-12);
    }

    @Test
    void resetRestoresTheOriginalSizeFromEitherDirection() {
        var zoom = new UiEditorZoom();

        for (double level : new double[]{20, -5, 2.5}) {
            zoom.setLevel(level);
            assertTrue(zoom.setLevel(0));
            assertEquals(0, zoom.level());
            assertEquals(1, zoom.factor());
            assertFalse(zoom.setLevel(0));
        }
    }

    @Test
    void preservesFractionalLevelsForSmoothZoom() {
        var zoom = new UiEditorZoom();

        assertTrue(zoom.setLevel(2.5));
        assertEquals(2.5, zoom.level());
        assertEquals(1.25, zoom.factor());
        assertFalse(zoom.setLevel(2.5));

        assertTrue(zoom.setLevel(-1.25));
        assertEquals(-1.25, zoom.level());
        assertEquals(0.875, zoom.factor());
    }

    @Test
    void rejectsNonFiniteLevelsWithoutChangingTheCurrentZoom() {
        var zoom = new UiEditorZoom();
        zoom.setLevel(2.5);

        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> zoom.setLevel(invalid));
            assertEquals(2.5, zoom.level());
            assertEquals(1.25, zoom.factor());
        }
    }

    @Test
    void repeatedZoomRoundTripsDoNotAccumulateScalingError() {
        var zoom = new UiEditorZoom();
        double originalFontSize = 13.5;

        for (int round = 0; round < 100; round++) {
            for (int level = -5; level <= 20; level++) {
                zoom.setLevel(level);
            }
            for (int level = 20; level >= -5; level--) {
                zoom.setLevel(level);
            }
            zoom.setLevel(2.5);
            assertEquals(16.875, originalFontSize * zoom.factor());
            zoom.setLevel(0);
            assertEquals(originalFontSize, originalFontSize * zoom.factor());
        }
    }
}
