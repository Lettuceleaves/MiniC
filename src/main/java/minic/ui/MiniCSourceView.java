package minic.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import minic.uiapi.UiCurrentStateDto;
import minic.uiapi.UiSourceRangeDto;

import java.util.List;
import java.util.Objects;

/**
 * 带行号和当前范围高亮的源码视图。
 */
public final class MiniCSourceView extends VBox {
    private final MiniCWorkbenchViewModel viewModel;
    private final MiniCSourceLineFactory lineFactory = new MiniCSourceLineFactory();
    private final MiniCDiagnosticSelection diagnosticSelection;
    private final Label header = new Label("Source");
    private final GridPane lines = new GridPane();

    /**
     * 创建源码视图。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCSourceView(MiniCWorkbenchViewModel viewModel) {
        this(viewModel, null);
    }

    /**
     * 创建源码视图。
     *
     * @param viewModel UI 状态模型
     * @param diagnosticSelection diagnostic 选择状态；可为 {@code null}
     */
    public MiniCSourceView(MiniCWorkbenchViewModel viewModel, MiniCDiagnosticSelection diagnosticSelection) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.diagnosticSelection = diagnosticSelection;
        getStyleClass().add("pane");
        header.getStyleClass().add("pane-head");
        lines.getStyleClass().add("code-lines");
        getChildren().addAll(header, lines);
        VBox.setVgrow(lines, Priority.ALWAYS);
        refresh();
        viewModel.sourceTextProperty().addListener((observable, oldValue, newValue) -> refresh());
        viewModel.currentStateProperty().addListener((observable, oldValue, newValue) -> refresh());
        if (diagnosticSelection != null) {
            diagnosticSelection.selectedRangeProperty().addListener((observable, oldValue, newValue) -> refresh());
        }
    }

    /**
     * 刷新源码行和当前高亮。
     */
    public void refresh() {
        UiCurrentStateDto state = viewModel.currentStateProperty().get();
        UiSourceRangeDto selectedRange = diagnosticSelection == null ? null : diagnosticSelection.selectedRangeProperty().get();
        UiSourceRangeDto range = selectedRange != null ? selectedRange : state == null ? null : state.sourceRange();
        header.setText(headerText(range));
        List<MiniCSourceLine> sourceLines = lineFactory.create(viewModel.sourceTextProperty().get(), range);
        lines.getChildren().clear();
        for (int index = 0; index < sourceLines.size(); index++) {
            MiniCSourceLine line = sourceLines.get(index);
            Label number = new Label(Integer.toString(line.lineNumber()));
            Label text = new Label(line.text());
            number.getStyleClass().add("line-number");
            text.getStyleClass().add("line-text");
            if (line.focused()) {
                number.getStyleClass().add("focus");
                text.getStyleClass().add("focus");
            }
            lines.add(number, 0, index);
            lines.add(text, 1, index);
        }
    }

    private String headerText(UiSourceRangeDto range) {
        if (range == null) {
            return "Source";
        }
        return "Source · current range " + range.startOffset() + "-" + range.endOffset();
    }
}
