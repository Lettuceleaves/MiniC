package craken.visualization.mutation;

import craken.visualization.api.ViewLocation;
import craken.visualization.model.*;
import java.util.*;

/** Computes the complete release set against final explicit references before any cleanup occurs. */
public final class LifetimePlanner {
    private LifetimePlanner() {}
    public static Set<ViewLocation> plan(Map<Long, PageModel> pages, OwnershipStore ownership,
                                         Set<ViewLocation> explicit) {
        var all = new LinkedHashMap<ViewLocation, ViewNode>();
        pages.values().forEach(page -> page.nodes().values().forEach(node -> all.put(node.location(), node)));
        var counts = new HashMap<ViewLocation, Integer>();
        var queue = new ArrayDeque<>(explicit);
        all.forEach((location, node) -> {
            int count = ownership.parents(location).size();
            counts.put(location, count);
            if (node.retention() == ViewNode.Retention.OWNED && count == 0) queue.add(location);
        });
        var deleted = new LinkedHashSet<ViewLocation>();
        while (!queue.isEmpty()) {
            var location = queue.removeFirst();
            if (!deleted.add(location)) continue;
            var node = all.get(location);
            if (node == null) throw new IllegalArgumentException("Unknown deletion target");
            queue.addAll(node.children());
            for (var child : ownership.children(location)) {
                int remaining = counts.compute(child, (unused, old) -> Objects.requireNonNull(old) - 1);
                if (remaining == 0 && all.get(child).retention() == ViewNode.Retention.OWNED)
                    queue.addLast(child);
            }
        }
        return Collections.unmodifiableSet(deleted);
    }
}
