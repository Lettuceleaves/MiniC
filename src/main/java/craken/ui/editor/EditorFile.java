package craken.ui.editor;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import craken.ui.component.editor.UiCodeEditor;
import craken.ui.component.layout.UiWorkspacePane;
import craken.ui.component.layout.UiWorkspace;
import craken.ui.editor.realtime.RealtimeSyntaxController;
import craken.ui.editor.realtime.RealtimeDiagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;

/** 一个磁盘文件、一个编辑器、一个叶子容器；切换标签不重建任何一个对象。 */
public final class EditorFile {
    private final SharedFile shared;
    private final UiCodeEditor editor;
    private final UiWorkspacePane pane;
    private final UiWorkspace root;
    private final RealtimeSyntaxController realtime;

    EditorFile(Path path, String source, UiCodeEditor editor, UiWorkspacePane pane, UiWorkspace root) {
        this(new SharedFile(path, source), editor, pane, root);
    }

    EditorFile(EditorFile original, UiCodeEditor editor, UiWorkspacePane pane, UiWorkspace root) {
        this(original.shared, editor, pane, root);
    }

    private EditorFile(SharedFile shared, UiCodeEditor editor, UiWorkspacePane pane, UiWorkspace root) {
        this.shared = shared;
        this.editor = editor;
        this.pane = pane;
        this.root = root;
        this.realtime = new RealtimeSyntaxController(editor, shared.path.toString());
        shared.views++;
        editor.setOnTextChanged(() -> shared.dirty.set(isDirty()));
    }

    public Path path() { return shared.path; }
    public UiCodeEditor editor() { return editor; }
    public UiWorkspacePane pane() { return pane; }
    public UiWorkspace root() { return root; }
    public ReadOnlyBooleanProperty dirtyProperty() { return shared.dirty.getReadOnlyProperty(); }
    int viewCount() { return shared.views; }

    /** 订阅本视图实时分析的最新报错列表；立即收到当前快照，之后随分析结果更新。 */
    public void setOnDiagnosticsChanged(Consumer<List<RealtimeDiagnostic>> listener) {
        realtime.setOnDiagnosticsChanged(listener);
    }

    void dispose() {
        shared.views--;
        realtime.close();
        editor.dispose();
    }

    /** 关闭前同步读取，避免 EDT 的最后一次修改尚未通知到 FX 线程。 */
    public boolean isDirty() {
        return !shared.savedText.equals(editor.text());
    }

    /** 先写同目录临时文件，写入成功后替换原文件；失败时保留编辑缓冲。 */
    public void save() throws IOException {
        String text = editor.text();
        Path path = path();
        Path temporary = Files.createTempFile(path.getParent(), ".craken-save-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            shared.savedText = text;
            shared.dirty.set(false);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 同一文件的多个分屏共享脏状态和保存基线，最后一个视图关闭时才提示保存。 */
    private static final class SharedFile {
        private final Path path;
        private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
        private String savedText;
        private int views;

        private SharedFile(Path path, String source) {
            this.path = path;
            this.savedText = source;
        }
    }
}
