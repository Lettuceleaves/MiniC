package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.Objects;

/** Source-only pack syntax, consumed by template substitution. */
public record CppPackExpansionExpr(Expression pattern, SourceRange range) implements Expression {
    public CppPackExpansionExpr { Objects.requireNonNull(pattern); Objects.requireNonNull(range); }
}
