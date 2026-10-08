package craken.ui.interaction.inputoutput;

import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;
import javafx.collections.ListChangeListener;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 以编辑器组件为键的 IO 标签表：同一组件的运行与调试共用、重复运行复用同一个 IO 列表项。
 *
 * <p>复用只替换 {@link InputOutputTab} 里的内容通道，标题、编号和列表位置都保持不变；
 * 用户关闭该项后，下一次运行按既有编号规则新建。编辑器组件关闭后映射被丢弃，
 * 已打开的输出仍保持可读，不随编辑器关闭而消失。</p>
 */
public final class InputOutputTabs implements AutoCloseable {
    /** 一个编辑器组件对应的 IO 内容宿主与列表项。 */
    public record Entry(InputOutputTab tab, InteractionItem<InputOutputTab> item) {
        public Entry {
            Objects.requireNonNull(tab, "tab");
            Objects.requireNonNull(item, "item");
        }
    }

    private final EditorArea editors;
    private final InteractionArea interactions;
    private final Map<EditorFile, Entry> entries = new IdentityHashMap<>();
    private final ListChangeListener<EditorFile> filesChanged = change -> {
        while (change.next()) {
            if (!change.wasRemoved()) continue;
            change.getRemoved().forEach(entries::remove);
        }
    };
    private boolean closed;

    public InputOutputTabs(EditorArea editors, InteractionArea interactions) {
        this.editors = Objects.requireNonNull(editors, "editors");
        this.interactions = Objects.requireNonNull(interactions, "interactions");
        editors.files().addListener(filesChanged);
    }

    /** 该编辑器组件当前的 IO 项；不存在或已被用户关闭时，用下一个编号新建（不自动选中）。 */
    public Entry open(EditorFile file) {
        Objects.requireNonNull(file, "file");
        if (closed) throw new IllegalStateException("input/output tabs are closed");
        if (!editors.files().contains(file)) throw new IllegalArgumentException("file is not open in this editor area");
        Entry existing = entries.get(file);
        if (existing != null && !existing.item().isClosed()) return existing;
        InputOutputTab tab = new InputOutputTab();
        InteractionItem<InputOutputTab> item = new InteractionItem<>(interactions.nextInputOutputTitle(),
                tab, tab::start, tab::activate, tab::close);
        interactions.addItem(item, false);
        Entry entry = new Entry(tab, item);
        entries.put(file, entry);
        return entry;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        editors.files().removeListener(filesChanged);
        entries.clear();
    }
}
