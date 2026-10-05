package craken.visualization.adapter.pipeline;

import craken.compiler.*;
import craken.compiler.link.LinkResult;
import craken.compiler.parser.ParserResult;
import craken.compiler.parser.node.AstNode;
import craken.compiler.semantic.SemanticResult;
import craken.ui.component.visualization.UiVisualizationContainer;
import craken.ui.pipeline.PipelineSession;
import craken.visualization.api.ViewLocation;
import craken.visualization.model.ViewNode;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineEndToEndVisualizationTest {
    private static final String SOURCE = "int a=1,b=2; int main(){return a+b;}";
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException alreadyStarted) { Platform.runLater(start); }
        ready.get(10, TimeUnit.SECONDS);
    }

    @Test void eightRealStagesKeepTwoSlotsTransferAstPositionsAndReviewHistoryWithoutExecution() throws Exception {
        var compiler = new CompilerApi(new SourceFile("e2e.mc", SOURCE), directory);
        var visualization = new PipelineVisualizationSession();
        var history = new LinkedHashMap<Integer, PipelineVisualizationFrame>();
        Map<AstNode, ViewLocation> parsed = Map.of(), core = Map.of();
        try (var session = new PipelineSession(compiler, visualization)) {
            PipelineVisualizationFrame previous = session.snapshot().visualization();
            int remaining = 2_000;
            while (session.snapshot().canAdvance()) {
                assertTrue(--remaining > 0, "bounded real compilation");
                int executedIndex = compiler.currentStageIndex();
                Stage executed = compiler.currentStage().orElseThrow();
                session.nextStep(() -> false);
                assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
                assertFalse(session.snapshot().failed(), session.snapshot().stages().toString());
                var frame = session.snapshot().visualization();
                assertEquals(executedIndex, frame.stageIndex());
                assertEquals(executed.getClass().getSimpleName(), frame.stageName());
                assertEquals(2, visualization.activeContainerCount());
                assertNotEquals(frame.input().containerId(), frame.output().containerId());
                if (previous.stageIndex() >= 0 && previous.stageIndex() != executedIndex) {
                    assertEquals(previous.output().containerId(), frame.input().containerId());
                    if (executedIndex == 3) assertTransferred(parsed, false);
                    if (executedIndex == 4) assertTransferred(core, true);
                    if (executedIndex == 5) core.keySet().forEach(node -> assertEquals(
                            new craken.compiler.parser.node.AstVisualSlots.PositionPair(null, null), node.visualSlots().snapshot()));
                }
                if (frame.lastStep()) {
                    assertTrue(frame.succeeded()); history.put(executedIndex, frame);
                    if (executedIndex == 2) parsed = outputPositions(((ParserResult) compiler.lastStepResult().orElseThrow().context()).program());
                    if (executedIndex == 3) core = outputPositions(((SemanticResult) compiler.lastStepResult().orElseThrow().context()).program());
                }
                previous = frame;
            }
            assertTrue(session.snapshot().succeeded());
            assertEquals(8, history.size());
            assertEquals(8, compiler.currentStageIndex());
            assertTrue(compiler.currentStage().orElseThrow().stageResult().isEmpty(), "native execution remains untouched");
            for (int index = 0; index < 8; index++) {
                var nodes = history.get(index).output().pages().values().stream().flatMap(page -> page.nodes().values().stream()).toList();
                if (index == 2 || index == 3) assertTrue(nodes.stream().anyMatch(node -> node.content().kind() == ViewNode.Kind.TREE));
                else {
                    String field = Map.of(0, "text", 1, "lexeme", 4, "instruction", 5, "assembly", 6, "coffBytes", 7, "peBytes").get(index);
                    assertTrue(nodes.stream().anyMatch(node -> node.content().fields().containsKey(field)), "actual stage output " + index);
                }
            }
            var linked = (LinkResult) compiler.lastStepResult().orElseThrow().context();
            byte[] image = Files.readAllBytes(linked.executableArtifact().path());
            assertEquals('M', image[0]); assertEquals('Z', image[1]);
            long steps = compiler.stepCount();
            for (int index = 7; index >= 0; index--) {
                assertTrue(session.selectStage(index));
                assertSame(history.get(index), session.snapshot().visualization());
                assertEquals(steps, compiler.stepCount());
                assertEquals(2, visualization.activeContainerCount());
            }
            assertTrue(compiler.stages().stream().allMatch(stage -> !stage.resultRecordingEnabled()
                    && stage.stepResults().stream().allMatch(Stage.Result::lastStep)), "history retains only the established terminal compiler results");
        }
        assertEquals(0, visualization.activeContainerCount());
        parsed.keySet().forEach(node -> assertNull(node.visualSlots().pre()));
        core.keySet().forEach(node -> assertNull(node.visualSlots().nxt()));
    }

    @Test void realSharedAstKeepsEveryObjectAndReferenceAndRendersThroughTheHost() throws Exception {
        var compiler = new CompilerApi(new SourceFile("shared.mc", SOURCE), directory);
        try (var session = new PipelineSession(compiler)) {
            session.nextStage(() -> false); session.nextStage(() -> false); session.nextStage(() -> false);
            assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
            assertEquals(2, session.snapshot().visualization().stageIndex());
            var snapshot = session.snapshot().visualization().output();
            var page = snapshot.pages().get(snapshot.root().pageId());
            assertEquals(page.nodes().size() - 1, page.topology().size(),
                    "a shared AST object keeps one canonical tree position instead of a diamond");
            var program = ((ParserResult) compiler.lastStepResult().orElseThrow().context()).program();
            assertEquals(AstPageProjector.project(program, null).nodes().size(), page.nodes().size());
            var ready = new CompletableFuture<Void>();
            UiVisualizationContainer host = onFx(() -> {
                var view = new UiVisualizationContainer(); new Scene(view, 1000, 640);
                view.layoutPendingProperty().addListener((property, old, pending) -> { if (!pending) ready.complete(null); });
                view.showSnapshot(snapshot); view.resize(1000, 640); view.applyCss(); view.layout();
                if (!view.isLayoutPending()) ready.complete(null);
                return view;
            });
            try {
                ready.get(15, TimeUnit.SECONDS);
                onFx(() -> {
                    assertFalse(host.isLayoutPending());
                    assertEquals(0L, host.diagnostics().engineRuns()
                                    .getOrDefault(craken.visualization.layout.LayoutRequest.Kind.GRAPH, 0L),
                            "AST tree pages must not spawn the native Graphviz layout");
                    assertTrue(host.diagnostics().engineRuns()
                                    .getOrDefault(craken.visualization.layout.LayoutRequest.Kind.TREE, 0L) > 0,
                            "AST pages use the in-process tree engine");
                    var occurrence = host.visibleOccurrences().getFirst();
                    assertEquals(1, occurrence.parts().size());
                    var part = occurrence.parts().getFirst();
                    assertEquals("", part.errorText()); assertNotNull(part.geometry());
                    assertEquals(page.nodes().size(), occurrence.nodeViews().size());
                    assertEquals(page.topology().size(), part.geometry().edgePaths().size());
                    return null;
                });
            } finally { onFx(() -> { host.close(); return null; }); }
        }
    }

    private static Map<AstNode, ViewLocation> outputPositions(AstNode root) {
        var positions = new IdentityHashMap<AstNode, ViewLocation>();
        AstPageProjector.project(root, null).nodes().forEach(node -> {
            if (node.key() instanceof ProjectionKey.Ast key) {
                assertNotNull(key.node().visualSlots().nxt()); positions.put(key.node(), key.node().visualSlots().nxt());
            }
        });
        return positions;
    }
    private static void assertTransferred(Map<AstNode, ViewLocation> positions, boolean outputHasNoAst) {
        assertFalse(positions.isEmpty());
        positions.forEach((node, previous) -> {
            assertEquals(previous, node.visualSlots().pre());
            if (outputHasNoAst) assertNull(node.visualSlots().nxt());
        });
    }
    private static <T> T onFx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        try { return result.get(10, TimeUnit.SECONDS); }
        catch (ExecutionException failure) { if (failure.getCause() instanceof Error error) throw error; throw failure; }
    }
}
