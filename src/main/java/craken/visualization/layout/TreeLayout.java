package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

public final class TreeLayout implements LayoutEngine {
    @Override public LayoutResult layout(LayoutRequest request, CancellationToken cancellation) {
        cancellation.check();
        var ordered = LayoutSupport.ordered(request);
        if (ordered.isEmpty()) return LayoutSupport.finish(request, Map.of(), cancellation, "tree");
        var owner = new HashMap<ViewLocation, ViewLocation>();
        var units = new HashMap<ViewLocation, Unit>();
        var adjacency = new LinkedHashMap<ViewLocation, Set<ViewLocation>>();
        for (var unit : ordered) {
            units.put(unit.node(), unit); adjacency.put(unit.node(), new LinkedHashSet<>());
            for (var member : unit.members()) owner.put(member.node(), unit.node());
        }
        for (var edge : request.links()) {
            var a = owner.get(edge.tail().node()); var b = owner.get(edge.head().node());
            if (a.equals(b)) {
                if (edge.tail().node().equals(edge.head().node())) invalid();
                continue; // Internal composite edges do not create independent macro vertices.
            }
            adjacency.get(a).add(b); adjacency.get(b).add(a);
        }
        long edgeCount = adjacency.values().stream().mapToLong(Set::size).sum() / 2;
        if (edgeCount != units.size() - 1) invalid();
        var rank = new HashMap<ViewLocation, Integer>();
        for (int i = 0; i < ordered.size(); i++) rank.put(ordered.get(i).node(), i);
        var root = request.hints().treeRoot() == null ? ordered.getFirst().node() : owner.get(request.hints().treeRoot());
        var parent = new HashMap<ViewLocation, ViewLocation>();
        var children = new HashMap<ViewLocation, List<ViewLocation>>();
        var depths = new HashMap<ViewLocation, Integer>();
        var queue = new ArrayDeque<ViewLocation>(); var traversal = new ArrayList<ViewLocation>();
        parent.put(root, root); depths.put(root, 0); queue.add(root);
        var levelHeights = new ArrayList<Double>();
        while (!queue.isEmpty()) {
            cancellation.check(); var current = queue.removeFirst(); traversal.add(current);
            int depth = depths.get(current);
            if (levelHeights.size() <= depth) levelHeights.add(0.0);
            levelHeights.set(depth, Math.max(levelHeights.get(depth), units.get(current).size().height()));
            var next = adjacency.get(current).stream().filter(n -> !parent.containsKey(n))
                    .sorted(Comparator.comparingInt(rank::get)).toList();
            children.put(current, next);
            for (var child : next) { parent.put(child, current); depths.put(child, depth + 1); queue.add(child); }
        }
        if (traversal.size() != units.size()) invalid();
        var subtreeWidths = new HashMap<ViewLocation, Double>();
        for (int i = traversal.size() - 1; i >= 0; i--) {
            var current = traversal.get(i); var list = children.get(current);
            double width = list.stream().mapToDouble(subtreeWidths::get).sum()
                    + Math.max(0, list.size() - 1) * request.hints().horizontalGap();
            subtreeWidths.put(current, Math.max(width, units.get(current).size().width()));
        }
        var ys = new double[levelHeights.size()]; ys[0] = request.hints().padding();
        for (int i = 1; i < ys.length; i++) ys[i] = ys[i - 1] + levelHeights.get(i - 1) + request.hints().verticalGap();
        var lefts = new HashMap<ViewLocation, Double>(); lefts.put(root, request.hints().padding());
        var positions = new LinkedHashMap<ViewLocation, Point>();
        for (var current : traversal) {
            cancellation.check(); double left = lefts.get(current), width = subtreeWidths.get(current);
            positions.put(current, new Point(left + (width - units.get(current).size().width()) / 2, ys[depths.get(current)]));
            var list = children.get(current);
            double total = list.stream().mapToDouble(subtreeWidths::get).sum()
                    + Math.max(0, list.size() - 1) * request.hints().horizontalGap();
            double childLeft = left + (width - total) / 2;
            for (var child : list) { lefts.put(child, childLeft); childLeft += subtreeWidths.get(child) + request.hints().horizontalGap(); }
        }
        return LayoutSupport.finish(request, positions, cancellation, "tree");
    }
    private static void invalid() { throw new LayoutException(LayoutException.Code.INVALID_TOPOLOGY, "Tree layout requires one acyclic connected macro topology"); }
}
