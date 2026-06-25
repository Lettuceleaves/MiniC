package minic.uilocal;
import javafx.scene.Parent;
import javafx.geometry.Point2D;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
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
    private final ArrayList<WorkspaceTab> stageTabs = new ArrayList<>();
    private final LinkedHashSet<String> rightWorkspaceTabIds = new LinkedHashSet<>();
    private final MiniCKeyBindingConfig keyBindings = MiniCKeyBindingConfig.loadDefault();
    private final MiniCWorkbenchControlHub controlHub = new MiniCWorkbenchControlHub();
    private final LinkedHashSet<KeyCode> pressedKeys = new LinkedHashSet<>();
    private BorderPane root;
    private HBox body;
    private HBox tabs;
    private HBox rightTabs;
    private VBox editor;
    private MiniCWorkbenchViewModel viewModel;
    private MiniCVisualPane visualPane;
    private MiniCSourceLoaderView sourceLoader;
    private VBox sourcePane;
    private StackPane mainContent;
    private StackPane rightMainContent;
    private StackPane workspaceHost;
    private SplitPane workspaceSplit;
    private MiniCHoverInspector hoverInspector;
    private ActivitySection activeSection = ActivitySection.CODE;
    private TextField editingTabField;
    private int activeDocumentIndex;
    private String activeLeftWorkspaceTabId = "";
    private String activeRightWorkspaceTabId;
    private boolean pipelineLeftSidebarCollapsed = MiniCSettings.pipelineLeftSidebarCollapsed();
    private boolean pipelineRightSidebarCollapsed = MiniCSettings.pipelineRightSidebarCollapsed();
    private String compilerControlsDock = MiniCSettings.compilerControlsDock();
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
        activeLeftWorkspaceTabId = sourceTabId(documents.get(activeDocumentIndex));
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
        VBox inspector = inspectorSidebar();
        editor.setMinWidth(0);
        editor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editor, Priority.ALWAYS);
        body.getChildren().addAll(sidebar, editor, inspector);
    }

    private VBox inspectorSidebar() {
        if (pipelineRightSidebarCollapsed) {
            return sidebarRail("metadata-sidebar-rail", "元数据", () -> setPipelineRightSidebarCollapsed(false));
        }
        VBox inspector = new VBox();
        inspector.getStyleClass().add("inspector");
        inspector.getChildren().add(sidebarCollapseBar("元数据", ">", () -> setPipelineRightSidebarCollapsed(true)));
        if ("RIGHT_METADATA_TOP".equals(compilerControlsDock)) {
            inspector.getChildren().add(compilerControlsHost("right-metadata-controls"));
        }
        inspector.getChildren().add(new MiniCInspectorView(viewModel, controlHub));
        return inspector;
    }

    private VBox sidebar() {
        if (pipelineLeftSidebarCollapsed) {
            return sidebarRail("pipeline-sidebar-rail", "Pipeline", () -> setPipelineLeftSidebarCollapsed(false));
        }
        MiniCSidebarView sidebar = new MiniCSidebarView(viewModel, this::openStageTabs);
        sidebar.getChildren().add(0, sidebarCollapseBar("Pipeline", "<", () -> setPipelineLeftSidebarCollapsed(true)));
        if ("LEFT_PIPELINE_BOTTOM".equals(compilerControlsDock)) {
            sidebar.getChildren().add(compilerControlsHost("left-pipeline-controls"));
        }
        return sidebar;
    }

    private HBox sidebarCollapseBar(String text, String glyph, Runnable collapseAction) {
        HBox bar = new HBox(6);
        bar.getStyleClass().add("sidebar-collapse-bar");
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-collapse-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button collapse = new Button(glyph);
        collapse.getStyleClass().add("sidebar-collapse-button");
        collapse.setTooltip(new Tooltip("收起 " + text));
        collapse.setOnAction(event -> collapseAction.run());
        bar.getChildren().addAll(label, spacer, collapse);
        return bar;
    }

    private VBox sidebarRail(String styleClass, String text, Runnable expandAction) {
        VBox rail = new VBox(8);
        rail.getStyleClass().add("sidebar-rail");
        rail.getStyleClass().add(styleClass);
        Button expand = new Button(">");
        expand.getStyleClass().add("sidebar-rail-toggle");
        expand.setTooltip(new Tooltip("展开 " + text));
        expand.setOnAction(event -> expandAction.run());
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-rail-label");
        rail.getChildren().addAll(expand, label);
        return rail;
    }

    private VBox compilerControlsHost(String styleClass) {
        VBox host = new VBox(6);
        host.getStyleClass().add("compiler-controls-host");
        host.getStyleClass().add(styleClass);
        HBox header = new HBox(6);
        header.getStyleClass().add("compiler-controls-dock-bar");
        Label title = new Label("控制台");
        title.getStyleClass().add("compiler-controls-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(
                title,
                spacer,
                dockButton("右", "RIGHT_METADATA_TOP"),
                dockButton("左", "LEFT_PIPELINE_BOTTOM"),
                dockButton("浮", "FLOATING")
        );
        host.getChildren().addAll(header, new MiniCCompilerControlsView(viewModel, controlHub));
        return host;
    }

    private Button dockButton(String text, String dock) {
        Button button = new Button(text);
        button.getStyleClass().add("compiler-controls-dock-button");
        if (dock.equals(compilerControlsDock)) {
            button.getStyleClass().add("active");
        }
        button.setOnAction(event -> setCompilerControlsDock(dock));
        return button;
    }

    private void setCompilerControlsDock(String dock) {
        compilerControlsDock = normalizeCompilerControlsDock(dock);
        MiniCSettings.setCompilerControlsDock(compilerControlsDock);
        rebuildWorkbenchBody();
    }

    private String normalizeCompilerControlsDock(String dock) {
        if ("LEFT_PIPELINE_BOTTOM".equals(dock) || "FLOATING".equals(dock)) {
            return dock;
        }
        return "RIGHT_METADATA_TOP";
    }

    private void setPipelineLeftSidebarCollapsed(boolean collapsed) {
        pipelineLeftSidebarCollapsed = collapsed;
        MiniCSettings.setPipelineLeftSidebarCollapsed(collapsed);
        rebuildWorkbenchBody();
    }

    private void setPipelineRightSidebarCollapsed(boolean collapsed) {
        pipelineRightSidebarCollapsed = collapsed;
        MiniCSettings.setPipelineRightSidebarCollapsed(collapsed);
        rebuildWorkbenchBody();
    }

    void setCompilerControlsDockForTesting(String dock) {
        setCompilerControlsDock(dock);
    }

    void setPipelineLeftSidebarCollapsedForTesting(boolean collapsed) {
        setPipelineLeftSidebarCollapsed(collapsed);
    }

    void setPipelineRightSidebarCollapsedForTesting(boolean collapsed) {
        setPipelineRightSidebarCollapsed(collapsed);
    }

    void setCompilerControlsFloatingRectForTesting(double x, double y, double width, double height) {
        MiniCSettings.setCompilerControlsFloatingRect(new MiniCSettings.FloatingRect(x, y, width, height));
        renderFloatingCompilerControls();
    }

    private VBox editorArea() {
        VBox editor = new VBox();
        editor.getStyleClass().add("editor-area");
        editor.setMinWidth(0);
        editor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editor, Priority.ALWAYS);

        workspaceSplit = new SplitPane();
        workspaceSplit.getStyleClass().add("split");
        workspaceSplit.setOrientation(Orientation.HORIZONTAL);
        workspaceSplit.setMinWidth(0);
        workspaceSplit.setMaxWidth(Double.MAX_VALUE);
        workspaceHost = new StackPane(workspaceSplit);
        workspaceHost.getStyleClass().add("workspace-host");
        workspaceHost.setMinWidth(0);
        workspaceHost.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(workspaceHost, Priority.ALWAYS);
        VBox.setVgrow(workspaceSplit, Priority.ALWAYS);
        rebuildWorkspaceSplit();
        renderFloatingCompilerControls();
        controlHub.setActiveTrackingAction(new MiniCActiveTrackingService(this::activeViewportAdapters)::trackActiveViewports);

        editor.getChildren().addAll(workspaceHost, new MiniCBottomPanel(hoverInspector));
        return editor;
    }

    private void rebuildWorkspaceSplit() {
        if (workspaceSplit == null) {
            return;
        }
        sourceLoader = null;
        visualPane = null;
        tabs = workspaceTabs(false);
        mainContent = new StackPane(workspaceContent(activeLeftWorkspaceTab(), true));
        mainContent.getStyleClass().add("workspace-content");
        mainContent.setMinWidth(0);
        mainContent.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        VBox leftGroup = workspaceGroup(tabs, mainContent);
        List<Node> groups = new ArrayList<>();
        groups.add(leftGroup);
        if (!rightWorkspaceTabIds.isEmpty()) {
            if (activeRightWorkspaceTabId == null || findWorkspaceTab(activeRightWorkspaceTabId) == null) {
                activeRightWorkspaceTabId = rightWorkspaceTabIds.stream().findFirst().orElse(null);
            }
            rightTabs = workspaceTabs(true);
            rightMainContent = new StackPane(workspaceContent(activeRightWorkspaceTab(), false));
            rightMainContent.getStyleClass().add("workspace-content");
            rightMainContent.setMinWidth(0);
            rightMainContent.setMaxWidth(Double.MAX_VALUE);
            VBox.setVgrow(rightMainContent, Priority.ALWAYS);
            groups.add(workspaceGroup(rightTabs, rightMainContent));
        } else {
            rightTabs = null;
            rightMainContent = null;
            activeRightWorkspaceTabId = null;
        }
        workspaceSplit.getItems().setAll(groups);
        if (groups.size() == 2) {
            workspaceSplit.setDividerPositions(0.5);
        }
    }

    private void renderFloatingCompilerControls() {
        if (workspaceHost == null) {
            return;
        }
        workspaceHost.getChildren().removeIf(node -> node.getStyleClass().contains("floating-compiler-controls"));
        if (!"FLOATING".equals(compilerControlsDock)) {
            return;
        }
        VBox host = compilerControlsHost("floating-compiler-controls");
        host.setManaged(false);
        MiniCSettings.FloatingRect rect = MiniCSettings.compilerControlsFloatingRect();
        host.setMinSize(rect.width(), rect.height());
        host.setPrefSize(rect.width(), rect.height());
        host.setMaxSize(rect.width(), rect.height());
        positionFloatingCompilerControls(host, rect);
        workspaceHost.widthProperty().addListener((observable, oldValue, newValue) ->
                positionFloatingCompilerControls(host, MiniCSettings.compilerControlsFloatingRect()));
        workspaceHost.heightProperty().addListener((observable, oldValue, newValue) ->
                positionFloatingCompilerControls(host, MiniCSettings.compilerControlsFloatingRect()));
        double[] dragOffset = new double[2];
        host.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            dragOffset[0] = event.getX();
            dragOffset[1] = event.getY();
        });
        host.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            Point2D point = workspaceHost.sceneToLocal(host.localToScene(event.getX(), event.getY()));
            MiniCSettings.FloatingRect next = new MiniCSettings.FloatingRect(
                    point.getX() - dragOffset[0],
                    point.getY() - dragOffset[1],
                    rect.width(),
                    rect.height()
            );
            MiniCSettings.setCompilerControlsFloatingRect(next);
            positionFloatingCompilerControls(host, next);
        });
        workspaceHost.getChildren().add(host);
    }

    private void positionFloatingCompilerControls(Region host, MiniCSettings.FloatingRect rect) {
        double width = Math.max(1, rect.width());
        double height = Math.max(1, rect.height());
        double maxX = Math.max(0, workspaceHost.getWidth() - width);
        double maxY = Math.max(0, workspaceHost.getHeight() - height);
        double x = Math.max(0, Math.min(maxX, rect.x()));
        double y = Math.max(0, Math.min(maxY, rect.y()));
        host.setMinSize(width, height);
        host.setPrefSize(width, height);
        host.setMaxSize(width, height);
        host.relocate(x, y);
    }

    private VBox workspaceGroup(HBox tabBar, StackPane content) {
        VBox group = new VBox();
        group.getStyleClass().add("workspace-group");
        group.setMinWidth(0);
        group.setMaxWidth(Double.MAX_VALUE);
        group.getChildren().addAll(tabBar, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return group;
    }

    private Node workspaceContent(WorkspaceTab tab, boolean primary) {
        if (tab.kind() == WorkspaceTabKind.SOURCE) {
            return sourceArea(tab.viewModel(), primary);
        }
        MiniCVisualPane pane = new MiniCVisualPane(viewModelForTab(tab), hoverInspector, tab.stage(), tab.side());
        pane.installViewportTargets(controlHub);
        pane.setMinWidth(0);
        pane.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> pauseIfPlaying(tab.viewModel()));
        if (primary) {
            visualPane = pane;
        }
        return pane;
    }

    private VBox sourceArea(MiniCWorkbenchViewModel model, boolean primary) {
        VBox sourceArea = new VBox();
        sourceArea.getStyleClass().add("source-area");
        MiniCSourceLoaderView loader = new MiniCSourceLoaderView(
                model,
                this::openDocument,
                () -> saveDocument(model),
                () -> saveDocumentAs(model)
        );
        loader.usePersistentEditorScrollBars("pipeline-source-editor-scroll");
        loader.installViewportTarget(controlHub);
        sourceArea.getChildren().add(loader);
        VBox.setVgrow(loader, Priority.ALWAYS);
        if (primary) {
            sourceLoader = loader;
        }
        return sourceArea;
    }

    private List<MiniCViewportAdapter> activeViewportAdapters() {
        WorkspaceTab active = activeLeftWorkspaceTab();
        if (active.kind() == WorkspaceTabKind.SOURCE && sourceLoader != null) {
            return List.of(sourceLoader.viewportAdapter());
        }
        if (active.kind() == WorkspaceTabKind.STAGE && visualPane != null) {
            return visualPane.activeViewportAdapters();
        }
        return List.of();
    }

    private void pauseIfPlaying(MiniCWorkbenchViewModel model) {
        var state = model.currentStateProperty().get();
        if (state != null && !"PAUSED".equals(state.playbackMode())) {
            model.pause();
        }
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
        if (workspaceSplit != null) {
            rebuildWorkspaceSplit();
        }
    }

    private HBox workspaceTabs(boolean rightGroup) {
        HBox tabBar = new HBox();
        tabBar.getStyleClass().add("tabs");
        for (WorkspaceTab workspaceTab : workspaceTabsForGroup(rightGroup)) {
            tabBar.getChildren().add(workspaceTabNode(workspaceTab, rightGroup));
        }
        if (!rightGroup) {
            tabBar.getChildren().add(toolbarButton("+", "新建文件", this::newDocument));
        }
        return tabBar;
    }

    private List<WorkspaceTab> workspaceTabsForGroup(boolean rightGroup) {
        return allWorkspaceTabs().stream()
                .filter(tab -> rightWorkspaceTabIds.contains(tab.id()) == rightGroup)
                .toList();
    }

    private HBox workspaceTabNode(WorkspaceTab workspaceTab, boolean rightGroup) {
        HBox tab = new HBox();
        tab.getStyleClass().add("tab");
        Label title = new Label(workspaceTab.kind() == WorkspaceTabKind.SOURCE
                ? "C  " + workspaceTab.title()
                : workspaceTab.title());
        title.getStyleClass().add("tab-title");
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        Label split = new Label(rightGroup ? "<" : ">");
        split.getStyleClass().add("tab-split");
        split.setTooltip(new Tooltip(rightGroup ? "移回左侧" : "向右拆分"));
        Label close = new Label("×");
        close.getStyleClass().add("tab-close");
        boolean active = rightGroup
                ? workspaceTab.id().equals(activeRightWorkspaceTabId)
                : workspaceTab.id().equals(activeLeftWorkspaceTabId);
        if (active) {
            tab.getStyleClass().add("active");
        }
        tab.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && workspaceTab.kind() == WorkspaceTabKind.SOURCE) {
                int documentIndex = documentIndex(workspaceTab.viewModel());
                beginRenameDocument(documentIndex, tab, title);
                event.consume();
                return;
            }
            switchWorkspaceTab(workspaceTab.id(), rightGroup);
        });
        if (workspaceTab.kind() == WorkspaceTabKind.SOURCE) {
            int tabIndex = documentIndex(workspaceTab.viewModel());
            tab.setOnDragDetected(event -> {
                draggedTabIndex = tabIndex;
                tab.startFullDrag();
                event.consume();
            });
            tab.setOnMouseDragEntered(event -> {
                reorderDraggedTab(tabIndex);
                event.consume();
            });
        }
        split.setOnMouseClicked(event -> {
            if (rightGroup) {
                moveWorkspaceTabLeft(workspaceTab.id());
            } else {
                splitWorkspaceTabRight(workspaceTab.id());
            }
            event.consume();
        });
        close.setOnMouseClicked(event -> {
            closeWorkspaceTab(workspaceTab.id());
            event.consume();
        });
        tab.getChildren().addAll(title, split, close);
        return tab;
    }

    private Button toolbarButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("tab-action");
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    private List<WorkspaceTab> allWorkspaceTabs() {
        ArrayList<WorkspaceTab> tabs = new ArrayList<>();
        for (DocumentTab document : documents) {
            tabs.add(sourceWorkspaceTab(document));
        }
        tabs.addAll(stageTabs);
        return tabs;
    }

    private WorkspaceTab sourceWorkspaceTab(DocumentTab document) {
        return new WorkspaceTab(
                sourceTabId(document),
                document.displayName(),
                WorkspaceTabKind.SOURCE,
                document.viewModel(),
                "source",
                MiniCVisualPane.VisualSide.BOTH
        );
    }

    private WorkspaceTab activeLeftWorkspaceTab() {
        WorkspaceTab found = findWorkspaceTab(activeLeftWorkspaceTabId);
        if (found != null) {
            return found;
        }
        WorkspaceTab first = allWorkspaceTabs().getFirst();
        activeLeftWorkspaceTabId = first.id();
        activeDocumentIndex = Math.max(0, documentIndex(first.viewModel()));
        return first;
    }

    private WorkspaceTab activeRightWorkspaceTab() {
        WorkspaceTab found = findWorkspaceTab(activeRightWorkspaceTabId);
        if (found != null) {
            return found;
        }
        String fallback = rightWorkspaceTabIds.stream().findFirst().orElse(null);
        activeRightWorkspaceTabId = fallback;
        return findWorkspaceTab(fallback);
    }

    private WorkspaceTab findWorkspaceTab(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return allWorkspaceTabs().stream()
                .filter(tab -> tab.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private MiniCWorkbenchViewModel viewModelForTab(WorkspaceTab tab) {
        return tab == null ? documents.get(activeDocumentIndex).viewModel() : tab.viewModel();
    }

    private DocumentTab documentFor(MiniCWorkbenchViewModel model) {
        return documents.stream()
                .filter(document -> document.viewModel() == model)
                .findFirst()
                .orElse(null);
    }

    private String sourceTabId(DocumentTab document) {
        return "source:" + System.identityHashCode(document.viewModel());
    }

    private String stageTabId(DocumentTab document, MiniCVisualPane.VisualSide side) {
        return sourceTabId(document) + ":pipeline:" + side.id();
    }

    private void switchWorkspaceTab(String id, boolean rightGroup) {
        WorkspaceTab tab = findWorkspaceTab(id);
        if (tab == null) {
            return;
        }
        int previousDocumentIndex = activeDocumentIndex;
        if (rightGroup) {
            activeRightWorkspaceTabId = id;
        } else {
            activeLeftWorkspaceTabId = id;
            int documentIndex = documentIndex(tab.viewModel());
            if (documentIndex >= 0) {
                activeDocumentIndex = documentIndex;
            }
        }
        if (body != null) {
            if (previousDocumentIndex == activeDocumentIndex) {
                refreshTabs();
            } else {
                body.getChildren().clear();
                rebuildWorkbenchBody();
            }
        }
    }

    private void splitWorkspaceTabRight(String id) {
        WorkspaceTab tab = findWorkspaceTab(id);
        if (tab == null) {
            return;
        }
        rightWorkspaceTabIds.add(id);
        activeRightWorkspaceTabId = id;
        if (id.equals(activeLeftWorkspaceTabId)) {
            activeLeftWorkspaceTabId = allWorkspaceTabs().stream()
                    .filter(candidate -> !rightWorkspaceTabIds.contains(candidate.id()))
                    .map(WorkspaceTab::id)
                    .findFirst()
                    .orElse(id);
        }
        normalizeWorkspaceGroups();
        refreshTabs();
    }

    private void moveWorkspaceTabLeft(String id) {
        if (findWorkspaceTab(id) == null) {
            return;
        }
        rightWorkspaceTabIds.remove(id);
        activeLeftWorkspaceTabId = id;
        if (id.equals(activeRightWorkspaceTabId)) {
            activeRightWorkspaceTabId = rightWorkspaceTabIds.stream().findFirst().orElse(null);
        }
        normalizeWorkspaceGroups();
        refreshTabs();
    }

    private void normalizeWorkspaceGroups() {
        rightWorkspaceTabIds.removeIf(id -> findWorkspaceTab(id) == null);
        if (!rightWorkspaceTabIds.isEmpty()) {
            boolean leftEmpty = allWorkspaceTabs().stream()
                    .noneMatch(tab -> !rightWorkspaceTabIds.contains(tab.id()));
            if (leftEmpty) {
                String promoted = activeRightWorkspaceTabId != null && findWorkspaceTab(activeRightWorkspaceTabId) != null
                        ? activeRightWorkspaceTabId
                        : rightWorkspaceTabIds.stream().findFirst().orElse(sourceTabId(documents.get(activeDocumentIndex)));
                rightWorkspaceTabIds.clear();
                activeLeftWorkspaceTabId = promoted;
                activeRightWorkspaceTabId = null;
                return;
            }
        }
        if (rightWorkspaceTabIds.isEmpty()) {
            activeRightWorkspaceTabId = null;
        } else if (activeRightWorkspaceTabId == null
                || findWorkspaceTab(activeRightWorkspaceTabId) == null
                || !rightWorkspaceTabIds.contains(activeRightWorkspaceTabId)) {
            activeRightWorkspaceTabId = rightWorkspaceTabIds.stream().findFirst().orElse(null);
        }
        if (activeLeftWorkspaceTabId == null
                || findWorkspaceTab(activeLeftWorkspaceTabId) == null
                || rightWorkspaceTabIds.contains(activeLeftWorkspaceTabId)) {
            activeLeftWorkspaceTabId = allWorkspaceTabs().stream()
                    .filter(tab -> !rightWorkspaceTabIds.contains(tab.id()))
                    .map(WorkspaceTab::id)
                    .findFirst()
                    .orElse(sourceTabId(documents.get(activeDocumentIndex)));
        }
    }

    void splitWorkspaceTabRightForTesting(String id) {
        splitWorkspaceTabRight(id);
    }

    void moveWorkspaceTabLeftForTesting(String id) {
        moveWorkspaceTabLeft(id);
    }

    private void closeWorkspaceTab(String id) {
        WorkspaceTab tab = findWorkspaceTab(id);
        if (tab == null) {
            return;
        }
        if (tab.kind() == WorkspaceTabKind.SOURCE) {
            closeDocument(documentIndex(tab.viewModel()));
            return;
        }
        stageTabs.removeIf(candidate -> candidate.id().equals(id));
        rightWorkspaceTabIds.remove(id);
        if (id.equals(activeLeftWorkspaceTabId)) {
            activeLeftWorkspaceTabId = allWorkspaceTabs().stream()
                    .filter(candidate -> !rightWorkspaceTabIds.contains(candidate.id()))
                    .map(WorkspaceTab::id)
                    .findFirst()
                    .orElse(sourceTabId(documents.get(activeDocumentIndex)));
        }
        if (id.equals(activeRightWorkspaceTabId)) {
            activeRightWorkspaceTabId = rightWorkspaceTabIds.stream().findFirst().orElse(null);
        }
        normalizeWorkspaceGroups();
        refreshTabs();
    }

    private void openStageTabs(String stage) {
        String normalizedStage = stage == null || stage.isBlank() ? "source" : stage;
        DocumentTab document = documents.get(activeDocumentIndex);
        document.viewModel().selectVisualStage(normalizedStage);
        if ("source".equals(normalizedStage)) {
            switchWorkspaceTab(sourceTabId(document), false);
            return;
        }
        boolean changed = ensurePipelineTabs(document);
        if (MiniCSettings.autoSplitPipelineTabs()) {
            String beforeId = stageTabId(document, MiniCVisualPane.VisualSide.BEFORE);
            String afterId = stageTabId(document, MiniCVisualPane.VisualSide.AFTER);
            changed |= rightWorkspaceTabIds.add(afterId);
            if (!Objects.equals(activeLeftWorkspaceTabId, beforeId)) {
                activeLeftWorkspaceTabId = beforeId;
                changed = true;
            }
            if (!Objects.equals(activeRightWorkspaceTabId, afterId)) {
                activeRightWorkspaceTabId = afterId;
                changed = true;
            }
        }
        if (changed) {
            refreshTabs();
        }
    }

    private WorkspaceTab ensureStageTab(DocumentTab document, MiniCVisualPane.VisualSide side) {
        String id = stageTabId(document, side);
        WorkspaceTab existing = findWorkspaceTab(id);
        if (existing != null) {
            return existing;
        }
        WorkspaceTab created = new WorkspaceTab(
                id,
                document.displayName() + " " + side.label(),
                WorkspaceTabKind.STAGE,
                document.viewModel(),
                "",
                side
        );
        stageTabs.add(created);
        return created;
    }

    private boolean ensurePipelineTabs(DocumentTab document) {
        boolean changed = findWorkspaceTab(stageTabId(document, MiniCVisualPane.VisualSide.BEFORE)) == null
                || findWorkspaceTab(stageTabId(document, MiniCVisualPane.VisualSide.AFTER)) == null;
        WorkspaceTab before = ensureStageTab(document, MiniCVisualPane.VisualSide.BEFORE);
        WorkspaceTab after = ensureStageTab(document, MiniCVisualPane.VisualSide.AFTER);
        if (changed && MiniCSettings.autoSplitPipelineTabs()) {
            changed |= rightWorkspaceTabIds.add(after.id());
            if (!Objects.equals(activeLeftWorkspaceTabId, before.id())) {
                activeLeftWorkspaceTabId = before.id();
                changed = true;
            }
            if (!Objects.equals(activeRightWorkspaceTabId, after.id())) {
                activeRightWorkspaceTabId = after.id();
                changed = true;
            }
        }
        return changed;
    }

    private void updatePipelineTabs(MiniCWorkbenchViewModel model) {
        DocumentTab document = documentFor(model);
        if (document == null) {
            return;
        }
        if (pipelineCompleted(model)) {
            if (closePipelineTabs(document)) {
                refreshTabs();
            }
            return;
        }
        if (model.currentStateProperty().get() != null && ensurePipelineTabs(document)) {
            refreshTabs();
        }
    }

    private boolean pipelineCompleted(MiniCWorkbenchViewModel model) {
        var state = model.currentStateProperty().get();
        var result = model.lastControlResultProperty().get();
        if (state == null || result == null) {
            return false;
        }
        if (!"execution".equals(state.currentStage()) || !"execution".equals(result.stage()) || state.canNext()) {
            return false;
        }
        return "STAGE_COMPLETED".equals(result.outcome())
                || "FAILED".equals(result.outcome())
                || "CANNOT_ADVANCE".equals(result.outcome());
    }

    private boolean closePipelineTabs(DocumentTab document) {
        String beforeId = stageTabId(document, MiniCVisualPane.VisualSide.BEFORE);
        String afterId = stageTabId(document, MiniCVisualPane.VisualSide.AFTER);
        boolean changed = stageTabs.removeIf(tab -> tab.id().equals(beforeId) || tab.id().equals(afterId));
        changed |= rightWorkspaceTabIds.remove(beforeId);
        changed |= rightWorkspaceTabIds.remove(afterId);
        if (beforeId.equals(activeLeftWorkspaceTabId) || afterId.equals(activeLeftWorkspaceTabId)) {
            activeLeftWorkspaceTabId = sourceTabId(document);
            changed = true;
        }
        if (beforeId.equals(activeRightWorkspaceTabId) || afterId.equals(activeRightWorkspaceTabId)) {
            activeRightWorkspaceTabId = rightWorkspaceTabIds.stream().findFirst().orElse(null);
            changed = true;
        }
        return changed;
    }

    private String stageName(String stage) {
        return switch (stage) {
            case "source" -> "源码";
            case "preprocess" -> "预编译";
            case "lexer" -> "词法分析";
            case "parser" -> "语法分析";
            case "semantic" -> "语义分析";
            case "ir" -> "IR 降级";
            case "codegen" -> "代码生成";
            case "toolchain" -> "工具链";
            case "execution" -> "执行";
            default -> stage;
        };
    }

    void openStageTabsForTesting(String stage) {
        openStageTabs(stage);
    }

    List<String> workspaceTabTitlesForTesting() {
        return allWorkspaceTabs().stream()
                .map(WorkspaceTab::title)
                .toList();
    }

    String activeLeftWorkspaceTabTitleForTesting() {
        return activeLeftWorkspaceTab().title();
    }

    java.util.Optional<String> activeRightWorkspaceTabTitleForTesting() {
        WorkspaceTab right = activeRightWorkspaceTab();
        return right == null ? java.util.Optional.empty() : java.util.Optional.of(right.title());
    }

    private void switchDocument(int index) {
        if (index < 0 || index >= documents.size() || index == activeDocumentIndex) {
            return;
        }
        syncActiveEditorToModel();
        activeDocumentIndex = index;
        activeLeftWorkspaceTabId = sourceTabId(documents.get(activeDocumentIndex));
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
        stageTabs.removeIf(tab -> tab.viewModel() == closed.viewModel());
        rightWorkspaceTabIds.removeIf(id -> findWorkspaceTab(id) == null);
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
        activeLeftWorkspaceTabId = sourceTabId(documents.get(activeDocumentIndex));
        activeRightWorkspaceTabId = rightWorkspaceTabIds.stream().findFirst().orElse(null);
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
        saveDocument(activeDocumentIndex);
    }

    private void saveDocument(MiniCWorkbenchViewModel model) {
        int index = documentIndex(model);
        if (index >= 0) {
            saveDocument(index);
        }
    }

    private void saveDocument(int documentIndex) {
        DocumentTab document = documents.get(documentIndex);
        Path path = document.path();
        if (path == null) {
            saveDocumentAs(documentIndex);
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
        saveDocumentAs(activeDocumentIndex);
    }

    private void saveDocumentAs(MiniCWorkbenchViewModel model) {
        int index = documentIndex(model);
        if (index >= 0) {
            saveDocumentAs(index);
        }
    }

    private void saveDocumentAs(int documentIndex) {
        DocumentTab document = documents.get(documentIndex);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("另存为 MiniC 源文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MiniC 源文件 (*.mc)", "*.mc"));
        chooser.setInitialFileName(document.displayName());
        applyRememberedDirectory(chooser);
        java.io.File file = chooser.showSaveDialog(window());
        if (file == null) {
            return;
        }
        saveDocumentAs(documentIndex, file.toPath());
    }

    void saveDocumentAsForTesting(Path path) {
        syncActiveEditorToModel();
        saveDocumentAs(activeDocumentIndex, path);
    }

    private void saveDocumentAs(Path rawPath) {
        saveDocumentAs(activeDocumentIndex, rawPath);
    }

    private void saveDocumentAs(int documentIndex, Path rawPath) {
        Path path = normalizePath(rawPath);
        DocumentTab document = documents.get(documentIndex);
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
        documents.set(documentIndex, saved);
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
        model.currentStateProperty().addListener((observable, oldValue, newValue) -> updatePipelineTabs(model));
        model.lastControlResultProperty().addListener((observable, oldValue, newValue) -> updatePipelineTabs(model));
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

    private int documentIndex(MiniCWorkbenchViewModel model) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).viewModel() == model) {
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

    private record WorkspaceTab(
            String id,
            String title,
            WorkspaceTabKind kind,
            MiniCWorkbenchViewModel viewModel,
            String stage,
            MiniCVisualPane.VisualSide side
    ) {
    }

    private enum WorkspaceTabKind {
        SOURCE,
        STAGE
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
