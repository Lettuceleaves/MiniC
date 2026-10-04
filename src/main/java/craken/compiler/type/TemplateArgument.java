package craken.compiler.type;

import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.Expression.TemplateValueExpr;

import java.util.Map;
import java.util.Objects;

/** Source template arguments distinguish types, dependent expressions, and canonical integral values. */
public sealed interface TemplateArgument permits TemplateArgument.Type, TemplateArgument.Value, TemplateArgument.Integral, TemplateArgument.Expansion {
    /** Source expansion; flattened during specialization, never part of a concrete cache key. */
    record Expansion(TemplateArgument pattern) implements TemplateArgument {
        public Expansion { Objects.requireNonNull(pattern); }
        @Override public String toString() { return pattern + "..."; }
    }
    record Type(CrakenType type) implements TemplateArgument {
        public Type { Objects.requireNonNull(type); }
        @Override public String toString() { return type.toString(); }
    }
    record Value(Expression expression) implements TemplateArgument {
        public Value { Objects.requireNonNull(expression); }
        @Override public String toString() { return expression instanceof TemplateValueExpr value ? value.parameter().toString() : "<constant-expression>"; }
    }
    record Integral(long value, CrakenType type) implements TemplateArgument {
        public Integral { Objects.requireNonNull(type); if (!type.isIntegerScalar()) throw new IllegalArgumentException("integral template value needs an integer type"); }
        @Override public String toString() { return type.isUnsignedIntegerScalar() ? Long.toUnsignedString(value) : Long.toString(value); }
    }
    default TemplateArgument substitute(Map<CrakenType.TemplateParameterType, CrakenType> types,
                                        Map<CrakenType.TemplateParameterType, Expression> values) {
        return switch (this) {
            case Expansion e -> new Expansion(e.pattern().substitute(types,values));
            case Type t -> new Type(t.type().substituteTemplateParameters(types,values));
            case Value v -> new Value(TemplateValues.substitute(v.expression(), types, values));
            case Integral i -> i;
        };
    }
}
