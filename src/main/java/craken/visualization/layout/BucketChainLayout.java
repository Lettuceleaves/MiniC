package craken.visualization.layout;

import craken.visualization.api.ViewLocation;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

/**
 * Layout for chained bucket arrays: the macro unit named by the page display root is the bucket
 * list, and every chain leaving one of its slot members is placed on the aligned row of that slot,
 * with columns advancing to the right. Structurally allocated entries keep their gap so the
 * contiguous bucket cells stay visibly different from the detached chain nodes.
 */
public final class BucketChainLayout implements LayoutEngine {
    @Override public LayoutResult layout(LayoutRequest request, CancellationToken cancellation) {
        cancellation.check();
        if (request.units().isEmpty()) return LayoutSupport.finish(request, Map.of(), cancellation, "buckets");
        var units = new LinkedHashMap<ViewLocation, Unit>();
        var owners = new HashMap<ViewLocation, ViewLocation>();
        for (var unit : request.units()) {
            units.put(unit.node(), unit);
            for (var member : unit.members()) owners.put(member.node(), unit.node());
        }
        ViewLocation container = selectContainer(request, owners);
        if (container == null || units.get(container).members().size() < 2)
            return new ArrayLayout().layout(request, cancellation);
        var slots = units.get(container).members().stream().filter(member -> !member.node().equals(container)).toList();

        var outgoing = new LinkedHashMap<ViewLocation, List<Link>>();
        for (var link : request.links()) {
            if (!owners.containsKey(link.tail().node()) || !owners.containsKey(link.head().node())) continue;
            outgoing.computeIfAbsent(link.tail().node(), ignored -> new ArrayList<>()).add(link);
        }
        // Row r walks the chain that leaves slot r; a unit already placed by an earlier row is not moved.
        var placed = new LinkedHashMap<ViewLocation, Placement>();
        var rows = new ArrayList<List<ViewLocation>>();
        int widestDepth = 1;
        for (int row = 0; row < slots.size(); row++) {
            cancellation.check();
            var chain = new ArrayList<ViewLocation>();
            var visited = new HashSet<ViewLocation>();
            ViewLocation current = slots.get(row).node();
            while (true) {
                var next = outgoing.getOrDefault(current, List.of()).stream()
                        .map(link -> link.head().node())
                        .filter(owners::containsKey).filter(node -> !owners.get(node).equals(container))
                        .filter(visited::add).findFirst().orElse(null);
                if (next == null) break;
                var unit = owners.get(next);
                if (placed.containsKey(unit)) break;
                var placement = new Placement(chain.size() + 1);
                placed.put(unit, placement);
                chain.add(unit);
                widestDepth = Math.max(widestDepth, placement.column());
                current = next;
            }
            rows.add(chain);
        }
        // Units with no bucket row (not yet linked, or shared) keep a readable trailing row.
        var trailing = new ArrayList<ViewLocation>();
        for (var unit : request.units()) if (!unit.node().equals(container) && !placed.containsKey(unit.node())) {
            placed.put(unit.node(), new Placement(1));
            trailing.add(unit.node());
        }

        var columnWidths = new double[widestDepth + 1];
        columnWidths[0] = units.get(container).size().width();
        for (var entry : placed.entrySet())
            columnWidths[entry.getValue().column()] = Math.max(columnWidths[entry.getValue().column()],
                    units.get(entry.getKey()).size().width());
        var xs = new double[widestDepth + 1];
        xs[0] = request.hints().padding();
        for (int column = 1; column <= widestDepth; column++)
            xs[column] = xs[column - 1] + columnWidths[column - 1] + request.hints().horizontalGap();

        var positions = new LinkedHashMap<ViewLocation, Point>();
        positions.put(container, new Point(xs[0], request.hints().padding()));
        double containerBottom = request.hints().padding() + units.get(container).size().height();
        for (int row = 0; row < slots.size(); row++) {
            cancellation.check();
            double center = request.hints().padding() + slots.get(row).bounds().center().y();
            for (var unit : rows.get(row)) {
                var placement = placed.get(unit);
                positions.put(unit, new Point(xs[placement.column()],
                        Math.max(request.hints().padding(), center - units.get(unit).size().height() / 2)));
            }
        }
        double y = containerBottom + request.hints().verticalGap();
        for (int index = 0; index < trailing.size(); index++) {
            var unit = trailing.get(index);
            positions.put(unit, new Point(xs[1], y));
            y += units.get(unit).size().height() + request.hints().verticalGap();
        }
        return LayoutSupport.finish(request, positions, cancellation, "buckets");
    }

    private record Placement(int column) {}

    private static ViewLocation selectContainer(LayoutRequest request, Map<ViewLocation, ViewLocation> owners) {
        var root = request.hints().treeRoot();
        if (root != null && owners.containsKey(root)) return owners.get(root);
        var degree = new LinkedHashMap<ViewLocation, Integer>();
        for (var link : request.links()) {
            var tail = owners.get(link.tail().node());
            if (tail != null) degree.merge(tail, 1, Integer::sum);
        }
        ViewLocation best = null; int bestDegree = 0;
        for (var unit : request.units()) {
            int value = degree.getOrDefault(unit.node(), 0);
            if (value > bestDegree) { best = unit.node(); bestDegree = value; }
        }
        return best;
    }
}
