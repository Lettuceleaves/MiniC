package craken.ui.pipeline;

import craken.compiler.CompilerApi;
import craken.compiler.execute.ExecutableRunner;
import craken.compiler.link.Linker;
import craken.ui.component.UiStyles;
import craken.ui.component.visualization.UiVisualizationContainer;
import craken.ui.display.DisplayArea;
import craken.ui.editor.EditorArea;
import craken.ui.editor.EditorFile;
import craken.ui.frame.AppFrame;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The actual right activity bar drives compilation and both real layout hosts. */
@Tag("visualization-adapter")
final class PipelineWorkbenchFlowTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void activityBarCompilesAllEightStagesRendersBothSidesAndRetainsHistoryUntilSourceChanges() throws Exception {
        String original = "int a=1,b=2; int square(int n) { return n * n; }\nint main() { return square(7) + a + b; }\n";
        Path source = Files.writeString(directory.resolve("workbench.mc"), original);
        try (Fixture ui = onFx(() -> new Fixture(directory, source))) {
            command(ui, () -> ui.icon().fire());
            settleExpansion(ui);
            onFx(() -> {
                assertEquals(0, ui.session().snapshot().stepCount());
                assertEquals(-1, ui.panel().visualization().stageIndex());
                assertTrue(ui.icon().isSelected());
                assertTrue(ui.display.getWidth() > 1_000, "the activity bar expands the real right display area");
                assertEquals(8, ui.stages().getItems().size());
                return null;
            });

            command(ui, () -> ui.step().fire());
            onFx(() -> {
                assertEquals(1, ui.session().snapshot().stepCount(), "one UI action consumes one compiler step");
                assertEquals(0, ui.panel().visualization().stageIndex());
                return null;
            });
            for (int stage = 0; stage < 8; stage++) {
                int expected = stage;
                command(ui, () -> {
                    assertEquals(expected, ui.session().snapshot().currentStageIndex());
                    assertFalse(ui.stage().isDisabled());
                    ui.stage().fire();
                });
                awaitLayout(ui);
                onFx(() -> {
                    var snapshot = ui.session().snapshot();
                    assertFalse(snapshot.failed(), () -> snapshot.stages().toString());
                    assertFalse(snapshot.visualizationPending(), snapshot.visualizationError());
                    assertEquals(expected, ui.panel().visualization().stageIndex());
                    assertTrue(ui.panel().visualization().lastStep());
                    assertTrue(ui.panel().visualization().succeeded());
                    assertEquals(PipelineSession.Status.COMPLETED, ui.stages().getItems().get(expected).status());
                    assertHealthyHosts(ui);
                    if (expected == 2) screenshot(ui, "pipeline-workbench-ast.png");
                    if (expected == 7) screenshot(ui, "pipeline-workbench-linked.png");
                    return null;
                });
            }

            PipelineSession completed = onFx(ui::session);
            for (var host : ui.hosts()) {
                assertEquals(0L, host.diagnostics().engineRuns()
                                .getOrDefault(craken.visualization.layout.LayoutRequest.Kind.GRAPH, 0L),
                        "the real pipeline must not fall back to a native graph layout");
                assertTrue(host.diagnostics().engineRuns()
                                .getOrDefault(craken.visualization.layout.LayoutRequest.Kind.TREE, 0L) > 0,
                        "AST pages use the in-process tree engine");
            }
            long finishedSteps = completed.snapshot().stepCount();
            onFx(() -> {
                assertTrue(completed.snapshot().succeeded());
                assertTrue(ui.step().isDisabled());
                assertTrue(ui.stage().isDisabled());
                var compiler = (CompilerApi) read(completed, "compiler");
                var artifact = compiler.stage(Linker.class).result().executableArtifactOptional().orElseThrow();
                assertTrue(Files.isRegularFile(artifact.path()));
                assertEquals(0, compiler.stage(ExecutableRunner.class).stepCount(), "the Pipeline stops after linking");
                return null;
            });
            command(ui, () -> ui.stages().getSelectionModel().select(2));
            awaitLayout(ui);
            var historical = onFx(() -> {
                assertEquals(2, ui.panel().visualization().stageIndex());
                assertEquals(8, completed.snapshot().currentStageIndex());
                assertEquals(finishedSteps, completed.snapshot().stepCount());
                assertHealthyHosts(ui);
                return ui.panel().visualization();
            });

            command(ui, () -> { ui.icon().fire(); ui.icon().fire(); });
            settleExpansion(ui);
            onFx(() -> {
                assertSame(completed, ui.session());
                assertSame(historical, ui.panel().visualization());
                assertEquals(2, ui.stages().getSelectionModel().getSelectedIndex());
                assertEquals(finishedSteps, ui.session().snapshot().stepCount());
                return null;
            });

            String replacement = "int main() { return 19; }\n";
            command(ui, () -> {
                ui.icon().fire();
                ui.file.editor().setSource(replacement);
                ui.icon().fire();
            });
            onFx(() -> {
                assertNotSame(completed, ui.session());
                assertEquals(0, ui.session().snapshot().stepCount());
                assertEquals(0, ui.session().snapshot().currentStageIndex());
                assertEquals(-1, ui.panel().visualization().stageIndex());
                assertTrue(ui.panel().visualization().input().pages().values().stream()
                        .flatMap(page -> page.nodes().values().stream())
                        .anyMatch(node -> replacement.stripTrailing().equals(node.content().fields().get("text"))));
                assertFalse(ui.step().isDisabled());
                return null;
            });
            assertEquals(original, Files.readString(source), "Pipeline must not save the edited buffer");
        }
    }

    @Test void realSemanticFailureStopsCommandsWhileCompletedHistoryRemainsInspectable() throws Exception {
        Path source = Files.writeString(directory.resolve("failure.mc"), "int main() { return missing_name; }\n");
        try (Fixture ui = onFx(() -> new Fixture(directory, source))) {
            command(ui, () -> ui.icon().fire());
            settleExpansion(ui);
            for (int stage = 0; stage < 4; stage++) command(ui, () -> ui.stage().fire());
            onFx(() -> {
                var snapshot = ui.session().snapshot();
                assertTrue(snapshot.failed());
                assertEquals(3, snapshot.currentStageIndex());
                assertEquals(PipelineSession.Status.FAILED, ui.stages().getItems().get(3).status());
                assertTrue(ui.status().getText().contains("missing_name"));
                assertTrue(ui.step().isDisabled());
                assertTrue(ui.stage().isDisabled());
                return null;
            });
            long failedAt = onFx(() -> ui.session().snapshot().stepCount());
            command(ui, () -> ui.stages().getSelectionModel().select(2));
            awaitLayout(ui);
            onFx(() -> {
                assertEquals(2, ui.panel().visualization().stageIndex());
                assertEquals(failedAt, ui.session().snapshot().stepCount());
                assertHealthyHosts(ui);
                ui.stages().getSelectionModel().select(7);
                assertEquals(2, ui.stages().getSelectionModel().getSelectedIndex());
                return null;
            });
        }
    }

    private static void command(Fixture ui, CheckedAction action) throws Exception {
        onFx(() -> { action.run(); return null; });
        // The serial worker queues publish before this barrier; the following FX call drains that publication.
        ui.worker.submit(() -> { }).get(60, TimeUnit.SECONDS);
        onFx(() -> { ui.layout(); return null; });
    }

    private static void settleExpansion(Fixture ui) throws Exception {
        var settled = onFx(() -> {
            var result = new CompletableFuture<Void>();
            var pause = new PauseTransition(Duration.millis(450));
            pause.setOnFinished(event -> { ui.layout(); result.complete(null); });
            pause.play();
            return result;
        });
        settled.get(10, TimeUnit.SECONDS);
    }

    private static void awaitLayout(Fixture ui) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            boolean settled = onFx(() -> {
                ui.layout();
                return ui.hosts().stream().noneMatch(UiVisualizationContainer::isLayoutPending);
            });
            if (settled) return;
            if (System.nanoTime() >= deadline) fail("both real Pipeline layouts must settle");
            Thread.sleep(10);
        }
    }

    private static void assertHealthyHosts(Fixture ui) {
        for (var host : ui.hosts()) {
            assertTrue(host.getWidth() > 100 && host.getHeight() > 100);
            assertFalse(host.visibleOccurrences().isEmpty());
            for (var page : host.visibleOccurrences()) {
                if (page.isSequenceList()) {
                    assertTrue(page.sequenceRowCount() > 0, "sequence pages must list at least one row");
                    continue;
                }
                assertFalse(page.parts().isEmpty(), "each published side must display actual content");
                for (var part : page.parts()) {
                    assertFalse(part.isPending());
                    assertEquals("", part.errorText());
                    assertNotNull(part.geometry());
                    assertFalse(part.geometry().nodeBounds().isEmpty());
                    assertTrue(part.geometry().contentBounds().width() > 0);
                    assertTrue(part.geometry().contentBounds().height() > 0);
                }
            }
        }
    }

    private static void screenshot(Fixture ui, String filename) throws Exception {
        Path screenshots = Path.of(System.getProperty("craken.visualization.screenshots", "build/verification/screenshots"));
        Files.createDirectories(screenshots);
        assertTrue(ImageIO.write(SwingFXUtils.fromFXImage(ui.scene.snapshot(null), null), "png", screenshots.resolve(filename).toFile()));
    }

    private static Object read(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        try { return result.get(30, TimeUnit.SECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            throw (Exception) failure.getCause();
        }
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }

    private static final class Fixture implements AutoCloseable {
        final EditorArea editors = new EditorArea();
        final DisplayArea display = new DisplayArea();
        final AppFrame frame = new AppFrame(editors, display, new Region(), editors.tabBar());
        final PipelineController controller;
        final EditorFile file;
        final ExecutorService worker;
        final Scene scene;

        Fixture(Path directory, Path source) throws Exception {
            controller = new PipelineController(editors, frame, display, directory);
            worker = (ExecutorService) read(controller, "worker");
            file = editors.openFile(source);
            scene = new Scene(frame, 1440, 900);
            UiStyles.install(scene);
            layout();
        }

        void layout() { frame.resize(1440, 900); frame.applyCss(); frame.layout(); }
        ToggleButton icon() { return (ToggleButton) frame.lookup("#app-pipeline-button"); }
        PipelinePanel panel() { return (PipelinePanel) display.getChildren().getFirst(); }
        Button step() { return (Button) panel().lookup("#pipeline-next-step"); }
        Button stage() { return (Button) panel().lookup("#pipeline-next-stage"); }
        Label status() { return (Label) panel().lookup("#pipeline-status"); }
        PipelineSession session() throws Exception { return (PipelineSession) read(read(controller, "active"), "session"); }
        @SuppressWarnings("unchecked") ListView<PipelineSession.StageView> stages() {
            return (ListView<PipelineSession.StageView>) panel().lookup("#pipeline-stage-list");
        }
        List<UiVisualizationContainer> hosts() {
            return ((SplitPane) panel().getCenter()).getItems().stream()
                    .map(pane -> (UiVisualizationContainer) ((BorderPane) pane).getCenter()).toList();
        }

        @Override public void close() throws Exception {
            onFx(() -> {
                try { controller.close(); }
                finally {
                    editors.setCloseDecisionHandler(file -> EditorArea.CloseChoice.DISCARD);
                    assertTrue(editors.closeAll());
                }
                return null;
            });
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
