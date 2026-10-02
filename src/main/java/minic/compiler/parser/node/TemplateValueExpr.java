package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.Objects;

/** Bound identity of a non-type template parameter; it never reaches core lowering. */
public record TemplateValueExpr(MiniType.TemplateParameterType parameter, MiniType valueType,
                                   SourceRange range) implements Expression {
    public TemplateValueExpr { Objects.requireNonNull(parameter); Objects.requireNonNull(valueType); Objects.requireNonNull(range); }
}
