package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.List;
import java.util.Objects;

/** Unevaluated compiler type query. Types remain source types until template substitution/binding. */
public record CppTypeQueryExpr(Kind kind, List<TypeArgument> arguments,
                               SourceRange nameRange, SourceRange range) implements Expression {
    public enum Kind {
        CONSTRUCTIBLE("__is_constructible"), ASSIGNABLE("__is_assignable"), CONVERTIBLE("__is_convertible");
        private final String spelling;
        Kind(String spelling) { this.spelling = spelling; }
        public String spelling() { return spelling; }
        public static Kind fromSpelling(String text) {
            for (Kind kind : values()) if (kind.spelling.equals(text)) return kind;
            return null;
        }
        public boolean acceptsArity(int count) { return this == CONSTRUCTIBLE ? count >= 1 : count == 2; }
    }

    public record TypeArgument(MiniType type, boolean packExpansion, SourceRange range) implements AstNode {
        public TypeArgument { Objects.requireNonNull(type); Objects.requireNonNull(range); }
        public TypeArgument(MiniType type, SourceRange range) { this(type, false, range); }
    }

    public CppTypeQueryExpr {
        Objects.requireNonNull(kind); Objects.requireNonNull(nameRange); Objects.requireNonNull(range);
        arguments = List.copyOf(arguments);
        if (arguments.stream().noneMatch(TypeArgument::packExpansion) && !kind.acceptsArity(arguments.size())) throw new IllegalArgumentException("Invalid type-query arity");
    }
}
