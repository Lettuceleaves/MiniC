package minic.ui.pipeline;

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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import minic.ui.component.UiStyles;
import minic.ui.component.action.UiButton;
import minic.ui.component.data.UiCollection;
import minic.ui.component.display.UiIcon;
import minic.ui.component.feedback.UiTooltip;

/** 编译展示台：左右输入输出留白，右侧承载执行控件与阶段列表。 */
public final class PipelinePanel extends BorderPane {
    private static final double SIDEBAR_WIDTH = 356;
    private static final double MIN_STAGE_HEIGHT = 56;
    private final UiButton nextStep = new UiButton("下一步");
    private final UiButton nextStage = new UiButton("下一阶段");
    private final UiCollection<PipelineSession.StageView> stages = new UiCollection<>();
    private final Label source = new Label("编译阶段");
    private final Label completed = new Label();
    private final Label status = new Label();
    private PipelineSession.Snapshot snapshot;
    private int selectedStage = -1;
    private boolean updating;
    private boolean busy;

    public PipelinePanel() {
        setId("pipeline-panel");
        setMinSize(0, 0);
        getStyleClass().add("pipeline-panel");
        SplitPane views = new SplitPane(placeholder("输入", "pipeline-input"), placeholder("输出", "pipeline-output"));
        views.setId("pipeline-io-split");
        views.setOrientation(Orientation.HORIZONTAL);
        views.setMinSize(0, 0);
        views.getStyleClass().addAll("app-split-pane", "app-horizontal-split");
        views.setDividerPositions(0.5);
        setCenter(views);
        setRight(createSidebar());
        clear();
    }

    public void setOnNextStep(Runnable action) {
        nextStep.setOnAction(event -> action.run());
    }

    public void setOnNextStage(Runnable action) {
        nextStage.setOnAction(event -> action.run());
    }

    public void clear() {
        snapshot = null;
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
        status.setText("正在准备编译…");
        setBusy(true);
    }

    public void show(PipelineSession.Snapshot current) {
        snapshot = current;
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
        if (current.failed()) {
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
        if (value) status.setText("正在编译…");
    }

    public void failed(String message) {
        busy = false;
        nextStep.setDisable(true);
        nextStage.setDisable(true);
        status.setText("编译失败：" + message);
        status.setTooltip(new UiTooltip(status.getText()));
        status.pseudoClassStateChanged(PseudoClass.getPseudoClass("failed"), true);
    }

    private void updateStatus() {
        if (snapshot == null || busy || snapshot.failed()) return;
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

    private BorderPane createSidebar() {
        nextStep.setId("pipeline-next-step");
        nextStage.setId("pipeline-next-stage");
        nextStep.getStyleClass().add("pipeline-step-button");
        nextStage.getStyleClass().add("pipeline-stage-button");
        nextStep.setGraphic(new UiIcon(UiIcon.Kind.STEP_FORWARD, 16));
        nextStage.setGraphic(new UiIcon(UiIcon.Kind.NEXT_STAGE, 16));
        nextStep.setGraphicTextGap(8);
        nextStage.setGraphicTextGap(8);
        nextStep.setTooltip(new UiTooltip("下一步", "执行当前编译阶段的一步。", ""));
        nextStage.setTooltip(new UiTooltip("下一阶段", "完成当前阶段，停在下一阶段入口。", ""));
        nextStep.setMaxWidth(Double.MAX_VALUE);
        nextStage.setMaxWidth(Double.MAX_VALUE);
        nextStep.setPrefWidth(0);
        nextStage.setPrefWidth(0);
        HBox.setHgrow(nextStep, Priority.ALWAYS);
        HBox.setHgrow(nextStage, Priority.ALWAYS);
        HBox actions = new HBox(8, nextStep, nextStage);
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
            if (requested < 0 || requested >= stages.getItems().size()
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
        sidebar.setBottom(status);
        sidebar.setMinSize(0, 0);
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMaxWidth(SIDEBAR_WIDTH);
        sidebar.getStyleClass().add("interaction-sidebar");
        return sidebar;
    }

    private static BorderPane placeholder(String title, String id) {
        Label heading = new Label(title);
        heading.getStyleClass().add("pipeline-pane-title");
        HBox bar = new HBox(heading);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 12, 0, 12));
        bar.setMinHeight(36);
        bar.setPrefHeight(36);
        bar.getStyleClass().add("interaction-toolbar");
        StackPane empty = new StackPane();
        empty.setMinSize(0, 0);
        BorderPane pane = new BorderPane(empty);
        pane.setTop(bar);
        pane.setId(id);
        pane.setMinSize(0, 0);
        pane.getStyleClass().add("pipeline-io-pane");
        return pane;
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
