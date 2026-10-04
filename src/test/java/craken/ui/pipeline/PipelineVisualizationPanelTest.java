package craken.ui.pipeline;

import craken.compiler.*;
import craken.ui.component.UiStyles;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineVisualizationPanelTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }
    @Test void bothPanesHostVisualizationWidgetsAndAFramePreservesTheDivider() throws Exception {
        PipelineSession session = session();
        onFx(() -> {
            PipelinePanel panel = new PipelinePanel();
            new Scene(panel, 1180, 640);
            var split = (SplitPane) panel.getCenter();
            split.setDividerPositions(0.32);
            panel.show(session.snapshot());
            assertEquals("craken.ui.component.visualization.UiVisualizationContainer",
                    ((BorderPane) split.getItems().get(0)).getCenter().getClass().getName());
            assertEquals("craken.ui.component.visualization.UiVisualizationContainer",
                    ((BorderPane) split.getItems().get(1)).getCenter().getClass().getName());
            assertEquals(0.32, split.getDividerPositions()[0], 0.005);
            assertSame(session.snapshot().visualization(), PipelinePanel.class.getMethod("visualization").invoke(panel));
            close(panel);
            return null;
        });
        session.close();
    }
    @Test void selectedHistoryIsSentBackToTheSessionAndUpdatesTheWholeFrame() throws Exception {
        PipelineSession session = session();
        session.nextStage(() -> false);
        var terminal = session.snapshot().visualization();
        session.nextStep(() -> false);
        onFx(() -> {
            PipelinePanel panel = new PipelinePanel();
            Scene scene = new Scene(panel, 1180, 640); UiStyles.install(scene);
            IntConsumer select = index -> { if (session.selectStage(index)) panel.show(session.snapshot()); };
            PipelinePanel.class.getMethod("setOnSelectStage", IntConsumer.class).invoke(panel, select);
            panel.show(session.snapshot());
            panel.resize(1180,640); panel.applyCss(); panel.layout();
            ((ListView<?>) panel.lookup("#pipeline-stage-list")).getSelectionModel().select(0);
            assertEquals(0, session.snapshot().selectedStageIndex());
            assertSame(terminal, PipelinePanel.class.getMethod("visualization").invoke(panel));
            close(panel); return null;
        });
        session.close();
    }
    @Test void projectionFailureKeepsRetryCommandsEnabledAndShowsDisplayError() throws Exception {
        PipelineSession session = session();
        var initial = session.snapshot();
        var pending = new PipelineSession.Snapshot(initial.stages(), initial.selectedStageIndex(), initial.currentStageIndex(),
                1, true, false, false, initial.visualization(), true, "display failure");
        onFx(() -> {
            PipelinePanel panel = new PipelinePanel(); new Scene(panel,1180,640);
            panel.show(pending); panel.applyCss(); panel.layout();
            assertTrue(((Label) panel.lookup("#pipeline-status")).getText().contains("展示"));
            assertTrue(((Label) panel.lookup("#pipeline-status")).getText().contains("display failure"));
            assertFalse(((Button) panel.lookup("#pipeline-next-step")).isDisabled());
            assertFalse(((Button) panel.lookup("#pipeline-next-stage")).isDisabled());
            close(panel); return null;
        });
        session.close();
    }
    private static PipelineSession session() { return new PipelineSession(new CompilerApi(new SourceFile("panel.mc", "int main(){return 7;}"))); }
    private static void close(PipelinePanel panel) throws Exception { PipelinePanel.class.getMethod("close").invoke(panel); }
    private static <T> T onFx(Callable<T> call) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(call.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        try { return result.get(10,TimeUnit.SECONDS); }
        catch (ExecutionException failure) { if (failure.getCause() instanceof Error error) throw error; throw failure; }
    }
}
