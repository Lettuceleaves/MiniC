package craken.ui.interaction.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import craken.ui.component.UiStyles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
final class TerminalFontTest {
    @Test
    void terminalUsesTheEditorFontFamilyWithItsOwnSize() {
        var settings = new TerminalPanel.Settings();
        Font font = settings.getTerminalFont();
        assertSame(UiStyles.terminalFont(), font);
        assertEquals(16, settings.getTerminalFontSize());
        assertEquals(settings.getTerminalFontSize(), font.getSize2D());
        assertEquals(Font.PLAIN, font.getStyle());
        assertFalse(font.isTransformed());
        assertNotEquals(Font.MONOSPACED, font.getFamily(Locale.ROOT));
        for (String family : new String[]{"Cascadia Mono", "Consolas"}) {
            if (new Font(family, Font.PLAIN, 16).getFamily(Locale.ROOT).equals(family)) {
                assertEquals(family, font.getFamily(Locale.ROOT));
                break;
            }
        }
    }

    @Test
    void chineseAndTerminalSymbolsRemainDisplayableAndAsciiColumnsStayEqual() {
        Font font = UiStyles.terminalFont();
        assertEquals(-1, font.canDisplayUpTo("中文终端：文件路径 / 你好 ─│┌┐└┘"));
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            var metrics = graphics.getFontMetrics(font);
            int cellWidth = metrics.charWidth('W');
            for (char character : "ilMW0123456789{}[]() +-_/\\".toCharArray()) {
                assertEquals(cellWidth, metrics.charWidth(character), "terminal grid: " + character);
            }
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void lineHeightIsRoomierAndTerminalGridStillUsesIntegerPixels() throws Exception {
        var settings = new TerminalPanel.Settings();
        assertTrue(settings.useAntialiasing());
        assertEquals(1.10f, settings.getLineSpacing());
        SwingUtilities.invokeAndWait(() -> {
            JediTermWidget widget = new JediTermWidget(80, 12, settings);
            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                int fontHeight = graphics.getFontMetrics(settings.getTerminalFont()).getHeight();
                int expectedRow = (int) Math.ceil(fontHeight * settings.getLineSpacing());
                assertTrue(expectedRow > fontHeight);
                assertEquals(expectedRow * 12, widget.getTerminalPanel().getPixelHeight());
            } finally {
                widget.close();
                graphics.dispose();
            }
        });
    }
}
