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
        super(80, 12, new Settings());
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
}
