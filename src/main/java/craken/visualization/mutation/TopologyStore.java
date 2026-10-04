package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.TopologyEdge;
import java.util.*;
import static craken.visualization.api.VisualizationError.Code.*;

public final class TopologyStore {
    private TopologyStore() {}
    public static PageModel connect(PageModel page, ViewLocation a, ViewLocation b,
                                    TopologyEdge.Direction direction, MonotonicIds ids) {
        requireSamePage(page, a, b);
        if (a.nodeId() > b.nodeId()) { var swap = a; a = b; b = swap; direction = direction.reversed(); }
        var edges = new LinkedHashMap<>(page.topology());
        long existing = find(page, a, b);
        long id = existing == 0 ? ids.next() : existing;
        edges.put(id, new TopologyEdge(id, a, b, direction));
        var ready = new HashSet<>(page.ready());
        ready.remove(a.nodeId());
        ready.remove(b.nodeId());
        return page.withTopology(edges, ready);
    }
    public static PageModel disconnect(PageModel page, ViewLocation a, ViewLocation b) {
        requireSamePage(page, a, b);
        var edges = new LinkedHashMap<>(page.topology());
        edges.remove(find(page, a, b));
        return page.withTopology(edges, page.ready());
    }
    private static long find(PageModel page, ViewLocation a, ViewLocation b) {
        for (var edge : page.topology().values())
            if ((edge.a().equals(a) && edge.b().equals(b)) || (edge.a().equals(b) && edge.b().equals(a)))
                return edge.id();
        return 0;
    }
    private static void requireSamePage(PageModel page, ViewLocation a, ViewLocation b) {
        if (!a.page().equals(page.ref()) || !b.page().equals(page.ref()))
            throw CommandValidator.failure(INVALID_COMMAND, "Topology must stay in one page");
    }
    public static Map<Long, Set<Long>> adjacency(PageModel page) {
        var neighbors = new LinkedHashMap<Long, Set<Long>>();
        page.nodes().keySet().forEach(id -> neighbors.put(id, new LinkedHashSet<>()));
        page.topology().values().forEach(edge -> link(neighbors, edge.a().nodeId(), edge.b().nodeId()));
        page.composition().values().forEach(link -> link(neighbors, link.parent().nodeId(), link.child().nodeId()));
        return neighbors;
    }
    private static void link(Map<Long, Set<Long>> neighbors, long a, long b) {
        if (!neighbors.containsKey(a) || !neighbors.containsKey(b))
            throw CommandValidator.failure(UNKNOWN_POSITION, "Dangling display edge");
        neighbors.get(a).add(b);
        neighbors.get(b).add(a);
    }
}
