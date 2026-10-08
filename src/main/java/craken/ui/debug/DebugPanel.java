package craken.ui.debug;

import craken.debug.DebugVariable;
import craken.visualization.api.ViewLocation;
import craken.ui.component.action.UiButton;
import craken.ui.component.data.UiCollection;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.visualization.UiVisualizationContainer;
import craken.ui.component.visualization.VisualizationTheme;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 调试工作台：半屏展示，左半部分更宽用于可视化，右半部分是控件与栈/堆信息。
 *
 * <p>“开始”前左半部分是捕获变量输入与列表：输入时按 300ms 防抖检索 IR 中的同名变量定义，
 * 结果显示在下拉列表里，键盘（上下箭头 + 回车）或鼠标都能选中；列表项悬停显示删除。开始后左半部分
 * 显示可视化容器，右半部分顶部是九个操作控件，下方栈区占 80%、堆区占 20%（可拖动；堆区暂未实现）。</p>
 */
public final class DebugPanel extends BorderPane implements AutoCloseable {
    record CaptureItem(DebugVariable variable, String type, String where) {}
    private static final Duration CAPTURE_SEARCH_DEBOUNCE = Duration.millis(300);
    private static final Color ROW_TEXT = Color.web(VisualizationTheme.TEXT.hex());
    private static final Color ROW_MUTED = Color.web(VisualizationTheme.IDE_MUTED_TEXT.hex());
    private final SplitPane split = new SplitPane();
    private final StackPane canvasHost = new StackPane();
    private final VBox capturePane = new VBox(8);
    private final TextField captureInput = new TextField();
    private final ComboBox<DebugVariable> captureCandidates = new ComboBox<>();
    private final PauseTransition captureDebounce = new PauseTransition(CAPTURE_SEARCH_DEBOUNCE);
    private final UiCollection<CaptureItem> captureList = new UiCollection<>(FXCollections.observableArrayList());
    private final UiVisualizationContainer visualization = new UiVisualizationContainer();
    private final Label status = new Label();
    private final FlowPane controls = new FlowPane(8, 8);
    private final UiButton start = new UiButton("开始");
    private final UiButton restart = new UiButton("重启");
    private final UiButton runToEnd = new UiButton("到结束");
    private final UiButton next = new UiButton("下一步");
    private final UiButton previous = new UiButton("上一步");
    private final UiButton stepInto = new UiButton("步入函数");
    private final UiButton stepOut = new UiButton("回退至调用处");
    private final UiButton nextBreakpoint = new UiButton("到下一个断点");
    private final UiButton previousBreakpoint = new UiButton("返回上一个断点");
    private final SplitPane memorySplit = new SplitPane();
    private final UiCollection<DebugWorkbenchSession.MemoryRow> stack = new UiCollection<>();
    private final UiCollection<DebugWorkbenchSession.MemoryRow> heap = new UiCollection<>();
    private Function<String, List<DebugVariable>> search = term -> List.of();
    private Function<DebugVariable, String> typeText = variable -> String.valueOf(variable.declaredType());
    private List<DebugVariable> candidates = List.of();
    private String searchedTerm = "";
    private boolean captureSelectionProgrammatic;
    private boolean busy;
    private java.util.function.Consumer<ViewLocation> onEntryActivated;

    public DebugPanel() {
        setId("debug-panel");
        getStyleClass().add("pipeline-panel");
        setMinSize(0, 0);

        captureInput.setId("debug-capture-input");
        captureInput.setPromptText("输入变量名搜索，回车选择第一个候选");
        captureInput.textProperty().addListener((observable, old, text) -> scheduleCaptureSearch(text));
        captureInput.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> { commitCaptureCandidate(); event.consume(); }
                case DOWN -> { openCaptureCandidates(); moveCaptureSelection(1); event.consume(); }
                case UP -> { openCaptureCandidates(); moveCaptureSelection(-1); event.consume(); }
                case ESCAPE -> { hideCaptureCandidates(); event.consume(); }
                default -> { }
            }
        });
        captureDebounce.setOnFinished(event -> runCaptureSearch(captureInput.getText()));
        Label prompt = new Label("请输入需要捕获的变量名");
        prompt.getStyleClass().add("interaction-sidebar-title");
        captureCandidates.setId("debug-capture-candidates");
        captureCandidates.getStyleClass().addAll("ui-combo-box", "debug-candidate-list");
        captureCandidates.setPromptText("匹配到的变量定义");
        captureCandidates.setVisibleRowCount(6);
        captureCandidates.setMaxWidth(Double.MAX_VALUE);
        captureCandidates.setCellFactory(list -> new CandidateCell());
        captureCandidates.setButtonCell(new CandidateCell());
        captureCandidates.setOnAction(event -> {
            if (captureSelectionProgrammatic) return;
            if (captureCandidates.getValue() != null) chooseCapture(captureCandidates.getValue());
        });

        captureList.setId("debug-capture-list");
        captureList.getStyleClass().addAll("interaction-list", "debug-capture-list");
        captureList.setPlaceholder(mutedPlaceholder("尚未选择捕获变量"));
        captureList.setCellFactory(list -> new CaptureCell());
        capturePane.setId("debug-capture-pane");
        capturePane.setPadding(new Insets(12));
        capturePane.getChildren().addAll(prompt, captureInput, captureCandidates, captureList);
        VBox.setVgrow(captureList, Priority.ALWAYS);

        visualization.setId("debug-visualization");
        visualization.setMinSize(0, 0);
        visualization.setPathPickerVisible(false);
        visualization.setVisible(false);
        visualization.setManaged(false);
        canvasHost.setId("debug-canvas-host");
        canvasHost.setMinSize(0, 0);
        canvasHost.setBackground(canvasBackground());
        canvasHost.getChildren().addAll(capturePane, visualization);

        status.setId("debug-status");
        status.getStyleClass().add("pipeline-status");
        status.setText("选择要捕获的变量后点击开始。");
        status.setMinSize(0, 30);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setPadding(new Insets(0, 12, 0, 12));
        VBox canvasSide = new VBox(canvasHost, status);
        VBox.setVgrow(canvasHost, Priority.ALWAYS);
        canvasSide.setMinSize(0, 0);
        canvasSide.setBackground(canvasBackground());

        configure(start, "debug-start", "开始调试并写入捕获插桩");
        configure(restart, "debug-restart", "用当前缓冲区与捕获列表重新开始");
        configure(runToEnd, "debug-run-to-end", "运行到程序结束");
        configure(next, "debug-next", "下一步（不进入被调函数）");
        configure(previous, "debug-previous", "上一步");
        configure(stepInto, "debug-step-into", "步入函数");
        configure(stepOut, "debug-step-out", "回退至调用处");
        configure(nextBreakpoint, "debug-next-breakpoint", "到下一个断点");
        configure(previousBreakpoint, "debug-prev-breakpoint", "返回上一个断点");
        controls.setId("debug-controls");
        controls.setPadding(new Insets(10, 12, 6, 12));
        controls.getStyleClass().add("interaction-toolbar");
        controls.getChildren().addAll(start, restart, runToEnd, next, previous, stepInto, stepOut,
                nextBreakpoint, previousBreakpoint);

        stack.setId("debug-stack-list");
        stack.getStyleClass().addAll("interaction-list", "debug-memory-list");
        stack.setCellFactory(list -> new MemoryCell());
        stack.setPlaceholder(mutedPlaceholder("栈为空"));
        heap.setId("debug-heap-list");
        heap.getStyleClass().addAll("interaction-list", "debug-memory-list");
        heap.setCellFactory(list -> new MemoryCell());
        heap.setPlaceholder(mutedPlaceholder("堆区暂未实现"));
        memorySplit.setId("debug-memory-split");
        memorySplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        memorySplit.getStyleClass().addAll("app-split-pane", "app-vertical-split");
        memorySplit.getItems().addAll(stack, heap);
        memorySplit.setDividerPositions(0.8);
        VBox side = new VBox(controls, memorySplit);
        side.setId("debug-side");
        side.setMinSize(0, 0);
        side.setBackground(canvasBackground());
        VBox.setVgrow(memorySplit, Priority.ALWAYS);

        split.setId("debug-split");
        split.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        split.getStyleClass().addAll("app-split-pane", "app-horizontal-split");
        split.getItems().addAll(canvasSide, side);
        split.setDividerPositions(0.62);
        setCenter(split);
        showCapturePhase();
    }

    private static void configure(UiButton button, String id, String tooltip) {
        button.setId(id);
        button.getStyleClass().add("pipeline-step-button");
        button.setTooltip(new UiTooltip(tooltip));
    }

    private static Background canvasBackground() {
        return new Background(new BackgroundFill(Color.web(VisualizationTheme.CANVAS.hex()),
                CornerRadii.EMPTY, Insets.EMPTY));
    }

    private static Label mutedPlaceholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("interaction-sidebar-title");
        return label;
    }

    public void setOnStart(Runnable action) { start.setOnAction(event -> action.run()); }
    public void setOnRestart(Runnable action) { restart.setOnAction(event -> action.run()); }
    public void setOnNext(Runnable action) { next.setOnAction(event -> action.run()); }
    public void setOnPrevious(Runnable action) { previous.setOnAction(event -> action.run()); }
    public void setOnStepInto(Runnable action) { stepInto.setOnAction(event -> action.run()); }
    public void setOnStepOut(Runnable action) { stepOut.setOnAction(event -> action.run()); }
    public void setOnRunToEnd(Runnable action) { runToEnd.setOnAction(event -> action.run()); }
    public void setOnNextBreakpoint(Runnable action) { nextBreakpoint.setOnAction(event -> action.run()); }
    public void setOnPreviousBreakpoint(Runnable action) { previousBreakpoint.setOnAction(event -> action.run()); }
    public void setCaptureSearch(Function<String, List<DebugVariable>> search) { this.search = Objects.requireNonNull(search); }
    public void setCaptureTypeText(Function<DebugVariable, String> typeText) { this.typeText = Objects.requireNonNull(typeText); }
    /** 已确认的捕获变量；“开始/重启”用它生成插桩计划。 */
    public List<DebugVariable> selectedVariables() {
        return captureList.getItems().stream().map(CaptureItem::variable).toList();
    }

    /** 重启回到捕获阶段：用新一份 IR 里等价的定义重建列表（同名函数同起始行）。 */
    public void replaceSelection(List<DebugVariable> variables) {
        captureList.getItems().clear();
        variables.forEach(this::addCapture);
    }

    public UiVisualizationContainer visualizationHost() { return visualization; }

    /** 回到“开始”之前的捕获阶段，保留已确认的捕获列表。 */
    public void showCapturePhase() {
        captureDebounce.stop();
        captureInput.clear();
        clearCaptureCandidates();
        capturePane.setVisible(true);
        capturePane.setManaged(true);
        visualization.setVisible(false);
        visualization.setManaged(false);
        start.setDisable(busy);
        restart.setDisable(true);
        next.setDisable(true);
        previous.setDisable(true);
        stepInto.setDisable(true);
        stepOut.setDisable(true);
        runToEnd.setDisable(true);
        nextBreakpoint.setDisable(true);
        previousBreakpoint.setDisable(true);
    }

    /** 显示一个已发布快照：切到可视化，刷新栈区与控件可用状态。 */
    public void show(DebugWorkbenchSession.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        busy = false;
        capturePane.setVisible(false);
        capturePane.setManaged(false);
        visualization.setVisible(true);
        visualization.setManaged(true);
        if (snapshot.visualization() != null) visualization.showSnapshot(snapshot.visualization());
        stack.getItems().setAll(snapshot.stack());
        StringBuilder text = new StringBuilder("历史 #").append(snapshot.contextIndex())
                .append(" · ").append(snapshot.status()).append(" · ").append(snapshot.trap());
        if (snapshot.line() > 0) text.append(" · 行 ").append(snapshot.line());
        if (!snapshot.function().isBlank()) text.append(" · ").append(snapshot.function());
        text.append(" · 栈深 ").append(snapshot.stackDepth());
        if (!snapshot.diagnostic().isBlank()) text.append(" · ").append(snapshot.diagnostic());
        status.setText(text.toString());
        start.setDisable(true);
        restart.setDisable(busy);
        next.setDisable(busy || !snapshot.canNext());
        previous.setDisable(busy || !snapshot.canPrevious());
        stepInto.setDisable(busy || !snapshot.canNext());
        stepOut.setDisable(busy || !snapshot.canStepOut());
        runToEnd.setDisable(busy || !snapshot.canNext());
        nextBreakpoint.setDisable(busy || !snapshot.canNextBreakpoint());
        previousBreakpoint.setDisable(busy || !snapshot.canPreviousBreakpoint());
    }

    public void setBusy(boolean value) {
        busy = value;
        if (value) {
            start.setDisable(true);
            restart.setDisable(true);
            controls.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .forEach(button -> button.setDisable(true));
            status.setText("正在调试…");
        } else {
            // 捕获阶段“开始”可用；已经发布过快照时由 show(snapshot) 管理控件状态。
            start.setDisable(snapshotStarted());
        }
    }

    public void failed(String message) {
        busy = false;
        status.setText("调试失败：" + message);
        status.setTooltip(new UiTooltip(message));
        if (!snapshotStarted()) showCapturePhase();
        else { restart.setDisable(false); }
    }

    private boolean snapshotStarted() { return visualization.isVisible(); }

    /** 输入即搜：每次键入重置 300ms 防抖，停止输入后才真正检索。 */
    private void scheduleCaptureSearch(String term) {
        captureDebounce.stop();
        if (term == null || term.isBlank()) {
            clearCaptureCandidates();
            return;
        }
        captureDebounce.playFromStart();
    }

    private void runCaptureSearch(String term) {
        String name = Objects.requireNonNullElse(term, "").trim();
        searchedTerm = name;
        if (name.isEmpty()) {
            clearCaptureCandidates();
            return;
        }
        candidates = List.copyOf(search.apply(name));
        captureCandidates.getItems().setAll(candidates);
        if (candidates.isEmpty()) {
            captureCandidates.setPromptText("未找到匹配的变量定义");
            captureCandidates.hide();
            return;
        }
        captureCandidates.setPromptText("匹配到的变量定义");
        // 输入框仍有焦点时把候选下拉展开，键鼠都可以直接选。
        if (captureInput.isFocused() || captureCandidates.isFocused()) showCaptureCandidates();
    }

    /** 方向键打开候选：防抖还没结束就立即补一次检索。 */
    private void openCaptureCandidates() {
        if (candidates.isEmpty()) {
            captureDebounce.stop();
            runCaptureSearch(captureInput.getText());
        }
        if (!candidates.isEmpty()) showCaptureCandidates();
    }

    /** 无窗口宿主（测试/内嵌场景）不弹浮层，避免 PopupWindow 缺少 owner。 */
    private void showCaptureCandidates() {
        var scene = captureCandidates.getScene();
        if (scene == null || scene.getWindow() == null) return;
        captureCandidates.show();
    }

    private void moveCaptureSelection(int delta) {
        if (candidates.isEmpty()) return;
        var selection = captureCandidates.getSelectionModel();
        int current = selection.getSelectedIndex();
        int next = current < 0 ? (delta > 0 ? 0 : candidates.size() - 1)
                : Math.floorMod(current + delta, candidates.size());
        // ComboBox.select 会同步触发 onAction，必须与"用户真正选中"区分开。
        captureSelectionProgrammatic = true;
        try { selection.select(next); }
        finally { captureSelectionProgrammatic = false; }
    }

    /** 回车：先补齐检索，再选中下拉里高亮的候选（默认第一个）。 */
    private void commitCaptureCandidate() {
        String term = Objects.requireNonNullElse(captureInput.getText(), "").trim();
        if (candidates.isEmpty() || !term.equals(searchedTerm)) {
            captureDebounce.stop();
            runCaptureSearch(term);
        }
        if (candidates.isEmpty()) return;
        int selected = captureCandidates.getSelectionModel().getSelectedIndex();
        chooseCapture(candidates.get(selected >= 0 && selected < candidates.size() ? selected : 0));
    }

    private void chooseCapture(DebugVariable variable) {
        addCapture(variable);
        // 选择提交可能仍在 ComboBox/ListView 的事件链里：同步清空 items 或选中会让 JavaFX 在处理
        // 选中索引变更时读到已清空的列表（IndexOutOfBoundsException）。让本次事件走完，下一帧再收起。
        Platform.runLater(() -> {
            captureDebounce.stop();
            captureInput.clear();
            clearCaptureCandidates();
            captureInput.requestFocus();
        });
    }

    private void hideCaptureCandidates() {
        captureCandidates.hide();
        captureCandidates.getSelectionModel().clearSelection();
    }

    private void clearCaptureCandidates() {
        candidates = List.of();
        searchedTerm = "";
        captureCandidates.hide();
        captureCandidates.getSelectionModel().clearSelection();
        captureCandidates.getItems().clear();
        captureCandidates.setPromptText("匹配到的变量定义");
    }

    private static String candidateText(DebugVariable variable) {
        String where = variable.function().isBlank() ? "全局" : variable.function();
        return variable.sourceName() + "（" + where + "，行 " + variable.definition().startLine() + "）";
    }

    private static final class CandidateCell extends ListCell<DebugVariable> {
        @Override
        protected void updateItem(DebugVariable variable, boolean empty) {
            super.updateItem(variable, empty);
            setText(empty || variable == null ? null : candidateText(variable));
        }
    }

    private void addCapture(DebugVariable variable) {
        boolean present = captureList.getItems().stream()
                .anyMatch(item -> item.variable().definition().equals(variable.definition()));
        if (present) return;
        String where = variable.function().isBlank() ? "全局" : variable.function();
        captureList.getItems().add(new CaptureItem(variable, typeText.apply(variable), where));
    }

    private final class CaptureCell extends ListCell<CaptureItem> {
        private final Label name = new Label();
        private final Label type = new Label();
        private final Label where = new Label();
        private final Button delete = new Button("×");
        private final HBox box = new HBox(8, name, type, where, new Region(), delete);

        CaptureCell() {
            // 自定义单元格中的 Label 不继承列表字色，必须显式使用主题色，否则黑字黑底。
            name.setTextFill(ROW_TEXT);
            type.setTextFill(ROW_MUTED);
            where.setTextFill(ROW_MUTED);
            type.getStyleClass().add("debug-capture-meta");
            where.getStyleClass().add("debug-capture-meta");
            delete.getStyleClass().add("debug-capture-delete");
            delete.setId("debug-capture-delete");
            delete.setVisible(false);
            delete.setManaged(false);
            HBox.setHgrow(box.getChildren().get(3), Priority.ALWAYS);
            setOnMouseEntered(event -> { delete.setVisible(true); delete.setManaged(true); });
            setOnMouseExited(event -> { delete.setVisible(false); delete.setManaged(false); });
            delete.setOnAction(event -> {
                CaptureItem item = getItem();
                if (item != null) captureList.getItems().remove(item);
                event.consume();
            });
        }

        @Override protected void updateItem(CaptureItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            name.setText(item.variable().sourceName());
            type.setText(item.type());
            where.setText(item.where());
            setGraphic(box);
        }
    }

    private static final class MemoryCell extends ListCell<DebugWorkbenchSession.MemoryRow> {
        private final Label name = new Label();
        private final Label value = new Label();
        private final HBox box = new HBox(8, name, new Region(), value);

        MemoryCell() {
            name.setTextFill(ROW_TEXT);
            value.setTextFill(ROW_TEXT);
            HBox.setHgrow(box.getChildren().get(1), Priority.ALWAYS);
            value.getStyleClass().add("debug-memory-value");
        }

        @Override protected void updateItem(DebugWorkbenchSession.MemoryRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            name.setText(item.name());
            value.setText(item.value());
            setGraphic(box);
        }
    }

    /** 悬停勾子：测试与非鼠标环境下也能验证删除按钮的显隐。 */
    void hoverCaptureCell(int index, boolean entered) {
        captureList.applyCss();
        captureList.layout();
        for (var node : captureList.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.getIndex() == index) {
                cell.fireEvent(new MouseEvent(entered ? MouseEvent.MOUSE_ENTERED : MouseEvent.MOUSE_EXITED,
                        0, 0, 0, 0, javafx.scene.input.MouseButton.NONE, 0, false, false, false, false, false, false, false,
                        false, false, false, null));
                return;
            }
        }
    }

    @Override public void close() {
        captureDebounce.stop();
        captureCandidates.hide();
        visualization.close();
    }
}
