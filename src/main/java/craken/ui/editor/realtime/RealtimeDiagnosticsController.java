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
            RealtimeDiagnosticsPanel panel = bindings.get(path).content();
            for (EditorFile view : live.get(path)) view.setOnDiagnosticsChanged(panel::update);
        }
        for (Iterator<Path> iterator = bindings.keySet().iterator(); iterator.hasNext(); ) {
            Path path = iterator.next();
            if (live.containsKey(path)) continue;
            interactions.closeItem(bindings.get(path));
            iterator.remove();
        }
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
