package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.Objects;

/** A structured type-id qualifier, including template arguments, on a value member name. */
public record CppTypeMemberExpr(MiniType ownerType, String memberName, SourceRange nameRange,
                                SourceRange range) implements Expression {
    public CppTypeMemberExpr {
        Objects.requireNonNull(ownerType, "ownerType");
        Objects.requireNonNull(memberName, "memberName");
        Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(range, "range");
    }
}
