package minic.ui;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    private int activeDocumentIndex;

    /**
     * 创建工作台外壳。
     *
     * @param viewModel UI 状态模型
     */
    public MiniCWorkbenchShell(MiniCWorkbenchViewModel viewModel) {
        addDocument("untitled-1.mc", "", null, Objects.requireNonNull(viewModel, "viewModel"));
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
        root.setCenter(workbenchBody());
        root.setBottom(statusBar());
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
        return root;
    }

    private VBox activityBar() {
        VBox activityBar = new VBox(6);
        activityBar.getStyleClass().add("activity-bar");
        lockWidth(activityBar, ACTIVITY_BAR_WIDTH);
        activityBar.getChildren().addAll(
                activityItem("▣", true),
                activityItem("⌕", false),
                activityItem("⑂", false),
                activityItem("▷", false),
                activityItem("⚙", false)
        );
        return activityBar;
    }

    private Label activityItem(String text, boolean active) {
        Label label = new Label(text);
        label.getStyleClass().add("activity-item");
        if (active) {
            label.getStyleClass().add("active");
        }
        return label;
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
        return !viewModel.sessionStartedProperty().get()
                || viewModel.currentStateProperty().get() == null
                || "source".equals(viewModel.currentStateProperty().get().currentStage());
    }

    private VBox sourceArea() {
        VBox sourceArea = new VBox();
        sourceArea.getStyleClass().add("source-area");
        MiniCSourceLoaderView loader = new MiniCSourceLoaderView(viewModel, this::openDocument, this::saveDocument);
        sourceArea.getChildren().add(loader);
        VBox.setVgrow(loader, Priority.ALWAYS);
        return sourceArea;
    }

    private HBox statusBar() {
        HBox status = new HBox();
        status.getStyleClass().add("status-bar");
        Label left = new Label("MiniC Visual Workbench · VS Code style");
        Label right = new Label("C030 · Shell");
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
            Label tab = new Label("C  " + document.displayName());
            tab.getStyleClass().add("tab");
            if (index == activeDocumentIndex) {
                tab.getStyleClass().add("active");
            }
            int tabIndex = index;
            tab.setOnMouseClicked(event -> switchDocument(tabIndex));
            tabs.getChildren().add(tab);
        }
        tabs.getChildren().add(toolbarButton("+", "New file", this::newDocument));
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

    private void newDocument() {
        int nextIndex = documents.size() + 1;
        addDocument("untitled-" + nextIndex + ".mc", "", null, new MiniCWorkbenchViewModel());
        switchDocument(documents.size() - 1);
    }

    private void openDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open MiniC source");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MiniC source (*.mc)", "*.mc"));
        java.io.File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            Path path = file.toPath();
            addDocument(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8), path, new MiniCWorkbenchViewModel());
            switchDocument(documents.size() - 1);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot open source file: " + file, exception);
        }
    }

    private void saveDocument() {
        DocumentTab document = documents.get(activeDocumentIndex);
        Path path = document.path();
        if (path == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save MiniC source");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MiniC source (*.mc)", "*.mc"));
            chooser.setInitialFileName(document.name());
            java.io.File file = chooser.showSaveDialog(window());
            if (file == null) {
                return;
            }
            path = file.toPath();
            document = document.withPath(path);
            documents.set(activeDocumentIndex, document);
            refreshTabs();
        }
        try {
            Files.writeString(path, document.viewModel().sourceTextProperty().get(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot save source file: " + path, exception);
        }
    }

    private Window window() {
        return root == null || root.getScene() == null ? null : root.getScene().getWindow();
    }

    private void addDocument(String name, String source, Path path, MiniCWorkbenchViewModel model) {
        model.loadSource(name, source);
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
            String sourceName = viewModel.sourceNameProperty().get();
            return sourceName == null || sourceName.isBlank() ? name : sourceName;
        }

        private DocumentTab withPath(Path path) {
            return new DocumentTab(path.getFileName().toString(), path, viewModel);
        }
    }
}
