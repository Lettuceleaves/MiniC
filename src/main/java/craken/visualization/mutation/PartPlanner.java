package craken.visualization.mutation;

import craken.visualization.model.*;
import java.util.*;

/** Recomputes weak components, including virtual composition edges, without recursion. */
public final class PartPlanner {
    private PartPlanner() {}
    public static PageModel plan(PageModel page) {
        var adjacency = TopologyStore.adjacency(page);
        var remaining = new TreeSet<>(page.nodes().keySet());
        var ready = new HashSet<>(page.ready());
        ready.retainAll(remaining);
        var parts = new LinkedHashMap<Long, Part>();
        var membership = new LinkedHashMap<Long, Long>();
        while (!remaining.isEmpty()) {
            long seed = remaining.first();
            var queue = new ArrayDeque<Long>();
            var component = new LinkedHashSet<Long>();
            queue.add(seed);
            remaining.remove(seed);
            boolean displayed = false;
            while (!queue.isEmpty()) {
                long current = queue.removeFirst();
                component.add(current);
                displayed |= !ready.contains(current);
                for (long neighbor : adjacency.get(current)) if (remaining.remove(neighbor)) queue.addLast(neighbor);
            }
            if (displayed) {
                ready.removeAll(component);
                parts.put(seed, new Part(seed, component));
                component.forEach(member -> membership.put(member, seed));
            }
        }
        return new PageModel(page.ref(), page.type(), page.nodes(), page.anchor(), page.composition(),
                page.topology(), ready, parts, membership);
    }
}
