package minic.compiler.parser.node;

import minic.SourceRange;
import java.util.Objects;

/** Source-only range loop; the declaration is initialized from each iterator dereference. */
public record RangeForStmt(Statement declaration, Expression initializer,
                              Statement body, SourceRange range) implements Statement {
    public RangeForStmt {
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(range, "range");
        if (!(declaration instanceof Statement.VarDeclStmt || declaration instanceof StructuredBindingDecl))
            throw new IllegalArgumentException("Invalid range declaration");
        if (declaration instanceof Statement.VarDeclStmt variable && (variable.initializer()!=null || variable.staticStorage())
                || declaration instanceof StructuredBindingDecl binding && binding.initializer()!=null)
            throw new IllegalArgumentException("A range declaration has no separate initializer or static storage");
    }
}
