package minic.ui.component.editor;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingNode;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Swing 只提供光标逻辑坐标；浮窗及选中回调由 JavaFX 线程管理。 */
final class UiEditorCaretListBridge {
    private final UiCodeEditor editor;
    private final SwingNode swingNode;
    private final UiCodeEditorTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final UiEditorCaretList popup;
    private final AtomicLong documentVersion = new AtomicLong();
    private final AtomicBoolean repositionQueued = new AtomicBoolean();
    private final InvalidationListener geometryChanged = observable -> queueReposition();
    private final ChangeListener<Window> windowChanged = (observable, previous, current) -> {
        observeWindow(previous, false);
        observeWindow(current, true);
        hide();
    };
    private long displayedVersion;
    private KeyCode acceptedKey;

    UiEditorCaretListBridge(
            UiCodeEditor editor,
            SwingNode swingNode,
            UiCodeEditorTextArea textArea,
            RTextScrollPane scrollPane
    ) {
        this.editor = editor;
        this.swingNode = swingNode;
        this.textArea = textArea;
        this.scrollPane = scrollPane;
        popup = new UiEditorCaretList(editor, this::handleEditorKey);
        swingNode.addEventFilter(KeyEvent.ANY, this::handleEditorKey);
        swingNode.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused) {
                acceptedKey = null;
            }
        });
        swingNode.localToSceneTransformProperty().addListener(geometryChanged);
        swingNode.boundsInLocalProperty().addListener(geometryChanged);
        editor.sceneProperty().addListener((observable, previous, current) -> {
            observeScene(previous, false);
            observeScene(current, true);
            hide();
        });
        observeScene(editor.getScene(), true);
        SwingUtilities.invokeLater(this::installSwingListeners);
    }

    void show(List<UiCodeEditorListItem> items, Consumer<UiCodeEditorListItem> onSelected) {
        long requestedVersion = documentVersion.get();
        onFxThread(() -> {
            if (items.isEmpty()) {
                popup.hide();
                return;
            }
            Anchor anchor = anchor();
            if (anchor == null) {
                popup.hide();
                return;
            }
            // EDT 提交列表后可能继续编辑，不能把旧条目当成新文档的结果。
            if (requestedVersion != anchor.version() || requestedVersion != documentVersion.get()) {
                return;
            }
            displayedVersion = requestedVersion;
            popup.show(items, item -> {
                // 文档变更通知尚在 FX 队列中时，也不能消费过期条目。
                if (requestedVersion == documentVersion.get()) {
                    onSelected.accept(item);
                }
            }, anchor.caret(), anchor.available(), anchor.lineHeight());
        });
    }

    void hide() {
        onFxThread(popup::hide);
    }

    void setWidth(double width) {
        onFxThread(() -> popup.setPopupWidth(width));
    }

    void setMaxVisibleItems(int count) {
        onFxThread(() -> popup.setMaxVisibleItems(count));
    }

    void applyStyle(UiCodeEditorStyle style, double zoomFactor) {
        onFxThread(() -> {
            popup.applyStyle(style, zoomFactor);
            queueReposition();
        });
    }

    private void handleEditorKey(KeyEvent event) {
        if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == acceptedKey) {
            event.consume();
            return;
        }
        if (event.getEventType() == KeyEvent.KEY_TYPED && acceptedKey != null) {
            if ("\r".equals(event.getCharacter()) || "\n".equals(event.getCharacter())
                    || "\t".equals(event.getCharacter())) {
                event.consume();
            }
            return;
        }
        if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == acceptedKey) {
            acceptedKey = null;
            event.consume();
            return;
        }
        if (event.getEventType() != KeyEvent.KEY_PRESSED || event.isAltDown()
                || event.isControlDown() || event.isMetaDown() || event.isShiftDown()) {
            return;
        }
        if (popup.isShowing() && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB)) {
            // 在回调前设置，回调转移焦点时可以正确清除此状态。
            acceptedKey = event.getCode();
        }
        if (popup.handleKey(event.getCode())) {
            event.consume();
        }
    }

    private void installSwingListeners() {
        textArea.addCaretListener(event -> queueReposition());
        scrollPane.getViewport().addChangeListener(event -> queueReposition());
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                documentChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                documentChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                documentChanged();
            }

            private void documentChanged() {
                long version = documentVersion.incrementAndGet();
                Platform.runLater(() -> {
                    // 延迟通知不能关闭调用者刚为新文本提交的列表。
                    if (displayedVersion < version) {
                        popup.hide();
                    }
                });
            }
        });
    }

    private void queueReposition() {
        if (!repositionQueued.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            repositionQueued.set(false);
            if (popup.isShowing()) {
                Anchor anchor = anchor();
                if (anchor == null) {
                    popup.hide();
                } else {
                    popup.reposition(anchor.caret(), anchor.available());
                }
            }
        });
    }

    private Anchor anchor() {
        Scene scene = editor.getScene();
        if (scene == null || scene.getWindow() == null || !scene.getWindow().isShowing()) {
            return null;
        }
        AtomicReference<LocalAnchor> captured = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Rectangle2D caret = textArea.modelToView2D(textArea.getCaretPosition());
                    if (caret == null) {
                        return;
                    }
                    Rectangle visible = textArea.getVisibleRect();
                    if (!visible.intersects(caret.getX(), caret.getY(),
                            Math.max(1, caret.getWidth()), caret.getHeight())) {
                        return;
                    }
                    Point origin = SwingUtilities.convertPoint(textArea, 0, 0, scrollPane);
                    captured.set(new LocalAnchor(
                            new BoundingBox(origin.x + caret.getX(), origin.y + caret.getY(),
                                    Math.max(1, caret.getWidth()), caret.getHeight()),
                            documentVersion.get()
                    ));
                } catch (BadLocationException exception) {
                    throw new IllegalStateException("caret is outside the editor document", exception);
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while locating editor caret", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("failed to locate editor caret", exception.getCause());
        }
        LocalAnchor local = captured.get();
        if (local == null) {
            return null;
        }
        // 不混用 AWT 屏幕像素与 JavaFX 坐标，Windows 高 DPI 下也能对准光标。
        Bounds caretOnScreen = swingNode.localToScreen(local.caret());
        Bounds available = scene.getRoot().localToScreen(scene.getRoot().getLayoutBounds());
        if (caretOnScreen == null || available == null) {
            return null;
        }
        return new Anchor(caretOnScreen, available, local.caret().getHeight(), local.version());
    }

    private void observeScene(Scene scene, boolean attach) {
        if (scene == null) {
            return;
        }
        if (attach) {
            scene.windowProperty().addListener(windowChanged);
            scene.widthProperty().addListener(geometryChanged);
            scene.heightProperty().addListener(geometryChanged);
        } else {
            scene.windowProperty().removeListener(windowChanged);
            scene.widthProperty().removeListener(geometryChanged);
            scene.heightProperty().removeListener(geometryChanged);
        }
        observeWindow(scene.getWindow(), attach);
    }

    private void observeWindow(Window window, boolean attach) {
        if (window == null) {
            return;
        }
        if (attach) {
            window.xProperty().addListener(geometryChanged);
            window.yProperty().addListener(geometryChanged);
        } else {
            window.xProperty().removeListener(geometryChanged);
            window.yProperty().removeListener(geometryChanged);
        }
    }

    private static void onFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private record LocalAnchor(Bounds caret, long version) {
    }

    private record Anchor(Bounds caret, Bounds available, double lineHeight, long version) {
    }
}
