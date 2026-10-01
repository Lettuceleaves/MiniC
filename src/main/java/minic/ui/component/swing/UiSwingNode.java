package minic.ui.component.swing;

import javafx.embed.swing.SwingNode;
import javafx.scene.AccessibleAction;
import javafx.scene.input.MouseEvent;

/** 以 FX 焦点为准，避免多个 SwingNode 的原生焦点回传相互激活。 */
public final class UiSwingNode extends SwingNode {
    public UiSwingNode() {
        // 鼠标选择先确定 FX 归属，再让 SwingNode 转发原生鼠标事件。
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> requestUserFocus());
    }

    /** 用于用户明确选择区域（点击、标签切换和菜单命令）。 */
    public void requestUserFocus() {
        super.requestFocus();
    }

    @Override
    public void requestFocus() {
        // SwingNode 把 AWT windowGainedFocus 异步回传到此方法。只允许确认已有
        // 焦点，不能让旧承载窗事件覆盖新选择。键盘遍历由 FX 的 focus-visible 路径处理。
        if (getScene() != null && (getScene().getFocusOwner() == null || getScene().getFocusOwner() == this)) {
            super.requestFocus();
        }
    }

    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if (action == AccessibleAction.REQUEST_FOCUS && isFocusTraversable()) requestUserFocus();
        else super.executeAccessibleAction(action, parameters);
    }
}
