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
import javafx.scene.control.MenuButton;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import craken.visualization.style.EdgeStyle;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.nio.file.Files;
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
    @Test void disabledAutomaticNavigationHighlightsAnotherVisibleNodeAndExplicitClickStillChangesFocus() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page, null, "focus A"); var b = add(session, page, null, "access B");
            session.modify(MutationBatch.of(new VisualizationCommand.Configure(new VisualizationOptions(false, false)),
                    new VisualizationCommand.SetFocus(new OperationPath(null, a)), new VisualizationCommand.Touch(new OperationPath(null, b), AccessKind.READ))).requireSuccess();
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); new Scene(host, 600, 500); host.applyCss(); host.layout(); host.refresh(); return host; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> {
                var occurrence = ui.visibleOccurrences().getFirst(); assertEquals(a, occurrence.occurrence().node());
                assertEquals(2, ((Rectangle)occurrence.nodeViews().get(b).getChildren().getFirst()).getStrokeWidth(), "Access B must highlight its actually visible card");
                assertEquals(1, ((Rectangle)occurrence.nodeViews().get(a).getChildren().getFirst()).getStrokeWidth());
                occurrence.nodeViews().get(b).fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, false, null));
                assertEquals(b, session.model().focus()); assertEquals(b, ui.visibleOccurrences().getFirst().occurrence().node()); return null;
            }); } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void aTopologyEdgeBetweenArrayCellsIsActuallyVisibleOverTheOpaqueArrayBackground() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.array()); var array = session.reserveNodeId(page);
            session.addNode(new OperationPath(null, array), new ViewNode.Spec(ViewNode.Kind.ARRAY, "array"));
            var a = add(session, page, null, "A"); var b = add(session, page, null, "B");
            session.modify(MutationBatch.of(new VisualizationCommand.Compose(array, a, 0), new VisualizationCommand.Compose(array, b, 1),
                    new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), TopologyEdge.Direction.NONE,
                            "east", "west", new EdgeStyle("#FFFFFF", null, "")))).requireSuccess();
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); new Scene(host, 600, 500); host.applyCss(); host.layout(); host.refresh(); return host; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> {
                var part = ui.visibleOccurrences().getFirst().parts().getFirst(); assertTrue(part.errorText().isEmpty(), part.errorText());
                var route = part.geometry().edgePaths().values().iterator().next(); assertEquals(1, route.segments().size());
                var extent = part.geometry().contentBounds(); var canvas = (Pane)part.getChildren().getLast();
                var middle = canvas.localToScene((route.start().x() + route.end().x()) / 2 - extent.x(), (route.start().y() + route.end().y()) / 2 - extent.y());
                var image = ui.getScene().snapshot(null); var directory = Path.of(System.getProperty("craken.visualization.screenshots", "build/verification/screenshots"));
                Files.createDirectories(directory); ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", directory.resolve("internal-array-edge.png").toFile());
                double brightestWhite = 0;
                for (int y = (int)Math.floor(middle.getY()) - 1; y <= (int)Math.ceil(middle.getY()) + 1; y++) {
                    var color = image.getPixelReader().getColor((int)Math.round(middle.getX()), y);
                    brightestWhite = Math.max(brightestWhite, Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue())));
                }
                assertTrue(brightestWhite > .5, "The real Scene must show the white edge in the gap between cells; geometry alone is insufficient"); return null;
            }); } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void aSceneAttachedOccurrenceRestoresRealScrollRangesAfterItIsHiddenAndRebuilt() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point()); var parent = add(session, root, null, "parent");
            for (int i = 0; i < 18; i++) add(session, root, null, "long visible card " + "value ".repeat(40));
            var child = session.initializePage(BuiltinPageTypes.point(), parent); add(session, child, parent, "child");
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); host.setManaged(false);
                new Scene(new Pane(host), 1500, 700); host.resize(1500, 700); host.applyCss(); host.layout(); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { ui.applyCss(); ui.layout(); var occurrence = ui.visibleOccurrences().getFirst(); occurrence.setZoom(1.2); ui.layout();
                    assertTrue(occurrence.scrollPane().getContent().getBoundsInLocal().getWidth() > occurrence.scrollPane().getViewportBounds().getWidth());
                    assertTrue(occurrence.scrollPane().getContent().getBoundsInLocal().getHeight() > occurrence.scrollPane().getViewportBounds().getHeight());
                    occurrence.scrollPane().setHvalue(.65); occurrence.scrollPane().setVvalue(.75);
                    ui.getScene().snapshot(null); assertEquals(.65, occurrence.scrollPane().getHvalue(), 1e-9); assertEquals(.75, occurrence.scrollPane().getVvalue(), 1e-9);
                    ui.resize(300, 700); ui.applyCss(); ui.layout(); assertEquals(1, ui.visibleOccurrences().size());
                    ui.resize(1500, 700); ui.applyCss(); ui.layout(); return null; });
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { ui.getScene().snapshot(null); var occurrence = ui.visibleOccurrences().getFirst();
                    assertEquals(1.2, occurrence.getZoom(), 1e-9); assertEquals(.65, occurrence.scrollPane().getHvalue(), 1e-9);
                    assertEquals(.75, occurrence.scrollPane().getVvalue(), 1e-9); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
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
    private static void snapshot(UiVisualizationContainer ui, String filename, int width, int height) throws Exception {
        var scene = Objects.requireNonNull(ui.getScene()); ui.applyCss(); ui.layout(); assertFalse(ui.isLayoutPending());
        var image = scene.snapshot(null); var directory = Path.of(System.getProperty("craken.visualization.screenshots", "build/verification/screenshots"));
        Files.createDirectories(directory); ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", directory.resolve(filename).toFile());
        assertEquals(width, image.getWidth()); assertEquals(height, image.getHeight());
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
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null, a)))).succeeded());
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
                assertSame(occurrence, onFx(() -> ui.visibleOccurrences().getFirst()), "Highlighting the same occurrence preserves its viewport instance");
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
    @Test void hiddenUpstreamOccurrencesRemainReachableThroughTheVirtualizedPathPicker() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point()); var first = add(session, root, null, "root"); var previous = first;
            for (int i = 0; i < 12; i++) { var page = session.initializePage(BuiltinPageTypes.point(), previous); previous = add(session, page, previous, "child " + i); }
            var historical = session.snapshot();
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); new Scene(host, 300, 500); host.applyCss(); host.layout(); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> {
                    assertEquals(1, ui.visibleOccurrences().size());
                    var picker = (MenuButton)ui.lookup("#visualization-path-picker"); assertNotNull(picker, "A hidden count label alone is not a navigation entry");
                    var list = (ListView<?>)((CustomMenuItem)picker.getItems().getFirst()).getContent(); assertEquals(13, list.getItems().size());
                    snapshot(ui, "hidden-ancestor-navigation.png", 300, 500);
                    list.getSelectionModel().select(0); list.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                    assertEquals(first, session.model().focus()); assertEquals(root, ui.visibleOccurrences().getFirst().occurrence().page()); return null;
                });
                var live = session.model();
                onFx(() -> { ui.showSnapshot(historical); var picker = (MenuButton)ui.lookup("#visualization-path-picker");
                    var list = (ListView<?>)((CustomMenuItem)picker.getItems().getFirst()).getContent(); var chosen = (OperationPath)list.getItems().get(5);
                    list.getSelectionModel().select(5); list.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                    assertEquals(chosen.nxt(), ui.displayModel().focus()); assertSame(live, session.model(), "Historical path selection only changes the display DTO"); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void hidingAndRebuildingAnOccurrenceRestoresItsOwnScrollAndZoomValues() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point()); var parent = add(session, root, null, "root");
            for (int i = 0; i < 8; i++) add(session, root, null, "a very long root card " + "value ".repeat(40));
            var child = session.initializePage(BuiltinPageTypes.point(), parent); add(session, child, parent, "child");
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); host.resize(1500, 700); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> {
                    var first = ui.visibleOccurrences().getFirst(); var last = ui.visibleOccurrences().getLast();
                    first.setZoom(1.3); last.setZoom(.8); first.scrollPane().setHvalue(.6); first.scrollPane().setVvalue(.7);
                    ui.resize(300, 700); assertEquals(.8, ui.visibleOccurrences().getFirst().getZoom(), 1e-9);
                    ui.resize(1500, 700); var restored = ui.visibleOccurrences().getFirst(); assertNotSame(first, restored);
                    assertEquals(1.3, restored.getZoom(), 1e-9); assertEquals(.6, restored.scrollPane().getHvalue(), 1e-9); assertEquals(.7, restored.scrollPane().getVvalue(), 1e-9);
                    assertEquals(.8, ui.visibleOccurrences().getLast().getZoom(), 1e-9); return null;
                });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void distinctNodesInTheSamePageKeepIndependentViewportState() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, page, null, "A"); var b = add(session, page, null, "B");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null, a)))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); host.resize(600, 500); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { ui.visibleOccurrences().getFirst().setZoom(1.3); return null; });
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null, b)))).succeeded());
                onFx(() -> { ui.refresh(); assertEquals(1, ui.visibleOccurrences().getFirst().getZoom(), 1e-9); ui.visibleOccurrences().getFirst().setZoom(.8); return null; });
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null, a)))).succeeded());
                onFx(() -> { ui.refresh(); assertEquals(1.3, ui.visibleOccurrences().getFirst().getZoom(), 1e-9); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void theSameNodeKeepsItsViewportWhenItsSelectedParentChangesPathDepth() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var root = session.initializeRoot(BuiltinPageTypes.point()); var a = add(session, root, null, "root");
            var middle = session.initializePage(BuiltinPageTypes.point(), a); var b = add(session, middle, a, "middle");
            var leaf = session.initializePage(BuiltinPageTypes.point(), a); var c = add(session, leaf, a, "shared leaf");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.AttachOwnership(b, c), new VisualizationCommand.SetFocus(new OperationPath(a, c)))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); host.resize(300, 500); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending()); var first = onFx(() -> ui.visibleOccurrences().getFirst());
                onFx(() -> { first.setZoom(1.3); return null; });
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(b, c)))).succeeded());
                onFx(() -> { ui.refresh(); assertSame(first, ui.visibleOccurrences().getFirst()); assertEquals(1.3, ui.visibleOccurrences().getFirst().getZoom(), 1e-9); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void viewportStateSurvivesMoreThanSixtyFourOtherVisitedNodes() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.point()); var nodes = new ArrayList<ViewLocation>();
            for (int i = 0; i < 66; i++) nodes.add(add(session, page, null, "node " + i));
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null, nodes.getFirst())))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, deterministic(new AtomicInteger())); host.resize(600, 500); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending()); onFx(() -> { ui.visibleOccurrences().getFirst().setZoom(1.25); return null; });
                for (var node : nodes.subList(1, nodes.size())) {
                    assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null, node)))).succeeded());
                    onFx(() -> { ui.refresh(); return null; });
                }
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetFocus(new OperationPath(null, nodes.getFirst())))).succeeded());
                onFx(() -> { ui.refresh(); assertEquals(1.25, ui.visibleOccurrences().getFirst().getZoom(), 1e-9); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void linkedPagesFollowTheirActualConnectionsRatherThanAllocationOrder() throws Exception {
        for (var direction : List.of(TopologyEdge.Direction.FORWARD, TopologyEdge.Direction.BOTH)) try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.linked(direction == TopologyEdge.Direction.BOTH));
            var a = add(session, page, null, "A"); var c = add(session, page, null, "C"); var b = add(session, page, null, "B");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), direction),
                    new VisualizationCommand.Connect(new OperationPath(null, b), new OperationPath(null, c), direction))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); host.resize(800, 500); host.refresh(); return host; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> { var bounds = ui.visibleOccurrences().getFirst().parts().getFirst().geometry().nodeBounds();
                assertTrue(bounds.get(a).x() < bounds.get(b).x() && bounds.get(b).x() < bounds.get(c).x(), "A→B→C must appear in connection order"); return null; }); }
            finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void temporaryLinkedCyclesAndBranchesUseTheRegisteredNativeGraphPath() throws Exception {
        for (boolean cycle : List.of(true, false)) try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.linked(false)); var nodes = new ArrayList<ViewLocation>();
            for (int i = 0; i < (cycle ? 3 : 4); i++) nodes.add(add(session, page, null, "node " + i));
            var commands = new ArrayList<VisualizationCommand>(); int[][] pairs = cycle ? new int[][]{{0, 1}, {1, 2}, {2, 0}} : new int[][]{{0, 1}, {0, 2}, {0, 3}};
            for (var pair : pairs) commands.add(new VisualizationCommand.Connect(new OperationPath(null, nodes.get(pair[0])), new OperationPath(null, nodes.get(pair[1]))));
            assertTrue(session.modify(new MutationBatch(commands, "temporary-linked-topology")).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); host.resize(800, 500); host.refresh(); return host; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> { var part = ui.visibleOccurrences().getFirst().parts().getFirst(); assertTrue(part.errorText().isEmpty());
                assertEquals("graphviz/neato-major", part.geometry().engine()); assertEquals(pairs.length, part.geometry().edgePaths().size()); return null; }); }
            finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void productionFieldPortsAndIndependentStrokeArrowColorsReachTheRealHost() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.composite("field-graph", PageType.Layout.STRESS, false));
            var a = session.reserveNodeId(page); session.addNode(new OperationPath(null, a), new ViewNode.Spec(ViewNode.Kind.POINT, "A", Map.of("next", "B", "other", "B")));
            var b = add(session, page, null, "B"); var style = new EdgeStyle("#7BB0DF", "#D6A64F", "next");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), TopologyEdge.Direction.FORWARD, "field:next", "west", style),
                    new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), TopologyEdge.Direction.NONE, "field:other", "north", EdgeStyle.DEFAULT))).succeeded());
            assertEquals(2, session.model().pages().get(page.pageId()).topology().size());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); new Scene(host, 800, 600); host.applyCss(); host.layout(); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> {
                    var part = ui.visibleOccurrences().getFirst().parts().getFirst(); assertTrue(part.errorText().isEmpty(), part.errorText());
                    var edge = session.model().pages().get(page.pageId()).topology().values().stream().filter(e -> e.aPort().equals("field:next")).findFirst().orElseThrow();
                    var expected = new ViewNodeRenderer().render(session.model().node(a), session.model().pages().get(page.pageId()).nodes(), VisualizationTheme.of(VisualizationTheme.Preset.BLUE), Set.of()).unit().ports().stream().filter(p -> p.ref().key().equals("field:next")).findFirst().orElseThrow();
                    var bounds = part.geometry().nodeBounds().get(a); var start = part.geometry().edgePaths().get(edge.id()).start();
                    assertEquals(bounds.x() + expected.anchor().x(), start.x(), 1e-7); assertEquals(bounds.y() + expected.anchor().y(), start.y(), 1e-7);
                    var graphics = ((Pane)part.getChildren().getLast()).getChildren().stream().filter(n -> n instanceof Group).map(n -> (Group)n).toList();
                    var colored = graphics.stream().filter(g -> g.getChildren().stream().anyMatch(n -> n instanceof Text t && t.getText().equals("next"))).findFirst().orElseThrow();
                    assertEquals(Color.web("#7BB0DF"), ((javafx.scene.shape.Path)colored.getChildren().getFirst()).getStroke());
                    assertEquals(Color.web("#D6A64F"), colored.getChildren().stream().filter(n -> n instanceof Polygon).map(n -> ((Polygon)n).getFill()).findFirst().orElseThrow());
                    assertEquals(Color.WHITE, colored.getChildren().stream().filter(n -> n instanceof Text).map(n -> ((Text)n).getFill()).findFirst().orElseThrow());
                    snapshot(ui, "field-ports-colored-edge.png", 800, 600); return null;
                });
                var before = onFx(() -> ui.visibleOccurrences().getFirst().parts().getFirst().geometry()); long runs = ui.diagnostics().engineRuns().getOrDefault(Kind.GRAPH, 0L);
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), TopologyEdge.Direction.FORWARD, "field:next", "west", new EdgeStyle("#FFFFFF", null, "changed")))).succeeded());
                onFx(() -> { ui.refresh(); return null; }); awaitFx(() -> !ui.isLayoutPending());
                assertEquals(runs, ui.diagnostics().engineRuns().getOrDefault(Kind.GRAPH, 0L));
                assertEquals(before.nodeBounds(), onFx(() -> ui.visibleOccurrences().getFirst().parts().getFirst().geometry().nodeBounds()));
                assertEquals(before.edgePaths(), onFx(() -> ui.visibleOccurrences().getFirst().parts().getFirst().geometry().edgePaths()));
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void productionFieldSelfReferencesHaveAnExteriorRouteAndVisibleArrow() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.composite("self-reference", PageType.Layout.STRESS, false)); var a = session.reserveNodeId(page);
            session.addNode(new OperationPath(null, a), new ViewNode.Spec(ViewNode.Kind.POINT, "A", Map.of("next", "A")));
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, a),
                    TopologyEdge.Direction.FORWARD, "field:next", "node", new EdgeStyle("#7BB0DF", "#D6A64F", "next")))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); host.resize(600, 500); host.refresh(); return host; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> { var part = ui.visibleOccurrences().getFirst().parts().getFirst();
                assertTrue(part.errorText().isEmpty(), part.errorText()); assertEquals("graphviz/neato-major", part.geometry().engine());
                var box = part.geometry().nodeBounds().get(a); var route = part.geometry().edgePaths().values().iterator().next();
                assertTrue(route.segments().stream().anyMatch(s -> s.end().x() > box.right() + 1));
                assertTrue(((Group)((Pane)part.getChildren().getLast()).getChildren().getFirst()).getChildren().stream().anyMatch(n -> n instanceof Polygon)); return null; }); }
            finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void edgeStyleIsARealSessionUpdateAndDoesNotInvalidateMeasuredGeometry() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.composite("colored-edge", PageType.Layout.STRESS, false));
            var a = add(session, page, null, "A"); var b = add(session, page, null, "B");
            var style = new EdgeStyle("#7BB0DF", "#D6A64F", "next");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), TopologyEdge.Direction.FORWARD, "node", "node", style))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); new Scene(host, 800, 600); host.applyCss(); host.layout(); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> {
                    var part = ui.visibleOccurrences().getFirst().parts().getFirst(); var graphic = (Group)((Pane)part.getChildren().getLast()).getChildren().getFirst();
                    assertEquals(Color.web("#7BB0DF"), ((javafx.scene.shape.Path)graphic.getChildren().getFirst()).getStroke());
                    assertEquals(Color.web("#D6A64F"), graphic.getChildren().stream().filter(n -> n instanceof Polygon).map(n -> ((Polygon)n).getFill()).findFirst().orElseThrow());
                    var label = graphic.getChildren().stream().filter(n -> n instanceof Text).map(n -> (Text)n).findFirst().orElseThrow();
                    assertEquals("next", label.getText()); assertEquals(Color.WHITE, label.getFill()); assertEquals(1, label.getOpacity()); return null;
                });
                var geometry = onFx(() -> ui.visibleOccurrences().getFirst().parts().getFirst().geometry()); long runs = ui.diagnostics().engineRuns().getOrDefault(Kind.GRAPH, 0L);
                String longLabel = "long field label ".repeat(40);
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b), TopologyEdge.Direction.FORWARD, "node", "node", new EdgeStyle("#FFFFFF", null, longLabel)))).succeeded());
                onFx(() -> { ui.refresh(); return null; }); awaitFx(() -> !ui.isLayoutPending());
                assertEquals(runs, ui.diagnostics().engineRuns().getOrDefault(Kind.GRAPH, 0L));
                assertEquals(geometry.nodeBounds(), onFx(() -> ui.visibleOccurrences().getFirst().parts().getFirst().geometry().nodeBounds()));
                assertEquals(geometry.edgePaths(), onFx(() -> ui.visibleOccurrences().getFirst().parts().getFirst().geometry().edgePaths()));
                onFx(() -> { var occurrence = ui.visibleOccurrences().getFirst(); var part = occurrence.parts().getFirst();
                    var graphic = (Group)((Pane)part.getChildren().getLast()).getChildren().getFirst();
                    var label = graphic.getChildren().stream().filter(n -> n instanceof Text).map(n -> (Text)n).findFirst().orElseThrow();
                    assertEquals(longLabel, label.getText());
                    var content = occurrence.scrollPane().getContent(); var inContent = content.sceneToLocal(label.localToScene(label.getBoundsInLocal()));
                    assertTrue(content.getBoundsInLocal().contains(inContent), "The scrollable content must include the complete long edge label"); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void longEdgeLabelsRemainScrollableWhenGeometryHasANegativeOrigin() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.linked(false)); var a = add(session, page, null, "A"); var b = add(session, page, null, "B");
            String label = "long edge label ".repeat(40);
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b),
                    TopologyEdge.Direction.FORWARD, "node", "node", new EdgeStyle("#FFFFFF", null, label)))).succeeded());
            LayoutEngine engine = (request, token) -> {
                var source = new ArrayLayout().layout(request, token); var bounds = new LinkedHashMap<ViewLocation, LayoutRequest.Rect>();
                source.nodeBounds().forEach((node, box) -> bounds.put(node, new LayoutRequest.Rect(box.x() - 500, box.y() - 100, box.width(), box.height())));
                var edges = new LinkedHashMap<Long, LayoutResult.EdgePath>();
                source.edgePaths().forEach((id, path) -> edges.put(id, new LayoutResult.EdgePath(new LayoutRequest.Point(path.start().x() - 500, path.start().y() - 100),
                        path.segments().stream().map(segment -> (LayoutResult.Segment)new LayoutResult.Line(new LayoutRequest.Point(segment.end().x() - 500, segment.end().y() - 100))).toList())));
                var box = source.contentBounds(); return new LayoutResult(source.stamp(), bounds, edges,
                        new LayoutRequest.Rect(box.x() - 500, box.y() - 100, box.width(), box.height()), source.engine(), source.engineVersion());
            };
            var engines = new EnumMap<Kind, LayoutEngine>(Kind.class); for (var kind : Kind.values()) engines.put(kind, engine);
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session, new LayoutCoordinator(engines, Platform::runLater));
                new Scene(host, 600, 500); host.applyCss(); host.layout(); host.refresh(); return host; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> {
                var occurrence = ui.visibleOccurrences().getFirst(); var part = occurrence.parts().getFirst(); assertTrue(part.geometry().contentBounds().x() < 0);
                var graphic = (Group)((Pane)part.getChildren().getLast()).getChildren().getFirst();
                var text = graphic.getChildren().stream().filter(n -> n instanceof Text).map(n -> (Text)n).findFirst().orElseThrow();
                var content = occurrence.scrollPane().getContent(); var inContent = content.sceneToLocal(text.localToScene(text.getBoundsInLocal()));
                assertTrue(text.getBoundsInLocal().getWidth() > part.geometry().contentBounds().width());
                assertTrue(content.getBoundsInLocal().contains(inContent), "Translation from a negative origin must retain the full long label in scroll bounds"); return null;
            }); } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void callerTreeRootAndChildOrderReachLiveAndHistoricalViews() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.tree(true)); var a = add(session, page, null, "A"); var b = add(session, page, null, "B"); var c = add(session, page, null, "C");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b)),
                    new VisualizationCommand.Connect(new OperationPath(null, b), new OperationPath(null, c)),
                    new VisualizationCommand.SetPageLayout(page, new PageLayoutHints(b, List.of(c, a))))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); host.resize(800, 600); host.refresh(); return host; });
            try {
                awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { var bounds = ui.visibleOccurrences().getFirst().parts().getFirst().geometry().nodeBounds();
                    assertTrue(bounds.get(b).y() < bounds.get(a).y() && bounds.get(b).y() < bounds.get(c).y(), "The caller's rotated root must be above its children");
                    assertTrue(bounds.get(c).x() < bounds.get(a).x(), "The explicit C,A child order must be visible"); return null; });
                assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.SetPageLayout(page, new PageLayoutHints(c, List.of(a))))).succeeded());
                var snapshot = session.snapshot(); onFx(() -> { ui.showSnapshot(snapshot); return null; }); awaitFx(() -> !ui.isLayoutPending());
                onFx(() -> { var bounds = ui.visibleOccurrences().getFirst().parts().getFirst().geometry().nodeBounds();
                    assertTrue(bounds.get(c).y() < bounds.get(b).y() && bounds.get(b).y() < bounds.get(a).y()); return null; });
            } finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
    @Test void anExplicitPartialLinkedOrderOverridesTheDefaultTopologyOrder() throws Exception {
        try (var session = new DefaultVisualizationSession()) {
            var page = session.initializeRoot(BuiltinPageTypes.linked(false)); var a = add(session, page, null, "A"); var c = add(session, page, null, "C"); var b = add(session, page, null, "B");
            assertTrue(session.modify(MutationBatch.of(new VisualizationCommand.Connect(new OperationPath(null, a), new OperationPath(null, b)),
                    new VisualizationCommand.Connect(new OperationPath(null, b), new OperationPath(null, c)),
                    new VisualizationCommand.SetPageLayout(page, new PageLayoutHints(null, List.of(b))))).succeeded());
            var ui = onFx(() -> { var host = new UiVisualizationContainer(session); host.resize(800, 600); host.refresh(); return host; });
            try { awaitFx(() -> !ui.isLayoutPending()); onFx(() -> { var bounds = ui.visibleOccurrences().getFirst().parts().getFirst().geometry().nodeBounds();
                assertTrue(bounds.get(b).x() < bounds.get(a).x() && bounds.get(a).x() < bounds.get(c).x()); return null; }); }
            finally { onFx(() -> { ui.close(); return null; }); }
        }
    }
}
