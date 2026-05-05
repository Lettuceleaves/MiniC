package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * do while 循环语句 AST 节点。
 *
 * @param body 循环体语句
 * @param condition 条件表达式
 * @param range 语句覆盖的源码范围
 */
public record DoWhileStmt(Statement body, Expression condition, SourceRange range) implements Statement {
    /**
     * 创建 do while 循环语句。
     *
     * @param body 循环体语句
     * @param condition 条件表达式
     * @param range 语句源码范围
     */
    public DoWhileStmt {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(range, "range");
    }
}
