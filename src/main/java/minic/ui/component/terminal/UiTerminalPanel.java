package minic.ui.component.terminal;

import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;

import java.awt.event.KeyEvent;

/** 统一 SwingNode 与 AWT 的“无字符”约定，供 shell 和用户程序终端共同使用。 */
public class UiTerminalPanel extends TerminalPanel {
    public UiTerminalPanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState style) {
        super(settings, buffer, style);
    }

    @Override
    public void processKeyEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_PRESSED && event.getKeyChar() == '\0'
                && !isIntentionalNul(event)) {
            // FX 的 CHAR_UNDEFINED 是 U+0000，AWT 的则是 U+FFFF。SwingNode 未转换它，
            // JediTerm 会发送 NUL 并吞掉下一次 KEY_TYPED（包括真正提交的中文）。
            // 仍让 JediTerm 处理按下事件：保留方向/F 键映射，并重置其 typed 去重状态。
            event.setKeyChar(KeyEvent.CHAR_UNDEFINED);
        }
        super.processKeyEvent(event);
    }

    private static boolean isIntentionalNul(KeyEvent event) {
        if (!event.isControlDown() || event.isAltDown() || event.isMetaDown()) return false;
        return event.getKeyCode() == KeyEvent.VK_SPACE || event.getKeyCode() == KeyEvent.VK_AT
                || event.getKeyCode() == KeyEvent.VK_2;
    }
}
