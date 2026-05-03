package minic.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * 当前阶段结构化可视化区域。
 */
public final class MiniCVisualPane extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCVisualModelFactory modelFactory = new MiniCVisualModelFactory();
    private final Label header = new Label("Graph View");
    private final FlowPane canvas = new FlowPane(10, 10);

    /**
     * 创建 Visual Pane。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCVisualPane(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        getStyleClass().add("pane");
        header.getStyleClass().add("pane-head");
        canvas.getStyleClass().add("visual-canvas");
        getChildren().addAll(header, canvas);
        VBox.setVgrow(canvas, Priority.ALWAYS);
        refresh();
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    /**
     * 刷新可视化内容。
     */
    public void refresh() {
        String stage = viewModel.currentStageDataProperty().get() == null
                ? "pending"
                : viewModel.currentStageDataProperty().get().stage();
        header.setText("Graph View · " + stage);
        List<MiniCVisualItem> items = modelFactory.create(
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        canvas.getChildren().setAll(items.stream().map(this::node).toList());
    }

    private Label node(MiniCVisualItem item) {
        Label label = new Label(item.label());
        label.getStyleClass().add("visual-node");
        if (item.hot()) {
            label.getStyleClass().add("hot");
        }
        return label;
    }
}
