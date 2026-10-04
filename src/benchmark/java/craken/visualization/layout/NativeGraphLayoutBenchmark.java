package craken.visualization.layout;

import craken.visualization.api.*;
import craken.visualization.layout.graphviz.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static craken.visualization.layout.LayoutRequest.*;

/** Reproducible native qualification; this is a measurement harness, not a latency assertion. */
public final class NativeGraphLayoutBenchmark {
    private static final PageRef PAGE = new PageRef(1, 1);
    private record Graph(List<Unit> units, List<Link> links) {}
    private record Batch(List<LayoutResult> results, double protocolMs, double nativeMs, double parseMs, double totalMs) {}
    private static ViewLocation node(long id) { return new ViewLocation(1, 1, id); }
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("Usage: NativeGraphLayoutBenchmark <runtime-directory> <report.json>");
        var runtime = Path.of(arguments[0]).toAbsolutePath(); var destination = Path.of(arguments[1]).toAbsolutePath();
        var report = new LinkedHashMap<String, Object>(); var samples = new ArrayList<Map<String, Object>>();
        report.put("version", "16.1.0"); report.put("runtime", runtime.toString()); report.put("java", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("seed", 42); report.put("warmup", 1); report.put("measuredIterations", 3);
        report.put("timingDefinition", "nativeProcessMs includes process startup, neato execution and pipe transport; startupProbeMs is a separate -V probe, not subtracted from layout time");
        report.put("fx", "FX measurement/rendering is recorded separately by the renderer/host qualification; this harness loads no JavaFX");
        try (var bridge = new GraphvizProcessBridge(runtime, Duration.ofSeconds(30))) {
            long started = System.nanoTime(); bridge.version(); report.put("startupProbeMs", millis(started));
            for (int[] input : List.of(new int[]{50, 30}, new int[]{100, 250})) {
                for (var direction : List.of(Direction.NONE, Direction.FORWARD)) {
                    var graph = graph(input[0], input[1], direction);
                    for (boolean components : List.of(false, true)) {
                        execute(bridge, graph, components); // Warmup is deliberately not reported as a measured sample.
                        for (int iteration = 1; iteration <= 3; iteration++) {
                            var batch = execute(bridge, graph, components);
                            var sample = describe(input[0] + "/" + input[1], direction, components, iteration, graph, batch);
                            samples.add(sample);
                        }
                    }
                }
            }
            var before = graph(100, 250, Direction.NONE); var base = execute(bridge, before, true);
            var addedUnits = new ArrayList<>(before.units()); addedUnits.add(Unit.simple(node(101), 104, 56));
            var addedLinks = new ArrayList<>(before.links()); addedLinks.add(link(251, 1, 101, Direction.NONE)); addedLinks.add(link(252, 2, 101, Direction.NONE));
            var grownUnits = before.units().stream().map(u -> u.node().nodeId() == 10 ? Unit.simple(u.node(), u.size().width() + 180, u.size().height()) : u).toList();
            var incremental = new ArrayList<Map<String, Object>>();
            incremental.add(change("add-linked-node", base, execute(bridge, new Graph(addedUnits, addedLinks), true)));
            incremental.add(change("remove-node", execute(bridge, new Graph(addedUnits, addedLinks), true), base));
            incremental.add(change("grow-measured-text-width", base, execute(bridge, new Graph(grownUnits, before.links()), true)));
            var pathUnits = new ArrayList<Unit>(); var pathLinks = new ArrayList<Link>();
            for (int i = 1; i <= 32; i++) pathUnits.add(Unit.simple(node(i), 80, 40));
            for (int i = 1; i <= 30; i++) pathLinks.add(link(i, i, i + 1, Direction.NONE));
            var path = execute(bridge, new Graph(pathUnits, pathLinks), true);
            var merged = new ArrayList<>(pathLinks); merged.add(link(31, 1, 32, Direction.NONE));
            incremental.add(change("merge-parts-local-coordinates", path, execute(bridge, new Graph(pathUnits, merged), true)));
            var split = pathLinks.stream().filter(e -> e.id() != 15).toList();
            incremental.add(change("split-parts-local-coordinates", path, execute(bridge, new Graph(pathUnits, split), true)));
            report.put("incremental", incremental);
            report.put("activeProcessesAfterCompletion", bridge.activeProcessCount());
        }
        report.put("samples", samples); Files.createDirectories(destination.getParent()); Files.writeString(destination, json(report) + "\n");
        System.out.println("Recorded " + samples.size() + " native samples and 5 incremental cases: " + destination);
    }
    private static Graph graph(int count, int edges, Direction direction) {
        var units = new ArrayList<Unit>(); var links = new ArrayList<Link>(); var pairs = new HashSet<String>();
        for (int i = 1; i <= count; i++) units.add(Unit.simple(node(i), 80 + i % 5 * 12, 40 + i % 3 * 8));
        // Dense comparison input is connected. The sparse input retains its disconnected components.
        if (edges >= count - 1) for (int i = 1; i < count; i++) { pairs.add(i + ":" + (i + 1)); links.add(link(links.size() + 1, i, i + 1, direction)); }
        var random = new Random(42);
        while (links.size() < edges) {
            int a = 1 + random.nextInt(count), b = 1 + random.nextInt(count); if (a == b) continue;
            int low = Math.min(a, b), high = Math.max(a, b);
            if (pairs.add(low + ":" + high)) links.add(link(links.size() + 1, a, b, direction));
        }
        return new Graph(List.copyOf(units), List.copyOf(links));
    }
    private static Link link(long id, long a, long b, Direction direction) { return new Link(id, PortRef.node(node(a)), PortRef.node(node(b)), direction); }
    private static List<Graph> components(Graph graph) {
        var adjacency = new LinkedHashMap<ViewLocation, Set<ViewLocation>>();
        graph.units().forEach(u -> adjacency.put(u.node(), new LinkedHashSet<>()));
        graph.links().forEach(e -> { adjacency.get(e.tail().node()).add(e.head().node()); adjacency.get(e.head().node()).add(e.tail().node()); });
        var visited = new HashSet<ViewLocation>(); var result = new ArrayList<Graph>();
        for (var entry : adjacency.keySet()) if (visited.add(entry)) {
            var members = new HashSet<ViewLocation>(); var queue = new ArrayDeque<ViewLocation>(); queue.add(entry);
            while (!queue.isEmpty()) { var current = queue.remove(); members.add(current); for (var next : adjacency.get(current)) if (visited.add(next)) queue.add(next); }
            result.add(new Graph(graph.units().stream().filter(u -> members.contains(u.node())).toList(),
                    graph.links().stream().filter(e -> members.contains(e.tail().node())).toList()));
        }
        return result;
    }
    private static Batch execute(GraphvizProcessBridge bridge, Graph graph, boolean separateParts) {
        long started = System.nanoTime(); double protocol = 0, nativeTime = 0, parsing = 0;
        var results = new ArrayList<LayoutResult>();
        for (var part : separateParts ? components(graph) : List.of(graph)) {
            var request = new LayoutRequest(new Stamp(PAGE, part.units().getFirst().node().nodeId(), 0, 0, 1, 1000),
                    Kind.GRAPH, part.units(), part.links(), Hints.defaults(), Map.of());
            long phase = System.nanoTime(); var encoded = new DotGraphWriter().write(request); protocol += millis(phase);
            var output = bridge.execute(encoded.dot(), CancellationToken.NONE); nativeTime += output.elapsed().toNanos() / 1_000_000.0;
            phase = System.nanoTime(); var result = new PlainExtReader().read(output.stdout(), encoded, request, "16.1.0"); parsing += millis(phase);
            if (result.nodeBounds().size() != part.units().size() || result.edgePaths().size() != part.links().size()) throw new IllegalStateException("Incomplete benchmark geometry");
            results.add(result);
        }
        return new Batch(results, protocol, nativeTime, parsing, millis(started));
    }
    private static Map<String, Object> describe(String input, Direction direction, boolean parts, int iteration, Graph graph, Batch batch) {
        var result = new LinkedHashMap<String, Object>(); result.put("input", input); result.put("direction", direction.name());
        result.put("mode", parts ? "product-per-part" : "whole-graph-comparison"); result.put("iteration", iteration);
        result.put("nodes", graph.units().size()); result.put("edges", graph.links().size()); result.put("nativeRequests", batch.results().size());
        result.put("protocolMs", batch.protocolMs()); result.put("nativeProcessMs", batch.nativeMs()); result.put("parseMs", batch.parseMs()); result.put("totalMs", batch.totalMs());
        int overlaps = 0;
        for (var layout : batch.results()) {
            var bounds = new ArrayList<>(layout.nodeBounds().values());
            for (int a = 0; a < bounds.size(); a++) for (int b = a + 1; b < bounds.size(); b++) {
                var x = bounds.get(a); var y = bounds.get(b);
                if (x.x() < y.right() - 1e-5 && x.right() > y.x() + 1e-5 && x.y() < y.bottom() - 1e-5 && x.bottom() > y.y() + 1e-5) overlaps++;
            }
        }
        if (overlaps != 0) throw new IllegalStateException("Overlap in qualified native geometry");
        result.put("overlapPairs", overlaps); return result;
    }
    private static Map<ViewLocation, Rect> bounds(Batch batch) {
        var result = new HashMap<ViewLocation, Rect>(); batch.results().forEach(r -> result.putAll(r.nodeBounds())); return result;
    }
    private static Map<String, Object> change(String operation, Batch before, Batch after) {
        var old = bounds(before); var updated = bounds(after);
        var common = old.keySet().stream().filter(updated::containsKey).sorted(Comparator.comparingLong(ViewLocation::nodeId)).toList();
        double dx = 0, dy = 0;
        for (var id : common) { dx += updated.get(id).center().x() - old.get(id).center().x(); dy += updated.get(id).center().y() - old.get(id).center().y(); }
        dx /= common.size(); dy /= common.size(); var distances = new ArrayList<Double>();
        for (var id : common) distances.add(Math.hypot(updated.get(id).center().x() - old.get(id).center().x() - dx,
                updated.get(id).center().y() - old.get(id).center().y() - dy));
        distances.sort(Double::compare); int changes = 0, triangles = 0;
        for (int i = 0; i + 2 < common.size(); i += 3) {
            double a = area(old.get(common.get(i)).center(), old.get(common.get(i + 1)).center(), old.get(common.get(i + 2)).center());
            double b = area(updated.get(common.get(i)).center(), updated.get(common.get(i + 1)).center(), updated.get(common.get(i + 2)).center());
            if (Math.abs(a) > 1e-5 && Math.abs(b) > 1e-5) { triangles++; if (Math.signum(a) != Math.signum(b)) changes++; }
        }
        var result = new LinkedHashMap<String, Object>(); result.put("operation", operation); result.put("partsBefore", before.results().size()); result.put("partsAfter", after.results().size());
        result.put("commonNodes", common.size()); result.put("translationRemovedX", dx); result.put("translationRemovedY", dy);
        result.put("meanDisplacementPx", distances.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        result.put("p95DisplacementPx", distances.get((int)Math.ceil(distances.size() * .95) - 1)); result.put("maxDisplacementPx", distances.getLast());
        result.put("nondegenerateTriangles", triangles); result.put("triangleOrientationChanges", changes);
        result.put("interpretation", "triangle sign changes also reflect local deformation; this is not a claim of global flip or incremental stability"); return result;
    }
    private static double area(Point a, Point b, Point c) { return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x()); }
    private static double millis(long started) { return (System.nanoTime() - started) / 1_000_000.0; }
    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream().map(e -> json(e.getKey().toString()) + ":" + json(e.getValue())).toList()) + "}";
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(NativeGraphLayoutBenchmark::json).toList()) + "]";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
