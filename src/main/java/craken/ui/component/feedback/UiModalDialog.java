package craken.ui.component.feedback;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import craken.ui.component.UiStyles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通用浅色模态窗口，承载任意 JavaFX 内容并返回业务类型的操作结果。
 * 在 JavaFX 线程构造和显示；多操作弹窗必须明确提供取消操作，X / Esc 与取消一致。
 */
public class UiModalDialog<R> extends Dialog<R> {
    public enum Role { PRIMARY, SECONDARY, CANCEL }

    /** 按钮文案、业务结果与交互角色，不包含业务回调或样式实现。 */
    public record Action<R>(String text, R result, Role role) {
        public Action {
            if (Objects.requireNonNull(text, "text").isBlank()) {
                throw new IllegalArgumentException("action text must not be blank");
            }
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(role, "role");
        }

        public static <R> Action<R> primary(String text, R result) {
            return new Action<>(text, result, Role.PRIMARY);
        }

        public static <R> Action<R> secondary(String text, R result) {
            return new Action<>(text, result, Role.SECONDARY);
        }

        public static <R> Action<R> cancel(String text, R result) {
            return new Action<>(text, result, Role.CANCEL);
        }
    }

    private final List<Action<R>> actions;
    private final Map<Action<R>, ButtonType> buttonTypes = new LinkedHashMap<>();
    private double dragOffsetX;
    private double dragOffsetY;

    public UiModalDialog(Window owner, String title, Node content, List<Action<R>> actions) {
        this.actions = List.copyOf(actions);
        validateActions(this.actions);
        Objects.requireNonNull(content, "content");
        initStyle(StageStyle.UNDECORATED);
        if (owner != null) initOwner(owner);
        initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        setTitle(title);
        setResizable(false);

        var pane = getDialogPane();
        pane.getStyleClass().add("ui-modal-dialog");
        pane.setPrefWidth(520);
        pane.setMinWidth(360);
        pane.setHeader(createTitleBar());
        StackPane body = new StackPane(content);
        body.setMinWidth(0);
        pane.setContent(body);

        Map<ButtonType, R> results = new LinkedHashMap<>();
        for (Action<R> action : this.actions) {
            ButtonBar.ButtonData data = switch (action.role()) {
                case PRIMARY -> ButtonBar.ButtonData.YES;
                case SECONDARY -> ButtonBar.ButtonData.NO;
                case CANCEL -> ButtonBar.ButtonData.CANCEL_CLOSE;
            };
            ButtonType type = new ButtonType(action.text(), data);
            buttonTypes.put(action, type);
            results.put(type, action.result());
            pane.getButtonTypes().add(type);
            Button button = (Button) pane.lookupButton(type);
            button.getStyleClass().add("ui-modal-dialog-action");
            if (action.role() == Role.PRIMARY) {
                button.getStyleClass().add("ui-modal-dialog-primary");
            }
        }
        // JavaFX 关闭单按钮弹窗时可能传入 null；仍返回其唯一的确认结果。
        R dismissed = this.actions.stream().filter(action -> action.role() == Role.CANCEL)
                .findFirst().orElse(this.actions.getFirst()).result();
        setResultConverter(type -> type == null ? dismissed : results.get(type));
        UiStyles.install(pane.getScene());
    }

    public final List<Action<R>> actions() { return actions; }

    /** 可用于禁用操作或安装校验过滤器；按钮的显示和结果仍由组件统一处理。 */
    public final Button actionButton(Action<R> action) {
        ButtonType type = buttonTypes.get(action);
        if (type == null) throw new IllegalArgumentException("action does not belong to this dialog");
        return (Button) getDialogPane().lookupButton(type);
    }

    private static void validateActions(List<? extends Action<?>> actions) {
        if (actions.isEmpty()) throw new IllegalArgumentException("at least one action is required");
        long primary = actions.stream().filter(action -> action.role() == Role.PRIMARY).count();
        long cancel = actions.stream().filter(action -> action.role() == Role.CANCEL).count();
        if (primary > 1 || cancel > 1 || actions.size() > 1 && cancel != 1) {
            throw new IllegalArgumentException("at most one primary action and one cancel action; "
                    + "multiple actions require a cancel action");
        }
        if (actions.stream().distinct().count() != actions.size()) {
            throw new IllegalArgumentException("actions must be distinct");
        }
    }

    private HBox createTitleBar() {
        Label title = new Label();
        title.textProperty().bind(titleProperty());
        title.getStyleClass().add("ui-modal-dialog-title");
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        Button close = new Button("\uE8BB");
        close.getStyleClass().addAll("window-control-button", "window-close-button", "ui-modal-dialog-close");
        close.setAccessibleText("关闭弹窗");
        close.setFocusTraversable(false);
        close.setMinSize(42, 36);
        close.setPrefSize(42, 36);
        close.setMaxSize(42, 36);
        close.setOnAction(event -> close());

        HBox bar = new HBox(title, close);
        bar.getStyleClass().add("ui-modal-dialog-titlebar");
        bar.setAlignment(Pos.CENTER_LEFT);
        title.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            Window window = getDialogPane().getScene().getWindow();
            dragOffsetX = event.getScreenX() - window.getX();
            dragOffsetY = event.getScreenY() - window.getY();
        });
        title.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            Window window = getDialogPane().getScene().getWindow();
            window.setX(event.getScreenX() - dragOffsetX);
            window.setY(event.getScreenY() - dragOffsetY);
        });
        return bar;
    }
}
