package minic.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Problems、Output、Terminal 风格底部面板。
 */
public final class MiniCBottomPanel extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCBottomPanelModelFactory modelFactory = new MiniCBottomPanelModelFactory();
    private final Label problems = new Label();
    private final Label output = new Label();
    private final Label terminal = new Label();

    /**
     * 创建底部面板。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCBottomPanel(MiniCWorkbenchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        getStyleClass().add("bottom-panel");
        Label tabs = new Label("Problems    Output    Debug Console    Terminal");
        tabs.getStyleClass().add("bottom-tabs");
        HBox body = new HBox();
        body.getStyleClass().add("bottom-body");
        problems.getStyleClass().add("problem-list");
        output.getStyleClass().add("bottom-output");
        terminal.getStyleClass().add("terminal");
        body.getChildren().addAll(problems, output, terminal);
        HBox.setHgrow(problems, Priority.ALWAYS);
        HBox.setHgrow(output, Priority.ALWAYS);
        HBox.setHgrow(terminal, Priority.ALWAYS);
        getChildren().addAll(tabs, body);
        refresh();
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    /**
     * 刷新底部面板。
     */
    public void refresh() {
        MiniCBottomPanelModel model = modelFactory.create(
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        problems.setText(String.join("\n", model.problems()));
        output.setText(String.join("\n", model.output()));
        terminal.setText(String.join("\n", model.terminal()));
    }
}
