package minic.compiler.ast.stmt;

import minic.compiler.ast.expr.Expression;
import minic.source.SourceRange;

import java.util.Objects;

/**
 * while 循环语句 AST 节点。
 *
 * @param condition 条件表达式
 * @param body 循环体语句
 * @param range 语句覆盖的源码范围
 */
public record WhileStmt(Expression condition, Statement body, SourceRange range) implements Statement {
    /**
     * 创建 while 循环语句。
     *
     * @param condition 条件表达式
     * @param body 循环体语句
     * @param range 语句源码范围
     */
    public WhileStmt {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(range, "range");
    }
}
