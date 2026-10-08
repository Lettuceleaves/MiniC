package craken.ui.interaction.cases;

import craken.ui.interaction.InteractionArea;
import craken.ui.interaction.InteractionItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 以源文件路径为键的用例标签表。
 *
 * <p>同一个源文件可以有多个用例标签，运行该文件时它们一起执行；编辑器关闭不会删除标签，
 * 用户预置的输入保留在标签里，重新打开同一文件后这些标签仍然绑定并在下一次运行时执行。
 * 用户关闭某个标签后，该标签不再参与后续运行，也不会按编号规则自动重建。</p>
 */
public final class CaseTabs implements AutoCloseable {
    /** 一个源文件绑定的用例列表项。 */
    public record Entry(InteractionItem<CasePanel> item) {
        public Entry {
            Objects.requireNonNull(item, "item");
        }
    }

    private final InteractionArea interactions;
    private final Map<Path, List<Entry>> bindings = new LinkedHashMap<>();
    private boolean closed;

    public CaseTabs(InteractionArea interactions) {
        this.interactions = Objects.requireNonNull(interactions, "interactions");
    }

    /** 新建一个绑定到该源文件的用例标签；新标签成为当前交互项并直接进入输入状态。 */
    public Entry open(Path sourcePath) {
        if (closed) throw new IllegalStateException("case tabs are closed");
        Objects.requireNonNull(sourcePath, "sourcePath");
        CasePanel panel = new CasePanel(sourcePath);
        InteractionItem<CasePanel> item = new InteractionItem<>(interactions.nextCaseTitle(), panel,
                panel::start, panel::activate, panel::close);
        panel.setOnDeleteRequest(() -> interactions.closeItem(item));
        // 判定写入列表项：通过 = 深绿，不匹配 = 深红，其余保持中性。
        panel.verdictProperty().addListener((observable, previous, next) -> item.setResult(switch (next) {
            case NONE -> InteractionItem.Result.NONE;
            case PASSED -> InteractionItem.Result.PASSED;
            case FAILED -> InteractionItem.Result.FAILED;
        }));
        interactions.addItem(item);
        Entry entry = new Entry(item);
        bindings.computeIfAbsent(sourcePath.toAbsolutePath().normalize(), ignored -> new ArrayList<>()).add(entry);
        return entry;
    }

    /** 该源文件当前绑定的、仍未关闭的用例，按列表顺序。 */
    public List<Entry> entries(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        List<Entry> entries = bindings.get(sourcePath.toAbsolutePath().normalize());
        if (entries == null) return List.of();
        entries.removeIf(entry -> entry.item().isClosed());
        return List.copyOf(entries);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        // 列表项由交互区统一关闭；这里只丢弃绑定，避免迟到运行继续引用已释放的标签。
        bindings.clear();
    }
}
