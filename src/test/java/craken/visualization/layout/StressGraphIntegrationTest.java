package craken.visualization.layout;

import craken.visualization.layout.graphviz.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static craken.visualization.layout.LayoutRequest.*;
import static craken.visualization.layout.LayoutFixtures.*;

@Tag("visualization-layout")
final class StressGraphIntegrationTest {
    private GraphvizProcessBridge bridge() {
        String runtime = System.getProperty("craken.graphviz.runtime");
        assertNotNull(runtime, "Real neato runtime must be configured; this test cannot be skipped");
        return new GraphvizProcessBridge(Path.of(runtime), Duration.ofSeconds(10));
    }
    @Test void realRuntimeReportsTheLockedVersionAndSingleNodeGeometry() {
        try (var bridge = bridge()) {
            assertEquals("16.1.0", bridge.version());
            var request = request(Kind.GRAPH, List.of(node(1, 96, 48)), List.of(), Hints.defaults());
            var result = new StressGraphLayout(bridge).layout(request);
            assertEquals(96, result.nodeBounds().get(id(1)).width(), .05);
            assertEquals(48, result.nodeBounds().get(id(1)).height(), .05);
            assertEquals(request.stamp(), result.stamp());
        }
    }
    @Test void realMixedSizesRingHasNoOverlapsAndRepeatsWithTheSameSeed() {
        try (var bridge = bridge()) {
            var nodes = List.of(node(1, 180, 70), node(2, 80, 50), node(3, 110, 95), node(4, 150, 40));
            var request = request(Kind.GRAPH, nodes, List.of(edge(1, 1, 2), edge(2, 2, 3), edge(3, 3, 4), edge(4, 4, 1)), Hints.defaults());
            var engine = new StressGraphLayout(bridge); var result = engine.layout(request);
            assertEquals(4, result.edgePaths().size());
            for (var a : result.nodeBounds().entrySet()) for (var b : result.nodeBounds().entrySet())
                if (!a.getKey().equals(b.getKey())) assertFalse(overlaps(a.getValue(), b.getValue()));
            assertEquals(result, engine.layout(request));
            assertTrue(result.edgePaths().values().stream().flatMap(p -> p.segments().stream()).allMatch(s -> s instanceof LayoutResult.Cubic));
        }
    }
    @Test void realSelfLoopIsPreservedWithoutAnEngineArrowGap() {
        try (var bridge = bridge()) {
            var request = request(Kind.GRAPH, List.of(node(1, 96, 48)), List.of(edge(1, 1, 1)), Hints.defaults());
            var result = new StressGraphLayout(bridge).layout(request);
            assertFalse(result.edgePaths().get(1L).segments().isEmpty());
        }
    }
    @Test void unusedFixedPortsPreserveTheAuthoritativeNativeAutoRoutes() {
        try (var bridge = bridge()) {
            var original = node(1, 120, 60); var ports = new ArrayList<>(original.ports());
            ports.add(new Port(new PortRef(id(1), "unused-east"), new Point(120, 30), Side.EAST));
            var expanded = new Unit(original.node(), original.size(), original.members(), ports, original.textObstacles());
            var request = request(Kind.GRAPH, List.of(expanded, node(2, 80, 50), node(3, 100, 70)),
                    List.of(edge(1, 1, 2), edge(2, 2, 3), edge(3, 3, 1)), Hints.defaults());
            var encoded = new DotGraphWriter().write(request); var nativeOutput = bridge.execute(encoded.dot(), CancellationToken.NONE);
            var nativeGeometry = new PlainExtReader().read(nativeOutput.stdout(), encoded, request, bridge.version());
            var actual = new StressGraphLayout(bridge).layout(request);
            assertEquals(nativeGeometry.edgePaths(), actual.edgePaths(), "Unused extension ports must not replace real native Cubic routes with Java polylines");
            assertTrue(actual.edgePaths().values().stream().flatMap(p -> p.segments().stream()).allMatch(s -> s instanceof LayoutResult.Cubic));
        }
    }
    @Test void aUsedFixedPortStillRoutesToItsExactlyMeasuredMemberBoundary() {
        try (var bridge = bridge()) {
            var a = node(1, 120, 60); var b = node(2, 80, 50);
            var east = new Port(new PortRef(id(1), "east"), new Point(120, 30), Side.EAST);
            var west = new Port(new PortRef(id(2), "west"), new Point(0, 25), Side.WEST);
            a = new Unit(a.node(), a.size(), a.members(), List.of(a.ports().getFirst(), east), List.of());
            b = new Unit(b.node(), b.size(), b.members(), List.of(b.ports().getFirst(), west), List.of());
            var request = request(Kind.GRAPH, List.of(a, b), List.of(new Link(1, east.ref(), west.ref(), Direction.FORWARD)), Hints.defaults());
            var result = new StressGraphLayout(bridge).layout(request); var route = result.edgePaths().get(1L);
            assertEquals(result.nodeBounds().get(id(1)).right(), route.start().x(), 1e-7);
            assertEquals(result.nodeBounds().get(id(1)).center().y(), route.start().y(), 1e-7);
            assertEquals(result.nodeBounds().get(id(2)).x(), route.end().x(), 1e-7);
            assertEquals(result.nodeBounds().get(id(2)).center().y(), route.end().y(), 1e-7);
            assertTrue(route.segments().stream().allMatch(s -> s instanceof LayoutResult.Line));
        }
    }
    @Test void aUsedInternalAutoPortStillAttachesToTheConcreteChild() {
        try (var bridge = bridge()) {
            var child = new Rect(20, 25, 80, 45);
            var composed = new Unit(id(1), new Size(220, 120), List.of(new Member(id(1), new Rect(0, 0, 220, 120)), new Member(id(3), child)),
                    List.of(new Port(PortRef.node(id(1)), new Point(110, 60), Side.AUTO), new Port(PortRef.node(id(3)), child.center(), Side.AUTO)), List.of());
            var request = request(Kind.GRAPH, List.of(composed, node(2, 100, 60)), List.of(edge(1, 3, 2)), Hints.defaults());
            var result = new StressGraphLayout(bridge).layout(request); var start = result.edgePaths().get(1L).start(); var bounds = result.nodeBounds().get(id(3));
            assertTrue(bounds.contains(start));
            assertTrue(Math.abs(start.x() - bounds.x()) < 1e-7 || Math.abs(start.x() - bounds.right()) < 1e-7
                    || Math.abs(start.y() - bounds.y()) < 1e-7 || Math.abs(start.y() - bounds.bottom()) < 1e-7);
            assertTrue(result.edgePaths().get(1L).segments().stream().allMatch(s -> s instanceof LayoutResult.Line));
        }
    }
    @Test void missingRuntimeIsAnErrorAndNeverAFallbackLayout() {
        try (var bridge = new GraphvizProcessBridge(Path.of("build/missing-runtime"), Duration.ofSeconds(1))) {
            var failure = assertThrows(LayoutException.class, () -> bridge.execute("graph{}", CancellationToken.NONE));
            assertEquals(LayoutException.Code.RUNTIME_UNAVAILABLE, failure.code());
        }
    }
    @Test void nativeBadInputCancellationTimeoutAndCloseHaveExplicitFailures() {
        try (var bridge = bridge()) {
            assertEquals(LayoutException.Code.PROCESS_FAILED,
                    assertThrows(LayoutException.class, () -> bridge.execute("invalid DOT !!!", CancellationToken.NONE)).code());
            assertEquals(LayoutException.Code.CANCELLED,
                    assertThrows(LayoutException.class, () -> bridge.execute("graph{}", () -> true)).code());
            bridge.close();
            assertEquals(LayoutException.Code.CANCELLED,
                    assertThrows(LayoutException.class, () -> bridge.execute("graph{}", CancellationToken.NONE)).code());
        }
        try (var bridge = new GraphvizProcessBridge(Path.of(System.getProperty("craken.graphviz.runtime")), Duration.ofNanos(1))) {
            assertEquals(LayoutException.Code.TIMEOUT,
                    assertThrows(LayoutException.class, () -> bridge.execute("graph{a--b}", CancellationToken.NONE)).code());
        }
    }
    @Test void plainReaderRejectsTruncationUnknownNodesAndNonFiniteGeometry() {
        var request = request(Kind.GRAPH, List.of(node(1, 96, 48)), List.of(), Hints.defaults());
        var encoded = new DotGraphWriter().write(request); var reader = new PlainExtReader();
        for (var output : List.of("graph 1 1 1\n", "graph 1 NaN 1\nstop\n",
                "graph 1 1 1\nnode unknown 0.5 0.5 1 .5 \"\" solid box black white\nstop\n"))
            assertEquals(LayoutException.Code.INVALID_RESULT,
                    assertThrows(LayoutException.class, () -> reader.read(output, encoded, request, "16.1.0")).code());
    }
    @Test void cancelsAStartedNativeProcessAndReapsItBeforeReturning() throws Exception {
        var cancel = new AtomicBoolean();
        var dot = new StringBuilder("graph G {graph[maxiter=5000];");
        for (int i = 1; i < 2000; i++) dot.append("n").append(i).append("--n").append(i + 1).append(';');
        dot.append('}');
        try (var bridge = bridge(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = executor.submit(() -> bridge.execute(dot.toString(), cancel::get));
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (bridge.activeProcessCount() == 0 && !running.isDone() && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(1, bridge.activeProcessCount(), "Cancellation must exercise a started native process");
            cancel.set(true);
            var error = assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
            assertEquals(LayoutException.Code.CANCELLED, ((LayoutException) error.getCause()).code());
            assertEquals(0, bridge.activeProcessCount());
        }
    }
}
