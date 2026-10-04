package craken.ui.component.editor;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class UiEditorLineNumberMetricsTest {
    @Test
    void cachedMetricsExactlyMatchOriginalMeasurements() {
        Graphics2D graphics = image().createGraphics();
        try {
            UiEditorBreakpoints.LineNumberMetricsCache cache = cache();
            for (Font font : new Font[]{
                    new Font(Font.MONOSPACED, Font.PLAIN, 13),
                    new Font(Font.SANS_SERIF, Font.BOLD, 17),
                    new Font(Font.MONOSPACED, Font.ITALIC, 21)
            }) {
                graphics.setFont(font);
                for (double scale : new double[]{1, 1.25, 2}) {
                    graphics.setTransform(java.awt.geom.AffineTransform.getScaleInstance(scale, scale));
                    cache.prepare(font, graphics.getFontRenderContext());
                    for (int line : new int[]{1, 8, 10, 99, 100, 12345, Integer.MAX_VALUE}) {
                        assertOriginalMetrics(graphics, line, cache.get(line, graphics.getFontMetrics()));
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void repeatedLineAndEquivalentContextReuseMeasurements() {
        Graphics2D graphics = image().createGraphics();
        try {
            UiEditorBreakpoints.LineNumberMetricsCache cache = cache();
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, 13);
            graphics.setFont(font);
            FontRenderContext context = graphics.getFontRenderContext();
            cache.prepare(font, context);
            var first = cache.get(42, graphics.getFontMetrics());
            cache.prepare(new Font(Font.MONOSPACED, Font.PLAIN, 13), new FontRenderContext(
                    context.getTransform(), context.getAntiAliasingHint(), context.getFractionalMetricsHint()
            ));
            assertSame(first, cache.get(42, graphics.getFontMetrics()));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void fontChangesInvalidateMeasurements() {
        Graphics2D graphics = image().createGraphics();
        try {
            UiEditorBreakpoints.LineNumberMetricsCache cache = cache();
            UiEditorBreakpoints.LineNumberMetrics previous = null;
            for (Font font : new Font[]{
                    new Font(Font.MONOSPACED, Font.PLAIN, 13),
                    new Font(Font.MONOSPACED, Font.PLAIN, 20),
                    new Font(Font.MONOSPACED, Font.BOLD, 20),
                    new Font(Font.SERIF, Font.BOLD, 20)
            }) {
                graphics.setFont(font);
                cache.prepare(font, graphics.getFontRenderContext());
                var current = cache.get(123, graphics.getFontMetrics());
                assertNotSame(previous, current);
                assertOriginalMetrics(graphics, 123, current);
                previous = current;
            }
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void scaleAndRenderingHintChangesInvalidateMeasurements() {
        Graphics2D graphics = image().createGraphics();
        try {
            UiEditorBreakpoints.LineNumberMetricsCache cache = cache();
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            var previous = cache.get(123, graphics.getFontMetrics());

            graphics.scale(1.5, 1.5);
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            var scaled = cache.get(123, graphics.getFontMetrics());
            assertNotSame(previous, scaled);
            assertOriginalMetrics(graphics, 123, scaled);

            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            var antialiased = cache.get(123, graphics.getFontMetrics());
            assertNotSame(scaled, antialiased);
            assertOriginalMetrics(graphics, 123, antialiased);

            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            var fractional = cache.get(123, graphics.getFontMetrics());
            assertNotSame(antialiased, fractional);
            assertOriginalMetrics(graphics, 123, fractional);
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void boundedCacheEvictsLeastRecentlyUsedLine() {
        Graphics2D graphics = image().createGraphics();
        try {
            UiEditorBreakpoints.LineNumberMetricsCache cache = cache();
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            FontMetrics metrics = graphics.getFontMetrics();
            var first = cache.get(1, metrics);
            var second = cache.get(2, metrics);
            for (int line = 3; line <= UiEditorBreakpoints.LineNumberMetricsCache.MAX_ENTRIES; line++) {
                cache.get(line, metrics);
            }
            assertSame(first, cache.get(1, metrics));
            cache.get(UiEditorBreakpoints.LineNumberMetricsCache.MAX_ENTRIES + 1, metrics);
            assertSame(first, cache.get(1, metrics));
            assertNotSame(second, cache.get(2, metrics));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void clearReleasesMeasurementsEvenWhenNextContextIsUnchanged() {
        Graphics2D graphics = image().createGraphics();
        try {
            UiEditorBreakpoints.LineNumberMetricsCache cache = cache();
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            var previous = cache.get(1, graphics.getFontMetrics());
            cache.clear();
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            assertNotSame(previous, cache.get(1, graphics.getFontMetrics()));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void cachedNormalAndCenteredLabelsRenderIdenticalPixels() {
        BufferedImage expected = image();
        BufferedImage actual = image();
        drawLabels(expected, false);
        drawLabels(actual, true);
        assertArrayEquals(
                ((DataBufferInt) expected.getRaster().getDataBuffer()).getData(),
                ((DataBufferInt) actual.getRaster().getDataBuffer()).getData()
        );
    }

    private static void drawLabels(BufferedImage image, boolean cached) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            UiEditorBreakpoints.LineNumberMetricsCache cache = cache();
            cache.prepare(graphics.getFont(), graphics.getFontRenderContext());
            int[] lines = {1, 8, 10, 99, 100, 12345};
            for (int index = 0; index < lines.length; index++) {
                String number = Integer.toString(lines[index]);
                var measured = cached ? cache.get(lines[index], graphics.getFontMetrics()) : null;
                Rectangle2D bounds = cached ? null : graphics.getFont()
                        .createGlyphVector(graphics.getFontRenderContext(), number).getVisualBounds();
                double centerX = cached ? measured.centerX() : bounds.getCenterX();
                double centerY = cached ? measured.centerY() : bounds.getCenterY();
                int width = cached ? measured.width() : graphics.getFontMetrics().stringWidth(number);
                float baseline = (float) (20 + index * 24 - centerY);
                graphics.drawString(number, 100 - 8 - width, baseline);
                graphics.drawString(number, (float) (170 - centerX), baseline);
            }
        } finally {
            graphics.dispose();
        }
    }

    private static void assertOriginalMetrics(
            Graphics2D graphics, int line, UiEditorBreakpoints.LineNumberMetrics actual
    ) {
        String number = Integer.toString(line);
        Rectangle2D expected = graphics.getFont()
                .createGlyphVector(graphics.getFontRenderContext(), number).getVisualBounds();
        assertEquals(number, actual.text());
        assertEquals(expected.getCenterX(), actual.centerX());
        assertEquals(expected.getCenterY(), actual.centerY());
        assertEquals(graphics.getFontMetrics().stringWidth(number), actual.width());
    }

    private static UiEditorBreakpoints.LineNumberMetricsCache cache() {
        return new UiEditorBreakpoints.LineNumberMetricsCache();
    }

    private static BufferedImage image() {
        return new BufferedImage(240, 180, BufferedImage.TYPE_INT_ARGB);
    }
}
