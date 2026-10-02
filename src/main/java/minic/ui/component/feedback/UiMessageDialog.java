package minic.ui.component.feedback;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/** 常用文字提示的预设入口；自定义表单和业务结果使用 {@link UiModalDialog}。 */
public final class UiMessageDialog extends UiModalDialog<UiMessageDialog.Result> {
    public enum Result { CONFIRMED, SAVE, DISCARD, CANCEL }

    private UiMessageDialog(Window owner, String title, String message, String details,
                            List<Action<Result>> actions) {
        super(owner, title, content(message, details), actions);
        getDialogPane().getStyleClass().add("ui-message-dialog");
    }

    /** 单按钮提示，也适用于操作失败说明；确定、X、Esc 均返回 CONFIRMED。 */
    public static UiMessageDialog notice(Window owner, String title, String message, String details) {
        return new UiMessageDialog(owner, title, message, details,
                List.of(Action.primary("确定", Result.CONFIRMED)));
    }

    /** 二次确认；X、Esc 与取消均返回 CANCEL。 */
    public static UiMessageDialog confirm(Window owner, String title, String message, String details) {
        return new UiMessageDialog(owner, title, message, details, List.of(
                Action.primary("确定", Result.CONFIRMED), Action.cancel("取消", Result.CANCEL)));
    }

    /** 保存确认；组件只返回选择，保存或丢弃操作由业务调用者执行。 */
    public static UiMessageDialog saveChanges(Window owner, String title, String message, String details) {
        return new UiMessageDialog(owner, title, message, details, List.of(
                Action.primary("保存", Result.SAVE), Action.secondary("不保存", Result.DISCARD),
                Action.cancel("取消", Result.CANCEL)));
    }

    private static VBox content(String message, String details) {
        Label heading = label(message, "ui-message-dialog-message");
        Label detail = label(details, "ui-message-dialog-details");
        detail.setVisible(details != null && !details.isBlank());
        detail.setManaged(detail.isVisible());
        return new VBox(14, heading, detail);
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        return label;
    }
}
