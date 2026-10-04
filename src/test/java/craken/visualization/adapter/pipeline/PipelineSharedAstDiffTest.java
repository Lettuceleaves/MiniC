package craken.visualization.adapter.pipeline;

import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.ui.pipeline.PipelineSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineSharedAstDiffTest {
    @TempDir Path directory;

    @Test void unchangedSharedAstReferencesKeepTheirRelationIdentityAcrossSemanticSteps() throws Exception {
        var compiler = new CompilerApi(new SourceFile("shared-diff.mc",
                "int a=1,b=2; int main(){return a+b;}"), directory);
        try (var session = new PipelineSession(compiler)) {
            for (int stage = 0; stage < 3; stage++) {
                session.nextStage(() -> false);
                assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
                assertFalse(session.snapshot().failed(), session.snapshot().stages().toString());
            }
            assertEquals(3, compiler.currentStageIndex());
            session.nextStep(() -> false);
            assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
            var first = session.snapshot().visualization().input();
            var before = first.pages().get(first.root().pageId());
            assertTrue(before.topology().values().stream().anyMatch(edge ->
                    edge.direction() == craken.visualization.model.relation.TopologyEdge.Direction.BACKWARD),
                    "The real shared AST has a later parent referring to an earlier allocated node");
            session.nextStep(() -> false);
            assertFalse(session.snapshot().visualizationPending(), session.snapshot().visualizationError());
            var second = session.snapshot().visualization().input();
            var after = second.pages().get(second.root().pageId());
            assertEquals(first.containerId(), second.containerId());
            assertEquals(before.nodes().keySet(), after.nodes().keySet(), "Semantic analysis does not replace its source AST");
            assertEquals(before.topology(), after.topology(),
                    "Changing the current AST highlight must not delete and recreate unchanged shared references");
        }
    }
}
