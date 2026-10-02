package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.Objects;

/** Source-only pack syntax, consumed by template substitution. */
public record SizeofPackExpr(String name, SourceRange range) implements Expression {
    public SizeofPackExpr { Objects.requireNonNull(name); Objects.requireNonNull(range); }
}
