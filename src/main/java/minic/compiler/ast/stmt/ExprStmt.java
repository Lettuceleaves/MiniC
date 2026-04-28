package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * 表达式语句 AST 节点。
 *
 * @param expression 表达式
 * @param range 表达式语句覆盖的源码范围
 */
public record ExprStmt(Expression expression, SourceRange range) implements Statement {
    /**
     * 创建表达式语句节点。
     *
     * @param expression 表达式
     * @param range 表达式语句覆盖的源码范围
     */
    public ExprStmt {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(range, "range");
    }
}
