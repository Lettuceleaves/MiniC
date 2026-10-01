package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.Objects;

/** Source-only functional conversion or object construction, preserving its written initializer. */
public record CppConstructionExpr(MiniType type, CppInitializer initializer,
                                  SourceRange typeRange, SourceRange range) implements Expression {
    public CppConstructionExpr {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(typeRange, "typeRange");
        Objects.requireNonNull(range, "range");
        if (initializer.kind() != CppInitializer.Kind.DIRECT_PAREN
                && initializer.kind() != CppInitializer.Kind.DIRECT_LIST) {
            throw new IllegalArgumentException("A construction expression requires direct initialization");
        }
    }
}
