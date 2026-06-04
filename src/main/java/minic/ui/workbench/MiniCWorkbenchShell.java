package minic.ui;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import minic.settings.MiniCSettingsPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

/**
 * MiniC Visual Workbench 的 VS Code 风格外壳。
 */
public final class MiniCWorkbenchShell {
    private static final double ACTIVITY_BAR_WIDTH = 48;
    private static final double SIDEBAR_WIDTH = 260;
    private static final double INSPECTOR_WIDTH = 360;
    private final ArrayList<DocumentTab> documents = new ArrayList<>();
    private final MiniCKeyBindingConfig keyBindings = MiniCKeyBindingConfig.loadDefault();
    private BorderPane root;
    private HBox body;
    private HBox tabs;
    private VBox editor;
    private MiniCWorkbenchViewModel viewModel;
    private MiniCVisualPane visualPane;
    private VBox sourcePane;
    private StackPane mainContent;
    private MiniCHoverInspector hoverInspector;
    private ActivitySection activeSection = ActivitySection.CODE;
    private TextField editingTabField;
    private int activeDocumentIndex;
    private int nextUntitledIndex = 1;
    private int draggedTabIndex = -1;

    /**
     * 创建工作台外壳。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCWorkbenchShell(MiniCWorkbenchViewModel viewModel) {
        addDocument(nextUntitledName(), "", null, Objects.requireNonNull(viewModel, "viewModel"));
    }

    /**
     * 创建 JavaFX 根节点。
     *
     * @return 工作台根节点
     */
    public Parent createRoot() {
        root = new BorderPane();
        root.getStyleClass().add("workbench-root");
        root.setLeft(activityBar());
        root.setCenter(sectionContent());
        root.setBottom(statusBar());
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
        return root;
    }

    private VBox activityBar() {
        VBox activityBar = new VBox(6);
        activityBar.getStyleClass().add("activity-bar");
        lockWidth(activityBar, ACTIVITY_BAR_WIDTH);
        activityBar.getChildren().addAll(
                activityItem(ActivitySection.CODE),
                activityItem(ActivitySection.DEBUG),
                activityItem(ActivitySection.SETTINGS),
                activityItem(ActivitySection.INFO)
        );
        return activityBar;
    }

    private Label activityItem(ActivitySection section) {
        Label label = new Label();
        label.getStyleClass().add("activity-item");
        label.setGraphic(activityIcon(section));
        label.setTooltip(new Tooltip(section.title));
        label.setAccessibleText(section.title);
        if (section == activeSection) {
            label.getStyleClass().add("active");
        }
        label.setOnMouseClicked(event -> selectActivitySection(section));
        return label;
    }

    private SVGPath activityIcon(ActivitySection section) {
        SVGPath icon = new SVGPath();
        icon.getStyleClass().add("activity-icon");
        icon.setContent(section.iconPath);
        return icon;
    }

    private void selectActivitySection(ActivitySection section) {
        if (section == activeSection) {
            return;
        }
        activeSection = section;
        if (root != null) {
            root.setLeft(activityBar());
            root.setCenter(sectionContent());
        }
    }

    private Parent sectionContent() {
        if (activeSection == ActivitySection.CODE) {
            return workbenchBody();
        }
        if (activeSection == ActivitySection.DEBUG) {
            return new MiniCDebugPane(documents.get(activeDocumentIndex).viewModel());
        }
        if (activeSection == ActivitySection.SETTINGS) {
            body = null;
            visualPane = null;
            sourcePane = null;
            mainContent = null;
            return new MiniCSettingsPane();
        }
        body = null;
        visualPane = null;
        sourcePane = null;
        mainContent = null;
        return placeholderPage(activeSection);
    }

    private VBox placeholderPage(ActivitySection section) {
        VBox page = new VBox(10);
        page.getStyleClass().add("activity-placeholder");
        Label title = new Label(section.title);
        title.getStyleClass().add("activity-placeholder-title");
        Label description = new Label(section.placeholder);
        description.getStyleClass().add("activity-placeholder-text");
        page.getChildren().addAll(title, description);
        return page;
    }

    private HBox workbenchBody() {
        body = new HBox();
        body.getStyleClass().add("workbench-body");
        rebuildWorkbenchBody();
        return body;
    }

    private void rebuildWorkbenchBody() {
        if (body == null) {
            return;
        }
        DocumentTab active = documents.get(activeDocumentIndex);
        viewModel = active.viewModel();
        hoverInspector = new MiniCHoverInspector();
        body.getChildren().clear();
        VBox sidebar = sidebar();
        editor = editorArea();
        VBox inspector = new MiniCInspectorView(viewModel);
        lockWidth(sidebar, SIDEBAR_WIDTH);
        lockWidth(inspector, INSPECTOR_WIDTH);
        editor.setMinWidth(0);
        editor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editor, Priority.ALWAYS);
        body.getChildren().addAll(sidebar, editor, inspector);
    }

    private VBox sidebar() {
        return new MiniCSidebarView(viewModel);
    }

    private VBox editorArea() {
        VBox editor = new VBox();
        editor.getStyleClass().add("editor-area");
        editor.setMinWidth(0);
        editor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editor, Priority.ALWAYS);

        tabs = new HBox();
        tabs.getStyleClass().add("tabs");
        refreshTabs();

        mainContent = new StackPane();
        mainContent.getStyleClass().add("split");
        mainContent.setMinWidth(0);
        mainContent.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        sourcePane = sourceArea();
        visualPane = new MiniCVisualPane(viewModel, hoverInspector);
        sourcePane.setMinWidth(0);
        visualPane.setMinWidth(0);
        mainContent.getChildren().addAll(sourcePane, visualPane);
        mainContent.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                var state = viewModel.currentStateProperty().get();
                if (state != null && !"PAUSED".equals(state.playbackMode())) {
                    viewModel.pause();
                }
            }
        });
        viewModel.sessionStartedProperty().addListener((observable, oldValue, newValue) -> updateMainContent());
        viewModel.currentStateProperty().addListener((observable, oldValue, newValue) -> updateMainContent());
        viewModel.selectedVisualStageProperty().addListener((observable, oldValue, newValue) -> updateMainContent());
        updateMainContent();

        editor.getChildren().addAll(tabs, mainContent, new MiniCBottomPanel(hoverInspector));
        return editor;
    }

    private void updateMainContent() {
        if (sourcePane == null || visualPane == null) {
            return;
        }
        boolean sourceMode = sourceMode();
        sourcePane.setVisible(sourceMode);
        sourcePane.setManaged(sourceMode);
        visualPane.setVisible(!sourceMode);
        visualPane.setManaged(!sourceMode);
    }

    private boolean sourceMode() {
        String selectedStage = viewModel.selectedVisualStageProperty().get();
        if ("source".equals(selectedStage)) {
            return true;
        }
        if (selectedStage != null && !selectedStage.isEmpty()) {
            return false;
        }
        return !viewModel.sessionStartedProperty().get()
                || viewModel.currentStateProperty().get() == null
                || "source".equals(viewModel.currentStateProperty().get().currentStage());
    }

    private VBox sourceArea() {
        VBox sourceArea = new VBox();
        sourceArea.getStyleClass().add("source-area");
        MiniCSourceLoaderView loader = new MiniCSourceLoaderView(viewModel, this::openDocument, this::saveDocument);
        loader.usePersistentEditorScrollBars("pipeline-source-editor-scroll");
        sourceArea.getChildren().add(loader);
        VBox.setVgrow(loader, Priority.ALWAYS);
        return sourceArea;
    }

    private HBox statusBar() {
        HBox status = new HBox();
        status.getStyleClass().add("status-bar");
        Label left = new Label("MiniC 可视化工作台");
        Label right = new Label("C030 · 工作台");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        status.getChildren().addAll(left, spacer, right);
        return status;
    }

    private void refreshTabs() {
        if (tabs == null) {
            return;
        }
        tabs.getChildren().clear();
        for (int index = 0; index < documents.size(); index++) {
            DocumentTab document = documents.get(index);
            HBox tab = new HBox();
            tab.getStyleClass().add("tab");
            Label title = new Label("C  " + document.displayName());
            title.getStyleClass().add("tab-title");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            Label close = new Label("×");
            close.getStyleClass().add("tab-close");
            if (index == activeDocumentIndex) {
                tab.getStyleClass().add("active");
            }
            int tabIndex = index;
            tab.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    beginRenameDocument(tabIndex, tab, title);
                    event.consume();
                    return;
                }
                switchDocument(tabIndex);
            });
            tab.setOnDragDetected(event -> {
                draggedTabIndex = tabIndex;
                tab.startFullDrag();
                event.consume();
            });
            tab.setOnMouseDragEntered(event -> {
                reorderDraggedTab(tabIndex);
                event.consume();
            });
            close.setOnMouseClicked(event -> {
                closeDocument(tabIndex);
                event.consume();
            });
            tab.getChildren().addAll(title, close);
            tabs.getChildren().add(tab);
        }
        tabs.getChildren().add(toolbarButton("+", "新建文件", this::newDocument));
    }

    private Button toolbarButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("tab-action");
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    private void switchDocument(int index) {
        if (index < 0 || index >= documents.size() || index == activeDocumentIndex) {
            return;
        }
        activeDocumentIndex = index;
        body.getChildren().clear();
        rebuildWorkbenchBody();
        refreshTabs();
    }

    private void closeDocument(int index) {
        if (index < 0 || index >= documents.size()) {
            return;
        }
        documents.remove(index);
        if (documents.isEmpty()) {
            addDocument(nextUntitledName(), "", null, new MiniCWorkbenchViewModel());
            activeDocumentIndex = 0;
        } else if (activeDocumentIndex >= documents.size()) {
            activeDocumentIndex = documents.size() - 1;
        } else if (index < activeDocumentIndex) {
            activeDocumentIndex--;
        }
        if (body != null) {
            body.getChildren().clear();
            rebuildWorkbenchBody();
        }
        refreshTabs();
    }

    private void newDocument() {
        addDocument(nextUntitledName(), "", null, new MiniCWorkbenchViewModel());
        switchDocument(documents.size() - 1);
    }

    private void beginRenameDocument(int index, HBox tab, Label title) {
        if (index < 0 || index >= documents.size() || editingTabField != null) {
            return;
        }
        String oldName = documents.get(index).displayName();
        TextField editor = new TextField(oldName);
        editingTabField = editor;
        editor.getStyleClass().add("tab-rename");
        editor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editor, Priority.ALWAYS);
        editor.setOnAction(event -> commitRenameDocument(index, editor.getText()));
        editor.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused && editingTabField == editor) {
                commitRenameDocument(index, editor.getText());
            }
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                editingTabField = null;
                refreshTabs();
                event.consume();
            }
        });
        int titleIndex = tab.getChildren().indexOf(title);
        tab.getChildren().set(titleIndex, editor);
        editor.requestFocus();
        editor.selectAll();
    }

    private void commitRenameDocument(int index, String rawName) {
        if (index < 0 || index >= documents.size()) {
            editingTabField = null;
            refreshTabs();
            return;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            editingTabField = null;
            refreshTabs();
            return;
        }
        DocumentTab document = documents.get(index).withName(name);
        document.viewModel().renameSource(name);
        documents.set(index, document);
        editingTabField = null;
        if (index == activeDocumentIndex && body != null) {
            body.getChildren().clear();
            rebuildWorkbenchBody();
        }
        refreshTabs();
    }

    private String nextUntitledName() {
        return "untitled-" + nextUntitledIndex++ + ".mc";
    }

    void reorderDocumentTabsForTesting(int fromIndex, int toIndex) {
        reorderDocumentTab(fromIndex, toIndex);
    }

    private void reorderDraggedTab(int targetIndex) {
        if (draggedTabIndex < 0 || draggedTabIndex == targetIndex) {
            return;
        }
        int previousIndex = draggedTabIndex;
        if (reorderDocumentTab(previousIndex, targetIndex)) {
            draggedTabIndex = targetIndex;
        }
    }

    private boolean reorderDocumentTab(int fromIndex, int toIndex) {
        if (fromIndex < 0
                || toIndex < 0
                || fromIndex >= documents.size()
                || toIndex >= documents.size()
                || fromIndex == toIndex) {
            return false;
        }
        DocumentTab moved = documents.remove(fromIndex);
        documents.add(toIndex, moved);
        if (activeDocumentIndex == fromIndex) {
            activeDocumentIndex = toIndex;
        } else if (fromIndex < activeDocumentIndex && toIndex >= activeDocumentIndex) {
            activeDocumentIndex--;
        } else if (fromIndex > activeDocumentIndex && toIndex <= activeDocumentIndex) {
            activeDocumentIndex++;
        }
        if (body != null) {
            body.getChildren().clear();
            rebuildWorkbenchBody();
        }
        refreshTabs();
        return true;
    }

    private void openDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("打开 MiniC 源文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MiniC 源文件 (*.mc)", "*.mc"));
        java.io.File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            Path path = file.toPath();
            addDocument(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8), path, new MiniCWorkbenchViewModel());
            switchDocument(documents.size() - 1);
        } catch (IOException exception) {
            throw new IllegalStateException("无法打开源文件: " + file, exception);
        }
    }

    private void saveDocument() {
        DocumentTab document = documents.get(activeDocumentIndex);
        Path path = document.path();
        if (path == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("保存 MiniC 源文件");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MiniC 源文件 (*.mc)", "*.mc"));
            chooser.setInitialFileName(document.name());
            java.io.File file = chooser.showSaveDialog(window());
            if (file == null) {
                return;
            }
            path = file.toPath();
            document = document.withPath(path);
            documents.set(activeDocumentIndex, document);
            document.viewModel().renameSource(path.toString());
            refreshTabs();
        }
        try {
            Files.writeString(path, document.viewModel().sourceTextProperty().get(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存源文件: " + path, exception);
        }
    }

    private Window window() {
        return root == null || root.getScene() == null ? null : root.getScene().getWindow();
    }

    private void addDocument(String name, String source, Path path, MiniCWorkbenchViewModel model) {
        model.loadSource(path == null ? name : path.toString(), source);
        model.sourceNameProperty().addListener((observable, oldValue, newValue) -> refreshTabs());
        documents.add(new DocumentTab(name, path, model));
    }

    private void lockWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    private void handleShortcut(KeyEvent event) {
        if (visualPane == null) {
            return;
        }
        if (keyBindings.matches("ast.zoom.in", event)) {
            visualPane.zoomAstIn();
            event.consume();
        } else if (keyBindings.matches("ast.zoom.out", event)) {
            visualPane.zoomAstOut();
            event.consume();
        }
    }

    private record DocumentTab(String name, Path path, MiniCWorkbenchViewModel viewModel) {
        private String displayName() {
            if (path != null) {
                return path.getFileName().toString();
            }
            String sourceName = viewModel.sourceNameProperty().get();
            return sourceName == null || sourceName.isBlank() ? name : sourceName;
        }

        private DocumentTab withPath(Path path) {
            return new DocumentTab(path.getFileName().toString(), path, viewModel);
        }

        private DocumentTab withName(String name) {
            return new DocumentTab(name, path, viewModel);
        }
    }

    private enum ActivitySection {
        CODE("M6 2 L14 2 L20 8 L20 22 L6 22 Z M14 2 L14 8 L20 8 M9 13 L17 13 M9 17 L17 17",
                "代码区", "在这里编辑 MiniC 源码并启动可视化管线。"),
        DEBUG("M8 9 A4 4 0 0 1 16 9 L16 17 A4 4 0 0 1 8 17 Z M9.2 5 L14.8 5 M10 5 L8 2 M14 5 L16 2 M4 11 L8 11 M16 11 L20 11 M4 15 L8 15 M16 15 L20 15 M6 20 L8.5 17.5 M15.5 17.5 L18 20",
                "调试", "调试视图"),
        SETTINGS("M9.7 3 L14.3 3 L14.9 4.8 L16.5 5.5 L18.2 4.7 L20.5 8.7 L19.1 9.9 L19.1 11.8 L20.5 13 L18.2 17 L16.5 16.5 L14.9 17.2 L14.3 19 L9.7 19 L9.1 17.2 L7.5 16.5 L5.8 17 L3.5 13 L4.9 11.8 L4.9 9.9 L3.5 8.7 L5.8 4.7 L7.5 5.5 L9.1 4.8 Z M12 7.6 A3.4 3.4 0 1 0 12 14.4 A3.4 3.4 0 1 0 12 7.6",
                "设置", "设置视图将在后续实现。"),
        INFO("M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 M12 10 L12 17 M12 7 L12 7.1",
                "信息", "信息视图将在后续实现。");

        private final String iconPath;
        private final String title;
        private final String placeholder;

        ActivitySection(String iconPath, String title, String placeholder) {
            this.iconPath = iconPath;
            this.title = title;
            this.placeholder = placeholder;
        }
    }
}
