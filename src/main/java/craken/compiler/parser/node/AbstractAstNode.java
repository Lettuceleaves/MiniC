package craken.compiler.parser.node;

/** Mutable presentation metadata is separate from immutable language semantics. */
public abstract class AbstractAstNode implements AstNode {
    private final AstVisualSlots visualSlots = new AstVisualSlots();

    @Override
    public final AstVisualSlots visualSlots() { return visualSlots; }
}
