package craken.visualization.adapter.pipeline;

import craken.compiler.parser.node.*;
import craken.visualization.api.ViewLocation;
import java.util.*;

/** Computes and applies reversible slot patches after both candidate model projections succeed. */
public final class PipelinePositionTransition {
    private final Map<AstNode, AstVisualSlots.PositionPair> before = new IdentityHashMap<>();
    private final Map<AstNode, AstVisualSlots.PositionPair> after = new IdentityHashMap<>();
    private final Set<AstNode> active = Collections.newSetFromMap(new IdentityHashMap<>());
    PipelinePositionTransition(Set<AstNode> known, Map<ProjectionKey, ViewLocation> input, Map<ProjectionKey, ViewLocation> output) {
        var all = Collections.newSetFromMap(new IdentityHashMap<AstNode, Boolean>());
        all.addAll(known);
        input.keySet().forEach(key -> { if (key instanceof ProjectionKey.Ast ast) active.add(ast.node()); });
        output.keySet().forEach(key -> { if (key instanceof ProjectionKey.Ast ast) active.add(ast.node()); });
        all.addAll(active);
        for (AstNode node : all) {
            before.put(node, node.visualSlots().snapshot());
            after.put(node, new AstVisualSlots.PositionPair(input.get(new ProjectionKey.Ast(node)), output.get(new ProjectionKey.Ast(node))));
        }
    }
    void apply() { after.forEach((node, value) -> node.visualSlots().restore(value)); }
    void restore() { before.forEach((node, value) -> node.visualSlots().restore(value)); }
    Set<AstNode> nodes() { return active; }
}
