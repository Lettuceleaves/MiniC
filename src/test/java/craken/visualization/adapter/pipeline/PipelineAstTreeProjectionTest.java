package craken.visualization.adapter.pipeline;

import craken.SourceRange;
import craken.compiler.CompilerApi;
import craken.compiler.SourceFile;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.parser.node.Expression;
import craken.ui.pipeline.PipelineSession;
import craken.visualization.snapshot.VisualizationSnapshot;
import craken.visualization.style.ColorSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** AST pages are trees: one parent per object, and pipeline cards use the approved IDE palette. */
@Tag("visualization-adapter")
final class PipelineAstTreeProjectionTest {
    @TempDir Path directory;

    @Test void crossParentSharedReferencesKeepEveryObjectButOnlyOneTreeEdge() {
        var range = new SourceRange(1, 0, 1, 1);
        var leaf = new Expression.IntegerLiteralExpr(1, "1", range);
        var other = new Expression.IntegerLiteralExpr(2, "2", range);
        var lhs = new Expression.BinaryExpr(leaf, TokenType.PLUS, other, range);
        var rhs = new Expression.BinaryExpr(other, TokenType.PLUS, leaf, range);
        var root = new Expression.BinaryExpr(lhs, TokenType.PLUS, rhs, range);

        var plan = AstPageProjector.project(root, leaf);

        assertEquals(5, plan.nodes().size(), "every distinct AST object stays visible");
        assertEquals(4, plan.edges().size(), "a tree has exactly one parent edge per non-root object");
        assertTreeEdges(plan.edges().stream().map(PipelineProjectionPlan.Edge::child).toList());
    }

    @Test void realParserAndSemanticStagesOnlyPublishTreePages() throws Exception {
        var compiler = new CompilerApi(new SourceFile("tree.mc", "int a=1,b=2; int main(){return a+b;}"), directory);
        try (var session = new PipelineSession(compiler)) {
            int astPages = 0;
            while (session.snapshot().canAdvance()) {
                session.nextStage(() -> false);
                var frame = session.snapshot().visualization();
                for (var snapshot : List.of(frame.input(), frame.output())) {
                    for (var page : snapshot.pages().values()) {
                        if (!page.type().key().equals(PipelinePageTypes.ast().key()) || page.nodes().isEmpty()) continue;
                        astPages++;
                        assertEquals(page.nodes().size() - 1, page.topology().size(),
                                "every published AST page keeps exactly one parent edge per non-root object");
                        assertConnectedTree(page);
                    }
                }
            }
            assertTrue(astPages > 0, "the real compiler exercises AST pages");
        }
    }

    @Test void pipelineCardsCarryTheApprovedIdeCardColor() {
        var sequence = PipelinePlans.sequence("Token", List.of(
                new PipelinePlans.Row("INT int", Map.of("lexeme", "int", "type", "INT"), null)));
        assertTrue(sequence.nodes().stream().allMatch(node -> node.content().color() == ColorSpec.Preset.NEUTRAL),
                "sequence cards use the neutral IDE surface");
        var range = new SourceRange(1, 0, 1, 1);
        var leaf = new Expression.IntegerLiteralExpr(1, "1", range);
        var plan = AstPageProjector.project(leaf, leaf);
        assertTrue(plan.nodes().stream().allMatch(node -> node.content().color() == ColorSpec.Preset.NEUTRAL),
                "AST cards use the neutral IDE surface");
    }

    private static void assertTreeEdges(List<ProjectionKey> children) {
        var seen = new HashSet<ProjectionKey>();
        for (var child : children) assertTrue(seen.add(child), "a tree gives every object at most one parent");
    }

    private static void assertConnectedTree(VisualizationSnapshot.PageState page) {
        var adjacency = new HashMap<Long, Set<Long>>();
        var incoming = new HashMap<Long, Integer>();
        page.nodes().keySet().forEach(id -> adjacency.put(id, new LinkedHashSet<>()));
        page.topology().values().forEach(edge -> {
            // The core stores the lower node ID first and reverses the arrow as needed.
            boolean reversed = edge.direction() == craken.visualization.model.relation.TopologyEdge.Direction.BACKWARD;
            long parent = reversed ? edge.b().nodeId() : edge.a().nodeId();
            long child = reversed ? edge.a().nodeId() : edge.b().nodeId();
            incoming.merge(child, 1, Integer::sum);
            adjacency.get(parent).add(child);
            adjacency.get(child).add(parent);
        });
        assertTrue(incoming.values().stream().noneMatch(count -> count > 1), "no node may have several parents");
        var roots = page.nodes().keySet().stream().filter(id -> incoming.getOrDefault(id, 0) == 0).toList();
        assertEquals(1, roots.size(), "a tree has exactly one parentless root");
        var visited = new HashSet<Long>();
        var pending = new ArrayDeque<Long>();
        pending.add(roots.getFirst());
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (visited.add(current)) pending.addAll(adjacency.get(current));
        }
        assertEquals(page.nodes().keySet(), visited, "the AST page must stay connected from its root");
    }
}
