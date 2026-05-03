package minic.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * Problems、Output、Terminal 风格底部面板。
 */
public final class MiniCBottomPanel extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCDiagnosticSelection diagnosticSelection;
    private final MiniCBottomPanelModelFactory modelFactory = new MiniCBottomPanelModelFactory();
    private final MiniCDiagnosticListFactory diagnosticListFactory = new MiniCDiagnosticListFactory();
    private final Label problems = new Label();
    private final Label output = new Label();
    private final Label terminal = new Label();

    /**
     * 创建底部面板。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCBottomPanel(MiniCWorkbenchViewModel viewModel) {
        this(viewModel, null);
    }

    /**
     * 创建底部面板。
     *
     * @param viewModel UI 状态模型
     * @param diagnosticSelection diagnostic 选择状态；可为 {@code null}
     */
    public MiniCBottomPanel(MiniCWorkbenchViewModel viewModel, MiniCDiagnosticSelection diagnosticSelection) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.diagnosticSelection = diagnosticSelection;
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
        List<MiniCDiagnosticItem> diagnosticItems = diagnosticListFactory.create(
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        problems.setText(diagnosticItems.isEmpty()
                ? String.join("\n", model.problems())
                : String.join("\n", diagnosticItems.stream().map(MiniCDiagnosticItem::displayText).toList()));
        if (diagnosticSelection != null && !diagnosticItems.isEmpty()) {
            diagnosticSelection.select(diagnosticItems.getFirst());
        }
        output.setText(String.join("\n", model.output()));
        terminal.setText(String.join("\n", model.terminal()));
    }
}
