package craken.ui.editor;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import craken.ui.component.editor.UiCodeEditor;
import craken.ui.component.feedback.UiMessageDialog;
import craken.ui.component.feedback.UiTooltip;
import craken.ui.component.layout.UiWorkspace;
import craken.ui.component.layout.UiWorkspacePane;
import craken.ui.interaction.terminal.TerminalPanel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** 文件、标签与编辑容器的统一生命周期；在 JavaFX 线程使用，框架仅挂载 tabBar() 和本区域。 */
public final class EditorArea extends StackPane {
    public enum CloseChoice { SAVE, DISCARD, CANCEL }
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private final ObservableList<EditorFile> files = FXCollections.observableArrayList();
    private final ObservableList<EditorFile> readOnlyFiles = FXCollections.unmodifiableObservableList(files);
    private final ReadOnlyObjectWrapper<EditorFile> active = new ReadOnlyObjectWrapper<>();
    private final StackPane rootHost = new StackPane();
    private final EditorTabBar tabBar = new EditorTabBar(this);
    private final EventHandler<KeyEvent> shortcuts = this::handleShortcut;
    private Function<EditorFile, CloseChoice> closeDecision = this::askClose;

    public EditorArea() {
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        getStyleClass().add("editor-area");
        Node empty = createEmptyView();
        empty.visibleProperty().bind(Bindings.isEmpty(files));
        empty.managedProperty().bind(empty.visibleProperty());
        rootHost.setMinSize(0, 0);
        getChildren().addAll(rootHost, empty);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        sceneProperty().addListener((observable, previous, current) -> {
            if (previous != null) previous.removeEventFilter(KeyEvent.KEY_PRESSED, shortcuts);
            if (current != null) current.addEventFilter(KeyEvent.KEY_PRESSED, shortcuts);
        });
        active.addListener(ignored -> updateActiveBorder());
    }

    public Node tabBar() { return tabBar; }
    /** 当前整棵根编辑区；无标签时返回 null。 */
    public UiWorkspace workspace() { return active.get() == null ? null : active.get().root(); }
    public ObservableList<EditorFile> files() { return readOnlyFiles; }
    public EditorFile activeFile() { return active.get(); }
    /**
     * 当前键盘焦点所在的编辑器文件；焦点不在任何编辑器时退回活动文件。
     * Pipeline/调试用它选目标，避免焦点与活动标记短暂不同步时跑错文件。
     */
    public EditorFile focusedFile() {
        if (getScene() != null && getScene().getFocusOwner() != null) {
            for (Node node = getScene().getFocusOwner(); node != null; node = node.getParent()) {
                if (node.getUserData() instanceof EditorFile file && files.contains(file)) return file;
            }
        }
        return active.get();
    }
    public ReadOnlyObjectProperty<EditorFile> activeFileProperty() { return active.getReadOnlyProperty(); }
    public UiCodeEditor editor() { return active.get() == null ? null : active.get().editor(); }

    /** 普通打开创建新标签和独立根编辑区；同一文件共享文本，不共享布局。 */
    public EditorFile openFile(Path path) throws IOException {
        UiWorkspace root = new UiWorkspace();
        EditorFile file = loadFile(path, root);
        root.show(file.pane(), null);
        select(file);
        return file;
    }

    public EditorFile createFile(Path path) throws IOException {
        Files.createFile(path); // 不覆盖同名磁盘文件。
        return openFile(path);
    }

    public EditorFile openFileBeside(Path path, EditorFile target, Orientation orientation) throws IOException {
        requireOpen(target);
        Objects.requireNonNull(orientation, "orientation");
        EditorFile file = loadFile(path, target.root());
        target.root().split(target.pane(), file.pane(), orientation, false);
        select(file);
        return file;
    }

    public EditorFile splitFile(EditorFile target, Orientation orientation) {
        requireOpen(target);
        Objects.requireNonNull(orientation, "orientation");
        EditorFile file = attachFile(target.path(), null, target, target.root());
        target.root().split(target.pane(), file.pane(), orientation, false);
        select(file);
        return file;
    }

    private EditorFile loadFile(Path path, UiWorkspace root) throws IOException {
        Path canonical = Objects.requireNonNull(path).toRealPath();
        EditorFile existing = files.stream().filter(file -> file.path().equals(canonical)).findFirst().orElse(null);
        String source = existing == null ? Files.readString(canonical, StandardCharsets.UTF_8) : null;
        return attachFile(canonical, source, existing, root);
    }

    private EditorFile attachFile(Path path, String source, EditorFile original, UiWorkspace root) {
        UiCodeEditor editor = original == null ? new UiCodeEditor(source) : UiCodeEditor.linkedTo(original.editor());
        UiWorkspacePane pane = root.createPane(null);
        EditorFile file = original == null ? new EditorFile(path, source, editor, pane, root)
                : new EditorFile(original, editor, pane, root);
        // 点击编辑器内部走的是 Swing 事件；活动文件必须跟着用户真正操作的那块走。
        editor.setOnUserActivated(() -> active.set(file));
        pane.setUserData(file);
        pane.getStyleClass().add("editor-file-pane");
        pane.setBorderWidth(1);
        pane.setContent(createFileView(file));
        pane.setOnCloseRequest(() -> prepareClose(file));
        pane.setOnClosed(() -> fileClosed(file));
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> active.set(file));
        files.add(file);
        return file;
    }

    public void select(EditorFile file) {
        requireOpen(file);
        if (rootHost.getChildren().isEmpty() || rootHost.getChildren().getFirst() != file.root()) {
            rootHost.getChildren().setAll(file.root());
        }
        if (active.get() != null && active.get() != file) active.get().editor().hideCaretList();
        active.set(file);
        updateActiveBorder();
        file.editor().requestEditorFocus();
    }

    public boolean closeFile(EditorFile file) {
        if (file != null && file.pane().isClosed()) return false;
        requireOpen(file);
        return file.pane().close();
    }

    public boolean closeAll() {
        for (EditorFile file : List.copyOf(files)) if (!closeFile(file)) return false;
        return true;
    }

    public void setCloseDecisionHandler(Function<EditorFile, CloseChoice> handler) {
        closeDecision = Objects.requireNonNull(handler);
    }

    private void fileClosed(EditorFile file) {
        int index = files.indexOf(file);
        boolean wasActive = active.get() == file;
        files.remove(file);
        file.dispose();
        if (wasActive) {
            active.set(null);
            if (!files.isEmpty()) {
                EditorFile next = files.stream().filter(item -> item.root() == file.root())
                        .findFirst().orElse(files.get(Math.min(index, files.size() - 1)));
                select(next);
            } else {
                rootHost.getChildren().clear();
            }
        }
        updateActiveBorder();
    }

    private void updateActiveBorder() {
        files.forEach(file -> file.pane().pseudoClassStateChanged(ACTIVE,
                file == active.get() && file.root().panes().size() > 1));
    }

    private boolean prepareClose(EditorFile file) {
        if (file.viewCount() > 1 || !file.isDirty()) return true;
        return switch (closeDecision.apply(file)) {
            case CANCEL -> false;
            case DISCARD -> true;
            case SAVE -> save(file);
        };
    }

    public boolean save(EditorFile file) {
        requireOpen(file);
        try {
            file.save();
            return true;
        } catch (IOException failure) {
            showError("保存失败，文件仍保持打开", failure);
            return false;
        }
    }

    private CloseChoice askClose(EditorFile file) {
        var dialog = UiMessageDialog.saveChanges(owner(), "关闭文件",
                "是否保存对“" + file.path().getFileName() + "”的修改？",
                "关闭后，未保存的修改将丢失。\n\n" + file.path());
        return switch (dialog.showAndWait().orElse(UiMessageDialog.Result.CANCEL)) {
            case SAVE -> CloseChoice.SAVE;
            case DISCARD -> CloseChoice.DISCARD;
            default -> CloseChoice.CANCEL;
        };
    }

    public void chooseOpenFiles() {
        List<File> chosen = chooser("打开文件").showOpenMultipleDialog(owner());
        if (chosen == null) return;
        for (File file : chosen) performIo(() -> openFile(file.toPath()));
    }

    public void chooseNewFile() {
        FileChooser chooser = chooser("新建文件（先选择磁盘位置）");
        chooser.setInitialFileName("untitled.c");
        File chosen = chooser.showSaveDialog(owner());
        if (chosen != null) performIo(() -> createFile(chosen.toPath()));
    }

    void chooseBeside(EditorFile target, Orientation orientation) {
        File chosen = chooser("打开文件到" + (orientation == Orientation.HORIZONTAL ? "右侧" : "下方"))
                .showOpenDialog(owner());
        if (chosen != null) performIo(() -> openFileBeside(chosen.toPath(), target, orientation));
    }

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        if (active.get() != null && Files.isDirectory(active.get().path().getParent())) {
            chooser.setInitialDirectory(active.get().path().getParent().toFile());
        }
        return chooser;
    }

    private Node createEmptyView() {
        Label title = new Label("尚未打开文件");
        title.getStyleClass().add("editor-empty-title");
        Label hint = new Label("打开磁盘文件开始编辑，或新建一个文件。\nCtrl+O 打开文件    Ctrl+N 新建文件");
        hint.getStyleClass().add("editor-empty-hint");
        hint.setWrapText(true);
        Button open = action("打开文件", "打开磁盘文件", this::chooseOpenFiles);
        open.setId("editor-empty-open");
        Button create = action("新建文件", "新建并绑定磁盘文件", this::chooseNewFile);
        HBox actions = new HBox(12, open, create);
        actions.setAlignment(Pos.CENTER);
        VBox empty = new VBox(14, title, hint, actions);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(24));
        empty.setMinSize(0, 0);
        empty.getStyleClass().add("editor-empty");
        return empty;
    }

    private Node createFileView(EditorFile file) {
        Runnable visibility = () -> file.editor().setVisible(
                file.editor().getWidth() >= 44 && file.editor().getHeight() >= 44);
        file.editor().widthProperty().addListener(ignored -> visibility.run());
        file.editor().heightProperty().addListener(ignored -> visibility.run());
        return file.editor();
    }

    static Button action(String text, String description, Runnable handler) {
        Button button = new Button(text);
        button.setAccessibleText(description);
        button.setTooltip(new UiTooltip(description));
        button.setFocusTraversable(false);
        button.getStyleClass().add("editor-file-action");
        button.setOnAction(event -> { handler.run(); event.consume(); });
        return button;
    }

    private void handleShortcut(KeyEvent event) {
        if (!event.isControlDown() || event.isAltDown() || event.isMetaDown()) return;
        // 场景捕获阶段不能抢走终端按键；交给终端自己的输入和快捷键处理。
        if (event.getTarget() instanceof Node target && isInsideTerminal(target)
                || getScene() != null && isInsideTerminal(getScene().getFocusOwner())) return;
        switch (event.getCode()) {
            case O -> { if (event.isShiftDown()) return; chooseOpenFiles(); }
            case N -> { if (event.isShiftDown()) return; chooseNewFile(); }
            case S -> { if (event.isShiftDown()) return; if (active.get() != null) save(active.get()); }
            case W -> { if (event.isShiftDown()) return; if (active.get() != null) closeFile(active.get()); }
            case TAB -> {
                if (!files.isEmpty()) {
                    int next = Math.floorMod(files.indexOf(active.get()) + (event.isShiftDown() ? -1 : 1), files.size());
                    select(files.get(next));
                }
            }
            default -> { return; }
        }
        event.consume();
    }

    private static boolean isInsideTerminal(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof TerminalPanel) return true;
        }
        return false;
    }

    private void requireOpen(EditorFile file) {
        if (!files.contains(file)) throw new IllegalArgumentException("file is not open in this editor area");
    }

    private Window owner() { return getScene() == null ? null : getScene().getWindow(); }

    private void performIo(IoAction action) {
        try { action.run(); } catch (IOException failure) { showError("无法打开或新建文件", failure); }
    }

    private void showError(String title, IOException failure) {
        UiMessageDialog.notice(owner(), "文件操作失败", title, failure.getMessage()).showAndWait();
    }

    @FunctionalInterface private interface IoAction { void run() throws IOException; }
}
