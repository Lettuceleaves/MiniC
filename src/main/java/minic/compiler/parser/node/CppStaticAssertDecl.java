package minic.compiler.parser.node;
import minic.SourceRange;
import java.util.Objects;
/** Source assertion, discarded only after constant evaluation. */
public record CppStaticAssertDecl(Expression condition,Expression.StringLiteralExpr message,SourceRange range)
        implements Declaration,Statement,Declaration.CppMember {
    public CppStaticAssertDecl{Objects.requireNonNull(condition);Objects.requireNonNull(range);}
}
