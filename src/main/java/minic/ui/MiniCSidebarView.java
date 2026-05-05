package minic.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * VS Code 风格侧边栏，包含 workspace 和 pipeline 阶段列表。
 */
public final class MiniCSidebarView extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCStageListFactory stageListFactory;
    private final VBox stageList = new VBox(5);

    /**
     * 创建侧边栏。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCSidebarView(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        stageListFactory = new MiniCStageListFactory();
        getStyleClass().add("sidebar");
        getChildren().add(stageList);
        stageList.getStyleClass().add("stage-list");
        refresh();
        viewModel.currentStateProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.selectedVisualStageProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    /**
     * 重新生成阶段列表。
     */
    public void refresh() {
        List<MiniCStageView> stages = stageListFactory.create(
                viewModel.currentStateProperty().get(),
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        stageList.getChildren().setAll(stages.stream().map(this::stageCard).toList());
    }

    private VBox stageCard(MiniCStageView stage) {
        VBox card = new VBox(4);
        card.getStyleClass().add("stage-card");
        card.getStyleClass().add(stage.state());
        if (stage.id().equals(viewModel.selectedVisualStageProperty().get())) {
            card.getStyleClass().add("selected");
        }
        if (stage.id().equals("source") || !stage.state().equals("queued")) {
            card.setOnMouseClicked(event -> viewModel.selectVisualStage(stage.id()));
        }
        Label top = label(stage.title() + "    " + stage.state(), "stage-top");
        Label meta = label(stage.progressPercent() + "% · " + stage.detail(), "stage-meta");
        card.getChildren().addAll(top, meta);
        return card;
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
