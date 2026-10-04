package craken.visualization.adapter.pipeline;

import craken.compiler.*;
import craken.compiler.parser.ParserResult;
import craken.visualization.api.ViewLocation;
import craken.ui.pipeline.PipelineSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineAstFrameLifecycleTest {
    @Test
    void astSlotFailureRestoresPositionsAndRetryConsumesANewCandidateIdentity() throws Exception {
        CompilerApi compiler = new CompilerApi(new SourceFile("slot-rollback.mc", "int first(){return 1;} int main(){return first();}"));
        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicReference<ViewLocation> failedLocation = new AtomicReference<>();
        PipelineVisualizationSession visual = visualization(name -> {
            if (name.equals("POSITIONS") && enabled.getAndSet(false)) {
                var program = ((ParserResult) compiler.lastStepResult().orElseThrow().context()).program();
                failedLocation.set(program.functions().getFirst().visualSlots().nxt());
                throw new IllegalStateException("after slots");
            }
        });
        PipelineSession session = session(compiler, visual);
        session.nextStage(() -> false); session.nextStage(() -> false);
        Object previous = get(session.snapshot(), "visualization");
        enabled.set(true);
        session.nextStep(() -> false);
        long steps = compiler.stepCount();
        var program = ((ParserResult) compiler.lastStepResult().orElseThrow().context()).program();
        var function = program.functions().getFirst();
        assertNotNull(failedLocation.get());
        assertNull(function.visualSlots().nxt());
        assertSame(previous, get(session.snapshot(), "visualization"));
        session.nextStep(() -> false);
        assertEquals(steps, compiler.stepCount());
        assertTrue(function.visualSlots().nxt().containerId() > failedLocation.get().containerId());
        session.close();
    }

    @Test
    void obsoleteParserRootPositionsAreClearedAndTerminalHistoryKeepsItsFrozenValues() throws Exception {
        CompilerApi compiler = new CompilerApi(new SourceFile("history.mc", "int first(){return 1;} int main(){return first();}"));
        PipelineSession session = session(compiler, visualization(name -> { }));
        session.nextStage(() -> false); session.nextStage(() -> false);
        session.nextStep(() -> false);
        var old = ((ParserResult) compiler.lastStepResult().orElseThrow().context()).program();
        var function = old.functions().getFirst();
        ViewLocation stable = function.visualSlots().nxt();
        assertNotNull(stable);
        session.nextStep(() -> false);
        assertNull(old.visualSlots().nxt(), "a replaced preview root is no longer displayed");
        assertEquals(stable, function.visualSlots().nxt(), "existing AST object IDs survive a later preview");
        session.nextStage(() -> false);
        Object terminal = get(session.snapshot(), "visualization");
        session.nextStage(() -> false);
        session.nextStage(() -> false);
        assertNull(function.visualSlots().nxt(), "IR output positions do not masquerade as AST positions");
        session.selectStage(2);
        assertSame(terminal, get(session.snapshot(), "visualization"));
        assertNotNull(get(terminal, "output"));
        session.close();
    }
    private static PipelineVisualizationSession visualization(PipelineCommitProbe probe) {
        return new PipelineVisualizationSession(PipelineProjectionRegistry.standard(), probe);
    }
    private static PipelineSession session(CompilerApi compiler, PipelineVisualizationSession visual) {
        return new PipelineSession(compiler, visual);
    }
    private static Object get(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }
}
