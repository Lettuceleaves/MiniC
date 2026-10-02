package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.Objects;

/** Source-only functional conversion or object construction, preserving its written initializer. */
public record ConstructionExpr(MiniType type, InitializerSyntax initializer,
                                  SourceRange typeRange, SourceRange range) implements Expression {
    public ConstructionExpr {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(typeRange, "typeRange");
        Objects.requireNonNull(range, "range");
        if (initializer.kind() != InitializerSyntax.Kind.DIRECT_PAREN
                && initializer.kind() != InitializerSyntax.Kind.DIRECT_LIST) {
            throw new IllegalArgumentException("A construction expression requires direct initialization");
        }
    }
}
