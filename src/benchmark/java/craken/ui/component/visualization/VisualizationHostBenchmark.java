package craken.ui.component.visualization;

import craken.visualization.api.*;
import craken.visualization.layout.*;
import craken.visualization.layout.graphviz.*;
import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static craken.visualization.layout.LayoutRequest.Kind;

/** Real Stage/host/native measurement. Timings are observations, never performance assertions. */
public final class VisualizationHostBenchmark {
    private static final java.lang.management.ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final com.sun.management.OperatingSystemMXBean OS =
            ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
    private static final Duration LIMIT = Duration.ofSeconds(40);
    private record Pair(int a, int b) {}
    private record Fixture(DefaultVisualizationSession session, PageRef page, List<ViewLocation> nodes) implements AutoCloseable {
        @Override public void close() { session.close(); }
    }
    private record Center(double x, double y) {}
    private record ViewBounds(double x, double y, double width, double height) { Center center() { return new Center(x + width / 2, y + height / 2); } }
    private record Counters(long publications, long publicationWall, long publicationCpu, long queueWall,
                            long workerWall, long workerCpu, LayoutCoordinator.Diagnostics coordinator) {}
    private static final class Host implements AutoCloseable {
        final LayoutCoordinator coordinator;
        final AtomicLong publications = new AtomicLong(), publicationWall = new AtomicLong(), publicationCpu = new AtomicLong();
        final AtomicLong queueWall = new AtomicLong(), workerWall = new AtomicLong(), workerCpu = new AtomicLong();
        UiVisualizationContainer ui; Stage stage;
        int peakWorkers, peakNative;
        Host(Path runtime) {
            var bridge = new GraphvizProcessBridge(runtime, Duration.ofSeconds(30));
            var engines = new EnumMap<Kind, LayoutEngine>(Kind.class);
            engines.put(Kind.POINT, new ArrayLayout()); engines.put(Kind.ARRAY, new ArrayLayout());
            engines.put(Kind.LINEAR, new LinearLayout()); engines.put(Kind.TREE, new TreeLayout()); engines.put(Kind.GRAPH, new StressGraphLayout(bridge));
            engines.replaceAll((kind, engine) -> (request, cancellation) -> {
                long started = System.nanoTime(), cpu = threadCpu();
                try { return engine.layout(request, cancellation); }
                finally { workerWall.addAndGet(System.nanoTime() - started); workerCpu.addAndGet(cpuDelta(cpu, threadCpu())); }
            });
            Consumer<Runnable> dispatcher = task -> {
                long queued = System.nanoTime();
                Platform.runLater(() -> {
                    long started = System.nanoTime(), cpu = threadCpu(); queueWall.addAndGet(started - queued);
                    try { task.run(); }
                    finally { publications.incrementAndGet(); publicationWall.addAndGet(System.nanoTime() - started); publicationCpu.addAndGet(cpuDelta(cpu, threadCpu())); }
                });
            };
            coordinator = new LayoutCoordinator(engines, dispatcher, 2, bridge);
        }
        void open(Fixture fixture) throws Exception {
            onFx(() -> {
                ui = new UiVisualizationContainer(fixture.session(), coordinator); stage = new Stage();
                stage.setTitle("Visualization host benchmark"); stage.setScene(new Scene(ui, 800, 600)); stage.show(); ui.refresh(); return null;
            });
        }
        Counters counters() {
            return new Counters(publications.get(), publicationWall.get(), publicationCpu.get(), queueWall.get(),
                    workerWall.get(), workerCpu.get(), coordinator.diagnostics());
        }
        void settle() throws Exception {
            long end = System.nanoTime() + LIMIT.toNanos();
            for (;;) {
                var diagnostics = coordinator.diagnostics(); peakWorkers = Math.max(peakWorkers, diagnostics.activeWorkers());
                peakNative = Math.max(peakNative, diagnostics.activeNativeProcesses());
                if (peakWorkers > 2 || peakNative > 2) throw new IllegalStateException("Two-worker/native limit exceeded");
                boolean settled = onFx(() -> !ui.isLayoutPending());
                if (settled && diagnostics.activeRequests() == 0 && diagnostics.activeWorkers() == 0) break;
                if (System.nanoTime() >= end) throw new IllegalStateException("Host did not settle in " + LIMIT);
                Thread.sleep(8);
            }
            onFx(() -> {
                ui.applyCss(); ui.layout();
                for (var occurrence : ui.visibleOccurrences()) for (var part : occurrence.parts()) {
                    if (!part.errorText().isEmpty()) throw new IllegalStateException(part.errorText());
                    if (part.geometry() == null) throw new IllegalStateException("Part has no committed geometry");
                }
                return null;
            });
        }
        Map<ViewLocation, ViewBounds> sceneBounds() throws Exception {
            return onFx(() -> {
                ui.applyCss(); ui.layout(); var result = new LinkedHashMap<ViewLocation, ViewBounds>();
                ui.visibleOccurrences().forEach(occurrence -> occurrence.nodeViews().forEach((location, node) -> {
                    var bounds = node.localToScene(node.getBoundsInLocal());
                    result.put(location, new ViewBounds(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));
                }));
                return result;
            });
        }
        @Override public void close() throws Exception {
            onFx(() -> { try { if (ui != null) ui.close(); else coordinator.close(); } finally { if (stage != null) stage.close(); } return null; });
            long end = System.nanoTime() + LIMIT.toNanos();
            while (coordinator.diagnostics().activeWorkers() != 0 && System.nanoTime() < end) Thread.sleep(2);
            if (coordinator.diagnostics().activeWorkers() != 0 || coordinator.diagnostics().activeNativeProcesses() != 0)
                throw new IllegalStateException("Host close leaked a worker or native process");
        }
    }
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("Usage: VisualizationHostBenchmark <Graphviz runtime-directory> <report.json>");
        var runtime = Path.of(arguments[0]).toAbsolutePath(); var destination = Path.of(arguments[1]).toAbsolutePath();
        if (!THREADS.isThreadCpuTimeSupported()) throw new IllegalStateException("This qualification requires thread CPU timing");
        THREADS.setThreadCpuTimeEnabled(true);
        var ready = new CompletableFuture<Void>(); Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); ready.get(10, TimeUnit.SECONDS);
        var report = new LinkedHashMap<String, Object>(); var samples = new ArrayList<Map<String, Object>>(); var cache = new ArrayList<Map<String, Object>>();
        report.put("java", System.getProperty("java.version")); report.put("javafx", System.getProperty("javafx.runtime.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch")); report.put("runtime", runtime.toString());
        report.put("viewport", Map.of("sceneWidth", 800, "sceneHeight", 600, "zoom", 1)); report.put("seed", 42);
        report.put("warmupPerInputAndDirection", 1); report.put("measuredColdIterations", 3);
        report.put("definitions", List.of(
                "Cold means a fresh host/coordinator/native bridge and geometry cache; the JVM and OS file cache are warm.",
                "hostSettleWallMs begins before JavaFX host/Stage construction or mutation and ends after current geometry is published and FX layout is flushed; fixture and coordinator setup are excluded; it is not display hardware presentation.",
                "jvmProcessCpuMs excludes native child-process CPU and includes JavaFX/background JVM work during the measured interval.",
                "fxSynchronousWallMs/CpuMs measure only the submitted FX construction or refresh runnable, including real CSS/font/layout work.",
                "lastFxPreparationMs is the host's last combined node construction, measured geometry and request preparation; it does not separate font measurement from node creation.",
                "fxPublicationWallMs/CpuMs sum dispatcher delivery runnables; queueWaitMs sums compute-to-dispatch waits and is not added to settle wall time.",
                "layoutWorkerWallSumMs is the sum across concurrent Java workers, includes waiting for native subprocesses, and may exceed end-to-end wall time; worker CPU excludes child CPU.",
                "peakWorkers/peakNativeProcesses are sampled at approximately 8 ms and are observed lower bounds; the two-worker correctness gate is separately tested.",
                "Topology and seed match NativeGraphLayoutBenchmark, but cards are truly measured text/field cards rather than the raw benchmark's synthetic rectangles.",
                "No latency or incremental-position threshold is claimed. Every sample checks exact node/edge coverage, finite geometry, macro overlap and resource cleanup."));
        try {
            for (int[] input : List.of(new int[]{50, 30}, new int[]{100, 250})) for (var direction : List.of(TopologyEdge.Direction.NONE, TopologyEdge.Direction.FORWARD)) {
                for (int iteration = 0; iteration <= 3; iteration++) try (var fixture = fixture(input[0], graph(input[0], input[1]), direction); var host = new Host(runtime)) {
                    var sample = measure(host, () -> host.open(fixture)); sample.put("input", input[0] + "/" + input[1]); sample.put("direction", direction.name()); sample.put("iteration", iteration);
                    describe(host, fixture, sample);
                    if (iteration > 0) samples.add(sample);
                    var hot = measure(host, () -> {
                        modify(fixture, List.of(new VisualizationCommand.Touch(path(fixture.nodes().getFirst()), AccessKind.READ)), "cache-touch");
                        onFx(() -> { host.ui.refresh(); return null; });
                    });
                    describe(host, fixture, hot); hot.put("input", input[0] + "/" + input[1]); hot.put("direction", direction.name()); hot.put("iteration", iteration);
                    if (((Number)hot.get("graphEngineRuns")).longValue() != 0) throw new IllegalStateException("Pure highlight reran native layout");
                    if (iteration > 0) cache.add(hot);
                    System.out.println("Measured " + input[0] + "/" + input[1] + " " + direction + " iteration " + iteration + " cold " + sample.get("hostSettleWallMs") + " ms; cache " + hot.get("hostSettleWallMs") + " ms");
                }
            }
            report.put("samples", samples); report.put("highlightCacheSamples", cache); report.put("incremental", incremental(runtime));
            report.put("screenshots", screenshots(runtime, destination.getParent()));
            report.put("resourceCleanup", "Each host was closed and observed with zero active workers and zero native processes.");
            Files.createDirectories(destination.getParent()); Files.writeString(destination, json(report) + "\n");
            System.out.println("Recorded " + samples.size() + " cold host samples, " + cache.size() + " cache updates and 5 incremental cases: " + destination);
        } finally { Platform.exit(); }
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static Map<String, Object> measure(Host host, Action action) throws Exception {
        host.peakWorkers = 0; host.peakNative = 0; var before = host.counters(); long started = System.nanoTime(), processCpu = OS.getProcessCpuTime();
        FX_WALL.set(0); FX_CPU.set(0); action.run(); long fxWall = FX_WALL.get(), fxCpu = FX_CPU.get(); host.settle();
        double wall = ms(System.nanoTime() - started), process = ms(cpuDelta(processCpu, OS.getProcessCpuTime())); var after = host.counters();
        var result = new LinkedHashMap<String, Object>(); result.put("hostSettleWallMs", wall); result.put("jvmProcessCpuMs", process);
        result.put("fxSynchronousWallMs", ms(fxWall)); result.put("fxSynchronousCpuMs", ms(fxCpu));
        result.put("lastFxPreparationMs", onFx(() -> ms(host.ui.lastFxPreparationNanos())));
        result.put("fxPublicationWallMs", ms(after.publicationWall() - before.publicationWall())); result.put("fxPublicationCpuMs", ms(after.publicationCpu() - before.publicationCpu()));
        result.put("queueWaitMsSum", ms(after.queueWall() - before.queueWall())); result.put("publicationDeliveries", after.publications() - before.publications());
        result.put("layoutWorkerWallSumMs", ms(after.workerWall() - before.workerWall())); result.put("layoutWorkerCpuSumMs", ms(after.workerCpu() - before.workerCpu()));
        result.put("graphEngineRuns", deltaRuns(before, after, Kind.GRAPH)); result.put("pointEngineRuns", deltaRuns(before, after, Kind.POINT));
        result.put("geometryCacheHits", after.coordinator().cacheHits() - before.coordinator().cacheHits()); result.put("layoutSubmissions", after.coordinator().submittedRequests() - before.coordinator().submittedRequests());
        result.put("peakWorkers", host.peakWorkers); result.put("peakNativeProcesses", host.peakNative); return result;
    }
    private static long deltaRuns(Counters before, Counters after, Kind kind) { return after.coordinator().engineRuns().getOrDefault(kind, 0L) - before.coordinator().engineRuns().getOrDefault(kind, 0L); }
    private static void describe(Host host, Fixture fixture, Map<String, Object> sample) throws Exception {
        var bounds = host.sceneBounds(); var page = fixture.session().model().pages().get(fixture.page().pageId());
        if (bounds.size() != page.nodes().size()) throw new IllegalStateException("Host did not display every allocated graph node");
        int renderedEdges = onFx(() -> host.ui.visibleOccurrences().stream().flatMap(o -> o.parts().stream()).mapToInt(p -> p.geometry().edgePaths().size()).sum());
        if (renderedEdges != page.topology().size()) throw new IllegalStateException("Host did not display every topology edge");
        int overlaps = 0; var boxes = new ArrayList<>(bounds.values());
        for (var b : boxes) if (!(Double.isFinite(b.x()) && Double.isFinite(b.y()) && Double.isFinite(b.width()) && Double.isFinite(b.height()) && b.width() > 0 && b.height() > 0)) throw new IllegalStateException("Invalid real FX bounds");
        for (int a = 0; a < boxes.size(); a++) for (int b = a + 1; b < boxes.size(); b++) {
            var x = boxes.get(a); var y = boxes.get(b);
            if (x.x() < y.x() + y.width() - 1e-5 && x.x() + x.width() > y.x() + 1e-5 && x.y() < y.y() + y.height() - 1e-5 && x.y() + x.height() > y.y() + 1e-5) overlaps++;
        }
        if (overlaps != 0) throw new IllegalStateException("Overlapping real FX macro cards: " + overlaps);
        sample.put("nodes", bounds.size()); sample.put("edges", renderedEdges); sample.put("parts", page.parts().size()); sample.put("overlapPairs", overlaps);
        sample.put("measuredWidthMin", boxes.stream().mapToDouble(ViewBounds::width).min().orElse(0)); sample.put("measuredWidthMax", boxes.stream().mapToDouble(ViewBounds::width).max().orElse(0));
        sample.put("measuredHeightMin", boxes.stream().mapToDouble(ViewBounds::height).min().orElse(0)); sample.put("measuredHeightMax", boxes.stream().mapToDouble(ViewBounds::height).max().orElse(0));
    }
    private static List<Map<String, Object>> incremental(Path runtime) throws Exception {
        var result = new ArrayList<Map<String, Object>>();
        try (var fixture = fixture(100, graph(100, 250), TopologyEdge.Direction.NONE); var host = new Host(runtime)) {
            host.open(fixture); host.settle(); var base = host.sceneBounds(); int parts = fixture.session().model().pages().get(fixture.page().pageId()).parts().size();
            var added = fixture.session().reserveNodeId(fixture.page());
            var sample = measure(host, () -> {
                modify(fixture, List.of(new VisualizationCommand.AddNode(path(added), spec(101)),
                        new VisualizationCommand.Connect(path(fixture.nodes().get(0)), path(added)), new VisualizationCommand.Connect(path(fixture.nodes().get(1)), path(added))), "benchmark-add");
                onFx(() -> { host.ui.refresh(); return null; });
            });
            describe(host, fixture, sample); result.add(change("add-linked-node", parts, base, fixture, host, sample));
            var beforeDelete = host.sceneBounds();
            sample = measure(host, () -> { modify(fixture, List.of(new VisualizationCommand.DeleteNode(path(added))), "benchmark-delete"); onFx(() -> { host.ui.refresh(); return null; }); });
            describe(host, fixture, sample); result.add(change("remove-node", parts, beforeDelete, fixture, host, sample));
            var beforeText = host.sceneBounds(); var node10 = fixture.nodes().get(9); var old = fixture.session().model().node(node10).content();
            sample = measure(host, () -> { modify(fixture, List.of(new VisualizationCommand.SetContent(path(node10), new ViewNode.Spec(old.kind(), "node 10 with a substantially longer real measured label", old.fields()))), "benchmark-text"); onFx(() -> { host.ui.refresh(); return null; }); });
            describe(host, fixture, sample); result.add(change("grow-measured-text-width", parts, beforeText, fixture, host, sample));
        }
        var path = new ArrayList<Pair>(); for (int i = 1; i <= 30; i++) path.add(new Pair(i, i + 1));
        for (boolean merge : List.of(true, false)) try (var fixture = fixture(32, path, TopologyEdge.Direction.NONE); var host = new Host(runtime)) {
            host.open(fixture); host.settle(); var before = host.sceneBounds(); int parts = fixture.session().model().pages().get(fixture.page().pageId()).parts().size();
            var sample = measure(host, () -> {
                var command = merge ? new VisualizationCommand.Connect(path(fixture.nodes().getFirst()), path(fixture.nodes().getLast()))
                        : new VisualizationCommand.Disconnect(path(fixture.nodes().get(14)), path(fixture.nodes().get(15)));
                modify(fixture, List.of(command), merge ? "benchmark-merge" : "benchmark-split"); onFx(() -> { host.ui.refresh(); return null; });
            });
            describe(host, fixture, sample); result.add(change(merge ? "merge-parts-scene-coordinates" : "split-parts-scene-coordinates", parts, before, fixture, host, sample));
        }
        return result;
    }
    private static Map<String, Object> change(String operation, int partsBefore, Map<ViewLocation, ViewBounds> before, Fixture fixture, Host host, Map<String, Object> sample) throws Exception {
        var after = host.sceneBounds(); var common = before.keySet().stream().filter(after::containsKey).sorted(Comparator.comparingLong(ViewLocation::nodeId)).toList();
        double dx = 0, dy = 0;
        for (var location : common) { dx += after.get(location).center().x() - before.get(location).center().x(); dy += after.get(location).center().y() - before.get(location).center().y(); }
        dx /= common.size(); dy /= common.size(); var distances = new ArrayList<Double>();
        for (var location : common) distances.add(Math.hypot(after.get(location).center().x() - before.get(location).center().x() - dx, after.get(location).center().y() - before.get(location).center().y() - dy));
        distances.sort(Double::compare); int changes = 0, triangles = 0;
        for (int i = 0; i + 2 < common.size(); i += 3) {
            double a = area(before.get(common.get(i)).center(), before.get(common.get(i + 1)).center(), before.get(common.get(i + 2)).center());
            double b = area(after.get(common.get(i)).center(), after.get(common.get(i + 1)).center(), after.get(common.get(i + 2)).center());
            if (Math.abs(a) > 1e-5 && Math.abs(b) > 1e-5) { triangles++; if (Math.signum(a) != Math.signum(b)) changes++; }
        }
        sample.put("operation", operation); sample.put("partsBefore", partsBefore); sample.put("partsAfter", fixture.session().model().pages().get(fixture.page().pageId()).parts().size());
        sample.put("commonNodes", common.size()); sample.put("translationRemovedX", dx); sample.put("translationRemovedY", dy);
        sample.put("meanDisplacementPx", distances.stream().mapToDouble(Double::doubleValue).average().orElse(0)); sample.put("p95DisplacementPx", distances.get((int)Math.ceil(distances.size() * .95) - 1)); sample.put("maxDisplacementPx", distances.getLast());
        sample.put("nondegenerateTriangles", triangles); sample.put("triangleOrientationChanges", changes);
        sample.put("coordinates", "Actual node Pane localToScene bounds after FX layout, including vertical part placement; scroll stays at its default origin.");
        sample.put("interpretation", "Triangle signs can change through local deformation, not necessarily global flip. The native graph does not use an incremental position seed.");
        return sample;
    }
    private static double area(Center a, Center b, Center c) { return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x()); }
    private static List<Map<String, Object>> screenshots(Path runtime, Path directory) throws Exception {
        Files.createDirectories(directory); var result = new ArrayList<Map<String, Object>>();
        try (var fixture = fixture(100, graph(100, 250), TopologyEdge.Direction.FORWARD); var host = new Host(runtime)) {
            host.open(fixture); host.settle();
            for (int width : List.of(600, 1200)) {
                onFx(() -> {
                    host.stage.setWidth(width + host.stage.getWidth() - host.stage.getScene().getWidth());
                    host.stage.setHeight(720 + host.stage.getHeight() - host.stage.getScene().getHeight()); return null;
                });
                long end = System.nanoTime() + LIMIT.toNanos();
                while (!onFx(() -> Math.abs(host.stage.getScene().getWidth() - width) < 1 && Math.abs(host.ui.getWidth() - width) < 1)) {
                    if (System.nanoTime() >= end) throw new IllegalStateException("Viewport resize did not settle"); Thread.sleep(8);
                }
                host.settle(); var file = directory.resolve("visualization-host-" + width + ".png").toAbsolutePath();
                var values = onFx(() -> {
                    var extent = host.ui.visibleOccurrences().getFirst().parts().getFirst().geometry().contentBounds();
                    double zoom = Math.min(1, Math.min((width - 40) / extent.width(), 560 / extent.height()) * .95);
                    host.ui.setZoom(zoom); host.ui.applyCss(); host.ui.layout();
                    if (!ImageIO.write(SwingFXUtils.fromFXImage(host.stage.getScene().snapshot(null), null), "png", file.toFile())) throw new IllegalStateException("PNG writer missing");
                    var metadata = new LinkedHashMap<String, Object>(); metadata.put("file", file.toString()); metadata.put("sceneWidth", host.stage.getScene().getWidth());
                    metadata.put("sceneHeight", host.stage.getScene().getHeight()); metadata.put("hostWidth", host.ui.getWidth()); metadata.put("zoom", zoom);
                    metadata.put("contentWidth", extent.width()); metadata.put("contentHeight", extent.height()); metadata.put("nodes", 100); metadata.put("edges", 250);
                    return metadata;
                }); result.add(values);
            }
        }
        return result;
    }
    private static List<Pair> graph(int count, int edges) {
        var result = new ArrayList<Pair>(); var seen = new HashSet<String>();
        if (edges >= count - 1) for (int i = 1; i < count; i++) { result.add(new Pair(i, i + 1)); seen.add(i + ":" + (i + 1)); }
        var random = new Random(42);
        while (result.size() < edges) {
            int a = 1 + random.nextInt(count), b = 1 + random.nextInt(count); if (a == b) continue;
            if (seen.add(Math.min(a, b) + ":" + Math.max(a, b))) result.add(new Pair(a, b));
        }
        return result;
    }
    private static Fixture fixture(int count, List<Pair> edges, TopologyEdge.Direction direction) {
        var session = new DefaultVisualizationSession();
        try {
            var page = session.initializeRoot(BuiltinPageTypes.composite("host-benchmark", PageType.Layout.STRESS, false));
            var nodes = new ArrayList<ViewLocation>(); var commands = new ArrayList<VisualizationCommand>();
            for (int i = 1; i <= count; i++) { var node = session.reserveNodeId(page); nodes.add(node); commands.add(new VisualizationCommand.AddNode(path(node), spec(i))); }
            for (var edge : edges) commands.add(new VisualizationCommand.Connect(path(nodes.get(edge.a() - 1)), path(nodes.get(edge.b() - 1)), direction));
            var fixture = new Fixture(session, page, List.copyOf(nodes)); modify(fixture, commands, "host-benchmark-fixture"); return fixture;
        } catch (RuntimeException | Error error) { session.close(); throw error; }
    }
    private static ViewNode.Spec spec(int id) { return new ViewNode.Spec(ViewNode.Kind.POINT, "node " + id, Map.of("value", Integer.toString(id * 11))); }
    private static OperationPath path(ViewLocation node) { return new OperationPath(null, node); }
    private static void modify(Fixture fixture, List<VisualizationCommand> commands, String source) {
        var result = fixture.session().modify(new MutationBatch(commands, source));
        if (!result.succeeded()) throw new IllegalStateException(result.error().code() + ": " + result.error().message());
    }
    private static final AtomicLong FX_WALL = new AtomicLong(), FX_CPU = new AtomicLong();
    private static <T> T onFx(Callable<T> action) throws Exception {
        var future = new CompletableFuture<T>();
        Platform.runLater(() -> {
            long started = System.nanoTime(), cpu = threadCpu();
            T value = null; Throwable failure = null;
            try { value = action.call(); } catch (Throwable error) { failure = error; }
            finally { FX_WALL.addAndGet(System.nanoTime() - started); FX_CPU.addAndGet(cpuDelta(cpu, threadCpu())); }
            if (failure == null) future.complete(value); else future.completeExceptionally(failure);
        });
        try { return future.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS); }
        catch (ExecutionException error) { if (error.getCause() instanceof Error cause) throw cause; throw (Exception)error.getCause(); }
    }
    private static long threadCpu() { return THREADS.getCurrentThreadCpuTime(); }
    private static long cpuDelta(long before, long after) { return before < 0 || after < 0 ? 0 : Math.max(0, after - before); }
    private static double ms(long nanos) { return nanos / 1_000_000.0; }
    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream().map(e -> json(e.getKey().toString()) + ":" + json(e.getValue())).toList()) + "}";
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(VisualizationHostBenchmark::json).toList()) + "]";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
