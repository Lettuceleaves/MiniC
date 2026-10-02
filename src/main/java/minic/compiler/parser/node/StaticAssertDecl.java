package minic.compiler.parser.node;
import minic.SourceRange;
import java.util.Objects;
/** Source assertion, discarded only after constant evaluation. */
public record StaticAssertDecl(Expression condition,Expression.StringLiteralExpr message,SourceRange range)
        implements Declaration,Statement,Declaration.RecordMember {
    public StaticAssertDecl{Objects.requireNonNull(condition);Objects.requireNonNull(range);}
}
