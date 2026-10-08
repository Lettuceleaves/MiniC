package craken.ui.interaction;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import craken.ui.component.UiStyles;
import craken.ui.component.data.UiCollection;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.editor.EditorArea;
import craken.ui.interaction.terminal.TerminalPanel;
import craken.ui.interaction.inputoutput.InputOutputPanel;
import craken.ui.interaction.inputoutput.InputOutputTabs;
import craken.ui.interaction.diagnostics.RealtimeDiagnosticsPanel;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** 底部交互区：左侧保留交互容器，右侧使用带滚动条的泛型列表切换。 */
public final class InteractionArea extends BorderPane implements AutoCloseable {
    private final Path projectRoot;
    private final ObservableList<InteractionItem<?>> items = FXCollections.observableArrayList();
    private final ObservableList<InteractionItem<?>> readOnlyItems = FXCollections.unmodifiableObservableList(items);
    private final UiCollection<InteractionItem<?>> list = new UiCollection<>(items);
    private final StackPane content = new StackPane();
    private final Label empty = new Label("点击 + 新建 PowerShell 终端；点击“用例”绑定当前编辑器");
    private final Map<EditorArea, InputOutputTabs> inputOutputTabs = new IdentityHashMap<>();
    private int terminalNumber;
    private int inputOutputNumber;
    private int diagnosticsNumber;
    private int caseNumber;
    private Button newCase;
    private Runnable onNewCase;
    private boolean newCaseDisabled;
    private boolean closed;

    public InteractionArea() {
        this(defaultProjectRoot());
    }

    public InteractionArea(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        setMinSize(0, 0);
        setPrefHeight(200);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        getStyleClass().add("interaction-area");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        content.setMinSize(0, 0);
        content.getStyleClass().add("interaction-content");
        empty.getStyleClass().add("placeholder-title");
        content.getChildren().add(empty);
        setCenter(content);
        setRight(createSidebar());
        list.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> show(selected));
        sceneProperty().addListener((observable, old, scene) -> {
            if (scene != null && activeItem() != null) activeItem().shown();
        });
        newTerminal();
    }

    public Path projectRoot() { return projectRoot; }

    public ObservableList<InteractionItem<?>> items() { return readOnlyItems; }

    public InteractionItem<?> activeItem() { return list.getSelectionModel().getSelectedItem(); }

    public ReadOnlyObjectProperty<InteractionItem<?>> activeItemProperty() {
        return list.getSelectionModel().selectedItemProperty();
    }

    /** 添加任意 Node 类型的交互容器，返回原来的泛型项供调用者继续使用。 */
    public <T extends Node> InteractionItem<T> addItem(InteractionItem<T> item) {
        return addItem(item, true);
    }

    /** 添加交互容器；{@code select} 为 false 时不切换当前展示，只挂到列表。 */
    public <T extends Node> InteractionItem<T> addItem(InteractionItem<T> item, boolean select) {
        if (closed) throw new IllegalStateException("interaction area is closed");
        Objects.requireNonNull(item, "item");
        if (item.isClosed()) throw new IllegalArgumentException("item is closed");
        if (items.contains(item)) return item;
        if (item.content().getParent() != null
                || items.stream().anyMatch(existing -> existing.content() == item.content())) {
            throw new IllegalArgumentException("content already belongs to an interaction container");
        }
        items.add(item);
        if (select) select(item);
        return item;
    }

    public void select(InteractionItem<?> item) {
        if (!items.contains(item)) throw new IllegalArgumentException("item does not belong to this area");
        list.getSelectionModel().select(item);
        list.scrollTo(item);
    }

    public InteractionItem<TerminalPanel> newTerminal() {
        if (closed) throw new IllegalStateException("interaction area is closed");
        TerminalPanel terminal = new TerminalPanel(projectRoot);
        return addItem(new InteractionItem<>("PowerShell " + ++terminalNumber, terminal,
                terminal::start, terminal::activate, terminal::close));
    }

    /** 为输入输出及其编译中包装项分配列表名称；与 PowerShell 独立编号，关闭后不复用。 */
    public String nextInputOutputTitle() {
        if (closed) throw new IllegalStateException("interaction area is closed");
        return "IO " + ++inputOutputNumber;
    }

    /**
     * 该编辑区的运行/调试共用 IO 项表：同一个编辑器组件重复运行或调试复用同一个 IO 项，
     * 后开始的会话接管该项并关闭旧内容（停止旧程序、释放旧视图）。
     */
    public InputOutputTabs inputOutputTabs(EditorArea editors) {
        Objects.requireNonNull(editors, "editors");
        if (closed) throw new IllegalStateException("interaction area is closed");
        return inputOutputTabs.computeIfAbsent(editors, area -> new InputOutputTabs(area, this));
    }

    /** 为用例标签分配列表名称；与 PowerShell、IO、ERR 独立编号，关闭后不复用。 */
    public String nextCaseTitle() {
        if (closed) throw new IllegalStateException("interaction area is closed");
        return "CASE " + ++caseNumber;
    }

    /**
     * 由运行控制器接管“新建用例”命令；命令为空时按钮不可用。
     * 用例由运行控制器绑定到当前编辑器，交互区只提供入口按钮。
     */
    public void setOnNewCase(Runnable action) {
        onNewCase = action;
        updateNewCaseButton();
    }

    /** “新建用例”当前是否不可用；没有活动编辑器或正在关闭时由调用者置为 true。 */
    public void setNewCaseDisabled(boolean disabled) {
        newCaseDisabled = disabled;
        updateNewCaseButton();
    }

    /** 新建用户程序专用的输入输出项；列表默认显示 IO 数字，不进入 PowerShell。 */
    public InteractionItem<InputOutputPanel> newInputOutput(Path workingDirectory, Path executable) {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(executable, "executable");
        return newInputOutput(nextInputOutputTitle(), workingDirectory, executable);
    }

    /** 对外提供有类型的入口；程序结束后回车只关闭它自己，其他项不受影响。 */
    public InteractionItem<InputOutputPanel> newInputOutput(String title, Path workingDirectory, Path executable) {
        if (closed) throw new IllegalStateException("interaction area is closed");
        Objects.requireNonNull(title, "title");
        InputOutputPanel panel = new InputOutputPanel(workingDirectory, executable);
        InteractionItem<InputOutputPanel> item = new InteractionItem<>(title, panel,
                panel::start, panel::activate, panel::close);
        panel.setOnCloseRequest(() -> closeItem(item));
        return addItem(item);
    }

    /** 为单个编辑器绑定 ERR 报错标签；序号独立递增且关闭后不复用，挂载时不抢占当前交互项。 */
    public InteractionItem<RealtimeDiagnosticsPanel> newDiagnostics() {
        if (closed) throw new IllegalStateException("interaction area is closed");
        String title = "ERR " + ++diagnosticsNumber;
        RealtimeDiagnosticsPanel panel = new RealtimeDiagnosticsPanel(title);
        return addItem(new InteractionItem<>(title, panel, () -> { }, panel::close), false);
    }

    public boolean closeItem(InteractionItem<?> item) {
        int index = items.indexOf(item);
        if (index < 0) return false;
        boolean selected = activeItem() == item;
        if (selected) list.getSelectionModel().clearSelection();
        items.remove(index);
        item.close();
        if (selected) {
            if (items.isEmpty()) show(null);
            else select(items.get(Math.min(index, items.size() - 1)));
        }
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        inputOutputTabs.values().forEach(InputOutputTabs::close);
        inputOutputTabs.clear();
        // 先清除选择，避免关闭过程中启动另一个尚未使用的终端。
        list.getSelectionModel().clearSelection();
        items.forEach(InteractionItem::close);
        items.clear();
        show(null);
        setDisable(true);
    }

    private void show(InteractionItem<?> selected) {
        content.getChildren().setAll(selected == null ? empty : selected.content());
        if (!closed && selected != null && getScene() != null) selected.selected();
    }

    private Node createSidebar() {
        list.setId("interaction-list");
        list.getStyleClass().add("interaction-list");
        Font font = UiStyles.listFont();
        Text sample = new Text("Ag");
        sample.setFont(font);
        list.setFixedCellSize(Math.max(24, Math.ceil(sample.getLayoutBounds().getHeight()) + 8));
        list.setPrefWidth(176);
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        list.setPlaceholder(new Label("暂无面板"));
        list.setCellFactory(view -> new ListCell<>() {
            private static final String PASSED_STYLE = "interaction-passed";
            private static final String FAILED_STYLE = "interaction-failed";
            private final UiTooltip fullTitle = new UiTooltip("");
            private final Text measurement = new Text();
            private final javafx.beans.value.ChangeListener<InteractionItem.Result> resultChanged =
                    (observable, previous, current) -> applyResult(current);
            private InteractionItem<?> bound;

            {
                setFont(font);
                setAlignment(Pos.CENTER_LEFT);
                setWrapText(false);
                setTextOverrun(OverrunStyle.ELLIPSIS);
                setEllipsisString("...");
                setMinWidth(0);
                // VirtualFlow 按视口宽度铺满单元格，长标题省略而不是撑出横向滚动条。
                setPrefWidth(0);
            }

            @Override
            protected void updateItem(InteractionItem<?> item, boolean emptyCell) {
                fullTitle.hide();
                super.updateItem(item, emptyCell);
                if (bound != null) {
                    bound.resultProperty().removeListener(resultChanged);
                    bound = null;
                }
                setGraphic(null);
                setText(emptyCell || item == null ? null : item.title());
                setAccessibleText(getText());
                fullTitle.setText(getText());
                setTooltip(null);
                if (!emptyCell && item != null) {
                    bound = item;
                    item.resultProperty().addListener(resultChanged);
                    applyResult(item.result());
                } else {
                    applyResult(InteractionItem.Result.NONE);
                }
            }

            private void applyResult(InteractionItem.Result result) {
                getStyleClass().removeAll(PASSED_STYLE, FAILED_STYLE);
                if (result == InteractionItem.Result.PASSED) getStyleClass().add(PASSED_STYLE);
                else if (result == InteractionItem.Result.FAILED) getStyleClass().add(FAILED_STYLE);
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                measurement.setText(getText());
                measurement.setFont(getFont());
                boolean clipped = !isEmpty() && getText() != null
                        && measurement.getLayoutBounds().getWidth()
                        > getWidth() - getInsets().getLeft() - getInsets().getRight();
                if (!clipped) fullTitle.hide();
                setTooltip(clipped ? fullTitle : null);
            }
        });
        Label title = new Label("交互面板");
        title.getStyleClass().add("interaction-sidebar-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        newCase = action("用例", "新建用例：绑定当前编辑器，运行时自动输入预置数据",
                "interaction-new-case", 40, () -> {
                    if (onNewCase != null) onNewCase.run();
                });
        Button add = action("+", "新建 PowerShell 终端", "interaction-new-terminal", this::newTerminal);
        Button remove = action("×", "关闭当前面板", "interaction-close-panel", () -> closeItem(activeItem()));
        remove.disableProperty().bind(activeItemProperty().isNull());
        updateNewCaseButton();
        HBox toolbar = new HBox(4, title, spacer, newCase, add, remove);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 6, 0, 10));
        toolbar.setMinHeight(32);
        toolbar.setPrefHeight(32);
        toolbar.getStyleClass().add("interaction-toolbar");
        BorderPane sidebar = new BorderPane(list);
        sidebar.setTop(toolbar);
        sidebar.setMinWidth(156);
        sidebar.setPrefWidth(176);
        sidebar.setMaxWidth(176);
        sidebar.setMinHeight(0);
        sidebar.getStyleClass().add("interaction-sidebar");
        return sidebar;
    }

    private static Button action(String text, String help, String id, Runnable action) {
        return action(text, help, id, 24, action);
    }

    private static Button action(String text, String help, String id, double width, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setMinSize(width, 24);
        button.setPrefSize(width, 24);
        button.setMaxSize(width, 24);
        button.setPadding(Insets.EMPTY);
        button.getStyleClass().add("interaction-action");
        button.setAccessibleText(help);
        button.setTooltip(new UiTooltip(help));
        button.setOnAction(event -> action.run());
        return button;
    }

    private void updateNewCaseButton() {
        if (newCase != null) newCase.setDisable(newCaseDisabled || onNewCase == null);
    }

    private static Path defaultProjectRoot() {
        String configured = System.getProperty("craken.project.root");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        // jpackage 应用镜像的启动器会把自身路径写入 jpackage.app-path；
        // 镜像根目录就是项目根，不能依赖启动时的工作目录。
        String launcherPath = System.getProperty("jpackage.app-path");
        Path launcherDir = launcherPath == null || launcherPath.isBlank()
                ? null
                : Path.of(launcherPath).getParent();
        return launcherDir == null ? Path.of("") : launcherDir;
    }
}
