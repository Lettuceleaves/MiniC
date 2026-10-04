package craken.ui.editor;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import craken.ui.component.UiStyles;
import craken.ui.component.action.UiHoverMenuButton;
import craken.ui.component.data.UiList;
import craken.ui.component.display.UiIcon;
import craken.ui.component.navigation.UiContextMenu;
import craken.ui.interaction.terminal.TerminalPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 显式启用真实 JavaFX/Swing 测试；只操作临时文件，不读取或关闭用户窗口。 */
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class EditorAreaTest {
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Runnable ready = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(ready); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready); }
        started.get(10, TimeUnit.SECONDS);
    }

    @Test
    void startsEmptyAndDoesNotCreateAnUnboundEditor() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture()) {
                assertTrue(ui.area.files().isEmpty());
                assertNull(ui.area.activeFile());
                assertNull(ui.area.editor());
                assertNull(ui.area.workspace());
                assertTrue(ui.area.lookup(".editor-empty").isVisible());
                assertTrue(ui.tabs().getItems().isEmpty());
                assertNotNull(ui.area.lookup("#editor-empty-open"));
            }
            return null;
        });
    }

    @Test
    void tabMoreMenuUsesThreeDotsAndStaysTransparentInEveryState() throws Exception {
        onFx(() -> {
            try (var ui = new Fixture()) {
                var more = (UiHoverMenuButton) ui.area.tabBar().lookup("#editor-tab-more");
                assertNotNull(more);
                assertEquals("", more.getText());
                assertInstanceOf(UiIcon.class, more.getGraphic());
                assertEquals(3, more.getGraphic().lookupAll(".ui-icon-fill").size());
                assertEquals(List.of("向右拆分当前文件", "向下拆分当前文件",
                                "打开其他文件到右侧…", "打开其他文件到下方…"),
                        more.getItems().stream().map(MenuItem::getText).toList());
                assertTrue(more.getItems().stream().allMatch(MenuItem::isDisable));
                assertEquals(36, more.getWidth());
                assertEquals(36, more.getHeight());
                for (String state : List.of("hover", "pressed", "focused", "showing")) {
                    for (boolean enabled : new boolean[]{false, true}) {
                        more.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), enabled);
                        ui.layout();
                        assertTrue(more.getBackground().getFills().stream().allMatch(fill ->
                                javafx.scene.paint.Color.TRANSPARENT.equals(fill.getFill())), state);
                        assertTrue(more.getBorder().getStrokes().stream().allMatch(stroke ->
                                stroke.getWidths().equals(javafx.scene.layout.BorderWidths.EMPTY)), state);
                    }
                    more.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), false);
                }
                Node arrow = more.lookup(".arrow");
                assertEquals(0, arrow.getLayoutBounds().getWidth());
                assertEquals(0, arrow.getLayoutBounds().getHeight());
                Bounds button = more.localToScene(more.getLayoutBounds());
                Bounds icon = more.getGraphic().localToScene(more.getGraphic().getLayoutBounds());
                assertEquals(button.getCenterX(), icon.getCenterX(), 0.1);
                assertEquals(button.getCenterY(), icon.getCenterY(), 0.1);
            }
            return null;
        });
    }

    @Test
    void tabMoreMenuStillSplitsTheActiveFileInBothDirections() throws Exception {
        Path path = source("more-menu.c", "int main() { return 0; }\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                EditorFile original = ui.area.openFile(path);
                var more = (UiHoverMenuButton) ui.area.tabBar().lookup("#editor-tab-more");
                assertTrue(more.getItems().stream().noneMatch(MenuItem::isDisable));
                more.getItems().get(0).fire();
                assertEquals(2, original.root().panes().size());
                assertEquals(Orientation.HORIZONTAL,
                        ((SplitPane) original.root().getChildren().getFirst()).getOrientation());
                EditorFile right = ui.area.activeFile();
                more.getItems().get(1).fire();
                assertEquals(3, original.root().panes().size());
                EditorFile below = ui.area.activeFile();
                assertSame(right.root(), below.root());
                SplitPane rootSplit = (SplitPane) original.root().getChildren().getFirst();
                SplitPane nested = (SplitPane) rootSplit.getItems().getLast();
                assertEquals(Orientation.VERTICAL, nested.getOrientation());
                assertEquals(List.of(right.pane(), below.pane()), nested.getItems());
                assertEquals(original.path(), below.path());
            }
            return null;
        });
    }

    @Test
    void regularOpenCreatesIndependentRootsAndSwitchingRetainsEditorState() throws Exception {
        Path first = source("first.c", "int first;\nint second;\n");
        Path second = source("second.c", "int other;\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                EditorFile a = ui.area.openFile(first);
                a.editor().setSource("int modified;\nint second;\n");
                a.editor().setBreakpoint(2, true);
                a.editor().zoomIn();
                EditorFile b = ui.area.openFile(second);
                ui.layout();
                Button close = (Button) ui.tab(a).lookup(".editor-tab-close");
                javafx.scene.text.Text glyph = (javafx.scene.text.Text) close.lookup(".text");
                assertNotNull(glyph);
                assertEquals("×", glyph.getText(), "a fixed-width button must not clip away its close glyph");
                assertNotSame(a.root(), b.root());
                assertNull(a.root().getScene());
                assertSame(ui.scene, b.root().getScene());
                press(ui.tab(a), MouseButton.PRIMARY);
                assertSame(a, ui.area.activeFile());
                assertSame(a.root(), ui.area.workspace());
                assertSame(ui.scene, a.root().getScene());
                assertNull(b.root().getScene());
                assertEquals("int modified;\nint second;\n", a.editor().text());
                assertEquals(1, a.editor().breakpoints().size());
                assertEquals(1, a.editor().zoomLevel());
                assertTrue(a.isDirty());
                assertFalse(a.pane().getPseudoClassStates().contains(ACTIVE));
                assertFalse(ui.area.lookup(".editor-empty").isVisible());
            }
            return null;
        });
    }

    @Test
    void splitSharesTextButKeepsOwnViewAndClosingOneViewDoesNotLoseEdits() throws Exception {
        Path path = source("shared.c", "int a;\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                EditorFile a = ui.area.openFile(path);
                EditorFile b = ui.area.splitFile(a, Orientation.HORIZONTAL);
                ui.layout();
                assertSame(a.root(), b.root());
                assertEquals(2, a.root().panes().size());
                assertSame(textArea(a).getDocument(), textArea(b).getDocument());
                a.editor().zoomIn();
                assertEquals(0, b.editor().zoomLevel());
                SwingUtilities.invokeAndWait(() -> textArea(a).append("int 中文;\n"));
                assertEquals(a.editor().text(), b.editor().text());
                assertTrue(b.isDirty());
                assertTrue(b.pane().getPseudoClassStates().contains(ACTIVE));
                assertFalse(a.pane().getPseudoClassStates().contains(ACTIVE));
                press(a.pane(), MouseButton.PRIMARY);
                assertSame(a, ui.area.activeFile());
                assertTrue(a.pane().getPseudoClassStates().contains(ACTIVE));
                assertFalse(b.pane().getPseudoClassStates().contains(ACTIVE));
                AtomicInteger prompts = new AtomicInteger();
                ui.area.setCloseDecisionHandler(file -> { prompts.incrementAndGet(); return EditorArea.CloseChoice.CANCEL; });
                assertTrue(a.pane().close());
                assertEquals(0, prompts.get(), "another view retains the unsaved document");
                assertSame(b.pane(), b.root().primaryPane());
                assertSame(b, ui.area.activeFile());
                assertFalse(b.pane().getPseudoClassStates().contains(ACTIVE));
                assertEquals("int a;\nint 中文;\n", b.editor().text());
                assertFalse(ui.area.closeFile(b));
                assertEquals(1, prompts.get());
                assertEquals(1, ui.tabs().getItems().size());
                assertTrue(Files.exists(path));
            }
            return null;
        });
    }

    @Test
    void switchingTabsRestoresWholeNestedRootAndClosingPromotesSiblingSubtree() throws Exception {
        Path aPath = source("a.c", "int a;\n");
        Path bPath = source("b.c", "int b;\n");
        Path cPath = source("c.c", "int c;\n");
        Path dPath = source("d.c", "int d;\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                EditorFile a = ui.area.openFile(aPath);
                EditorFile b = ui.area.openFileBeside(bPath, a, Orientation.HORIZONTAL);
                EditorFile c = ui.area.openFileBeside(cPath, b, Orientation.VERTICAL);
                SplitPane root = (SplitPane) a.root().getChildren().getFirst();
                SplitPane sibling = (SplitPane) root.getItems().getLast();
                root.setDividerPositions(0.3);
                sibling.setDividerPositions(0.6);
                EditorFile d = ui.area.openFile(dPath);
                assertNotSame(a.root(), d.root());
                assertNull(a.root().getScene());
                ui.area.select(c);
                assertSame(root, ui.area.workspace().getChildren().getFirst());
                assertSame(sibling, root.getItems().getLast());
                assertEquals(0.3, root.getDividerPositions()[0], 0.0001);
                assertEquals(0.6, sibling.getDividerPositions()[0], 0.0001);
                ui.layout(); // SplitPane 在 CSS 创建 Skin 后才把其 items 挂入 Scene。
                assertSame(ui.scene, a.editor().getScene());
                assertSame(ui.scene, b.editor().getScene());
                assertSame(ui.scene, c.editor().getScene());
                assertNull(d.editor().getScene());
                ((Button) ui.tab(a).lookup(".editor-tab-close")).fire();
                assertTrue(a.pane().isClosed());
                assertSame(sibling, b.root().getChildren().getFirst());
                assertSame(c, ui.area.activeFile());
                assertEquals(3, ui.area.files().size());
                ((Button) b.pane().lookup(".ui-workspace-pane-close")).fire();
                assertEquals(2, ui.area.files().size());
                assertSame(c.pane(), c.root().getChildren().getFirst());
                assertEquals("int c;\n", c.editor().text());
                assertTrue(Files.exists(aPath));
                assertTrue(Files.exists(bPath));
            }
            return null;
        });
    }

    @Test
    void dirtyCloseSupportsCancelSaveAndDiscardAndFinallyReturnsToEmpty() throws Exception {
        Path path = source("save.c", "int original;\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                EditorFile a = ui.area.openFile(path);
                a.editor().setSource("int saved;\n");
                ui.area.setCloseDecisionHandler(file -> EditorArea.CloseChoice.CANCEL);
                assertFalse(a.pane().close());
                assertFalse(a.pane().isClosed());
                assertSame(a, ui.area.activeFile());
                assertEquals("int original;\n", Files.readString(path));
                ui.area.setCloseDecisionHandler(file -> EditorArea.CloseChoice.SAVE);
                assertTrue(ui.area.closeFile(a));
                assertEquals("int saved;\n", Files.readString(path));
                EditorFile b = ui.area.openFile(path);
                b.editor().setSource("discard this");
                ui.area.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
                assertTrue(ui.area.closeFile(b));
                assertFalse(ui.area.closeFile(b), "closing twice is harmless");
                assertEquals("int saved;\n", Files.readString(path));
                assertTrue(ui.area.files().isEmpty());
                assertTrue(ui.tabs().getItems().isEmpty());
                assertNull(ui.area.workspace());
                assertTrue(ui.area.lookup(".editor-empty").isVisible());
            }
            return null;
        });
    }

    @Test
    void reopeningSameFileInIndependentRootUsesUnsavedBufferAndSharedSaveBaseline() throws Exception {
        Path path = source("reopened.c", "int initial;\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                EditorFile a = ui.area.openFile(path);
                a.editor().setSource("int unsaved;\n");
                EditorFile b = ui.area.openFile(path);
                assertNotSame(a.root(), b.root());
                assertEquals(a.editor().text(), b.editor().text());
                assertTrue(b.isDirty());
                assertTrue(ui.area.save(b));
                assertFalse(a.isDirty());
                assertFalse(b.isDirty());
                assertFalse(a.dirtyProperty().get());
                assertEquals("int unsaved;\n", Files.readString(path));
            }
            return null;
        });
    }

    @Test
    void failedOpenOrSplitDoesNotAddGhostTabsOrOverwriteExistingFiles() throws Exception {
        Path existing = source("existing.c", "keep");
        onFx(() -> {
            try (var ui = new Fixture()) {
                assertThrows(IOException.class, () -> ui.area.openFile(directory.resolve("missing.c")));
                assertThrows(IOException.class, () -> ui.area.createFile(existing));
                assertTrue(ui.area.files().isEmpty());
                assertEquals("keep", Files.readString(existing));
                EditorFile file = ui.area.openFile(existing);
                assertThrows(NullPointerException.class, () -> ui.area.splitFile(file, null));
                assertThrows(IOException.class, () -> ui.area.openFileBeside(directory.resolve("missing.c"), file, Orientation.HORIZONTAL));
                assertEquals(1, ui.area.files().size());
                assertEquals(1, file.root().panes().size());
                assertSame(file, ui.area.activeFile());
                assertThrows(UnsupportedOperationException.class, () -> ui.area.files().clear());
            }
            return null;
        });
    }

    @Test
    void shortcutsSwitchWholeRootsSaveAndCloseAndMiddleClickClosesTab() throws Exception {
        Path first = source("keys-a.c", "a");
        Path second = source("keys-b.c", "b");
        onFx(() -> {
            try (var ui = new Fixture()) {
                EditorFile a = ui.area.openFile(first);
                EditorFile b = ui.area.openFile(second);
                key(ui.root, KeyCode.TAB, false);
                assertSame(a, ui.area.activeFile());
                a.editor().setSource("saved using Ctrl+S");
                key(ui.root, KeyCode.S, false);
                assertEquals("saved using Ctrl+S", Files.readString(first));
                key(ui.root, KeyCode.TAB, true);
                assertSame(b, ui.area.activeFile());
                key(ui.root, KeyCode.W, false);
                assertTrue(b.pane().isClosed());
                assertSame(a, ui.area.activeFile());
                press(ui.tab(a), MouseButton.MIDDLE);
                assertTrue(ui.area.files().isEmpty());
            }
            return null;
        });
    }

    @Test
    void terminalKeyTargetsReceiveControlShortcutsWithoutChangingEditorFiles() throws Exception {
        Path first = source("terminal-keys-a.c", "first");
        Path second = source("terminal-keys-b.c", "original");
        onFx(() -> {
            try (var ui = new Fixture(); var terminal = new TerminalPanel(directory)) {
                EditorFile a = ui.area.openFile(first);
                EditorFile b = ui.area.openFile(second);
                b.editor().setSource("unsaved editor changes");
                AtomicInteger closePrompts = new AtomicInteger();
                ui.area.setCloseDecisionHandler(file -> {
                    closePrompts.incrementAndGet();
                    return EditorArea.CloseChoice.CANCEL;
                });
                ui.root.setBottom(terminal);
                ui.layout();
                Node surface = terminal.lookup("#terminal-surface");
                assertInstanceOf(SwingNode.class, surface);
                var received = new ArrayList<KeyCode>();
                // 不启动 PowerShell；模拟终端接收并消费事件，同时防止菜单触发任何对话框。
                terminal.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    assertSame(surface, event.getTarget());
                    received.add(event.getCode());
                    event.consume();
                });
                for (KeyCode code : List.of(KeyCode.W, KeyCode.TAB, KeyCode.S)) key(surface, code, false);
                assertEquals(List.of(KeyCode.W, KeyCode.TAB, KeyCode.S), received,
                        "scene capture must let every surface-targeted shortcut reach the terminal");
                assertEquals(0, closePrompts.get());
                assertEquals(2, ui.area.files().size());
                assertSame(b, ui.area.activeFile());
                assertFalse(a.pane().isClosed());
                assertFalse(b.pane().isClosed());
                assertTrue(b.isDirty());
                assertEquals("original", Files.readString(second));

                ui.root.setBottom(null);
                ui.root.requestFocus();
                key(ui.root, KeyCode.S, false);
                assertEquals("unsaved editor changes", Files.readString(second),
                        "the original file shortcuts remain active outside the terminal");
                key(ui.root, KeyCode.TAB, false);
                assertSame(a, ui.area.activeFile());
                key(ui.root, KeyCode.W, false);
                assertTrue(a.pane().isClosed());
            }
            return null;
        });
    }

    @Test
    void terminalFocusAlsoProtectsShortcutsDeliveredToTheSceneRoot() throws Exception {
        Path path = source("terminal-focus.c", "original");
        onFx(() -> {
            try (var ui = new Fixture(); var terminal = new TerminalPanel(directory)) {
                EditorFile file = ui.area.openFile(path);
                file.editor().setSource("unsaved");
                AtomicInteger closePrompts = new AtomicInteger();
                ui.area.setCloseDecisionHandler(closing -> {
                    closePrompts.incrementAndGet();
                    return EditorArea.CloseChoice.CANCEL;
                });
                ui.root.setBottom(terminal);
                ui.layout();
                Node surface = terminal.lookup("#terminal-surface");
                ((craken.ui.component.swing.UiSwingNode) surface).requestUserFocus();
                assertSame(surface, ui.scene.getFocusOwner());
                var received = new ArrayList<KeyCode>();
                ui.root.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                    received.add(event.getCode());
                    event.consume();
                });
                key(ui.root, KeyCode.W, false);
                key(ui.root, KeyCode.S, false);
                key(ui.root, KeyCode.TAB, false);
                assertEquals(List.of(KeyCode.W, KeyCode.S, KeyCode.TAB), received);
                assertEquals(0, closePrompts.get());
                assertSame(file, ui.area.activeFile());
                assertFalse(file.pane().isClosed());
                assertTrue(file.isDirty());
                assertEquals("original", Files.readString(path));
            }
            return null;
        });
    }

    private Path source(String name, String text) throws IOException {
        return Files.writeString(directory.resolve(name), text);
    }

    @Test
    void tabContextMenuSplitsThePointedFileDownWithinItsOwnRoot() throws Exception {
        checkContextSplit("向下拆分", Orientation.VERTICAL);
    }

    @Test
    void tabContextMenuSplitsThePointedFileRightWithinItsOwnRoot() throws Exception {
        checkContextSplit("向右拆分", Orientation.HORIZONTAL);
    }

    private void checkContextSplit(String command, Orientation direction) throws Exception {
        Path first = source("context-a.c", "int selected;\n");
        Path second = source("context-b.c", "int unrelated;\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                ui.show();
                EditorFile pointed = ui.area.openFile(first);
                EditorFile active = ui.area.openFile(second);
                ui.layout();
                UiContextMenu popup = contextMenu(ui.tab(pointed));
                assertEquals(UiContextMenu.Style.CONTEXT, popup.getMenuStyle());
                assertEquals("向下拆分", popup.getItems().get(0).getText());
                assertEquals("向右拆分", popup.getItems().get(1).getText());
                popup.getItems().stream().filter(item -> command.equals(item.getText())).findFirst().orElseThrow().fire();
                assertFalse(popup.isShowing(), "the dropdown closes when its command is executed");
                assertEquals(3, ui.area.files().size());
                EditorFile created = ui.area.activeFile();
                assertNotSame(pointed, created);
                assertEquals(pointed.path(), created.path());
                assertSame(pointed.root(), created.root());
                assertEquals(2, pointed.root().panes().size());
                assertEquals(1, active.root().panes().size(), "right-click target, not the previously active tab, is split");
                SplitPane split = (SplitPane) created.root().getChildren().getFirst();
                assertEquals(direction, split.getOrientation());
                assertSame(pointed.pane(), split.getItems().getFirst());
                assertSame(created.pane(), split.getItems().getLast());
                assertTrue(created.pane().getPseudoClassStates().contains(ACTIVE));
                assertEquals(pointed.editor().text(), created.editor().text());
            }
            return null;
        });
    }

    @Test
    void removingTheContextTargetAlsoDismissesItsFloatingMenu() throws Exception {
        Path path = source("context-close.c", "int a;\n");
        onFx(() -> {
            try (var ui = new Fixture()) {
                ui.show();
                EditorFile file = ui.area.openFile(path);
                ui.layout();
                UiContextMenu popup = contextMenu(ui.tab(file));
                assertTrue(ui.area.closeFile(file));
                assertFalse(popup.isShowing());
            }
            return null;
        });
    }

    private static UiContextMenu contextMenu(Node tab) {
        Node title = tab.lookup(".editor-tab-title");
        Bounds bounds = title.localToScreen(title.getBoundsInLocal());
        Event.fireEvent(title, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                4, 4, bounds.getMinX() + 4, bounds.getMinY() + 4, false, null));
        return Window.getWindows().stream().filter(window -> window instanceof UiContextMenu menu
                        && menu.isShowing() && menu.getOwnerNode() == tab)
                .map(UiContextMenu.class::cast).findFirst().orElseThrow();
    }

    private static RSyntaxTextArea textArea(EditorFile file) {
        SwingNode swing = (SwingNode) file.editor().getChildren().getFirst();
        return (RSyntaxTextArea) ((RTextScrollPane) swing.getContent()).getTextArea();
    }

    private static void press(Node node, MouseButton button) {
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_PRESSED, 3, 3, 3, 3, button, 1,
                false, false, false, false, button == MouseButton.PRIMARY, button == MouseButton.MIDDLE,
                false, false, false, true, null));
    }

    private static void key(Node node, KeyCode code, boolean shift) {
        Event.fireEvent(node, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, true, false, false));
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    private static final class Fixture implements AutoCloseable {
        final EditorArea area = new EditorArea();
        final BorderPane root = new BorderPane(area);
        final Scene scene;
        private Stage stage;

        Fixture() {
            root.setTop(area.tabBar());
            scene = new Scene(root, 1000, 650);
            UiStyles.install(scene);
            layout();
        }

        void layout() { root.applyCss(); root.resize(1000, 650); root.layout(); }
        void show() { stage = new Stage(); stage.setScene(scene); stage.show(); layout(); }
        UiList tabs() { return (UiList) area.tabBar().lookup(".ui-list"); }
        Node tab(EditorFile file) {
            return tabs().getItems().stream().filter(node -> node.getUserData() == file).findFirst().orElseThrow();
        }

        @Override public void close() {
            area.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
            area.closeAll();
            if (stage != null) stage.close();
        }
    }
}
