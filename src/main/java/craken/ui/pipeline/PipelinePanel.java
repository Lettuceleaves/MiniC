package craken.ui.pipeline;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import craken.ui.component.UiStyles;
import craken.ui.component.action.UiButton;
import craken.ui.component.data.UiCollection;
import craken.ui.component.display.UiIcon;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.visualization.UiVisualizationContainer;
import craken.visualization.adapter.pipeline.PipelineVisualizationFrame;
import java.util.function.IntConsumer;

/** 编译展示台：左右显示同一步的输入输出快照，右侧承载执行控件与阶段列表。 */
public final class PipelinePanel extends BorderPane implements AutoCloseable {
    private static final double SIDEBAR_WIDTH = 356;
    private static final double MIN_STAGE_HEIGHT = 56;
    private static final String[] INPUT_TYPES = {"源码", "预处理结果", "Token", "源码 AST", "AST", "IR", "汇编与编码", "目标文件"};
    private static final String[] OUTPUT_TYPES = {"预处理结果", "Token", "AST", "语义 AST", "IR", "汇编", "COFF 目标文件", "PE 与链接产物"};
    private final UiButton restart = new UiButton("重置");
    private final UiButton nextStep = new UiButton("下一步");
    private final UiButton nextStage = new UiButton("下一阶段");
    private final UiCollection<PipelineSession.StageView> stages = new UiCollection<>();
    private final Label source = new Label("编译阶段");
    private final Label completed = new Label();
    private final Label status = new Label();
    private final Label frameTitle = new Label();
    private final Label frameDetail = new Label();
    private final Label inputTitle = new Label("输入");
    private final Label outputTitle = new Label("输出");
    private final UiButton returnCurrent = new UiButton("返回当前阶段");
    private final TextArea diagnosticsText = new TextArea();
    private final TitledPane diagnostics = new TitledPane("诊断详情", diagnosticsText);
    private PipelineSession.Snapshot snapshot;
    private int selectedStage = -1;
    private boolean updating;
    private boolean busy;
    private final UiVisualizationContainer inputView;
    private final UiVisualizationContainer outputView;
    private PipelineVisualizationFrame visualization;
    private IntConsumer selectStage;

    public PipelinePanel() {
        setId("pipeline-panel");
        setMinSize(0, 0);
        getStyleClass().add("pipeline-panel");
        inputView = new UiVisualizationContainer();
        outputView = new UiVisualizationContainer();
        SplitPane views = new SplitPane(pane(inputTitle, "pipeline-input", inputView), pane(outputTitle, "pipeline-output", outputView));
        views.setId("pipeline-io-split");
        views.setOrientation(Orientation.HORIZONTAL);
        views.setMinSize(0, 0);
        views.getStyleClass().addAll("app-split-pane", "app-horizontal-split");
        views.setDividerPositions(0.5);
        setCenter(views);
        setTop(createFrameBar());
        setRight(createSidebar());
        clear();
    }

    public void setOnNextStep(Runnable action) {
        nextStep.setOnAction(event -> action.run());
    }

    public void setOnRestart(Runnable action) {
        restart.setOnAction(event -> action.run());
    }

    public void setOnNextStage(Runnable action) {
        nextStage.setOnAction(event -> action.run());
    }

    public void setOnSelectStage(IntConsumer action) { selectStage = action; }

    public PipelineVisualizationFrame visualization() { return visualization; }

    public void clear() {
        snapshot = null;
        visualization = null;
        frameTitle.setText("等待编译");
        frameDetail.setText("打开源文件后，从右侧编译流水线开始。");
        frameTitle.setTooltip(null); frameDetail.setTooltip(null);
        inputTitle.setText("输入"); outputTitle.setText("输出");
        returnCurrent.setVisible(false); returnCurrent.setManaged(false);
        showDiagnostics("");
        inputView.refresh();
        outputView.refresh();
        selectedStage = -1;
        source.setText("编译阶段");
        source.setTooltip(null);
        updating = true;
        try {
            stages.getItems().setAll(
                    java.util.stream.IntStream.range(0, PipelineSession.stageLabels().size())
                            .mapToObj(index -> new PipelineSession.StageView(index,
                                    PipelineSession.stageLabels().get(index), PipelineSession.Status.PENDING, false, ""))
                            .toList());
            stages.getSelectionModel().clearSelection();
        } finally {
            updating = false;
        }
        status.setText("打开源文件后，点击编译 Pipeline 开始。");
        completed.setText("0 / " + stages.getItems().size() + " 已完成");
        status.setTooltip(null);
        status.pseudoClassStateChanged(PseudoClass.getPseudoClass("failed"), false);
        setBusy(false);
    }

    public void preparing(String fileName) {
        clear();
        source.setText(fileName);
        source.setTooltip(new UiTooltip(fileName));
        frameDetail.setText("正在准备 " + fileName);
        status.setText("正在准备编译…");
        setBusy(true);
    }

    public void show(PipelineSession.Snapshot current) {
        snapshot = current;
        if (current.visualization() != null && visualization != current.visualization()) {
            inputView.showSnapshot(current.visualization().input());
            outputView.showSnapshot(current.visualization().output());
            visualization = current.visualization();
        }
        updateFramePresentation(current);
        long finished = current.stages().stream()
                .filter(stage -> stage.status() == PipelineSession.Status.COMPLETED).count();
        completed.setText(finished + " / " + current.stages().size() + " 已完成");
        selectedStage = current.selectedStageIndex();
        updating = true;
        try {
            stages.getItems().setAll(current.stages());
            stages.getSelectionModel().select(selectedStage);
            if (selectedStage >= 0) stages.scrollTo(selectedStage);
        } finally {
            updating = false;
        }
        setBusy(false);
        if (!current.visualizationError().isBlank()) {
            diagnostics.setText("展示诊断 · 待重试");
            status.setText("展示失败：" + current.visualizationError() + " · 点击下一步重试");
            status.setTooltip(new UiTooltip(status.getText()));
            status.pseudoClassStateChanged(PseudoClass.getPseudoClass("failed"), true);
        } else if (current.failed()) {
            String error = current.stages().stream().filter(stage -> !stage.error().isBlank())
                    .map(PipelineSession.StageView::error).findFirst().orElse("编译失败");
            failed(error);
        } else {
            updateStatus();
        }
    }

    public void setBusy(boolean value) {
        busy = value;
        boolean disabled = value || snapshot == null || !snapshot.canAdvance();
        nextStep.setDisable(disabled);
        nextStage.setDisable(disabled);
        stages.setDisable(value);
        returnCurrent.setDisable(value);
        if (value) status.setText("正在编译…");
    }

    public void failed(String message) {
        busy = false;
        nextStep.setDisable(true);
        nextStage.setDisable(true);
        stages.setDisable(false);
        returnCurrent.setDisable(false);
        showDiagnostics(message);
        String failedStage = snapshot == null ? "" : snapshot.stages().stream()
                .filter(stage -> stage.status() == PipelineSession.Status.FAILED)
                .map(PipelineSession.StageView::label).findFirst().orElse("");
        diagnostics.setText(failedStage.isBlank() ? "编译诊断" : "诊断详情 · " + failedStage);
        status.setText("编译失败：" + message);
        status.setTooltip(new UiTooltip(status.getText()));
        status.pseudoClassStateChanged(PseudoClass.getPseudoClass("failed"), true);
    }

    private void updateStatus() {
        if (snapshot == null || busy || snapshot.failed() || !snapshot.visualizationError().isBlank()) return;
        status.setTooltip(null);
        status.pseudoClassStateChanged(PseudoClass.getPseudoClass("failed"), false);
        if (snapshot.succeeded() && selectedStage == snapshot.stages().size() - 1) {
            status.setText("编译完成 · " + snapshot.stepCount() + " 步");
        } else if (selectedStage >= 0 && selectedStage != snapshot.currentStageIndex()) {
            status.setText("查看已完成阶段 · " + snapshot.stages().get(selectedStage).label());
        } else {
            status.setText("已执行 " + snapshot.stepCount() + " 步");
        }
    }

    /** Describe the published frame, which can still be the previous stage's terminal at a stage boundary. */
    private void updateFramePresentation(PipelineSession.Snapshot current) {
        int currentIndex = Math.min(current.currentStageIndex(), current.stages().size() - 1);
        boolean history = current.selectedStageIndex() >= 0 && current.selectedStageIndex() != currentIndex;
        returnCurrent.setVisible(history); returnCurrent.setManaged(history);
        var frame = current.visualization();
        if (frame == null || frame.stageIndex() < 0) {
            frameTitle.setText("准备就绪");
            frameDetail.setText("点击下一步开始预处理");
            inputTitle.setText("输入 · 源码"); outputTitle.setText("输出 · 等待预处理");
        } else {
            String name = frame.stageIndex() < current.stages().size()
                    ? current.stages().get(frame.stageIndex()).label() : frame.stageName();
            frameTitle.setText((history ? "回看 · " : "当前画面 · ") + name + " · 第 " + (frame.stepIndex() + 1) + " 步"
                    + (frame.lastStep() ? frame.succeeded() ? " · 阶段完成" : " · 阶段结束" : ""));
            String detail = frame.operation().isBlank() ? "阶段结束" : "操作：" + frame.operation();
            if (frame.sourceRange() != null) {
                var range = frame.sourceRange();
                detail += " · 源码第 " + range.startLine()
                        + (range.endLine() == range.startLine() ? "" : "–" + range.endLine()) + " 行";
            }
            if (!history && current.selectedStageIndex() != frame.stageIndex() && currentIndex >= 0)
                detail += " · " + current.stages().get(currentIndex).label() + "尚未开始";
            frameDetail.setText(detail);
            inputTitle.setText("输入 · " + (frame.stageIndex() < INPUT_TYPES.length ? INPUT_TYPES[frame.stageIndex()] : "阶段输入"));
            outputTitle.setText("输出 · " + (frame.stageIndex() < OUTPUT_TYPES.length ? OUTPUT_TYPES[frame.stageIndex()] : "阶段输出"));
        }
        if (current.visualizationPending()) frameDetail.setText("展示尚未更新，保留上次画面 · 点击下一步重试");
        frameTitle.setTooltip(new UiTooltip(frameTitle.getText()));
        frameDetail.setTooltip(new UiTooltip(frameDetail.getText()));
        String error = current.visualizationError();
        if (current.selectedStageIndex() >= 0 && current.selectedStageIndex() < current.stages().size()) {
            String stageError = current.stages().get(current.selectedStageIndex()).error();
            if (!stageError.isBlank()) error = error.isBlank() ? stageError : error + "\n\n" + stageError;
        }
        showDiagnostics(error);
    }

    private void showDiagnostics(String message) {
        diagnostics.setText("诊断详情");
        boolean present = message != null && !message.isBlank();
        if (present && !message.equals(diagnosticsText.getText())) diagnostics.setExpanded(true);
        diagnosticsText.setText(present ? message : "");
        diagnostics.setVisible(present); diagnostics.setManaged(present);
    }

    private HBox createFrameBar() {
        frameTitle.setId("pipeline-frame-title"); frameDetail.setId("pipeline-frame-detail");
        frameTitle.getStyleClass().add("pipeline-frame-title"); frameDetail.getStyleClass().add("pipeline-frame-detail");
        for (var label : new Label[]{frameTitle, frameDetail}) { label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE); }
        var text = new VBox(4, frameTitle, frameDetail); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
        returnCurrent.setId("pipeline-return-current"); returnCurrent.setMinWidth(Region.USE_PREF_SIZE);
        returnCurrent.setTooltip(new UiTooltip("返回当前阶段", "返回编译进度所在阶段，不执行新的编译步骤。", ""));
        returnCurrent.setOnAction(event -> {
            if (!busy && snapshot != null) stages.getSelectionModel().select(Math.min(snapshot.currentStageIndex(), snapshot.stages().size() - 1));
        });
        var bar = new HBox(12, text, returnCurrent); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 12, 10, 12)); bar.getStyleClass().add("pipeline-frame-bar");
        return bar;
    }

    private BorderPane createSidebar() {
        restart.setId("pipeline-restart");
        nextStep.setId("pipeline-next-step");
        nextStage.setId("pipeline-next-stage");
        restart.getStyleClass().add("pipeline-step-button");
        nextStep.getStyleClass().add("pipeline-step-button");
        nextStage.getStyleClass().add("pipeline-stage-button");
        restart.setGraphic(new UiIcon(UiIcon.Kind.RESET, 16));
        nextStep.setGraphic(new UiIcon(UiIcon.Kind.STEP_FORWARD, 16));
        nextStage.setGraphic(new UiIcon(UiIcon.Kind.NEXT_STAGE, 16));
        restart.setGraphicTextGap(8);
        nextStep.setGraphicTextGap(8);
        nextStage.setGraphicTextGap(8);
        restart.setTooltip(new UiTooltip("重置", "重新读取当前编辑缓冲区，清空展示状态并从预处理重新开始。", ""));
        nextStep.setTooltip(new UiTooltip("下一步", "执行当前编译阶段的一步。", ""));
        nextStage.setTooltip(new UiTooltip("下一阶段", "完成当前阶段，停在下一阶段入口。", ""));
        restart.setMaxWidth(Double.MAX_VALUE);
        nextStep.setMaxWidth(Double.MAX_VALUE);
        nextStage.setMaxWidth(Double.MAX_VALUE);
        restart.setPrefWidth(0);
        nextStep.setPrefWidth(0);
        nextStage.setPrefWidth(0);
        HBox.setHgrow(restart, Priority.ALWAYS);
        HBox.setHgrow(nextStep, Priority.ALWAYS);
        HBox.setHgrow(nextStage, Priority.ALWAYS);
        HBox actions = new HBox(8, restart, nextStep, nextStage);
        actions.setPadding(new Insets(10, 12, 10, 12));
        actions.getStyleClass().add("interaction-toolbar");
        source.setTextOverrun(OverrunStyle.ELLIPSIS);
        source.setMaxWidth(Double.MAX_VALUE);
        source.setMinWidth(0);
        source.getStyleClass().add("interaction-sidebar-title");
        HBox.setHgrow(source, Priority.ALWAYS);
        completed.setMinWidth(Region.USE_PREF_SIZE);
        completed.setId("pipeline-completed-count");
        completed.getStyleClass().add("pipeline-completed-count");
        HBox sourceBar = new HBox(8, new UiIcon(UiIcon.Kind.CODE, 14), source, completed);
        sourceBar.setAlignment(Pos.CENTER_LEFT);
        sourceBar.setPadding(new Insets(0, 14, 0, 14));
        sourceBar.setMinHeight(38);
        sourceBar.setPrefHeight(38);
        sourceBar.getStyleClass().add("pipeline-source-bar");

        Font font = UiStyles.listFont();
        stages.setId("pipeline-stage-list");
        stages.getStyleClass().addAll("interaction-list", "pipeline-stage-list");
        // 八个阶段均分列表的实际内高；矮窗口保持可读行高并由 ListView 原生滚动。
        stages.fixedCellSizeProperty().bind(Bindings.createDoubleBinding(() -> {
            // 为不同 DPI 下 VirtualFlow 的视口取整留 1px，避免刚好铺满时误显示滚动条。
            double available = stages.getHeight() - stages.getInsets().getTop() - stages.getInsets().getBottom() - 1;
            return Math.max(MIN_STAGE_HEIGHT, available / Math.max(1, stages.getItems().size()));
        }, stages.heightProperty(), stages.insetsProperty(), Bindings.size(stages.getItems())));
        stages.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        stages.setCellFactory(view -> new StageCell(font, stages));
        stages.getSelectionModel().selectedIndexProperty().addListener((observable, old, index) -> {
            if (updating) return;
            int requested = index.intValue();
            if (busy || requested < 0 || requested >= stages.getItems().size()
                    || !stages.getItems().get(requested).selectable()) {
                updating = true;
                try {
                    stages.getSelectionModel().select(selectedStage);
                } finally {
                    updating = false;
                }
                return;
            }
            selectedStage = requested;
            if (selectStage != null) selectStage.accept(requested);
            updateStatus();
        });

        status.setId("pipeline-status");
        status.setWrapText(false);
        status.setTextOverrun(OverrunStyle.ELLIPSIS);
        status.setMinSize(0, 32);
        status.setPrefHeight(32);
        status.setMaxHeight(32);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setPadding(new Insets(0, 14, 0, 14));
        status.getStyleClass().add("pipeline-status");
        BorderPane sidebar = new BorderPane(stages);
        sidebar.setId("pipeline-sidebar");
        sidebar.setTop(new VBox(actions, sourceBar));
        diagnostics.setId("pipeline-diagnostics"); diagnostics.setAnimated(false);
        diagnosticsText.setId("pipeline-diagnostics-text"); diagnosticsText.setEditable(false); diagnosticsText.setWrapText(true);
        diagnosticsText.setPrefRowCount(5); diagnosticsText.setPrefHeight(140); diagnosticsText.setMinHeight(60);
        diagnosticsText.setAccessibleText("编译诊断详情，可选择复制");
        sidebar.setBottom(new VBox(diagnostics, status));
        sidebar.setMinSize(0, 0);
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMaxWidth(SIDEBAR_WIDTH);
        sidebar.getStyleClass().add("interaction-sidebar");
        return sidebar;
    }

    private static BorderPane pane(Label heading, String id, UiVisualizationContainer view) {
        heading.setId(id + "-title");
        heading.getStyleClass().add("pipeline-pane-title");
        HBox bar = new HBox(heading);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 12, 0, 12));
        bar.setMinHeight(36);
        bar.setPrefHeight(36);
        bar.getStyleClass().add("interaction-toolbar");
        view.setMinSize(0, 0);
        BorderPane pane = new BorderPane(view);
        pane.setTop(bar);
        pane.setId(id);
        pane.setMinSize(0, 0);
        pane.getStyleClass().add("pipeline-io-pane");
        return pane;
    }

    @Override public void close() {
        Throwable failure = null;
        try { inputView.close(); } catch (RuntimeException | Error error) { failure = error; }
        try { outputView.close(); } catch (RuntimeException | Error error) {
            if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error);
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    private static final class StageCell extends ListCell<PipelineSession.StageView> {
        private final StageRow row;

        StageCell(Font font, UiCollection<PipelineSession.StageView> list) {
            row = new StageRow(font);
            setFont(font);
            setAlignment(Pos.CENTER_LEFT);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setMinWidth(0);
            setPrefWidth(0);
            row.prefWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, getWidth() - getInsets().getLeft() - getInsets().getRight()),
                    widthProperty(), insetsProperty()));
            row.prefHeightProperty().bind(list.fixedCellSizeProperty());
        }

        @Override
        protected void updateItem(PipelineSession.StageView stage, boolean empty) {
            super.updateItem(stage, empty);
            boolean absent = empty || stage == null;
            setText(null);
            setGraphic(absent ? null : row);
            setDisable(!absent && !stage.selectable());
            for (PipelineSession.Status value : PipelineSession.Status.values()) {
                pseudoClassStateChanged(PseudoClass.getPseudoClass(value.name().toLowerCase(java.util.Locale.ROOT)),
                        !absent && stage.status() == value);
            }
            if (absent) {
                setTooltip(null);
                setAccessibleText(null);
                return;
            }
            String description = switch (stage.status()) {
                case PENDING -> "待开始";
                case CURRENT -> "当前";
                case COMPLETED -> "已完成";
                case FAILED -> "失败";
            };
            row.show(stage, description, getListView().getItems().size());
            setAccessibleText(stage.label() + "，" + description);
            setTooltip(new UiTooltip(stage.label(), description +
                    (stage.error().isBlank() ? "" : "\n" + stage.error()), ""));
        }
    }

    /** 连接线在每行中心的图标上下各留 17px，相邻行在边界处连续相接。 */
    private static final class StageRow extends Region {
        private static final UiIcon.Kind[] ICONS = {
                UiIcon.Kind.PREPROCESS, UiIcon.Kind.LEXER, UiIcon.Kind.PARSER, UiIcon.Kind.SEMANTIC,
                UiIcon.Kind.IR, UiIcon.Kind.ASSEMBLY, UiIcon.Kind.OBJECT_FILE, UiIcon.Kind.LINK
        };
        private static final String[] DESCRIPTIONS = {
                "展开宏与头文件", "识别关键字与符号", "构建抽象语法树", "校验类型与作用域",
                "降低为中间表示", "生成目标指令", "编码并写入 COFF", "解析符号与重定位"
        };
        private final Line before = new Line();
        private final Line after = new Line();
        private final StackPane icon = new StackPane();
        private final Label title = new Label();
        private final Label detail = new Label();
        private final Label state = new Label();
        private final HBox stateBox = new HBox(4);
        private final HBox body;
        private int iconIndex = -1;

        StageRow(Font font) {
            setMinSize(0, 0);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            getStyleClass().add("pipeline-stage-row");
            before.setManaged(false);
            after.setManaged(false);
            before.setStrokeLineCap(StrokeLineCap.BUTT);
            after.setStrokeLineCap(StrokeLineCap.BUTT);
            before.getStyleClass().add("pipeline-rail-before");
            after.getStyleClass().add("pipeline-rail-after");
            before.setMouseTransparent(true);
            after.setMouseTransparent(true);
            icon.setMinSize(32, 32);
            icon.setPrefSize(32, 32);
            icon.setMaxSize(32, 32);
            icon.getStyleClass().add("pipeline-stage-icon");
            title.setFont(font);
            title.setMinWidth(0);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setTextOverrun(OverrunStyle.ELLIPSIS);
            title.getStyleClass().add("pipeline-stage-title");
            detail.setFont(Font.font(font.getFamily(), 11));
            detail.setMinWidth(0);
            detail.setMaxWidth(Double.MAX_VALUE);
            detail.setTextOverrun(OverrunStyle.ELLIPSIS);
            detail.getStyleClass().add("pipeline-stage-detail");
            VBox copy = new VBox(3, title, detail);
            copy.setMinWidth(0);
            copy.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(copy, Priority.ALWAYS);
            state.getStyleClass().add("pipeline-stage-state");
            stateBox.setAlignment(Pos.CENTER_RIGHT);
            stateBox.setMinWidth(Region.USE_PREF_SIZE);
            stateBox.getStyleClass().add("pipeline-stage-state-box");
            body = new HBox(12, icon, copy, stateBox);
            body.setAlignment(Pos.CENTER_LEFT);
            body.setPadding(new Insets(8, 10, 8, 10));
            body.setManaged(false);
            getChildren().addAll(before, after, body);
        }

        void show(PipelineSession.StageView stage, String description, int count) {
            int index = stage.index();
            if (iconIndex != index) {
                icon.getChildren().setAll(new UiIcon(index < ICONS.length ? ICONS[index] : UiIcon.Kind.PIPELINE, 22));
                iconIndex = index;
            }
            title.setText(stage.label());
            detail.setText(index < DESCRIPTIONS.length ? DESCRIPTIONS[index] : "");
            state.setText(description);
            stateBox.getChildren().clear();
            if (stage.status() == PipelineSession.Status.COMPLETED) {
                stateBox.getChildren().add(new UiIcon(UiIcon.Kind.CHECK, 12));
            } else if (stage.status() == PipelineSession.Status.CURRENT) {
                stateBox.getChildren().add(new UiIcon(UiIcon.Kind.CHEVRON_RIGHT, 12));
            }
            stateBox.getChildren().add(state);
            before.setVisible(index > 0);
            after.setVisible(index < count - 1);
        }

        @Override
        protected void layoutChildren() {
            double middle = getHeight() / 2;
            before.setStartX(26);
            before.setEndX(26);
            before.setStartY(0);
            before.setEndY(Math.max(0, middle - 17));
            after.setStartX(26);
            after.setEndX(26);
            after.setStartY(Math.min(getHeight(), middle + 17));
            after.setEndY(getHeight());
            body.resizeRelocate(0, 0, getWidth(), getHeight());
        }
    }
}
