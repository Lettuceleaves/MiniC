package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.Objects;

/** Source-only pack syntax, consumed by template substitution. */
public record CppSizeofPackExpr(String name, SourceRange range) implements Expression {
    public CppSizeofPackExpr { Objects.requireNonNull(name); Objects.requireNonNull(range); }
}
