package craken.visualization.api;

import java.util.*;

/** Caller-owned presentation preferences; no pointer field is interpreted as a tree relationship. */
public record PageLayoutHints(ViewLocation treeRoot, List<ViewLocation> order) {
    public static final PageLayoutHints EMPTY = new PageLayoutHints(null, List.of());
    public PageLayoutHints {
        order = List.copyOf(order);
        if (new HashSet<>(order).size() != order.size()) throw new IllegalArgumentException("Duplicate layout order entry");
    }
    public void validate(PageRef page, Set<Long> nodes) {
        if (treeRoot != null) requireMember(treeRoot, page, nodes);
        order.forEach(node -> requireMember(node, page, nodes));
    }
    private static void requireMember(ViewLocation node, PageRef page, Set<Long> nodes) {
        if (!node.page().equals(page) || !nodes.contains(node.nodeId())) throw new IllegalArgumentException("Layout hint must name a live node in its page");
    }
    public PageLayoutHints without(Set<ViewLocation> deleted) {
        return new PageLayoutHints(treeRoot != null && deleted.contains(treeRoot) ? null : treeRoot,
                order.stream().filter(node -> !deleted.contains(node)).toList());
    }
}
