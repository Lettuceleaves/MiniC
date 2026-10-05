package craken.ui.pipeline;

import craken.ui.display.DisplayArea;
import craken.ui.editor.EditorArea;
import craken.ui.frame.AppFrame;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real compiler snapshots with a blocked worker make in-flight UI interaction deterministic. */
@Tag("visualization-adapter")
final class PipelineControllerInteractionTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void reviewingAStageLocksCommandsUntilTheSelectedSnapshotHasArrived() throws Exception {
        try (Fixture ui = fixture()) {
            advanceStage(ui);
            advanceStage(ui);
            try (BlockedWorker blocked = new BlockedWorker(ui.worker)) {
                onFx(() -> {
                    assertEquals(2, ui.stages().getSelectionModel().getSelectedIndex());
                    ui.stages().getSelectionModel().select(0);
                    assertAll(
                            () -> assertTrue(ui.step().isDisabled(), "a queued history request must visibly lock next step"),
                            () -> assertTrue(ui.stage().isDisabled(), "a queued history request must visibly lock next stage"),
                            () -> assertTrue(ui.stages().isDisabled(), "another stage cannot be selected while its frame is loading"));
                    return null;
                });
            }
            ui.awaitPublish();
            onFx(() -> {
                assertEquals(0, ui.stages().getSelectionModel().getSelectedIndex());
                assertEquals(0, ui.panel().visualization().stageIndex());
                assertEquals(2, ui.session().snapshot().currentStageIndex(), "review must not move compiler execution");
                assertFalse(ui.step().isDisabled());
                assertFalse(ui.stage().isDisabled());
                assertFalse(ui.stages().isDisabled());
                return null;
            });
        }
    }

    @Test void anInFlightStageAdvanceCannotOptimisticallySelectAHistoryFrame() throws Exception {
        try (Fixture ui = fixture()) {
            advanceStage(ui);
            advanceStage(ui);
            try (BlockedWorker blocked = new BlockedWorker(ui.worker)) {
                onFx(() -> {
                    ui.stage().fire();
                    ui.stages().getSelectionModel().select(0);
                    assertAll(
                            () -> assertTrue(ui.stages().isDisabled(), "compile commands must lock the stage selector"),
                            () -> assertEquals(2, ui.stages().getSelectionModel().getSelectedIndex(),
                                    "a rejected request must not change the selected stage while the displayed frame stays unchanged"));
                    return null;
                });
            }
            ui.awaitPublish();
            onFx(() -> {
                assertEquals(3, ui.session().snapshot().currentStageIndex());
                assertEquals(3, ui.stages().getSelectionModel().getSelectedIndex());
                assertFalse(ui.stages().isDisabled());
                return null;
            });
        }
    }

    private Fixture fixture() throws Exception {
        Path source = Files.writeString(directory.resolve("interaction.mc"), "int main(){return 42;}");
        Fixture fixture = onFx(() -> new Fixture(directory, source));
        fixture.awaitPublish();
        return fixture;
    }

    private static void advanceStage(Fixture ui) throws Exception {
        onFx(() -> { ui.stage().fire(); return null; });
        ui.awaitPublish();
    }

    private static Object read(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        try { return result.get(20, TimeUnit.SECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private static final class BlockedWorker implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);

        BlockedWorker(ExecutorService worker) throws Exception {
            var entered = new CountDownLatch(1);
            worker.submit(() -> {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS), "worker must reach the deterministic gate");
        }

        @Override public void close() { release.countDown(); }
    }

    private static final class Fixture implements AutoCloseable {
        private final EditorArea editors = new EditorArea();
        private final DisplayArea display = new DisplayArea();
        private final AppFrame frame = new AppFrame(editors, display, new Region(), editors.tabBar());
        private final PipelineController controller;
        private final ExecutorService worker;

        Fixture(Path directory, Path source) throws Exception {
            controller = new PipelineController(editors, frame, display, directory);
            worker = (ExecutorService) read(controller, "worker");
            editors.openFile(source);
            controller.open();
        }

        PipelinePanel panel() { return (PipelinePanel) display.getChildren().getFirst(); }
        Button step() { return (Button) panel().lookup("#pipeline-next-step"); }
        Button stage() { return (Button) panel().lookup("#pipeline-next-stage"); }
        PipelineSession session() throws Exception { return (PipelineSession) read(read(controller, "active"), "session"); }

        @SuppressWarnings("unchecked")
        ListView<PipelineSession.StageView> stages() {
            return (ListView<PipelineSession.StageView>) panel().lookup("#pipeline-stage-list");
        }

        void awaitPublish() throws Exception {
            worker.submit(() -> { }).get(20, TimeUnit.SECONDS);
            onFx(() -> null);
        }

        @Override public void close() throws Exception {
            onFx(() -> {
                controller.close();
                editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
                assertTrue(editors.closeAll());
                return null;
            });
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
