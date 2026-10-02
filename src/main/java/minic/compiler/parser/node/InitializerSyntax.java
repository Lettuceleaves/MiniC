package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.List;
import java.util.Objects;

/** Source-only initialization grammar; binding must consume it before core expression analysis. */
public record InitializerSyntax(Kind kind, List<Expression> arguments, SourceRange range) implements Expression {
    public enum Kind { DEFAULT, COPY, DIRECT_PAREN, DIRECT_LIST, COPY_LIST }

    public InitializerSyntax {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(range, "range");
        arguments = List.copyOf(arguments);
        if (kind == Kind.DEFAULT && !arguments.isEmpty()) {
            throw new IllegalArgumentException("Default initialization has no initializer arguments");
        }
        if (kind == Kind.COPY && arguments.size() != 1) {
            throw new IllegalArgumentException("Copy initialization requires one expression");
        }
    }

    /** The old execution view must share the source operands rather than copying or rebinding them. */
    public boolean isCompatibilityProjection(Expression expression) {
        // Only newly introduced direct forms need the current binder's explicit execution guard.
        if (expression == this) return kind == Kind.DIRECT_PAREN || kind == Kind.DIRECT_LIST;
        return switch (kind) {
            case DEFAULT -> expression == null;
            case COPY -> expression == arguments.getFirst();
            case DIRECT_PAREN -> arguments.size() == 1 && expression instanceof Expression.GroupingExpr group
                    && group.expression() == arguments.getFirst();
            case DIRECT_LIST, COPY_LIST -> expression instanceof Expression.AggregateInitExpr aggregate
                    && sameObjects(arguments, aggregate.values());
        };
    }

    private static boolean sameObjects(List<Expression> first, List<Expression> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) if (first.get(index) != second.get(index)) return false;
        return true;
    }
}
