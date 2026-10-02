package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.List;
import java.util.Objects;

/** Source-only placement construction; allocation lookup and object lifetime require binding. */
public record PlacementNewExpr(MiniType type, List<Expression> placementArguments, InitializerSyntax initializer,
                         boolean global, SourceRange typeRange, SourceRange range) implements Expression {
    public PlacementNewExpr {
        Objects.requireNonNull(type, "type");
        placementArguments = List.copyOf(placementArguments);
        if (placementArguments.isEmpty()) throw new IllegalArgumentException("Placement arguments must not be empty");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(typeRange, "typeRange");
        Objects.requireNonNull(range, "range");
    }
}
