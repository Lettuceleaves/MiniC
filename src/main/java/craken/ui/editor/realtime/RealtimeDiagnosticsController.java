package craken.ui.editor.realtime;

import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import craken.ui.interaction.diagnostics.RealtimeDiagnosticsPanel;
import javafx.collections.ListChangeListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 把每个打开的编辑器绑定到底部交互栏的一个“报错信息”标签。
 *
 * <p>文件首次打开时创建面板标签；同一文件的分屏视图共享文档，各视图的分析结果都汇入
 * 同一个面板；最后一个视图关闭时移除标签。标签挂载时不抢占当前交互项。</p>
 */
public final class RealtimeDiagnosticsController implements AutoCloseable {
    private final EditorArea editors;
    private final InteractionArea interactions;
    private final Map<Path, InteractionItem<RealtimeDiagnosticsPanel>> bindings = new LinkedHashMap<>();
    private final ListChangeListener<EditorFile> filesChanged = change -> sync();
    private boolean closed;

    public RealtimeDiagnosticsController(EditorArea editors, InteractionArea interactions) {
        this.editors = Objects.requireNonNull(editors, "editors");
        this.interactions = Objects.requireNonNull(interactions, "interactions");
        editors.files().addListener(filesChanged);
        sync();
    }

    /** 把一批诊断写进该文件绑定的 ERR 标签；标签不存在就新建绑定，已存在就复用并整体替换内容。 */
    public void publish(EditorFile file, List<RealtimeDiagnostic> diagnostics) {
        if (closed) return;
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(diagnostics, "diagnostics");
        try {
            panelFor(file).update(diagnostics);
        } catch (IllegalStateException areaClosed) {
            // 交互区已关闭时丢弃迟到输出。
        }
    }

    private void sync() {
        if (closed) return;
        Map<Path, List<EditorFile>> live = new LinkedHashMap<>();
        for (EditorFile file : editors.files()) {
            live.computeIfAbsent(file.path(), ignored -> new ArrayList<>()).add(file);
        }
        for (Path path : live.keySet()) {
            if (!bindings.containsKey(path)) {
                InteractionItem<RealtimeDiagnosticsPanel> item = interactions.newDiagnostics();
                bindings.put(path, item);
            }
        }
        // 所有视图都向同一面板汇报；分屏共享同一文档，重复推送的内容一致，不会闪烁。
        for (Path path : live.keySet()) {
            for (EditorFile view : live.get(path)) view.setOnDiagnosticsChanged(publisher(view));
        }
        for (Iterator<Path> iterator = bindings.keySet().iterator(); iterator.hasNext(); ) {
            Path path = iterator.next();
            if (live.containsKey(path)) continue;
            interactions.closeItem(bindings.get(path));
            iterator.remove();
        }
    }

    /** 确保标签存在（用户手动关闭后按需重建），并让该文件的所有视图都汇入同一面板。 */
    private RealtimeDiagnosticsPanel panelFor(EditorFile file) {
        Path path = file.path();
        InteractionItem<RealtimeDiagnosticsPanel> item = bindings.get(path);
        if (item == null || item.isClosed()) {
            item = interactions.newDiagnostics();
            bindings.put(path, item);
            for (EditorFile view : editors.files()) {
                if (view.path().equals(path)) view.setOnDiagnosticsChanged(publisher(view));
            }
        }
        return item.content();
    }

    private Consumer<List<RealtimeDiagnostic>> publisher(EditorFile view) {
        return diagnostics -> publish(view, diagnostics);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        editors.files().removeListener(filesChanged);
        for (InteractionItem<RealtimeDiagnosticsPanel> item : bindings.values()) {
            interactions.closeItem(item);
        }
        bindings.clear();
    }
}
