package minic.uilocal;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import minic.uilocal.control.MiniCWorkbenchControlHub;
import minic.uilocal.text.MiniCExplanationTextHighlighter;
import minic.uilocal.text.MiniCTextFlowFactory;

import java.util.Objects;

/**
 * 右侧观测详情面板。
 */
public final class MiniCInspectorView extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCInspectorModelFactory modelFactory = new MiniCInspectorModelFactory();
    private final MiniCExplanationTextHighlighter explanationTextHighlighter = new MiniCExplanationTextHighlighter();
    private final TextFlow currentState = body("");
    private final TextFlow currentItem = body("");
    private final TextFlow accumulatedOutput = body("");

    /**
     * 创建 Inspector。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCInspectorView(MiniCWorkbenchViewModel viewModel) {
        this(viewModel, new MiniCWorkbenchControlHub());
    }

    /**
     * 创建 Inspector。
     *
     * @param viewModel UI 状态模型
     * @param controlHub 共享控制中心
     */
    public MiniCInspectorView(MiniCWorkbenchViewModel viewModel, MiniCWorkbenchControlHub controlHub) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(controlHub, "controlHub");
        getStyleClass().add("inspector-metadata");
        getChildren().addAll(
                label("MiniC 观测", "panel-title"),
                label("当前状态", "section-label"),
                currentState,
                label("当前项", "section-label"),
                currentItem,
                label("累计输出", "section-label"),
                accumulatedOutput
        );
        refresh();
        viewModel.sessionStartedProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStateProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStageDataProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.globalDataProperty().addListener((observable, oldValue, newValue) -> refresh());
    }

    /**
     * 刷新 Inspector 文本和控制按钮状态。
     */
    public void refresh() {
        MiniCInspectorModel model = modelFactory.create(
                viewModel.currentStateProperty().get(),
                viewModel.currentStageDataProperty().get(),
                viewModel.globalDataProperty().get()
        );
        setBody(currentState, model.currentState());
        setBody(currentItem, model.currentItem());
        setBody(accumulatedOutput, model.accumulatedOutput());
    }

    private TextFlow body(String text) {
        return MiniCTextFlowFactory.textFlow(
                explanationTextHighlighter.highlight(text),
                "body-text",
                false
        );
    }

    private void setBody(TextFlow target, String text) {
        TextFlow replacement = body(text);
        java.util.List<javafx.scene.Node> children = new java.util.ArrayList<>(replacement.getChildren());
        replacement.getChildren().clear();
        target.getChildren().setAll(children);
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
