package craken.ui.component.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.AwtTransformers;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import craken.ui.component.UiStyles;

import javax.swing.JScrollBar;
import java.awt.Font;
import java.util.Objects;

/** 共享控制台显示和按键兼容处理；不决定执行 shell 还是用户程序。 */
public class UiTerminalWidget extends JediTermWidget {
    public UiTerminalWidget() {
        this(new Settings());
    }

    /** 指定设置的终端；IO 项传入 {@link TabDefaultFontSettings}，PowerShell 终端用 {@link Settings}。 */
    public UiTerminalWidget(SettingsProvider settings) {
        super(80, 12, settings);
    }

    /** 供测试确认终端当前使用的设置（字体来源）。 */
    public SettingsProvider settingsProvider() {
        return mySettingsProvider;
    }

    @Override
    protected UiTerminalPanel createTerminalPanel(SettingsProvider settings, StyleState style,
                                                  TerminalTextBuffer buffer) {
        return new UiTerminalPanel(settings, buffer, style);
    }

    @Override
    protected JScrollBar createScrollBar() {
        JScrollBar scrollBar = new JScrollBar();
        UiStyles.installScrollBarStyle(scrollBar, (graphics, track) -> {
            var result = getTerminalPanel().getFindResult();
            int rows = scrollBar.getMaximum() - scrollBar.getMinimum();
            if (result == null || rows <= 0) return;
            var color = mySettingsProvider.getTerminalColorPalette().getBackground(
                    Objects.requireNonNull(mySettingsProvider.getFoundPatternColor().getBackground()));
            graphics.setColor(AwtTransformers.toAwtColor(color));
            int markerHeight = Math.max(2, track.height / rows);
            for (var item : result.getItems()) {
                int y = track.y + (int) ((long) track.height * item.getStart().y / rows);
                graphics.fillRect(track.x, y, track.width, markerHeight);
            }
        });
        return scrollBar;
    }

    public static class Settings extends DefaultSettingsProvider {
        private final TerminalColor foreground = TerminalColor.rgb(230, 237, 243);
        private final TerminalColor background = TerminalColor.rgb(13, 17, 23);

        @Override public Font getTerminalFont() { return UiStyles.terminalFont(); }
        @Override public float getTerminalFontSize() { return getTerminalFont().getSize2D(); }
        @Override public float getLineSpacing() { return 1.10f; }
        @Override public boolean useAntialiasing() { return true; }
        @Override public TerminalColor getDefaultForeground() { return foreground; }
        @Override public TerminalColor getDefaultBackground() { return background; }
        @Override public TextStyle getDefaultStyle() { return new TextStyle(foreground, background); }
        @Override public TextStyle getSelectionColor() {
            return new TextStyle(TerminalColor.WHITE, TerminalColor.rgb(31, 111, 235));
        }
        @Override public boolean useInverseSelectionColor() { return false; }
        @Override public boolean audibleBell() { return false; }
        @Override public int getBufferMaxLinesCount() { return 10_000; }
    }

    /** IO 项跟随所在 tab 的默认字体：与交互列表标签、编辑器相同的等宽字体，不单独挑选终端字体。 */
    public static class TabDefaultFontSettings extends Settings {
        @Override public Font getTerminalFont() { return UiStyles.tabDefaultFont(); }
        @Override public float getTerminalFontSize() { return getTerminalFont().getSize2D(); }
    }
}
