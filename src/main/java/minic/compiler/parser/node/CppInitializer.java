package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.List;
import java.util.Objects;

/** Source-only initialization grammar; binding must consume it before core expression analysis. */
public record CppInitializer(Kind kind, List<Expression> arguments, SourceRange range) implements Expression {
    public enum Kind { DEFAULT, COPY, DIRECT_PAREN, DIRECT_LIST, COPY_LIST }

    public CppInitializer {
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
}
