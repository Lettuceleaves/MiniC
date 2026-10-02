package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.TemplateArgument;
import java.util.List;
import java.util.Objects;

/** Explicit template arguments on a function or member reference. */
public record TemplateIdExpr(Expression target,List<TemplateArgument> arguments,SourceRange range) implements Expression {
    public TemplateIdExpr {Objects.requireNonNull(target);arguments=List.copyOf(arguments);Objects.requireNonNull(range);}
}
