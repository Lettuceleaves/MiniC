package minic.compiler.parser.node;

import minic.SourceRange;
import minic.compiler.type.MiniType;
import java.util.Objects;

/** Source-only explicit destructor invocation, never an ordinary member field access. */
public record CppDestructorCallExpr(Expression receiver, MiniType ownerType, QualifiedName destructorName,
                                    boolean viaPointer, SourceRange nameRange, SourceRange range) implements Expression {
    public CppDestructorCallExpr {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(destructorName, "destructorName");
        Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(range, "range");
        // ownerType is the surrounding-scope candidate, if any. Binding must also look up
        // the injected class name of receiver; N::A* p can legally call p->~A() outside N.
    }
}
