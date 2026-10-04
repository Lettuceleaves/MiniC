package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.parser.node.*;
import craken.compiler.semantic.*;
import craken.visualization.adapter.pipeline.*;
import craken.visualization.model.ViewNode;
import craken.visualization.model.relation.TopologyEdge.Direction;
import java.util.*;

public final class SemanticProjector implements PipelineStageProjector<SemanticResult> {
    @Override public Class<? extends Stage> stageType() { return SemanticAnalyzer.class; }
    @Override public Class<SemanticResult> contextType() { return SemanticResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, SemanticResult context, PipelineProjectionPlan input) {
        AstNode current = observation.currentAstNode();
        AstNode coreCurrent = context.sourceToCore().getOrDefault(current, current);
        var ast = AstPageProjector.project(context.program(), coreCurrent);
        var nodes = new ArrayList<PipelineProjectionPlan.Node>();
        for (var node : ast.nodes()) {
            var fields = new LinkedHashMap<>(node.content().fields());
            if (node.key() instanceof ProjectionKey.Ast key && key.node() instanceof Expression expression
                    && context.expressionTypes().containsKey(expression)) fields.put("type", context.expressionTypes().get(expression).toString());
            nodes.add(new PipelineProjectionPlan.Node(node.key(), new ViewNode.Spec(node.content().kind(), node.content().label(), fields), node.range()));
        }
        var edges = new ArrayList<>(ast.edges());
        addScope(context.scopeSnapshot(), "0", new ProjectionKey.Ast(context.program()), nodes, edges);
        var output = AstPresentationMapper.withSourceAliases(new PipelineProjectionPlan(ast.pageType(), nodes, edges,
                ast.composition(), ast.highlights(), ast.focus(), ast.aliases()), context.sourceToCore());
        var source = input == null ? AstPageProjector.project(context.sourceProgram(), null) : input;
        AstNode sourceCurrent = current;
        if (!source.aliases().containsKey(sourceCurrent)) {
            for (var candidate : source.nodes()) if (candidate.key() instanceof ProjectionKey.Ast key
                    && context.sourceToCore().get(key.node()) == coreCurrent) { sourceCurrent = key.node(); break; }
        }
        source = AstPresentationMapper.highlight(source, sourceCurrent);
        return new StageProjection(source, output);
    }
    private static void addScope(SemanticResult.ScopeSnapshot scope, String path, ProjectionKey parent,
            List<PipelineProjectionPlan.Node> nodes, List<PipelineProjectionPlan.Edge> edges) {
        var key = new ProjectionKey.Named("scope:" + path);
        nodes.add(new PipelineProjectionPlan.Node(key, new ViewNode.Spec(ViewNode.Kind.POINT, "作用域 " + path,
                Map.of("scope", path)), scope.range()));
        edges.add(new PipelineProjectionPlan.Edge(parent, key, Direction.FORWARD));
        for (int index = 0; index < scope.symbols().size(); index++) {
            var symbol = scope.symbols().get(index);
            var child = new ProjectionKey.Indexed("symbol:" + path, index);
            nodes.add(new PipelineProjectionPlan.Node(child, new ViewNode.Spec(ViewNode.Kind.POINT, symbol.name(),
                    Map.of("symbol", symbol.name(), "kind", symbol.kind().name(), "type", symbol.type().toString())), symbol.declarationRange()));
            edges.add(new PipelineProjectionPlan.Edge(key, child, Direction.FORWARD));
        }
        for (int index = 0; index < scope.children().size(); index++) addScope(scope.children().get(index), path + "." + index, key, nodes, edges);
    }
}
