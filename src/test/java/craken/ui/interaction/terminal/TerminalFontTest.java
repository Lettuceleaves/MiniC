package craken.ui.interaction.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import craken.ui.component.UiStyles;
import craken.ui.component.terminal.UiTerminalWidget;
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
    void terminalKeepsItsOwnPlainSizeAndCjkCoverage() {
        var settings = new TerminalPanel.Settings();
        Font font = settings.getTerminalFont();
        assertSame(UiStyles.terminalFont(), font);
        assertEquals(16, settings.getTerminalFontSize());
        assertEquals(settings.getTerminalFontSize(), font.getSize2D());
        assertEquals(Font.PLAIN, font.getStyle());
        assertFalse(font.isTransformed());
        assertNotEquals(Font.MONOSPACED, font.getFamily(Locale.ROOT));
        // 终端字体不再复用编辑器字体族：终端逐格绘制，要求物理字体满足中英宽度严格 1:2，
        // 只补字形的编辑器复合字体做不到（契约与候选见 craken.ui.component.UiStylesTerminalFontTest）。
        assertTrue(font.canDisplay('中'), "终端字体必须自身覆盖中文字形");
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

    /** IO 项（运行/调试共用）不再由终端挑字体，而是跟随该 tab 的默认字体（等宽字体 14px）。 */
    @Test
    void ioTabsFollowTheTabDefaultFont() {
        var settings = new UiTerminalWidget.TabDefaultFontSettings();
        assertSame(UiStyles.tabDefaultFont(), settings.getTerminalFont());
        assertEquals(UiStyles.tabDefaultFont().getSize2D(), settings.getTerminalFontSize());
        assertEquals(UiStyles.listFont().getFamily(), settings.getTerminalFont().getFamily(Locale.ROOT));
        assertEquals(14, settings.getTerminalFontSize());
        assertTrue(settings.getTerminalFont().canDisplay('中'), "tab 默认字体必须能显示中文");
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
