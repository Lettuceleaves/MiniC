package craken.ui.editor.completion;

import craken.compiler.library.SystemLibraryCatalog;
import craken.ui.component.editor.UiCodeEditor;
import craken.ui.component.editor.UiCodeEditorListItem;
import javafx.application.Platform;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一个编辑器视图的补全粘合层。Swing 文档与光标事件触发后台合并式计算，
 * 结果回到 JavaFX 线程后用光标浮窗展示；选中项替换光标左侧前缀。
 * 自动触发：include 上下文，或非空标识符前缀；Ctrl+Space 强制展开全部候选。
 */
public final class EditorCompletionController implements AutoCloseable {
    private static final String COMPLETE_ACTION = "craken-editor-complete";
    private static final KeyStroke COMPLETE_KEY = KeyStroke.getKeyStroke(
            KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK);
    private static final double POPUP_WIDTH = 320;

    private final UiCodeEditor editor;
    private final CompletionCatalog catalog;
    private final BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
    private final AtomicLong nextRequestId = new AtomicLong();
    private final AtomicBoolean applyingCompletion = new AtomicBoolean();

    private final DocumentListener documentListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { documentChanged(); }
        @Override public void removeUpdate(DocumentEvent event) { documentChanged(); }
        @Override public void changedUpdate(DocumentEvent event) { documentChanged(); }

        private void documentChanged() {
            if (!applyingCompletion.get()) {
                // DocumentListener 先于 DefaultCaret 移动光标触发；延迟到本轮 EDT
                // 事件结束后再取样，光标才是插入后的最终位置。
                SwingUtilities.invokeLater(() -> request(false));
            }
        }
    };

    private final CaretListener caretListener = new CaretListener() {
        @Override public void caretUpdate(CaretEvent event) {
            // 浮窗打开时跟着光标刷新候选；普通点击不主动弹窗，避免抢走 Enter 换行。
            if (!applyingCompletion.get() && showing) {
                request(false);
            }
        }
    };

    private final FocusAdapter focusListener = new FocusAdapter() {
        @Override public void focusLost(FocusEvent event) {
            hide();
        }
    };

    private final AbstractAction completeAction = new AbstractAction() {
        @Override public void actionPerformed(ActionEvent event) {
            request(true);
        }
    };

    private volatile boolean running = true;
    private volatile boolean showing;
    private volatile long latestRequestId;
    private Thread worker;
    private boolean listenersInstalled;

    /** 使用项目 lib 目录的头文件名单，不包含当前文件目录的本地头文件。 */
    public EditorCompletionController(UiCodeEditor editor) {
        this(editor, CompletionCatalog.fromDirectories(
                SystemLibraryCatalog.defaults().includeRoot(), null));
    }

    public EditorCompletionController(UiCodeEditor editor, CompletionCatalog catalog) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        editor.setCaretListWidth(POPUP_WIDTH);
        editor.onTextArea(this::installListeners);
    }

    private void installListeners(RSyntaxTextArea area) {
        if (!running || listenersInstalled) {
            return;
        }
        listenersInstalled = true;
        area.getDocument().addDocumentListener(documentListener);
        area.addCaretListener(caretListener);
        area.addFocusListener(focusListener);
        area.getInputMap(JComponent.WHEN_FOCUSED).put(COMPLETE_KEY, COMPLETE_ACTION);
        area.getActionMap().put(COMPLETE_ACTION, completeAction);
    }

    private void request(boolean force) {
        if (!running) {
            return;
        }
        Snapshot snapshot = snapshot(force);
        if (snapshot == null) {
            hide();
            return;
        }
        long requestId = nextRequestId.incrementAndGet();
        latestRequestId = requestId;
        queue.offer(new Request(snapshot, force, requestId));
        ensureStarted();
    }

    private Snapshot snapshot(boolean force) {
        AtomicReference<Snapshot> captured = new AtomicReference<>();
        editor.onTextArea(area -> {
            if (!area.isEditable() || !force && !area.hasFocus()) {
                return;
            }
            captured.set(new Snapshot(area.getText(), area.getCaretPosition()));
        });
        return captured.get();
    }

    private synchronized void ensureStarted() {
        if (worker != null) {
            return;
        }
        worker = new Thread(this::runLoop, "craken-editor-completion");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        while (running) {
            try {
                Request first = queue.take();
                Request latest = first;
                Request next;
                while ((next = queue.poll()) != null) {
                    latest = next;
                }
                if (latest.id() != latestRequestId) {
                    continue;
                }
                Request selected = latest;
                CompletionResult result = CompletionEngine.complete(
                        selected.snapshot().source(), selected.snapshot().caret(), catalog);
                Platform.runLater(() -> deliver(selected, result));
            } catch (InterruptedException interrupted) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void deliver(Request request, CompletionResult result) {
        if (!running || request.id() != latestRequestId) {
            return;
        }
        if (!request.force()
                && result.prefix().context() == CompletionContext.IDENTIFIER
                && result.prefix().text().isEmpty()) {
            hide();
            return;
        }
        if (result.candidates().isEmpty()) {
            hide();
            return;
        }
        List<UiCodeEditorListItem> items = result.candidates().stream()
                .map(candidate -> new UiCodeEditorListItem(
                        candidate.kind().name().toLowerCase(Locale.ROOT) + ":" + candidate.text(),
                        candidate.text()))
                .toList();
        showing = true;
        editor.showCaretList(items, this::applyItem);
    }

    private void applyItem(UiCodeEditorListItem item) {
        showing = false;
        editor.onTextArea(area -> {
            if (!area.isEditable()) {
                return;
            }
            int caret = area.getCaretPosition();
            CompletionPrefix prefix = CompletionEngine.prefixAt(area.getText(), caret);
            applyingCompletion.set(true);
            try {
                area.replaceRange(item.text(), prefix.startOffset(), caret);
                area.setCaretPosition(prefix.startOffset() + item.text().length());
            } finally {
                applyingCompletion.set(false);
            }
        });
    }

    private void hide() {
        showing = false;
        editor.hideCaretList();
    }

    @Override
    public void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        hide();
        editor.onTextArea(area -> {
            if (!listenersInstalled) {
                return;
            }
            listenersInstalled = false;
            area.getDocument().removeDocumentListener(documentListener);
            area.removeCaretListener(caretListener);
            area.removeFocusListener(focusListener);
            area.getInputMap(JComponent.WHEN_FOCUSED).remove(COMPLETE_KEY);
            area.getActionMap().remove(COMPLETE_ACTION);
        });
    }

    private record Snapshot(String source, int caret) {
        private Snapshot {
            Objects.requireNonNull(source, "source");
            if (caret < 0) {
                throw new IllegalArgumentException("caret must not be negative");
            }
        }
    }

    private record Request(Snapshot snapshot, boolean force, long id) {
        private Request {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
