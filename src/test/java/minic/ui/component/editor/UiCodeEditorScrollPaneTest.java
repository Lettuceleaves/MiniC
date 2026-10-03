package minic.ui.component.editor;

import org.fife.ui.rtextarea.RTextArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/** 纯 Swing 离屏测试，不启动 JavaFX 或桌面窗口。 */
final class UiCodeEditorScrollPaneTest {
    @Test
    void paintsTheComponentTreeOnceAndReusesExactPixelsAtIntegerAndFractionalScale() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (double scale : new double[] {1, 1.25, 1.5, 2}) {
                Fixture fixture = new Fixture(scale);
                fixture.pane.paintNormally();
                BufferedImage nativeImage = fixture.pane.nativeImage;
                int[] expected = nativeImage.getRGB(0, 0, nativeImage.getWidth(),
                        nativeImage.getHeight(), null, 0, nativeImage.getWidth());
                fixture.content.paints = 0;
                Graphics2D clear = nativeImage.createGraphics();
                try {
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(0, 0, nativeImage.getWidth(), nativeImage.getHeight());
                } finally {
                    clear.dispose();
                }
                fixture.pane.paintResizeSnapshot(fixture.buffer, 101, 67, scale, scale);

                assertEquals(1, fixture.content.paints, "native submission must not paint the tree twice");
                assertEquals(1, fixture.pane.nativePaints);
                for (int y = 0; y < fixture.pane.nativeImage.getHeight(); y++) {
                    for (int x = 0; x < fixture.pane.nativeImage.getWidth(); x++) {
                        assertEquals(expected[y * nativeImage.getWidth() + x], nativeImage.getRGB(x, y),
                                "same physical pixel at " + scale + "x: " + x + "," + y);
                    }
                }
                assertEquals(Color.MAGENTA.getRGB(), fixture.buffer.getRGB(
                        fixture.buffer.getWidth() - 1, fixture.buffer.getHeight() - 1),
                        "unused buffer capacity is neither cleared nor painted");
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"false, 100", "false, 255", "true, 100"})
    void transparentContentUsesOrdinaryNativePaintingWithoutExtraBackground(boolean opaque, int alpha)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (double scale : new double[] {1, 1.25, 1.5, 2}) {
                Fixture fixture = new Fixture(scale);
                fixture.pane.setOpaque(opaque);
                fixture.pane.setBackground(new Color(0, 0, 255, alpha));
                fixture.pane.getViewport().setOpaque(false);
                fixture.content.setOpaque(false);
                fixture.content.color = new Color(255, 0, 0, 100);
                BufferedImage nativeImage = fixture.pane.nativeImage;
                fill(nativeImage, Color.GRAY);
                fixture.pane.paintNormally();
                int[] expected = nativeImage.getRGB(0, 0, nativeImage.getWidth(),
                        nativeImage.getHeight(), null, 0, nativeImage.getWidth());

                fixture.content.paints = 0;
                fill(nativeImage, Color.GRAY);
                fixture.pane.paintResizeSnapshot(fixture.buffer, 101, 67, scale, scale);

                assertArrayEquals(expected, nativeImage.getRGB(0, 0, nativeImage.getWidth(),
                                nativeImage.getHeight(), null, 0, nativeImage.getWidth()),
                        "no extra background at scale=" + scale + ", opaque=" + opaque + ", alpha=" + alpha);
                assertEquals(2, fixture.content.paints, "transparent native painting must use the original tree");
                assertEquals(1, fixture.pane.nativePaints);
            }
        });
    }

    @Test
    void ordinaryPaintingSeesChangesAfterTheNativeSubmission() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = new Fixture(1);
            fixture.pane.paintResizeSnapshot(fixture.buffer, 101, 67, 1, 1);
            fixture.content.color = Color.GREEN;
            fixture.pane.paintNormally();

            assertEquals(2, fixture.content.paints, "ordinary input repaint cannot reuse the old snapshot");
            assertEquals(Color.GREEN.getRGB(), fixture.pane.nativeImage.getRGB(20, 20));
        });
    }

    @Test
    void clearsTheTemporarySnapshotEvenIfNativeSubmissionFails() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = new Fixture(1);
            fixture.pane.failNativePaint = true;
            assertThrows(IllegalStateException.class,
                    () -> fixture.pane.paintResizeSnapshot(fixture.buffer, 101, 67, 1, 1));
            fixture.content.color = Color.GREEN;
            fixture.pane.paintNormally();

            assertEquals(2, fixture.content.paints);
            assertEquals(Color.GREEN.getRGB(), fixture.pane.nativeImage.getRGB(20, 20));
        });
    }

    private static void fill(BufferedImage image, Color color) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
    }

    private static final class Fixture {
        private final CountingContent content = new CountingContent();
        private final NativePaintProbe pane;
        private final BufferedImage buffer;

        Fixture(double scale) {
            pane = new NativePaintProbe(scale);
            pane.setViewportView(content);
            pane.setSize(101, 67);
            pane.doLayout();
            pane.getViewport().doLayout();
            buffer = new BufferedImage((int) Math.ceil(101 * scale) + 12,
                    (int) Math.ceil(67 * scale) + 12, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D graphics = buffer.createGraphics();
            try {
                graphics.setColor(Color.MAGENTA);
                graphics.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());
            } finally {
                graphics.dispose();
            }
        }
    }

    private static final class CountingContent extends RTextArea {
        private int paints;
        private Color color = Color.RED;

        CountingContent() {
            setOpaque(true);
            setPreferredSize(new Dimension(400, 300));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paints++;
            graphics.setColor(color);
            graphics.fillRect(0, 0, getWidth(), getHeight());
            graphics.setColor(Color.BLUE);
            graphics.fillRect(5, 0, 1, getHeight());
        }
    }

    private static final class NativePaintProbe extends UiCodeEditorScrollPane {
        private final double scale;
        private final BufferedImage nativeImage;
        private int nativePaints;
        private boolean failNativePaint;

        NativePaintProbe(double scale) {
            super(new RTextArea(), false);
            this.scale = scale;
            // 与 JLightweightFrame.resizeBuffer 的物理像素取整方式保持一致。
            nativeImage = new BufferedImage((int) Math.round(101 * scale),
                    (int) Math.round(67 * scale), BufferedImage.TYPE_INT_ARGB_PRE);
        }

        @Override
        public void paintImmediately(int x, int y, int width, int height) {
            nativePaints++;
            if (failNativePaint) throw new IllegalStateException("test native paint failure");
            paintNormally();
        }

        private void paintNormally() {
            Graphics2D graphics = nativeImage.createGraphics();
            try {
                graphics.scale(scale, scale);
                paint(graphics);
            } finally {
                graphics.dispose();
            }
        }
    }
}
