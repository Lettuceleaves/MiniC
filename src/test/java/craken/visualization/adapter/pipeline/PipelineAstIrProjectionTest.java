package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.compiler.*;
import craken.compiler.ir.IrResult;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.parser.ParserResult;
import craken.compiler.parser.node.*;
import craken.compiler.semantic.SemanticResult;
import craken.visualization.model.ViewNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("visualization-adapter")
final class PipelineAstIrProjectionTest {
    @Test
    void parserProjectsParametersAndUsesObjectIdentityWithoutTouchingVisualSlots() {
        CompilerApi api = compiler();
        while (api.currentStageIndex() < 2) api.step();
        Stage executed = api.currentStage().orElseThrow();
        Stage.Result result;
        do { result = api.stepResult(); } while (!result.lastStep());
        var projection = PipelineProjectionRegistry.standard().project(PipelineStepObservation.capture(executed, 2, result), null);
        var program = ((ParserResult) result.context()).program();
        var function = program.functions().getFirst();
        assertTrue(projection.output().nodes().stream().anyMatch(n -> n.key().equals(new ProjectionKey.Ast(function.parameters().getFirst()))));
        assertTrue(projection.output().nodes().stream().anyMatch(n -> n.key().equals(new ProjectionKey.Ast(program))));
        assertEquals(PipelinePageTypes.ast().key(), projection.output().pageType().key());
        assertNull(function.visualSlots().nxt());
        assertEquals(projection.output().nodes().size(), projection.output().nodes().stream().map(PipelineProjectionPlan.Node::key).distinct().count());
    }

    @Test
    void equalAstValuesRemainTwoDisplayNodesAndAliasedChildrenAreVisitedOnce() throws Exception {
        var range = new SourceRange(1, 0, 1, 1);
        var first = new Expression.IntegerLiteralExpr(1, "1", range);
        var second = new Expression.IntegerLiteralExpr(1, "1", range);
        assertEquals(first, second);
        var root = new Expression.BinaryExpr(first, TokenType.PLUS, second, range);
        var type = Class.forName("craken.visualization.adapter.pipeline.AstPageProjector");
        var plan = (PipelineProjectionPlan) type.getMethod("project", AstNode.class, AstNode.class).invoke(null, root, second);
        assertEquals(3, plan.nodes().size());
        assertEquals(2, plan.edges().size());
        assertEquals(Set.of(new ProjectionKey.Ast(second)), plan.highlights());
        var shared = new Expression.BinaryExpr(first, TokenType.PLUS, first, range);
        var sharedPlan = (PipelineProjectionPlan) type.getMethod("project", AstNode.class, AstNode.class).invoke(null, shared, first);
        assertEquals(2, sharedPlan.nodes().size());
        assertEquals(1, sharedPlan.edges().size());
        assertNull(first.visualSlots().pre());
    }

    @Test
    void semanticProjectionShowsCoreTypesAndFrozenScopeSymbols() {
        CompilerApi api = compiler();
        while (api.currentStageIndex() < 3) api.step();
        Stage executed = api.currentStage().orElseThrow();
        Stage.Result result;
        do { result = api.stepResult(); } while (!result.lastStep());
        var context = (SemanticResult) result.context();
        var projection = PipelineProjectionRegistry.standard().project(PipelineStepObservation.capture(executed, 3, result), null);
        assertTrue(projection.output().nodes().stream().anyMatch(n -> n.key().equals(new ProjectionKey.Ast(context.program()))));
        assertTrue(projection.input().nodes().stream().anyMatch(n -> n.key().equals(new ProjectionKey.Ast(context.sourceProgram()))));
        for (var entry : context.expressionTypes().entrySet()) {
            var node = projection.output().nodes().stream().filter(n -> n.key().equals(new ProjectionKey.Ast(entry.getKey()))).findFirst();
            if (node.isPresent()) assertEquals(entry.getValue().toString(), node.orElseThrow().content().fields().get("type"));
        }
        assertTrue(projection.output().nodes().stream().anyMatch(n -> n.content().fields().containsKey("symbol")));
        assertEquals(PipelinePageTypes.ast().key(), projection.output().pageType().key());
    }

    @Test
    void irUsesCoreInputIdentityAndProjectsActualInstructionRowsWithoutAstOutputSlots() {
        CompilerApi api = compiler();
        while (api.currentStageIndex() < 3) api.step();
        Stage semanticStage = api.currentStage().orElseThrow();
        Stage.Result semanticEnd;
        do { semanticEnd = api.stepResult(); } while (!semanticEnd.lastStep());
        var semantic = (SemanticResult) semanticEnd.context();
        var registry = PipelineProjectionRegistry.standard();
        var semanticPlan = registry.project(PipelineStepObservation.capture(semanticStage, 3, semanticEnd), null).output();
        Stage irStage = api.currentStage().orElseThrow();
        Stage.Result irStep;
        do { irStep = api.stepResult(); } while (((IrResult) irStep.context()).currentAstNode() == null
                || ((IrResult) irStep.context()).functions().stream().flatMap(f -> f.blocks().stream()).allMatch(b -> b.instructions().isEmpty()));
        var context = (IrResult) irStep.context();
        var projection = registry.project(PipelineStepObservation.capture(irStage, 4, irStep), semanticPlan);
        AstNode core = semantic.sourceToCore().getOrDefault(context.currentAstNode(), context.currentAstNode());
        assertTrue(projection.input().highlights().contains(new ProjectionKey.Ast(core)));
        long instructions = context.functions().stream().flatMap(f -> f.blocks().stream()).mapToLong(b -> b.instructions().size()).sum();
        assertTrue(instructions > 0);
        assertEquals(instructions, projection.output().nodes().stream().filter(n -> n.content().fields().containsKey("instruction")).count());
        assertEquals(ViewNode.Kind.ARRAY, projection.output().nodes().getFirst().content().kind());
        assertTrue(projection.output().nodes().stream().noneMatch(n -> n.key() instanceof ProjectionKey.Ast));
        assertNull(core.visualSlots().nxt());
    }

    private static CompilerApi compiler() {
        CompilerApi api = new CompilerApi(new SourceFile("ast-projection.mc", "int plus(int x){return x+1;} int main(){return plus(2);}"));
        api.setCaptureLatestContext(Stage.class, true);
        return api;
    }
}
