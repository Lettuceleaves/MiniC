package craken.ui.interaction.terminal;

import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.ui.AwtTransformers;
import craken.ui.component.UiStyles;
import craken.ui.component.swing.UiSwingScrollBarUi;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 实际 Swing 绘制与鼠标事件，不启动 shell 或可见窗口。 */
final class TerminalScrollBarTest {
    private static final Color BACKGROUND = new Color(0x0d1117);
    private static final Color NORMAL = new Color(0x424850);
    private static final Color HOVER = new Color(0x5e656d);
    private static final Color ACTIVE = new Color(0x8b9197);

    @Test
    void arrowRepaintsCoverEveryEdgeOverALightBackingSurface() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int orientation : new int[]{JScrollBar.VERTICAL, JScrollBar.HORIZONTAL}) {
                var bar = new JScrollBar(orientation, 40, 20, 0, 100);
                UiStyles.installScrollBarStyle(bar, null);
                bar.setSize(orientation == JScrollBar.VERTICAL
                        ? new Dimension(16, 240) : new Dimension(240, 16));
                bar.doLayout();
                for (var child : bar.getComponents()) {
                    if (!(child instanceof JButton arrow)) continue;
                    for (int state = 0; state < 4; state++) {
                        arrow.setEnabled(state != 3);
                        arrow.getModel().setRollover(state == 1);
                        arrow.getModel().setArmed(state == 2);
                        arrow.getModel().setPressed(state == 2);
                        for (double scale : new double[]{1, 1.25, 1.5, 2}) {
                            var image = new BufferedImage((int) (arrow.getWidth() * scale),
                                    (int) (arrow.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
                            var graphics = image.createGraphics();
                            try {
                                graphics.setColor(Color.WHITE);
                                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                                graphics.scale(scale, scale);
                                arrow.paint(graphics);
                            } finally {
                                graphics.dispose();
                            }
                            for (int x = 0; x < image.getWidth(); x++) {
                                assertEquals(BACKGROUND.getRGB(), image.getRGB(x, 0), "top edge");
                                assertEquals(BACKGROUND.getRGB(), image.getRGB(x, image.getHeight() - 1), "bottom edge");
                            }
                            for (int y = 0; y < image.getHeight(); y++) {
                                assertEquals(BACKGROUND.getRGB(), image.getRGB(0, y), "left edge");
                                assertEquals(BACKGROUND.getRGB(), image.getRGB(image.getWidth() - 1, y), "right edge");
                            }
                        }
                    }
                }
            }
        });
    }

    @Test
    void sharedStyleKeepsBothOrientationsGeometryModelAndThemeStates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int orientation : new int[]{JScrollBar.VERTICAL, JScrollBar.HORIZONTAL}) {
                var bar = new JScrollBar(orientation, 40, 20, 0, 100);
                Dimension preferred = orientation == JScrollBar.VERTICAL
                        ? new Dimension(14, 240) : new Dimension(240, 14);
                bar.setPreferredSize(preferred);
                var model = bar.getModel();
                UiStyles.installScrollBarStyle(bar, null);
                assertInstanceOf(UiSwingScrollBarUi.class, bar.getUI());
                assertSame(model, bar.getModel());
                assertEquals(preferred, bar.getPreferredSize());
                assertEquals(40, bar.getValue());
                bar.setSize(preferred);
                bar.doLayout();
                assertEquals(BACKGROUND, bar.getBackground());
                Point thumb = findColor(paint(bar), NORMAL);
                bar.dispatchEvent(mouse(bar, MouseEvent.MOUSE_MOVED, thumb, 0, MouseEvent.NOBUTTON));
                assertEquals(HOVER.getRGB(), paint(bar).getRGB(thumb.x, thumb.y));
                bar.setValueIsAdjusting(true);
                assertEquals(ACTIVE.getRGB(), paint(bar).getRGB(thumb.x, thumb.y));
                bar.setValueIsAdjusting(false);
            }
        });
    }

    @Test
    void terminalRetainsItsOwnHistoryModelAndWheelAndThumbDragging() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                var bar = fixture.scrollBar;
                var panel = fixture.widget.getTerminalPanel();
                assertInstanceOf(UiSwingScrollBarUi.class, bar.getUI());
                assertSame(panel.getVerticalScrollModel(), bar.getModel());
                assertTrue(bar.getMinimum() < 0, "history must be available");
                int bottom = bar.getValue();
                var wheel = new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 0, 0,
                        10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1);
                panel.dispatchEvent(wheel);
                assertTrue(wheel.isConsumed());
                assertTrue(bar.getValue() < bottom, "wheel must still scroll terminal history");

                Point thumb = findColor(paint(bar), NORMAL);
                int beforeDrag = bar.getValue();
                bar.dispatchEvent(mouse(bar, MouseEvent.MOUSE_PRESSED, thumb,
                        InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
                Point target = new Point(thumb.x, Math.max(25, thumb.y - 70));
                bar.dispatchEvent(mouse(bar, MouseEvent.MOUSE_DRAGGED, target,
                        InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
                assertTrue(bar.getValue() < beforeDrag, "drag must still update the terminal's own model");
                bar.dispatchEvent(mouse(bar, MouseEvent.MOUSE_RELEASED, target, 0, MouseEvent.BUTTON1));
                assertFalse(bar.getValueIsAdjusting());
            }
        });
    }

    @Test
    void terminalSearchMarkersRemainVisibleAndCanBeCleared() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                var finder = new SubstringFinder("needle", false);
                var text = new CharBuffer("needle");
                for (int i = 0; i < text.length(); i++) finder.nextChar(0, 10, text, i);
                assertEquals(1, finder.getResult().getItems().size());
                var panel = fixture.widget.getTerminalPanel();
                panel.setFindResult(finder.getResult());
                var settings = new TerminalPanel.Settings();
                Color marker = AwtTransformers.toAwtColor(settings.getTerminalColorPalette()
                        .getBackground(settings.getFoundPatternColor().getBackground()));
                Point position = findColor(paint(fixture.scrollBar), marker);
                panel.setFindResult(null);
                assertEquals(BACKGROUND.getRGB(), paint(fixture.scrollBar).getRGB(position.x, position.y));
                fixture.scrollBar.setValues(0, 0, 0, 0);
                panel.setFindResult(finder.getResult());
                assertDoesNotThrow(() -> paint(fixture.scrollBar));
            }
        });
    }

    @Test
    void terminalTrackPaintsTheThemeAtNormalAndFractionalScale() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                for (double scale : new double[]{1, 1.25}) {
                    var bar = fixture.scrollBar;
                    BufferedImage image = new BufferedImage((int) Math.ceil(bar.getWidth() * scale),
                            (int) Math.ceil(bar.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    try { graphics.scale(scale, scale); bar.paint(graphics); }
                    finally { graphics.dispose(); }
                    assertEquals(BACKGROUND.getRGB(), image.getRGB(0, image.getHeight() / 2));
                    findColor(image, NORMAL);
                }
                Path preview = Path.of("build/terminal-scrollbar-check/terminal.png");
                Files.createDirectories(preview.getParent());
                ImageIO.write(paint(fixture.widget), "png", preview.toFile());
            } catch (java.io.IOException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    private static MouseEvent mouse(JScrollBar bar, int id, Point point, int modifiers, int button) {
        return new MouseEvent(bar, id, System.currentTimeMillis(), modifiers,
                point.x, point.y, 1, false, button);
    }

    private static Point findColor(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == color.getRGB()) return new Point(x, y);
            }
        }
        throw new AssertionError("missing painted color: " + color);
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { component.paint(graphics); }
        finally { graphics.dispose(); }
        return image;
    }

    private static void layout(Container component) {
        component.doLayout();
        for (var child : component.getComponents()) {
            if (child instanceof Container container) layout(container);
        }
    }

    private static JScrollBar findScrollBar(Container component) {
        for (var child : component.getComponents()) {
            if (child instanceof JScrollBar bar) return bar;
            if (child instanceof Container container) {
                JScrollBar found = findScrollBar(container);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Fixture implements AutoCloseable {
        final TerminalPanel.Widget widget = new TerminalPanel.Widget();
        final JScrollBar scrollBar;

        Fixture() {
            widget.setSize(700, 280);
            layout(widget);
            var terminal = widget.getTerminal();
            for (int i = 0; i < 120; i++) {
                terminal.writeCharacters("PS E:\\projects\\Craken> echo terminal history " + i);
                terminal.carriageReturn();
                terminal.newLine();
            }
            paint(widget); // JediTerm 在重绘时同步历史行到滚动模型。
            scrollBar = findScrollBar(widget);
            assertNotNull(scrollBar);
        }

        @Override public void close() { widget.close(); }
    }
}
