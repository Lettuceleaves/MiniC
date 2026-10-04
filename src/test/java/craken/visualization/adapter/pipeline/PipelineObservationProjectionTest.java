package craken.visualization.adapter.pipeline;

import craken.compiler.*;
import craken.compiler.lexer.LexerResult;
import craken.compiler.preprocess.PreprocessResult;
import craken.SourceRange;
import craken.visualization.model.ViewNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineObservationProjectionTest {
    private static final String PACKAGE = "craken.visualization.adapter.pipeline.";

    @Test
    void adjacentHalfOpenRangesHighlightTheActualCellAndNotItsPredecessor() {
        var first = new SourceRange(1,0,1,1);
        var second = new SourceRange(1,1,1,2);
        var plan = PipelinePlans.sequence("Token", List.of(new PipelinePlans.Row("a", java.util.Map.of(), first),
                new PipelinePlans.Row("b", java.util.Map.of(), second))).highlightRange(second);
        assertEquals(java.util.Set.of(new ProjectionKey.Indexed("Token",1)), plan.highlights());
        assertEquals(new ProjectionKey.Indexed("Token",1), plan.focus());
    }

    @Test
    void observationRetainsExecutedStageAndTerminalContextAfterCompilerAdvances() {
        CompilerApi api = compiler();
        Stage executed = api.currentStage().orElseThrow();
        Stage.Result terminal = null;
        while (api.currentStageIndex() == 0) terminal = api.stepResult();
        Object observation = capture(executed, 0, terminal);
        assertSame(executed, get(observation, "executedStage"));
        assertEquals(0, get(observation, "stageIndex"));
        assertSame(terminal, get(observation, "result"));
        assertNotSame(api.currentStage().orElseThrow(), executed);
        assertSame(terminal.context(), get(observation, "context"));
        assertThrows(IllegalArgumentException.class, () -> capture(api.currentStage().orElseThrow(), 1, terminalResult(executed)));
    }

    @Test
    void preprocessProjectionUsesTheActualExpandedTextAndOriginalInput() {
        CompilerApi api = compiler();
        Stage stage = api.currentStage().orElseThrow();
        Stage.Result result;
        do { result = api.stepResult(); } while (!result.lastStep());
        Object projection = project(capture(stage, 0, result), null);
        Object output = get(projection, "output");
        List<?> nodes = nodes(output);
        assertEquals(((PreprocessResult) result.context()).sourceFile().content(), joinTextRows(nodes));
        assertEquals("#define ANSWER 42\nint main(){return ANSWER;}\n", joinTextRows(nodes(get(projection, "input"))));
        assertTrue(((ViewNode.Spec) get(nodes.getFirst(), "content")).kind() == ViewNode.Kind.ARRAY);
        assertThrows(UnsupportedOperationException.class, () -> nodes.clear());
    }

    @Test
    void tokenProjectionKeepsRepeatedTokensInDistinctCellsAndTheirRealFields() {
        CompilerApi api = compiler();
        while (api.currentStageIndex() == 0) api.step();
        Stage stage = api.currentStage().orElseThrow();
        Stage.Result result;
        do { result = api.stepResult(); } while (!result.lastStep());
        Object projection = project(capture(stage, 1, result), null);
        List<?> nodes = nodes(get(projection, "output"));
        var tokens = ((LexerResult) result.context()).tokens();
        assertEquals(tokens.size() + 1, nodes.size());
        assertEquals(tokens.size(), nodes.stream().skip(1).map(node -> get(node, "key")).distinct().count());
        for (int index = 0; index < tokens.size(); index++) {
            ViewNode.Spec spec = (ViewNode.Spec) get(nodes.get(index + 1), "content");
            assertEquals(tokens.get(index).type().name(), spec.fields().get("type"));
            assertEquals(tokens.get(index).lexeme(), spec.fields().get("lexeme"));
        }
    }

    @Test
    void capturedSourceValueDoesNotFollowTheLiveStageWhenItIsReinitialized() {
        CompilerApi api = compiler();
        craken.compiler.preprocess.Preprocessor stage = (craken.compiler.preprocess.Preprocessor) api.currentStage().orElseThrow();
        Stage.Result result = api.stepResult();
        Object observation = capture(stage, 0, result);
        stage.begin(new SourceFile("other.mc", "replacement"), craken.compiler.preprocess.Preprocessor.Options.defaults());
        Object projection = project(observation, null);
        assertEquals("#define ANSWER 42\nint main(){return ANSWER;}\n", joinTextRows(nodes(get(projection, "input"))));
        assertSame(result.context(), get(observation, "context"));
    }

    private static CompilerApi compiler() {
        CompilerApi api = new CompilerApi(new SourceFile("projection.mc", "#define ANSWER 42\nint main(){return ANSWER;}\n"));
        api.setCaptureLatestContext(Stage.class, true);
        return api;
    }

    private static Stage.Result terminalResult(Stage stage) { return stage.stageResult().orElseThrow(); }

    private static Object capture(Stage stage, int index, Stage.Result result) {
        return invoke(type("PipelineStepObservation"), null, "capture", new Class<?>[]{Stage.class, int.class, Stage.Result.class}, stage, index, result);
    }

    private static Object project(Object observation, Object input) {
        Class<?> registry = type("PipelineProjectionRegistry");
        Object standard = invoke(registry, null, "standard", new Class<?>[0]);
        return invoke(registry, standard, "project", new Class<?>[]{type("PipelineStepObservation"), type("PipelineProjectionPlan")}, observation, input);
    }

    @SuppressWarnings("unchecked")
    private static List<?> nodes(Object plan) { return (List<?>) get(plan, "nodes"); }

    private static String joinTextRows(List<?> nodes) {
        return nodes.stream().skip(1).map(node -> ((ViewNode.Spec) get(node, "content")).fields().get("text"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static Object get(Object target, String name) { return invoke(target.getClass(), target, name, new Class<?>[0]); }

    private static Class<?> type(String name) {
        try { return Class.forName(PACKAGE + name); }
        catch (ClassNotFoundException failure) { throw new AssertionError("missing projection contract: " + name, failure); }
    }

    private static Object invoke(Class<?> owner, Object target, String method, Class<?>[] types, Object... args) {
        try { return owner.getMethod(method, types).invoke(target, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
