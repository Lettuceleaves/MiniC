package craken.ui.component;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 终端按“英文 1 格、中文 2 格”逐格绘制；终端字体必须是中英宽度严格 1:2 的物理字体。
 * 只补字形的复合字体（编辑器当前使用的那种）度量不满足该约束，回退字形会被裁成半个汉字。
 * 这里只约束终端字体，编辑器字体保持原样。
 */
final class UiStylesTerminalFontTest {
    @Test
    void terminalFontKeepsStrictHalfDoubleWidthMetrics() {
        Font font = UiStyles.terminalFont();
        FontMetrics metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                .createGraphics().getFontMetrics(font);
        int half = metrics.charWidth('M');
        assertTrue(half > 0, "terminal font must measure a latin cell");
        for (char latin = 0x20; latin <= 0x7E; latin++)
            assertEquals(half, metrics.charWidth(latin),
                    "printable ascii '" + latin + "' must be fixed pitch");
        for (char wide : "中文，：后索引".toCharArray())
            assertEquals(2 * half, metrics.charWidth(wide),
                    "full-width '" + wide + "' must advance exactly two cells or the glyph is clipped");
    }
}
