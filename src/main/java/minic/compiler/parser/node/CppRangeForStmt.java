package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.Objects;

/** Source-only range loop; the declaration is initialized from each iterator dereference. */
public record CppRangeForStmt(Statement.VarDeclStmt declaration, Expression initializer,
                              Statement body, SourceRange range) implements Statement {
    public CppRangeForStmt {
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(range, "range");
        if (declaration.initializer() != null || declaration.staticStorage())
            throw new IllegalArgumentException("A range declaration has no separate initializer or static storage");
    }
}
