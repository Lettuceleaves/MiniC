package minic.uilocal;
import javafx.scene.Parent;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
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
import minic.color.ThemeManager;
import minic.settings.MiniCSettings;
import minic.uilocal.control.MiniCActiveTrackingService;
import minic.uilocal.control.MiniCControlTargetType;
import minic.uilocal.control.MiniCViewportAdapter;
import minic.uilocal.control.MiniCWorkbenchControlHub;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * MiniC Visual Workbench 的 VS Code 风格外壳。
 */
public final class MiniCWorkbenchShell {
    private static final double TEXT_ZOOM_STEP = 1.0;
    private static final double VIEWPORT_KEY_SCROLL_DELTA = 48.0;
    private static final List<String> COMPILER_SHORTCUT_ACTIONS = List.of(
            MiniCWorkbenchControlHub.COMPILER_NEXT,
            MiniCWorkbenchControlHub.COMPILER_NEXT_STAGE,
            MiniCWorkbenchControlHub.COMPILER_RUN_TO_EXECUTION,
            MiniCWorkbenchControlHub.COMPILER_PLAY,
            MiniCWorkbenchControlHub.COMPILER_PLAY_FAST,
            MiniCWorkbenchControlHub.COMPILER_PAUSE
    );
    private static final List<String> SETTINGS_SHORTCUT_ACTIONS = List.of(
            MiniCWorkbenchControlHub.SETTINGS_THEME_NEXT,
            MiniCWorkbenchControlHub.SETTINGS_THEME_PREVIOUS,
            MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_INCREASE,
            MiniCWorkbenchControlHub.SETTINGS_FRAME_INTERVAL_DECREASE,
            MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_INCREASE,
            MiniCWorkbenchControlHub.SETTINGS_UI_SCALE_DECREASE
    );
    private final ArrayList<DocumentTab> documents = new ArrayList<>();
    private final MiniCKeyBindingConfig keyBindings = MiniCKeyBindingConfig.loadDefault();
    private final MiniCWorkbenchControlHub controlHub = new MiniCWorkbenchControlHub();
    private final LinkedHashSet<KeyCode> pressedKeys = new LinkedHashSet<>();
    private BorderPane root;
    private HBox body;
    private HBox tabs;
    private VBox editor;
    private MiniCWorkbenchViewModel viewModel;
    private MiniCVisualPane visualPane;
    private MiniCSourceLoaderView sourceLoader;
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
        MiniCWorkbenchViewModel initialModel = Objects.requireNonNull(viewModel, "viewModel");
        if (!restorePersistedDocuments(initialModel)) {
            addDocument(nextUntitledName(), "", null, BigDecimal.ZERO, initialModel, false);
        }
        registerSettingsCommands();
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
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        root.addEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyReleased);
        root.addEventFilter(ScrollEvent.SCROLL, this::handleShortcut);
        return root;
    }

    private VBox activityBar() {
        VBox activityBar = new VBox(6);
        activityBar.getStyleClass().add("activity-bar");
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
            return settingsPage();
        }
        if (activeSection == ActivitySection.INFO) {
            body = null;
            visualPane = null;
            sourcePane = null;
            mainContent = null;
            return new MiniCInfoView();
        }
        body = null;
        visualPane = null;
        sourcePane = null;
        mainContent = null;
        return placeholderPage(activeSection);
    }

    private ScrollPane settingsPage() {
        ScrollPane scroll = new ScrollPane(new MiniCSettingsPane());
        scroll.getStyleClass().add("settings-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        return scroll;
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
        VBox inspector = new MiniCInspectorView(viewModel, controlHub);
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
        sourceLoader.installViewportTarget(controlHub);
        visualPane.installViewportTargets(controlHub);
        controlHub.setActiveTrackingAction(new MiniCActiveTrackingService(this::activeViewportAdapters)::trackActiveViewports);
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
        sourceLoader = new MiniCSourceLoaderView(viewModel, this::openDocument, this::saveDocument, this::saveDocumentAs);
        sourceLoader.usePersistentEditorScrollBars("pipeline-source-editor-scroll");
        sourceArea.getChildren().add(sourceLoader);
        VBox.setVgrow(sourceLoader, Priority.ALWAYS);
        return sourceArea;
    }

    private List<MiniCViewportAdapter> activeViewportAdapters() {
        if (sourceLoader == null || visualPane == null) {
            return List.of();
        }
        if (sourceMode()) {
            return List.of(sourceLoader.viewportAdapter());
        }
        return visualPane.activeViewportAdapters();
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
        syncActiveEditorToModel();
        activeDocumentIndex = index;
        body.getChildren().clear();
        rebuildWorkbenchBody();
        refreshTabs();
    }

    private void closeDocument(int index) {
        if (index < 0 || index >= documents.size()) {
            return;
        }
        syncActiveEditorToModel();
        DocumentTab closed = documents.remove(index);
        if (closed.path() != null) {
            MiniCSettings.forgetOpenFile(closed.path());
        }
        if (documents.isEmpty()) {
            addDocument(nextUntitledName(), "", null, BigDecimal.ZERO, new MiniCWorkbenchViewModel(), false);
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
        syncActiveEditorToModel();
        addDocument(nextUntitledName(), "", null, nextDocumentOrder(), new MiniCWorkbenchViewModel(), false);
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
        syncActiveEditorToModel();
        DocumentTab moved = documents.remove(fromIndex);
        documents.add(toIndex, moved);
        BigDecimal nextOrder = orderForIndex(toIndex);
        moved = moved.withOrder(nextOrder);
        documents.set(toIndex, moved);
        if (moved.path() != null) {
            MiniCSettings.updateOpenFileOrder(moved.path(), moved.order());
        }
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
        applyRememberedDirectory(chooser);
        java.io.File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            syncActiveEditorToModel();
            Path path = normalizePath(file.toPath());
            MiniCSettings.rememberFileDialogLocation(path);
            int existing = documentIndex(path);
            if (existing >= 0) {
                switchDocument(existing);
                return;
            }
            addDocument(
                    path.getFileName().toString(),
                    Files.readString(path, StandardCharsets.UTF_8),
                    path,
                    nextDocumentOrder(),
                    new MiniCWorkbenchViewModel(),
                    true
            );
            switchDocument(documents.size() - 1);
        } catch (IOException exception) {
            throw new IllegalStateException("无法打开源文件: " + file, exception);
        }
    }

    private void saveDocument() {
        syncActiveEditorToModel();
        DocumentTab document = documents.get(activeDocumentIndex);
        Path path = document.path();
        if (path == null) {
            saveDocumentAs();
            return;
        }
        try {
            Files.writeString(path, document.viewModel().sourceTextProperty().get(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存源文件: " + path, exception);
        }
    }

    private void saveDocumentAs() {
        syncActiveEditorToModel();
        DocumentTab document = documents.get(activeDocumentIndex);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("另存为 MiniC 源文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MiniC 源文件 (*.mc)", "*.mc"));
        chooser.setInitialFileName(document.displayName());
        applyRememberedDirectory(chooser);
        java.io.File file = chooser.showSaveDialog(window());
        if (file == null) {
            return;
        }
        saveDocumentAs(file.toPath());
    }

    void saveDocumentAsForTesting(Path path) {
        syncActiveEditorToModel();
        saveDocumentAs(path);
    }

    private void saveDocumentAs(Path rawPath) {
        Path path = normalizePath(rawPath);
        DocumentTab document = documents.get(activeDocumentIndex);
        Path oldPath = document.path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, document.viewModel().sourceTextProperty().get(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法另存源文件: " + path, exception);
        }
        MiniCSettings.rememberFileDialogLocation(path);
        if (oldPath != null && !oldPath.equals(path)) {
            MiniCSettings.forgetOpenFile(oldPath);
        }
        DocumentTab saved = document.withPath(path);
        documents.set(activeDocumentIndex, saved);
        saved.viewModel().renameSource(path.toString());
        MiniCSettings.rememberOpenFile(path, saved.order());
        refreshTabs();
    }

    private Window window() {
        return root == null || root.getScene() == null ? null : root.getScene().getWindow();
    }

    private boolean restorePersistedDocuments(MiniCWorkbenchViewModel initialModel) {
        List<MiniCSettings.OpenFileState> files = MiniCSettings.openFiles();
        if (files.isEmpty()) {
            return false;
        }
        int restored = 0;
        for (MiniCSettings.OpenFileState file : files) {
            Path path = file.path();
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                MiniCWorkbenchViewModel model = restored == 0 ? initialModel : new MiniCWorkbenchViewModel();
                addDocument(
                        path.getFileName().toString(),
                        Files.readString(path, StandardCharsets.UTF_8),
                        path,
                        file.order(),
                        model,
                        false
                );
                restored++;
            } catch (IOException ignored) {
            }
        }
        if (restored > 0) {
            activeDocumentIndex = 0;
            if (restored != files.size()) {
                persistOpenDocuments();
            }
            return true;
        }
        MiniCSettings.setOpenFiles(List.of());
        return false;
    }

    private void addDocument(
            String name,
            String source,
            Path path,
            BigDecimal order,
            MiniCWorkbenchViewModel model,
            boolean persist
    ) {
        Path normalizedPath = path == null ? null : normalizePath(path);
        model.loadSource(normalizedPath == null ? name : normalizedPath.toString(), source);
        model.sourceNameProperty().addListener((observable, oldValue, newValue) -> refreshTabs());
        DocumentTab document = new DocumentTab(name, normalizedPath, order, model);
        documents.add(document);
        if (persist && normalizedPath != null) {
            MiniCSettings.rememberOpenFile(normalizedPath, order);
        }
    }

    private void persistOpenDocuments() {
        MiniCSettings.setOpenFiles(documents.stream()
                .filter(document -> document.path() != null)
                .map(document -> new MiniCSettings.OpenFileState(document.path(), document.order()))
                .toList());
    }

    private void syncActiveEditorToModel() {
        if (sourceLoader != null && activeDocumentIndex >= 0 && activeDocumentIndex < documents.size()) {
            sourceLoader.loadCurrentSource();
        }
    }

    private void applyRememberedDirectory(FileChooser chooser) {
        MiniCSettings.lastFileDialogDirectory()
                .filter(Files::isDirectory)
                .ifPresent(path -> chooser.setInitialDirectory(path.toFile()));
    }

    private BigDecimal nextDocumentOrder() {
        if (documents.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return MiniCSettings.tabOrderAfter(documents.get(documents.size() - 1).order());
    }

    private BigDecimal orderForIndex(int index) {
        BigDecimal previous = index <= 0 ? null : documents.get(index - 1).order();
        BigDecimal next = index + 1 >= documents.size() ? null : documents.get(index + 1).order();
        BigDecimal order = MiniCSettings.tabOrderBetween(previous, next);
        if ((previous != null && order.compareTo(previous) <= 0) || (next != null && order.compareTo(next) >= 0)) {
            renumberDocumentOrders();
            previous = index <= 0 ? null : documents.get(index - 1).order();
            next = index + 1 >= documents.size() ? null : documents.get(index + 1).order();
            order = MiniCSettings.tabOrderBetween(previous, next);
        }
        return order;
    }

    private void renumberDocumentOrders() {
        for (int i = 0; i < documents.size(); i++) {
            DocumentTab document = documents.get(i).withOrder(BigDecimal.valueOf(i + 1L));
            documents.set(i, document);
        }
        persistOpenDocuments();
    }

    private int documentIndex(Path path) {
        Path normalized = normalizePath(path);
        for (int i = 0; i < documents.size(); i++) {
            if (normalized.equals(documents.get(i).path())) {
                return i;
            }
        }
        return -1;
    }

    private static Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    List<Path> documentPathsForTesting() {
        return documents.stream()
                .map(DocumentTab::path)
                .filter(Objects::nonNull)
                .toList();
    }

    List<String> documentNamesForTesting() {
        return documents.stream()
                .map(DocumentTab::displayName)
                .toList();
    }

    private void handleKeyPressed(KeyEvent event) {
        if (!isModifier(event.getCode())) {
            pressedKeys.add(event.getCode());
        }
        handleShortcut(event);
    }

    private void handleKeyReleased(KeyEvent event) {
        if (!isModifier(event.getCode())) {
            pressedKeys.remove(event.getCode());
        }
    }

    private void handleShortcut(KeyEvent event) {
        if (event.isConsumed()) {
            return;
        }
        if (handleCommandShortcut(event, COMPILER_SHORTCUT_ACTIONS)
                || handleCommandShortcut(event, SETTINGS_SHORTCUT_ACTIONS)
                || handleViewportShortcut(event)) {
            return;
        }
    }

    private boolean handleCommandShortcut(KeyEvent event, List<String> actions) {
        for (String action : actions) {
            if (keyBindings.matches(action, event, pressedKeys)) {
                controlHub.execute(action);
                event.consume();
                return true;
            }
        }
        return false;
    }

    private boolean handleViewportShortcut(KeyEvent event) {
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, event, pressedKeys)) {
            controlHub.handleZoom(Point2D.ZERO, viewportZoomDelta(1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, event, pressedKeys)) {
            controlHub.handleZoom(Point2D.ZERO, viewportZoomDelta(-1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP, event, pressedKeys)) {
            controlHub.handleScrollVertical(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, event, pressedKeys)) {
            controlHub.handleScrollVertical(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE, event, pressedKeys)) {
            controlHub.handleCenterActive();
            event.consume();
            return true;
        }
        return false;
    }

    private void handleShortcut(ScrollEvent event) {
        if (event.isConsumed()) {
            return;
        }
        if (handleCommandShortcut(event, COMPILER_SHORTCUT_ACTIONS)
                || handleCommandShortcut(event, SETTINGS_SHORTCUT_ACTIONS)
                || handleViewportShortcut(event)) {
            return;
        }
    }

    private boolean handleCommandShortcut(ScrollEvent event, List<String> actions) {
        for (String action : actions) {
            if (keyBindings.matches(action, event, pressedKeys)) {
                controlHub.execute(action);
                event.consume();
                return true;
            }
        }
        return false;
    }

    private boolean handleViewportShortcut(ScrollEvent event) {
        Point2D point = new Point2D(event.getX(), event.getY());
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_IN, event, pressedKeys)) {
            controlHub.handleZoom(point, viewportZoomDelta(1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_ZOOM_OUT, event, pressedKeys)) {
            controlHub.handleZoom(point, viewportZoomDelta(-1.0));
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_UP, event, pressedKeys)) {
            controlHub.handleScrollVertical(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_DOWN, event, pressedKeys)) {
            controlHub.handleScrollVertical(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_LEFT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(-VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_SCROLL_RIGHT, event, pressedKeys)) {
            controlHub.handleScrollHorizontal(VIEWPORT_KEY_SCROLL_DELTA);
            event.consume();
            return true;
        }
        if (keyBindings.matches(MiniCWorkbenchControlHub.VIEWPORT_CENTER_ACTIVE, event, pressedKeys)) {
            controlHub.handleCenterActive();
            event.consume();
            return true;
        }
        return false;
    }

    private double viewportZoomDelta(double direction) {
        return direction * controlHub.viewportRegistry().currentTarget()
                .filter(adapter -> adapter.type() == MiniCControlTargetType.TEXT)
                .map(adapter -> TEXT_ZOOM_STEP)
                .orElse(MiniCSettings.graphZoomStep());
    }

    private static boolean isModifier(KeyCode code) {
        return code == KeyCode.CONTROL
                || code == KeyCode.ALT
                || code == KeyCode.SHIFT
                || code == KeyCode.META;
    }

    private void registerSettingsCommands() {
        controlHub.registerSettingsCommands(new MiniCWorkbenchControlHub.SettingsCommands(
                ThemeManager::setTheme,
                () -> shiftTheme(1),
                () -> shiftTheme(-1),
                MiniCSettings::setFrameIntervalMillis,
                MiniCSettings::frameIntervalMillis,
                MiniCSettings::minFrameInterval,
                MiniCSettings::maxFrameInterval,
                50,
                MiniCSettings::setUiScale,
                MiniCSettings::uiScale,
                MiniCSettings::minUiScale,
                MiniCSettings::maxUiScale,
                0.05
        ));
    }

    private void shiftTheme(int delta) {
        List<String> themes = ThemeManager.availableThemes();
        if (themes.isEmpty()) {
            return;
        }
        String current = ThemeManager.currentTheme();
        int index = current == null ? -1 : themes.indexOf(current);
        String next = themes.get(Math.floorMod(index + delta, themes.size()));
        ThemeManager.setTheme(next);
    }

    private record DocumentTab(String name, Path path, BigDecimal order, MiniCWorkbenchViewModel viewModel) {
        private String displayName() {
            if (path != null) {
                return path.getFileName().toString();
            }
            String sourceName = viewModel.sourceNameProperty().get();
            return sourceName == null || sourceName.isBlank() ? name : sourceName;
        }

        private DocumentTab withPath(Path path) {
            return new DocumentTab(path.getFileName().toString(), path, order, viewModel);
        }

        private DocumentTab withName(String name) {
            return new DocumentTab(name, path, order, viewModel);
        }

        private DocumentTab withOrder(BigDecimal order) {
            return new DocumentTab(name, path, order, viewModel);
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
