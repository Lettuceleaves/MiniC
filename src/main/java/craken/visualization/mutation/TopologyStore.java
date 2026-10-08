package craken.visualization.mutation;

import craken.visualization.api.*;
import craken.visualization.model.*;
import craken.visualization.model.relation.TopologyEdge;
import craken.visualization.style.EdgeStyle;
import java.util.*;
import static craken.visualization.api.VisualizationError.Code.*;

public final class TopologyStore {
    private TopologyStore() {}
    public static PageModel connect(PageModel page, ViewLocation a, ViewLocation b,
                                    TopologyEdge.Direction direction, MonotonicIds ids) {
        return connect(page, a, b, direction, "node", "node", EdgeStyle.DEFAULT, ids);
    }
    public static PageModel connect(PageModel page, ViewLocation a, ViewLocation b, TopologyEdge.Direction direction,
                                    String aPort, String bPort, EdgeStyle style, MonotonicIds ids) {
        requireSamePage(page, a, b);
        validatePort(page.nodes().get(a.nodeId()), aPort); validatePort(page.nodes().get(b.nodeId()), bPort);
        if (a.nodeId() > b.nodeId() || a.equals(b) && aPort.compareTo(bPort) > 0) {
            var swap = a; a = b; b = swap; var port = aPort; aPort = bPort; bPort = port; direction = direction.reversed();
        }
        var edges = new LinkedHashMap<>(page.topology());
        long existing = find(page, a, b, aPort, bPort);
        long id = existing == 0 ? ids.next() : existing;
        edges.put(id, new TopologyEdge(id, a, b, direction, aPort, bPort, style));
        var ready = new HashSet<>(page.ready());
        ready.remove(a.nodeId());
        ready.remove(b.nodeId());
        return page.withTopology(edges, ready);
    }
    public static PageModel disconnect(PageModel page, ViewLocation a, ViewLocation b) {
        return disconnect(page, a, b, "node", "node");
    }
    public static PageModel disconnect(PageModel page, ViewLocation a, ViewLocation b, String aPort, String bPort) {
        requireSamePage(page, a, b);
        var edges = new LinkedHashMap<>(page.topology());
        edges.remove(find(page, a, b, aPort, bPort));
        return page.withTopology(edges, page.ready());
    }
    private static long find(PageModel page, ViewLocation a, ViewLocation b, String aPort, String bPort) {
        for (var edge : page.topology().values())
            if ((edge.a().equals(a) && edge.b().equals(b) && edge.aPort().equals(aPort) && edge.bPort().equals(bPort))
                    || (edge.a().equals(b) && edge.b().equals(a) && edge.aPort().equals(bPort) && edge.bPort().equals(aPort)))
                return edge.id();
        return 0;
    }
    public static void validatePort(ViewNode node, String key) {
        TopologyEdge.requirePort(key);
        if (node == null) throw new IllegalArgumentException("Topology port belongs to a missing node");
        if (Set.of("node", "north", "east", "south", "west").contains(key)) return;
        // field: 是字段行的东侧入口，field-west: 是同高的西侧入口（链表/回边可以水平接入）。
        String field = key.startsWith("field-west:") ? key.substring("field-west:".length())
                : key.startsWith("field:") ? key.substring("field:".length()) : null;
        if (field == null || !node.content().fields().containsKey(field))
            throw new IllegalArgumentException("Unknown topology port: " + key);
    }
    private record Endpoint(ViewLocation node, String port) {}
    /** Shared final-transaction and snapshot check; does not mutate a page or invoke JavaFX. */
    public static void validate(PageModel page) {
        var pairs = new HashSet<Set<Endpoint>>();
        for (var edge : page.topology().values()) {
            if (!edge.a().page().equals(page.ref()) || !edge.b().page().equals(page.ref()) || edge.direction() == null || edge.style() == null)
                throw new IllegalArgumentException("Invalid topology endpoints/style");
            validatePort(page.nodes().get(edge.a().nodeId()), edge.aPort()); validatePort(page.nodes().get(edge.b().nodeId()), edge.bPort());
            if (!pairs.add(new HashSet<>(List.of(new Endpoint(edge.a(), edge.aPort()), new Endpoint(edge.b(), edge.bPort())))))
                throw new IllegalArgumentException("Duplicate concrete topology port pair");
        }
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
