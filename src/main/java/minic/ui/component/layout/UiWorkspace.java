package minic.ui.component.layout;

import javafx.geometry.Orientation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import minic.ui.component.UiComponent;

/**
 * 可递归拆分的通用工作区。
 *
 * <p>工作区本身只维护一棵布局树：叶子是 {@link UiWorkspacePane}，分支是
 * {@link SplitPane}。拆分某个叶子时，原叶子会保留原有内容，并与一个新叶子
 * 一起放入新的分支，因此任意叶子都可以继续独立拆分。</p>
 */
public final class UiWorkspace extends StackPane implements UiComponent {
    private final ObservableList<UiWorkspacePane> panes = FXCollections.observableArrayList();
    private final ObservableList<UiWorkspacePane> readOnlyPanes = FXCollections.unmodifiableObservableList(panes);

    /** 创建空工作区；隐藏的叶子仍保留内容，直到显式 close。 */
    public UiWorkspace() {
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        setRadius(0);
        setRotate(0);
        getStyleClass().add("ui-workspace");

    }

    /** 创建一个以 initialContent 为首个叶子内容的工作区；内容可以为 null。 */
    public UiWorkspace(Node initialContent) {
        this();
        show(createPane(initialContent), null);
    }

    /** 当前布局的第一个叶子；空工作区返回 null。 */
    public UiWorkspacePane primaryPane() {
        return getChildren().isEmpty() ? null : firstPane(getChildren().getFirst());
    }

    public ObservableList<UiWorkspacePane> panes() {
        return readOnlyPanes;
    }

    /** 创建保留自身内容的叶子；由 show 或 split 将其放入布局。 */
    public UiWorkspacePane createPane(Node content) {
        UiWorkspacePane pane = new UiWorkspacePane(this, content);
        panes.add(pane);
        return pane;
    }

    public boolean isDisplayed(UiWorkspacePane pane) {
        return !getChildren().isEmpty() && (getChildren().getFirst() == pane
                || findParentSplit(getChildren().getFirst(), pane) != null);
    }

    /** 在指定位置切换叶子，不销毁被替换的叶子或编辑器状态。 */
    public void show(UiWorkspacePane pane, UiWorkspacePane replaced) {
        requireOwned(pane);
        if (isDisplayed(pane)) return;
        if (getChildren().isEmpty()) {
            getChildren().add(pane);
        } else {
            UiWorkspacePane target = isDisplayed(replaced) ? replaced : primaryPane();
            replaceNode(target, pane);
        }
        updateCloseButtons();
    }

    UiWorkspacePane split(
            UiWorkspacePane target,
            Node newContent,
            Orientation orientation,
            boolean newPaneFirst
    ) {
        requireOwned(target);
        if (!isDisplayed(target)) throw new IllegalStateException("pane is not displayed");
        if (newContent != null && newContent.getParent() != null) {
            throw new IllegalArgumentException("new content already belongs to a parent");
        }

        UiWorkspacePane pane = createPane(newContent);
        split(target, pane, orientation, newPaneFirst);
        return pane;
    }

    /** 将已有但隐藏的叶子放到目标旁边；每个叶子最多出现在一个位置。 */
    public void split(UiWorkspacePane target, UiWorkspacePane pane, Orientation orientation, boolean newPaneFirst) {
        requireOwned(target);
        requireOwned(pane);
        if (!isDisplayed(target)) throw new IllegalStateException("target pane is not displayed");
        if (isDisplayed(pane)) throw new IllegalArgumentException("new pane is already displayed");
        Node root = getChildren().get(0);
        // SplitPane 的视觉父节点由 Skin 创建，未应用 CSS 时甚至还没有父节点。
        // 归属必须从 getItems() 构成的布局树查找，不能依赖 Node.getParent()。
        SplitPane parentSplit = findParentSplit(root, target);
        if (root != target && parentSplit == null) {
            throw new IllegalStateException("workspace pane is not attached to this workspace");
        }
        int parentIndex = parentSplit == null ? -1 : parentSplit.getItems().indexOf(target);
        double[] parentDividers = parentSplit == null ? null : parentSplit.getDividerPositions();
        UiSplit branch = new UiSplit();
        branch.setOrientation(orientation);
        branch.setMinSize(0, 0);
        branch.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        branch.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        branch.getStyleClass().addAll(
                "ui-workspace-split",
                orientation == Orientation.HORIZONTAL
                        ? "ui-workspace-horizontal-split"
                        : "ui-workspace-vertical-split"
        );
        if (parentSplit == null) {
            getChildren().clear();
        } else {
            parentSplit.getItems().remove(parentIndex);
        }
        if (newPaneFirst) {
            branch.getItems().addAll(pane, target);
        } else {
            branch.getItems().addAll(target, pane);
        }

        if (parentSplit == null) {
            getChildren().add(branch);
        } else {
            parentSplit.getItems().add(parentIndex, branch);
            parentSplit.setDividerPositions(parentDividers);
        }
        branch.setDividerPositions(0.5);
        updateCloseButtons();
    }

    boolean close(UiWorkspacePane pane) {
        if (pane.isClosed()) return false;
        requireOwned(pane);
        if (!pane.allowClose()) return false;
        if (isDisplayed(pane)) {
            Node root = getChildren().getFirst();
            SplitPane parent = findParentSplit(root, pane);
            if (parent == null) {
                getChildren().clear();
            } else {
                parent.getItems().remove(pane);
                Node sibling = parent.getItems().removeFirst();
                replaceNode(parent, sibling);
            }
        }
        panes.remove(pane);
        updateCloseButtons();
        pane.closed();
        return true;
    }

    private void updateCloseButtons() {
        for (UiWorkspacePane pane : panes) {
            pane.setCloseButtonVisible(false);
        }
        if (!getChildren().isEmpty() && getChildren().getFirst() instanceof SplitPane split) {
            showCloseButtons(split);
        }
    }

    private static void showCloseButtons(Node node) {
        if (node instanceof UiWorkspacePane pane) {
            pane.setCloseButtonVisible(true);
        } else if (node instanceof SplitPane split) {
            split.getItems().forEach(UiWorkspace::showCloseButtons);
        }
    }

    private void requireOwned(UiWorkspacePane pane) {
        if (pane == null || pane.workspace() != this || !panes.contains(pane)) {
            throw new IllegalArgumentException("pane does not belong to this workspace");
        }
    }

    private void replaceNode(Node target, Node replacement) {
        Node root = getChildren().getFirst();
        if (root == target) {
            getChildren().setAll(replacement);
        } else {
            SplitPane parent = findParentSplit(root, target);
            if (parent == null) throw new IllegalStateException("pane is not attached");
            double[] positions = parent.getDividerPositions();
            parent.getItems().set(parent.getItems().indexOf(target), replacement);
            parent.setDividerPositions(positions);
        }
    }

    private static UiWorkspacePane firstPane(Node node) {
        return node instanceof UiWorkspacePane pane ? pane
                : firstPane(((SplitPane) node).getItems().getFirst());
    }

    private static SplitPane findParentSplit(Node node, Node target) {
        if (node instanceof SplitPane split) {
            for (Node item : split.getItems()) {
                if (item == target) {
                    return split;
                }
                SplitPane parent = findParentSplit(item, target);
                if (parent != null) {
                    return parent;
                }
            }
        }
        return null;
    }
}
