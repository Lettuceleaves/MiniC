package minic.compiler.parser.node;

import minic.SourceRange;

import java.util.Objects;

/**
 * Internal expression boundary: capture the value (or construct directly at its supplied
 * destination), execute a void cleanup, then yield that value. Reference results are already
 * normalized to pointer values. Registration and lifetime-extension policy belong to the binder.
 */
public record CleanupExpr(Expression value, Expression cleanup, SourceRange range) implements Expression {
    public CleanupExpr {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(range, "range");
    }
}
