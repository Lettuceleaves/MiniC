package minic.compiler.parser.node;

import minic.SourceRange;

import java.util.Objects;

/**
 * Internal lifetime region. The cleanup is bound in the surrounding scope and runs once
 * after an entered body exits normally or transfers control through return/break/continue.
 * The body has its own lexical scope. A return value is captured before cleanup executes.
 */
public record CleanupScopeStmt(Statement body, Expression cleanup, SourceRange range) implements Statement {
    public CleanupScopeStmt {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(range, "range");
    }
}
