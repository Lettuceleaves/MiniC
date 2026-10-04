package craken.ui.component.visualization;

import craken.visualization.api.*;
import craken.visualization.layout.*;
import craken.visualization.layout.graphviz.GraphvizProcessBridge;
import craken.visualization.model.ViewNode;
import craken.visualization.model.ContainerModel;
import craken.visualization.model.relation.TopologyEdge;
import craken.visualization.mutation.DefaultVisualizationSession;
import craken.visualization.type.BuiltinPageTypes;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.shape.Rectangle;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static craken.visualization.layout.LayoutRequest.Kind;

@Tag("visualization-ui")
@EnabledIfSystemProperty(named = "craken.ui.test", matches = "true")
final class UiVisualizationContainerFxTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>(); Runnable task = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(task); } catch (IllegalStateException started) { Platform.runLater(task); } ready.get(10, TimeUnit.SECONDS);
    }
    private static <T> T onFx(Callable<T> task) throws Exception {
        var result = new CompletableFuture<T>(); Platform.runLater(() -> { try { result.complete(task.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        try { return result.get(10, TimeUnit.SECONDS); }
        catch (ExecutionException error) { if (error.getCause() instanceof Error failure) throw failure; throw (Exception)error.getCause(); }
    }
    private static void awaitFx(java.util.function.BooleanSupplier predicate) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!onFx(predicate::getAsBoolean) && System.nanoTime() < end) Thread.sleep(5);
        assertTrue(onFx(predicate::getAsBoolean), "Current FX layout did not settle");
    }
    private static ViewLocation add(VisualizationSession session, PageRef page, ViewLocation pre, String label) {
        var id = session.reserveNodeId(page); session.addNode(new OperationPath(pre, id), ViewNode.Spec.point(label)); return id;
    }
    private static LayoutCoordinator deterministic(AtomicInteger calls) {
        LayoutEngine engine = (r, token) -> { calls.incrementAndGet(); return new ArrayLayout().layout(r, token); };
        var engines = new EnumMap<Kind, LayoutEngine>(Kind.class); for (var kind : Kind.values()) engines.put(kind, engine);
        return new LayoutCoordinator(engines, Platform::runLater);
    }
    @Test void repeatedPageOccurrencesUseIndependentNodesAndHighlightsAndNarrowWidthCreatesOnlyFocus() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var p = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, p, null, "a");
            var q = session.initializePage(BuiltinPageTypes.point(), a); var b = add(session, q, a, "b"); var c = add(session, p, b, "c");
            var ui = onFx(() -> { var view = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); view.resize(1500, 700); view.refresh(); return view; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> {
                    assertEquals(3, ui.visibleOccurrences().size());
                    var first = ui.visibleOccurrences().getFirst(); var last = ui.visibleOccurrences().getLast();
                    assertEquals(first.occurrence().page(), last.occurrence().page());
                    assertNotSame(first.nodeViews().get(a), last.nodeViews().get(a));
                    assertEquals(2, ((Rectangle)first.nodeViews().get(a).getChildren().getFirst()).getStrokeWidth());
                    assertEquals(1, ((Rectangle)last.nodeViews().get(a).getChildren().getFirst()).getStrokeWidth());
                    ui.resize(300, 700); assertEquals(1, ui.visibleOccurrences().size()); assertEquals(c, ui.visibleOccurrences().getFirst().occurrence().node());
                    return null;
                });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void readyIsOnlyAPlaceholderAndIsActivatedIntoSortedVerticalParts() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.linked(false)); var a = add(session, page, null, "first"); var b = add(session, page, null, "ready");
            var ui = onFx(() -> { var view = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); view.resize(600, 500); view.refresh(); return view; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { var view = ui.visibleOccurrences().getFirst(); assertEquals(1, view.readyCount()); assertEquals(Set.of(a), view.nodeViews().keySet()); return null; });
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b)))).succeeded());
                onFx(() -> { ui.refresh(); return null; }); awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { assertEquals(0, ui.visibleOccurrences().getFirst().readyCount()); assertEquals(Set.of(a, b), ui.visibleOccurrences().getFirst().nodeViews().keySet()); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void zoomAndHighlightKeepGeometryCachedWhileTheViewportBoundsChange() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page, null, "first"); add(session, page, null, "second");
            var calls = new AtomicInteger(); var ui = onFx(() -> { var view = new UiVisualizationContainer(session, deterministic(calls)); view.resize(600, 500); view.refresh(); return view; });
            try {
                awaitFx(() -> !ui.isLayoutPending()); int initial = calls.get();
                var occurrence = onFx(() -> ui.visibleOccurrences().getFirst());
                double before = onFx(() -> ui.visibleOccurrences().getFirst().scrollPane().getContent().getBoundsInParent().getWidth());
                onFx(() -> { ui.setZoom(2); return null; });
                double after = onFx(() -> ui.visibleOccurrences().getFirst().scrollPane().getContent().getBoundsInParent().getWidth());
                assertEquals(before * 2, after, .1); assertEquals(initial, calls.get());
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Touch(new OperationPath(null, a), AccessKind.READ))).succeeded());
                onFx(() -> { ui.refresh(); return null; }); awaitFx(() -> !ui.isLayoutPending()); assertEquals(initial, calls.get());
                assertSame(occurrence, onFx(() -> ui.visibleOccurrences().getFirst()), "Selection within a page preserves its viewport instance");
                onFx(() -> { var parts = ui.visibleOccurrences().getFirst().parts(); assertEquals(List.of(1L, 2L), parts.stream().map(PartView::partId).toList()); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void foreignSnapshotsClearOldGraphicsAndNeverRestoreOrCloseTheBorrowedSession() throws Exception {
        try (var source = new DefaultVisualizationSession(); var borrowed = new DefaultVisualizationSession()) {
            var root = source.initializeRoot(BuiltinPageTypes.point()); add(source, root, null, "historical"); var snapshot = source.snapshot();
            long version = borrowed.model().version();
            var ui = onFx(() -> { var view = new UiVisualizationContainer(borrowed, deterministic(new AtomicInteger())); view.resize(600, 500); view.showSnapshot(snapshot); return view; });
            awaitFx(() -> !ui.isLayoutPending());
            onFx(() -> { assertEquals(source.model().id(), ui.displayModel().id()); ui.refresh(); assertTrue(ui.visibleOccurrences().isEmpty()); ui.close(); return null; });
            assertEquals(version, borrowed.model().version()); borrowed.initializeRoot(BuiltinPageTypes.point());
            onFx(() -> { var own = new UiVisualizationContainer(); own.close(); assertTrue(own.isClosed()); assertThrows(IllegalStateException.class, () -> own.session().initializeRoot(BuiltinPageTypes.point())); return null; });
        }
    }
    @Test void singletonGraphPartsUseExactPointPlacementAndNeverStartTheGraphEngine() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.composite("disconnected-graph", PageType.Layout.STRESS, false));
            for (int i = 0; i < 22; i++) add(session, root, null, "point" + i);
            var graphCalls = new AtomicInteger(); var engines = new EnumMap<Kind, LayoutEngine>(Kind.class);
            for (var kind : Kind.values()) engines.put(kind, new ArrayLayout());
            engines.put(Kind.GRAPH, (r, token) -> { graphCalls.incrementAndGet(); throw new AssertionError("Singleton must not run neato"); });
            var ui = onFx(() -> { var view = new UiVisualizationContainer(session, new LayoutCoordinator(engines, Platform::runLater)); view.resize(700, 500); view.refresh(); return view; });
            try { awaitFx(() -> !ui.isLayoutPending()); assertEquals(0, graphCalls.get()); onFx(() -> { assertEquals(22, ui.visibleOccurrences().getFirst().parts().size()); return null; }); }
            finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void nativeGraphIsLaidOutForRealAndClosingTheHostClosesItsBridge() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.graph(true)); var a = add(session, root, null, "a"); var b = add(session, root, null, "b");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), TopologyEdge.Direction.FORWARD))).succeeded());
            String runtime = System.getProperty("craken.graphviz.runtime"); assertNotNull(runtime, "The host native test must not be skipped");
            var bridge = new GraphvizProcessBridge(Path.of(runtime), Duration.ofSeconds(10)); var engines = new EnumMap<Kind, LayoutEngine>(Kind.class);
            for (var kind : Kind.values()) engines.put(kind, new ArrayLayout()); engines.put(Kind.GRAPH, new StressGraphLayout(bridge));
            var ui = onFx(() -> { var view = new UiVisualizationContainer(session, new LayoutCoordinator(engines, Platform::runLater, 2, bridge)); view.resize(700, 500); view.refresh(); return view; });
            awaitFx(() -> !ui.isLayoutPending());
            onFx(() -> { var part = ui.visibleOccurrences().getFirst().parts().getFirst(); assertEquals("graphviz/neato-major", part.geometry().engine()); assertTrue(part.errorText().isEmpty()); ui.close(); return null; });
            assertEquals(0, bridge.activeProcessCount()); assertEquals(LayoutException.Code.CANCELLED, assertThrows(LayoutException.class, () -> bridge.version()).code());
        }
    }
    @Test void realStageCanShowScrollablePartsAndCloseWithoutBorrowedModelWrites() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point()); for (int i = 0; i < 8; i++) add(session, root, null, "wide label " + i);
            long version = session.model().version();
            var ui = onFx(() -> { var view = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); var stage = new Stage(); stage.setScene(new Scene(view, 600, 300)); stage.show(); view.refresh(); view.setUserData(stage); return view; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> { ui.applyCss(); ui.layout(); assertTrue(ui.visibleOccurrences().getFirst().scrollPane().getContent().getBoundsInLocal().getHeight() > 300); return null; }); }
            finally { onFx(() -> { ((Stage)ui.getUserData()).close(); ui.close(); return null; }); }
            assertEquals(version, session.model().version());
        }
    }
    @Test void aNewSnapshotImmediatelyClearsTheOldFrameAndDiscardsItsDelayedResult() throws Exception {
        try (var old = new DefaultVisualizationSession(); var next = new DefaultVisualizationSession(); var borrowed = new DefaultVisualizationSession()) {
            var a = old.initializeRoot(BuiltinPageTypes.point()); add(old, a, null, "old");
            var b = next.initializeRoot(BuiltinPageTypes.point()); add(next, b, null, "next");
            var started = new CountDownLatch(1); var release = new Semaphore(0); var count = new AtomicInteger();
            LayoutEngine delayed = (request, token) -> {
                if (count.incrementAndGet() == 1) { started.countDown(); release.acquireUninterruptibly(); }
                return new ArrayLayout().layout(request, CancellationToken.NONE);
            };
            var engines = new EnumMap<Kind, LayoutEngine>(Kind.class); for (var kind : Kind.values()) engines.put(kind, delayed);
            var ui = onFx(() -> { var view = new UiVisualizationContainer(borrowed, new LayoutCoordinator(engines, Platform::runLater)); view.resize(600, 500); view.showSnapshot(old.snapshot()); return view; });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                onFx(() -> { ui.showSnapshot(next.snapshot()); assertTrue(ui.visibleOccurrences().getFirst().nodeViews().isEmpty()); assertEquals(next.model().id(), ui.displayModel().id()); return null; });
                release.release(); awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { assertTrue(ui.visibleOccurrences().getFirst().nodeViews().keySet().stream().allMatch(n -> n.containerId() == next.model().id())); return null; });
            } finally { release.release(); onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void aFailedNewGeometryKeepsTheLastValidGeometryAndReportsTheActualError() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var at = add(session, page, null, "small");
            var fail = new java.util.concurrent.atomic.AtomicBoolean();
            LayoutEngine engine = (request, token) -> { if (fail.get()) throw new LayoutException(LayoutException.Code.PROCESS_FAILED, "native test failure"); return new ArrayLayout().layout(request, token); };
            var engines = new EnumMap<Kind, LayoutEngine>(Kind.class); for (var kind : Kind.values()) engines.put(kind, engine);
            var ui = onFx(() -> { var view = new UiVisualizationContainer(session, new LayoutCoordinator(engines, Platform::runLater)); view.resize(600, 500); view.refresh(); return view; });
            try {
                awaitFx(() -> !ui.isLayoutPending()); var previous = onFx(() -> ui.visibleOccurrences().getFirst().parts().getFirst().geometry());
                fail.set(true); assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetContent(new OperationPath(null, at), ViewNode.Spec.point("a much wider value that changes measurement")))).succeeded());
                onFx(() -> { ui.refresh(); return null; }); awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { var part = ui.visibleOccurrences().getFirst().parts().getFirst(); assertSame(previous, part.geometry()); assertTrue(part.errorText().contains("native test failure")); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void closeCancelsAStartedNativeRequestWithoutPublishingIntoTheDisposedHost() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.graph(false)); var nodes = new ArrayList<ViewLocation>();
            for (int i = 0; i < 100; i++) nodes.add(add(session, page, null, "node " + i));
            var connections = new ArrayList<VisualizationCommand>();
            for (int i = 1; i < nodes.size(); i++) connections.add(new VisualizationCommand.Connect(new OperationPath(null, nodes.get(i - 1)), new OperationPath(null, nodes.get(i))));
            assertTrue(session.modify(new MutationBatch(connections, "close-native-case")).succeeded());
            String runtime = System.getProperty("craken.graphviz.runtime"); assertNotNull(runtime);
            var bridge = new GraphvizProcessBridge(Path.of(runtime), Duration.ofSeconds(10));
            var engines = new EnumMap<Kind, LayoutEngine>(Kind.class); for (var kind : Kind.values()) engines.put(kind, new ArrayLayout()); engines.put(Kind.GRAPH, new StressGraphLayout(bridge));
            var coordinator = new LayoutCoordinator(engines, Platform::runLater, 2, bridge);
            var ui = onFx(() -> { var view = new UiVisualizationContainer(session, coordinator); view.resize(700, 500); view.refresh(); return view; });
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (bridge.activeProcessCount() == 0 && System.nanoTime() < deadline) Thread.sleep(1);
                assertTrue(bridge.activeProcessCount() > 0, "Must close during a real native process");
                onFx(() -> { ui.close(); assertFalse(ui.isLayoutPending()); assertTrue(ui.visibleOccurrences().isEmpty()); return null; });
                deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (coordinator.diagnostics().activeWorkers() != 0 && System.nanoTime() < deadline) Thread.sleep(2);
                assertEquals(0, coordinator.diagnostics().activeWorkers()); assertEquals(0, coordinator.diagnostics().activeNativeProcesses()); assertTrue(coordinator.isClosed());
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void historicalSelectionPreservesSourceVersionDistinctFromTheDisplayVersion() throws Exception {
        try (var source = new DefaultVisualizationSession(); var borrowed = new DefaultVisualizationSession()) {
            var page = source.initializeRoot(BuiltinPageTypes.point()); var node = add(source, page, null, "history");
            var snapshot = source.snapshot();
            var ui = onFx(() -> { var host = new UiVisualizationContainer(borrowed, deterministic(new AtomicInteger())); host.resize(600, 500); host.showSnapshot(snapshot); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> {
                    // Historical source metadata must survive even when a restored presentation has its own version.
                    var before = ui.displayModel();
                    var field = UiVisualizationContainer.class.getDeclaredField("model"); field.setAccessible(true);
                    field.set(ui, new ContainerModel(before.id(), before.root(), before.pages(), before.version() + 100,
                            before.ownership(), before.pageRules(), before.interaction(), before.epoch(), before.sourceStep(), before.sourceVersion()));
                    var view = ui.visibleOccurrences().getFirst().nodeViews().get(node);
                    view.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                            false, false, false, false, false, false, false, false, false, false, null));
                    assertEquals(snapshot.sourceVersion(), ui.displayModel().sourceVersion());
                    assertEquals(before.version() + 100, ui.displayModel().version()); return null;
                });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void ownedSessionAndGraphicsAreClosedEvenWhenALayoutResourceThrows() throws Exception {
        var session = new DefaultVisualizationSession(); session.initializeRoot(BuiltinPageTypes.point());
        var coordinator = new LayoutCoordinator(Map.of(Kind.POINT, new ArrayLayout()), Platform::runLater, 2,
                () -> { throw new IllegalStateException("test close failure"); });
        onFx(() -> {
            var constructor = UiVisualizationContainer.class.getDeclaredConstructor(VisualizationSession.class, LayoutCoordinator.class, boolean.class);
            constructor.setAccessible(true); var ui = constructor.newInstance(session, coordinator, true);
            assertThrows(IllegalStateException.class, ui::close);
            assertTrue(ui.isClosed()); assertNull(ui.getCenter()); assertNull(ui.getTop());
            assertTrue(ui.visibleOccurrences().isEmpty()); assertFalse(ui.isLayoutPending());
            assertThrows(IllegalStateException.class, () -> session.initializeRoot(BuiltinPageTypes.point()));
            ui.close(); return null;
        });
    }
    @Test void treePageDispatchesSharedDiamondAndSelfLoopToNativeGraphWithoutDroppingEdges() throws Exception {
        for (int scenario : List.of(0, 1, 2)) try (var session = new DefaultVisualizationSession()) {
            boolean loop = scenario == 1;
            var page = session.initializeRoot(BuiltinPageTypes.composite("shared-ast", PageType.Layout.TREE, false));
            var nodes = new ArrayList<ViewLocation>(); for (int i = 0; i < (loop ? 1 : 4); i++) nodes.add(add(session, page, null, "AST " + i));
            var commands = new ArrayList<VisualizationCommand>();
            int[][] edges = loop ? new int[][] {{0, 0}} : scenario == 2 ? new int[][] {{0, 1}, {0, 2}, {1, 3}} : new int[][] {{0, 1}, {0, 2}, {1, 3}, {2, 3}};
            for (var edge : edges) commands.add(new VisualizationCommand.Connect(new OperationPath(null, nodes.get(edge[0])), new OperationPath(null, nodes.get(edge[1])), TopologyEdge.Direction.FORWARD));
            assertTrue(session.modify(new MutationBatch(commands, "shared-ast")).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); host.resize(700, 500); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> {
                    var part = ui.visibleOccurrences().getFirst().parts().getFirst(); assertTrue(part.errorText().isEmpty(), part.errorText());
                    assertEquals(scenario == 2 ? "tree" : "graphviz/neato-major", part.geometry().engine());
                    assertEquals(edges.length, part.geometry().edgePaths().size()); assertEquals(nodes.size(), part.geometry().nodeBounds().size());
                    assertEquals(PageType.Layout.TREE, ui.displayModel().pages().get(page.pageId()).type().layout()); return null;
                });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
}
