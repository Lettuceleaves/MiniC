package craken.ui.pipeline;

import craken.compiler.*;
import craken.ui.display.DisplayArea;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.component.visualization.UiVisualizationContainer;
import craken.ui.frame.AppFrame;
import craken.visualization.adapter.pipeline.*;
import craken.visualization.layout.*;
import craken.visualization.mutation.DefaultVisualizationSession;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.control.SplitPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic in-flight lifecycle checks use the real first preprocessing step. */
@Tag("visualization-adapter")
final class PipelineControllerLifecycleTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void switchingSourceCancelsInFlightProjectionReleasesBothOldSlotsAndRejectsItsLateFrame() throws Exception {
        try (Fixture ui = fixture()) {
            BlockingStep blocked = installBlockingFirstStep(ui);
            onFx(() -> { ui.step().fire(); return null; });
            assertTrue(blocked.entered.await(10, TimeUnit.SECONDS));
            String replacement = "int main(){return 27;}";
            onFx(() -> { ui.file.editor().setSource(replacement); ui.controller.open(); return null; });
            ui.worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
            onFx(() -> {
                assertEquals(0, blocked.visualization.activeContainerCount());
                assertNotSame(blocked.session, read(read(ui.controller, "active"), "session"));
                var frame = ui.panel().visualization();
                assertNotNull(frame); assertEquals(-1, frame.stageIndex());
                assertTrue(frame.input().pages().values().stream().flatMap(page -> page.nodes().values().stream())
                        .anyMatch(node -> replacement.equals(node.content().fields().get("text"))));
                assertFalse(ui.step().isDisabled());
                return null;
            });
            assertTrue(blocked.interrupted.await(10, TimeUnit.SECONDS));
        }
    }

    @Test void restartCancelsAnInFlightStepAndReleasesTheReplacedSession() throws Exception {
        try (Fixture ui = fixture()) {
            BlockingStep blocked = installBlockingFirstStep(ui);
            onFx(() -> { ui.step().fire(); return null; });
            assertTrue(blocked.entered.await(10, TimeUnit.SECONDS));
            String replacement = "int main(){return 9;}";
            onFx(() -> { ui.file.editor().setSource(replacement); ui.restart().fire(); return null; });
            ui.worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
            onFx(() -> {
                assertEquals(0, blocked.visualization.activeContainerCount());
                assertNotSame(blocked.session, read(read(ui.controller, "active"), "session"));
                var frame = ui.panel().visualization();
                assertNotNull(frame);
                assertEquals(-1, frame.stageIndex());
                assertTrue(frame.input().pages().values().stream().flatMap(page -> page.nodes().values().stream())
                        .anyMatch(node -> replacement.equals(node.content().fields().get("text"))));
                assertFalse(ui.step().isDisabled());
                return null;
            });
            assertTrue(blocked.interrupted.await(10, TimeUnit.SECONDS));
        }
    }

    @Test void closingDuringARealStepStopsTheWorkerAndClosesBothHostsAndSessions() throws Exception {
        try (Fixture ui = fixture()) {
            BlockingStep blocked = installBlockingFirstStep(ui);
            UiVisualizationContainer[] hosts = onFx(() -> {
                var split = (SplitPane) ui.panel().getCenter();
                return split.getItems().stream().map(pane -> (UiVisualizationContainer) ((BorderPane) pane).getCenter())
                        .toArray(UiVisualizationContainer[]::new);
            });
            onFx(() -> { ui.step().fire(); return null; });
            assertTrue(blocked.entered.await(10, TimeUnit.SECONDS));
            onFx(() -> { ui.controller.close(); return null; });
            assertTrue(ui.worker.awaitTermination(10, TimeUnit.SECONDS));
            assertTrue(blocked.interrupted.await(10, TimeUnit.SECONDS));
            onFx(() -> {
                assertEquals(0, blocked.visualization.activeContainerCount());
                assertNull(read(ui.controller, "active"));
                for (var host : hosts) { assertTrue(host.isClosed()); assertTrue(host.visibleOccurrences().isEmpty()); }
                return null;
            });
        }
    }

    @Test void panelClosesTheOtherHostEvenWhenTheFirstLayoutResourceThrows() throws Exception {
        onFx(() -> {
            var panel = new PipelinePanel();
            var broken = replaceInputWithFailingHost(panel);
            var output = host(panel, 1);
            try {
                assertThrows(IllegalStateException.class, panel::close);
                assertTrue(broken.isClosed());
                assertTrue(output.isClosed(), "one failed cleanup must not skip the other host");
            } finally { broken.close(); broken.session().close(); output.close(); }
            return null;
        });
    }

    @Test void controllerStillReleasesCoreAndStopsWorkerAfterHostCloseFails() throws Exception {
        Fixture ui = fixture();
        var activeSession = onFx(() -> (PipelineSession) read(read(ui.controller, "active"), "session"));
        var visual = (PipelineVisualizationSession) read(activeSession, "visualization");
        var broken = onFx(() -> replaceInputWithFailingHost(ui.panel()));
        var output = onFx(() -> host(ui.panel(), 1));
        try {
            onFx(() -> { assertThrows(IllegalStateException.class, ui.controller::close); return null; });
            boolean stopped = ui.worker.awaitTermination(2, TimeUnit.SECONDS);
            onFx(() -> {
                assertAll(() -> assertTrue(output.isClosed(), "output host must close"),
                        () -> assertTrue(stopped, "worker must shut down despite FX cleanup failure"),
                        () -> assertEquals(0, visual.activeContainerCount(), "queued core cleanup must run"));
                return null;
            });
        } finally {
            ui.worker.shutdownNow(); activeSession.close();
            onFx(() -> { broken.close(); broken.session().close(); output.close(); return null; });
            ui.close();
        }
    }

    private static UiVisualizationContainer replaceInputWithFailingHost(PipelinePanel panel) throws Exception {
        host(panel, 0).close();
        var coordinator = new LayoutCoordinator(Map.of(LayoutRequest.Kind.POINT, new ArrayLayout()), Platform::runLater, 2,
                () -> { throw new IllegalStateException("test layout close failure"); });
        var broken = new UiVisualizationContainer(new DefaultVisualizationSession(), coordinator);
        Field input = PipelinePanel.class.getDeclaredField("inputView"); input.setAccessible(true); input.set(panel, broken);
        ((BorderPane) ((SplitPane) panel.getCenter()).getItems().getFirst()).setCenter(broken);
        return broken;
    }
    private static UiVisualizationContainer host(PipelinePanel panel, int index) {
        return (UiVisualizationContainer) ((BorderPane) ((SplitPane) panel.getCenter()).getItems().get(index)).getCenter();
    }

    private BlockingStep installBlockingFirstStep(Fixture ui) throws Exception {
        var blocked = new BlockingStep();
        var compiler = new CompilerApi(new SourceFile(ui.file.path().toString(), "int main(){return 1;}"), directory.resolve("controlled"));
        blocked.visualization = new PipelineVisualizationSession(PipelineProjectionRegistry.standard(), name -> {
            if (!name.equals("OUTPUT")) return;
            blocked.entered.countDown();
            try { blocked.release.await(); }
            catch (InterruptedException cancelled) {
                blocked.interrupted.countDown(); Thread.currentThread().interrupt();
                throw new IllegalStateException("cancelled in-flight projection", cancelled);
            }
        });
        blocked.session = new PipelineSession(compiler, blocked.visualization);
        onFx(() -> {
            Object request = read(ui.controller, "active");
            ((PipelineSession) read(request, "session")).close();
            Field sessionField = request.getClass().getDeclaredField("session"); sessionField.setAccessible(true);
            sessionField.set(request, blocked.session);
            ui.panel().show(blocked.session.snapshot());
            return null;
        });
        return blocked;
    }
    private Fixture fixture() throws Exception {
        Path source = Files.writeString(directory.resolve("source.mc"), "int main(){return 1;}");
        Fixture fixture = onFx(() -> new Fixture(directory, source));
        fixture.worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
        onFx(() -> { assertNotNull(fixture.panel().visualization()); return null; });
        return fixture;
    }
    private static Object read(Object instance, String field) throws Exception {
        Field target = instance.getClass().getDeclaredField(field); target.setAccessible(true); return target.get(instance);
    }
    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        try { return result.get(10, TimeUnit.SECONDS); }
        catch (ExecutionException failure) { if (failure.getCause() instanceof Error error) throw error; throw failure; }
    }
    private static final class BlockingStep {
        final CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1), release = new CountDownLatch(1);
        PipelineVisualizationSession visualization;
        PipelineSession session;
    }
    private static final class Fixture implements AutoCloseable {
        final EditorArea editors = new EditorArea();
        final DisplayArea display = new DisplayArea();
        final AppFrame frame = new AppFrame(editors, display, new Region(), editors.tabBar());
        final PipelineController controller;
        final EditorFile file;
        final ExecutorService worker;
        Fixture(Path directory, Path source) throws Exception {
            controller = new PipelineController(editors, frame, display, directory);
            worker = (ExecutorService) read(controller, "worker");
            file = editors.openFile(source); controller.open();
        }
        PipelinePanel panel() { return (PipelinePanel) display.getChildren().getFirst(); }
        Button step() { return (Button) panel().lookup("#pipeline-next-step"); }
        Button restart() { return (Button) panel().lookup("#pipeline-restart"); }
        @Override public void close() throws Exception {
            onFx(() -> { controller.close(); editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD); assertTrue(editors.closeAll()); return null; });
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
