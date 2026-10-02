package minic.ui.component.layout;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import minic.ui.component.UiComponent;
import minic.ui.component.action.UiButton;
import minic.ui.component.feedback.UiTooltip;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * {@link UiWorkspace} 的叶子容器，可承载任意 JavaFX 节点。
 *
 * <p>四个拆分方法都保留当前叶子及其内容，并返回承载新内容的叶子。调用者
 * 可以继续对任意一个返回值拆分，从而构建任意深度的横向、纵向混合布局。</p>
 */
public final class UiWorkspacePane extends StackPane implements UiComponent {
    private static final double CLOSE_BUTTON_SIZE = 24;
    private static final double CLOSE_BAR_HEIGHT = 28;
    private final UiWorkspace workspace;
    private final BorderPane body = new BorderPane();
    private final UiButton closeButton = new UiButton("×");
    private final UiRow closeBar = new UiRow(closeButton);
    private BooleanSupplier onCloseRequest = () -> true;
    private Runnable onClosed = () -> {};
    private boolean closed;

    UiWorkspacePane(UiWorkspace workspace, Node content) {
        this.workspace = workspace;
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
        getStyleClass().add("ui-workspace-pane");

        closeButton.getStyleClass().add("ui-workspace-pane-close");
        closeButton.setMinSize(CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
        closeButton.setPrefSize(CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
        closeButton.setMaxSize(CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
        closeButton.setFocusTraversable(false);
        closeButton.setAccessibleText("关闭此容器");
        closeButton.setTooltip(new UiTooltip("关闭区域", "关闭此容器，合并到相邻区域。", ""));
        closeButton.setOnAction(event -> {
            if (closeButton.isVisible()) close();
        });
        closeBar.getStyleClass().add("ui-workspace-pane-controls");
        closeBar.setAlignment(Pos.CENTER_RIGHT);
        closeBar.setPadding(new Insets(2));
        closeBar.setMinHeight(CLOSE_BAR_HEIGHT);
        closeBar.setPrefHeight(CLOSE_BAR_HEIGHT);
        closeBar.setMaxHeight(CLOSE_BAR_HEIGHT);
        body.setMinSize(0, 0);
        body.setTop(closeBar);
        getChildren().add(body);
        setCloseButtonVisible(false);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        setContent(content);
    }

    /** 返回当前承载的组件；空叶子返回 null。 */
    public Node content() {
        return body.getCenter();
    }

    /** 替换叶子中的组件；传入 null 会保留一个空叶子。 */
    public void setContent(Node content) {
        if (closed) throw new IllegalStateException("pane is closed");
        if (content == content()) {
            return;
        }
        if (content != null && content.getParent() != null) {
            throw new IllegalArgumentException("content already belongs to a parent");
        }
        body.setCenter(content);
    }

    public UiWorkspacePane splitLeft(Node content) {
        return workspace.split(this, content, Orientation.HORIZONTAL, true);
    }

    public UiWorkspacePane splitRight(Node content) {
        return workspace.split(this, content, Orientation.HORIZONTAL, false);
    }

    public UiWorkspacePane splitTop(Node content) {
        return workspace.split(this, content, Orientation.VERTICAL, true);
    }

    public UiWorkspacePane splitBottom(Node content) {
        return workspace.split(this, content, Orientation.VERTICAL, false);
    }

    /** 关闭当前叶子并提升其兄弟节点；拒绝关闭时不会改变布局。 */
    public boolean close() {
        return workspace.close(this);
    }

    public boolean isClosed() {
        return closed;
    }

    public void setOnCloseRequest(BooleanSupplier handler) {
        onCloseRequest = Objects.requireNonNull(handler);
    }

    public void setOnClosed(Runnable handler) {
        onClosed = Objects.requireNonNull(handler);
    }

    boolean allowClose() {
        return onCloseRequest.getAsBoolean();
    }

    /** 仅拆分布局中的可见叶子显示按钮，不改变显式 close() 的文档生命周期语义。 */
    void setCloseButtonVisible(boolean visible) {
        closeButton.setVisible(visible);
        closeBar.setVisible(visible);
        closeBar.setManaged(visible);
    }

    void closed() {
        closed = true;
        setCloseButtonVisible(false);
        body.setCenter(null);
        body.setTop(null);
        getChildren().clear();
        Runnable handler = onClosed;
        onClosed = () -> {};
        onCloseRequest = () -> false;
        handler.run();
    }

    UiWorkspace workspace() {
        return workspace;
    }
}
