package craken.visualization.adapter.pipeline;

import craken.compiler.parser.node.AstNode;
import java.util.Objects;

/** Sequence cells use positions; AST projections use object identity. */
public sealed interface ProjectionKey {
    record Named(String value) implements ProjectionKey { public Named { Objects.requireNonNull(value); } }
    record Indexed(String group, int index) implements ProjectionKey {
        public Indexed { Objects.requireNonNull(group); if (index < 0) throw new IllegalArgumentException("negative index"); }
    }
    final class Ast implements ProjectionKey {
        private final AstNode node;
        public Ast(AstNode node) { this.node = Objects.requireNonNull(node); }
        public AstNode node() { return node; }
        @Override public boolean equals(Object other) { return other instanceof Ast key && key.node == node; }
        @Override public int hashCode() { return System.identityHashCode(node); }
    }
}
